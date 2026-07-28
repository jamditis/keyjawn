"""Bounded ownership for subprocess trees launched by the worker."""

from __future__ import annotations

import asyncio
import ctypes
import logging
import os
import select
import signal
import subprocess
import sys
import threading
from collections.abc import Awaitable, Callable
from ctypes import wintypes
from dataclasses import dataclass
from pathlib import Path
from typing import Any

log = logging.getLogger(__name__)

_WINDOWS_NEW_PROCESS_GROUP = getattr(
    subprocess,
    "CREATE_NEW_PROCESS_GROUP",
    0x00000200,
)
_WINDOWS_CREATE_SUSPENDED = getattr(
    subprocess,
    "CREATE_SUSPENDED",
    0x00000004,
)
_LINUX_FORCE_SIGNAL = getattr(signal, "SIGUSR1", None)
_SUPERVISOR = Path(__file__).with_name("subprocess_supervisor.py")


def _spawn_group_kwargs(platform: str = sys.platform) -> dict[str, Any]:
    """Return platform settings that isolate a spawned process tree."""
    if platform == "win32":
        return {
            "creationflags": (_WINDOWS_NEW_PROCESS_GROUP | _WINDOWS_CREATE_SUSPENDED)
        }
    return {"start_new_session": True}


def _windows_taskkill_command(pid: int, *, force: bool) -> tuple[str, ...]:
    command = ("taskkill", "/PID", str(pid), "/T")
    return command + (("/F",) if force else ())


class _ThreadEntry32(ctypes.Structure):
    _fields_ = [
        ("dwSize", wintypes.DWORD),
        ("cntUsage", wintypes.DWORD),
        ("th32ThreadID", wintypes.DWORD),
        ("th32OwnerProcessID", wintypes.DWORD),
        ("tpBasePri", ctypes.c_long),
        ("tpDeltaPri", ctypes.c_long),
        ("dwFlags", wintypes.DWORD),
    ]


def _resume_windows_process(pid: int) -> None:
    """Resume a process only after its Job Object owns the suspended root."""
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    snapshot_flag = 0x00000004
    thread_suspend_resume = 0x0002
    invalid_handle = ctypes.c_void_p(-1).value

    kernel32.CreateToolhelp32Snapshot.argtypes = [
        wintypes.DWORD,
        wintypes.DWORD,
    ]
    kernel32.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
    kernel32.Thread32First.argtypes = [
        wintypes.HANDLE,
        ctypes.POINTER(_ThreadEntry32),
    ]
    kernel32.Thread32First.restype = wintypes.BOOL
    kernel32.Thread32Next.argtypes = [
        wintypes.HANDLE,
        ctypes.POINTER(_ThreadEntry32),
    ]
    kernel32.Thread32Next.restype = wintypes.BOOL
    kernel32.OpenThread.argtypes = [
        wintypes.DWORD,
        wintypes.BOOL,
        wintypes.DWORD,
    ]
    kernel32.OpenThread.restype = wintypes.HANDLE
    kernel32.ResumeThread.argtypes = [wintypes.HANDLE]
    kernel32.ResumeThread.restype = wintypes.DWORD
    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel32.CloseHandle.restype = wintypes.BOOL

    snapshot = kernel32.CreateToolhelp32Snapshot(snapshot_flag, 0)
    if snapshot == invalid_handle:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        entry = _ThreadEntry32()
        entry.dwSize = ctypes.sizeof(entry)
        has_entry = kernel32.Thread32First(snapshot, ctypes.byref(entry))
        while has_entry:
            if entry.th32OwnerProcessID == pid:
                thread = kernel32.OpenThread(
                    thread_suspend_resume,
                    False,
                    entry.th32ThreadID,
                )
                if not thread:
                    raise ctypes.WinError(ctypes.get_last_error())
                try:
                    if kernel32.ResumeThread(thread) == 0xFFFFFFFF:
                        raise ctypes.WinError(ctypes.get_last_error())
                finally:
                    kernel32.CloseHandle(thread)
                return
            has_entry = kernel32.Thread32Next(snapshot, ctypes.byref(entry))
    finally:
        kernel32.CloseHandle(snapshot)
    raise ProcessLookupError(f"no thread found for suspended process {pid}")


class _JobObjectBasicLimitInformation(ctypes.Structure):
    _fields_ = [
        ("PerProcessUserTimeLimit", ctypes.c_longlong),
        ("PerJobUserTimeLimit", ctypes.c_longlong),
        ("LimitFlags", wintypes.DWORD),
        ("MinimumWorkingSetSize", ctypes.c_size_t),
        ("MaximumWorkingSetSize", ctypes.c_size_t),
        ("ActiveProcessLimit", wintypes.DWORD),
        ("Affinity", ctypes.c_size_t),
        ("PriorityClass", wintypes.DWORD),
        ("SchedulingClass", wintypes.DWORD),
    ]


class _IoCounters(ctypes.Structure):
    _fields_ = [
        ("ReadOperationCount", ctypes.c_ulonglong),
        ("WriteOperationCount", ctypes.c_ulonglong),
        ("OtherOperationCount", ctypes.c_ulonglong),
        ("ReadTransferCount", ctypes.c_ulonglong),
        ("WriteTransferCount", ctypes.c_ulonglong),
        ("OtherTransferCount", ctypes.c_ulonglong),
    ]


class _JobObjectExtendedLimitInformation(ctypes.Structure):
    _fields_ = [
        ("BasicLimitInformation", _JobObjectBasicLimitInformation),
        ("IoInfo", _IoCounters),
        ("ProcessMemoryLimit", ctypes.c_size_t),
        ("JobMemoryLimit", ctypes.c_size_t),
        ("PeakProcessMemoryUsed", ctypes.c_size_t),
        ("PeakJobMemoryUsed", ctypes.c_size_t),
    ]


class _JobObjectBasicAccountingInformation(ctypes.Structure):
    _fields_ = [
        ("TotalUserTime", ctypes.c_longlong),
        ("TotalKernelTime", ctypes.c_longlong),
        ("ThisPeriodTotalUserTime", ctypes.c_longlong),
        ("ThisPeriodTotalKernelTime", ctypes.c_longlong),
        ("TotalPageFaultCount", wintypes.DWORD),
        ("TotalProcesses", wintypes.DWORD),
        ("ActiveProcesses", wintypes.DWORD),
        ("TotalTerminatedProcesses", wintypes.DWORD),
    ]


class _WindowsJob:
    """Own a Windows process tree independently of its root process."""

    _BASIC_ACCOUNTING_INFORMATION = 1
    _EXTENDED_LIMIT_INFORMATION = 9
    _LIMIT_KILL_ON_JOB_CLOSE = 0x00002000
    _PROCESS_TERMINATE = 0x0001
    _PROCESS_SET_QUOTA = 0x0100
    _PROCESS_QUERY_LIMITED_INFORMATION = 0x1000

    def __init__(self, pid: int) -> None:
        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        self._kernel32 = kernel32
        self._handle = None
        self._configure_signatures()

        handle = kernel32.CreateJobObjectW(None, None)
        if not handle:
            self._raise_last_error()
        self._handle = handle

        limits = _JobObjectExtendedLimitInformation()
        limits.BasicLimitInformation.LimitFlags = self._LIMIT_KILL_ON_JOB_CLOSE
        if not kernel32.SetInformationJobObject(
            handle,
            self._EXTENDED_LIMIT_INFORMATION,
            ctypes.byref(limits),
            ctypes.sizeof(limits),
        ):
            self.close()
            self._raise_last_error()

        process_handle = kernel32.OpenProcess(
            self._PROCESS_TERMINATE
            | self._PROCESS_SET_QUOTA
            | self._PROCESS_QUERY_LIMITED_INFORMATION,
            False,
            pid,
        )
        if not process_handle:
            self.close()
            self._raise_last_error()
        try:
            if not kernel32.AssignProcessToJobObject(handle, process_handle):
                self.close()
                self._raise_last_error()
        finally:
            kernel32.CloseHandle(process_handle)

    def _configure_signatures(self) -> None:
        kernel32 = self._kernel32
        kernel32.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
        kernel32.CreateJobObjectW.restype = wintypes.HANDLE
        kernel32.SetInformationJobObject.argtypes = [
            wintypes.HANDLE,
            ctypes.c_int,
            ctypes.c_void_p,
            wintypes.DWORD,
        ]
        kernel32.SetInformationJobObject.restype = wintypes.BOOL
        kernel32.OpenProcess.argtypes = [
            wintypes.DWORD,
            wintypes.BOOL,
            wintypes.DWORD,
        ]
        kernel32.OpenProcess.restype = wintypes.HANDLE
        kernel32.AssignProcessToJobObject.argtypes = [
            wintypes.HANDLE,
            wintypes.HANDLE,
        ]
        kernel32.AssignProcessToJobObject.restype = wintypes.BOOL
        kernel32.QueryInformationJobObject.argtypes = [
            wintypes.HANDLE,
            ctypes.c_int,
            ctypes.c_void_p,
            wintypes.DWORD,
            ctypes.c_void_p,
        ]
        kernel32.QueryInformationJobObject.restype = wintypes.BOOL
        kernel32.TerminateJobObject.argtypes = [
            wintypes.HANDLE,
            wintypes.UINT,
        ]
        kernel32.TerminateJobObject.restype = wintypes.BOOL
        kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel32.CloseHandle.restype = wintypes.BOOL

    @staticmethod
    def _raise_last_error() -> None:
        raise ctypes.WinError(ctypes.get_last_error())

    def active_processes(self) -> int:
        if self._handle is None:
            return 0
        accounting = _JobObjectBasicAccountingInformation()
        if not self._kernel32.QueryInformationJobObject(
            self._handle,
            self._BASIC_ACCOUNTING_INFORMATION,
            ctypes.byref(accounting),
            ctypes.sizeof(accounting),
            None,
        ):
            self._raise_last_error()
        return accounting.ActiveProcesses

    def terminate(self) -> None:
        if self._handle is None:
            return
        if not self._kernel32.TerminateJobObject(self._handle, 1):
            self._raise_last_error()

    def close(self) -> None:
        if self._handle is None:
            return
        handle, self._handle = self._handle, None
        if not self._kernel32.CloseHandle(handle):
            self._raise_last_error()


@dataclass(frozen=True)
class ProcessResult:
    returncode: int | None
    stdout: bytes
    stderr: bytes
    timed_out: bool = False
    forced: bool = False


@dataclass(frozen=True)
class ShutdownReport:
    terminated: int
    forced: int


@dataclass
class _ProcessEntry:
    process: asyncio.subprocess.Process
    communication: asyncio.Task[tuple[bytes, bytes]]
    root_status: asyncio.Task[int | None] | None = None
    root_status_stop: threading.Event | None = None
    windows_job: Any | None = None
    termination: asyncio.Task[tuple[bytes, bytes, bool]] | None = None


class SubprocessOwner:
    """Launch CLI calls in OS ownership boundaries and reap them as one unit."""

    def __init__(
        self,
        *,
        grace_period: float = 5.0,
        force_wait: float = 5.0,
        platform: str = sys.platform,
        logger: logging.Logger | None = None,
        windows_job_factory: Callable[[int], Any] | None = None,
        windows_resume_factory: Callable[[int], None] | None = None,
    ) -> None:
        self.grace_period = grace_period
        self.force_wait = force_wait
        self.platform = platform
        self.logger = logger or log
        self._windows_job_factory = windows_job_factory or _WindowsJob
        self._windows_resume_factory = windows_resume_factory or _resume_windows_process
        self._entries: dict[int, _ProcessEntry] = {}
        self._guard = asyncio.Lock()
        self._closing = False

    @property
    def active_count(self) -> int:
        return len(self._entries)

    async def run_exec(
        self,
        *program_and_args: str,
        timeout: float | None = None,
        input: bytes | None = None,
        **kwargs: Any,
    ) -> ProcessResult:
        if self.platform.startswith("linux"):
            return await self._run(
                asyncio.create_subprocess_exec,
                *program_and_args,
                timeout=timeout,
                input=input,
                linux_supervision_mode="exec",
                **kwargs,
            )
        return await self._run(
            asyncio.create_subprocess_exec,
            *program_and_args,
            timeout=timeout,
            input=input,
            **kwargs,
        )

    async def run_shell(
        self,
        command: str,
        *,
        timeout: float | None = None,
        input: bytes | None = None,
        **kwargs: Any,
    ) -> ProcessResult:
        if self.platform.startswith("linux"):
            return await self._run(
                asyncio.create_subprocess_exec,
                command,
                timeout=timeout,
                input=input,
                linux_supervision_mode="shell",
                **kwargs,
            )
        return await self._run(
            asyncio.create_subprocess_shell,
            command,
            timeout=timeout,
            input=input,
            **kwargs,
        )

    async def _run(
        self,
        spawn: Callable[..., Awaitable[asyncio.subprocess.Process]],
        *args: str,
        timeout: float | None,
        input: bytes | None,
        linux_supervision_mode: str | None = None,
        **kwargs: Any,
    ) -> ProcessResult:
        status_read_fd: int | None = None
        status_write_fd: int | None = None
        if linux_supervision_mode is not None:
            status_read_fd, status_write_fd = os.pipe()
            inherited_fds = tuple(kwargs.pop("pass_fds", ()))
            kwargs["pass_fds"] = (*inherited_fds, status_write_fd)
            args = (
                sys.executable,
                str(_SUPERVISOR),
                linux_supervision_mode,
                str(status_write_fd),
                *args,
            )
        if input is not None:
            kwargs.setdefault("stdin", asyncio.subprocess.PIPE)
        kwargs.setdefault("stdout", asyncio.subprocess.PIPE)
        kwargs.setdefault("stderr", asyncio.subprocess.PIPE)
        self._apply_process_group(kwargs)

        registration = asyncio.create_task(
            self._spawn_and_register(
                spawn,
                args,
                kwargs,
                input,
                status_read_fd=status_read_fd,
                status_write_fd=status_write_fd,
            )
        )
        try:
            entry = await asyncio.shield(registration)
        except asyncio.CancelledError as cancellation:
            while True:
                try:
                    entry = await asyncio.shield(registration)
                    break
                except asyncio.CancelledError:
                    if registration.cancelled():
                        raise cancellation
                except BaseException:
                    raise cancellation
            cleanup = asyncio.create_task(
                self._terminate(entry, reason="task cancellation during spawn")
            )
            while True:
                try:
                    await asyncio.shield(cleanup)
                    break
                except asyncio.CancelledError:
                    if cleanup.cancelled():
                        raise cancellation
            raise cancellation

        process = entry.process
        communication = entry.communication
        try:
            wait_target = entry.root_status or communication
            try:
                if timeout is None:
                    command_result = await asyncio.shield(wait_target)
                else:
                    command_result = await asyncio.wait_for(
                        asyncio.shield(wait_target),
                        timeout=timeout,
                    )
            except asyncio.TimeoutError:
                stdout, stderr, forced = await self._terminate(
                    entry,
                    reason=f"timeout after {timeout}s",
                )
                return ProcessResult(
                    self._resolved_returncode(entry),
                    stdout,
                    stderr,
                    timed_out=True,
                    forced=forced,
                )
            except asyncio.CancelledError as cancellation:
                cleanup = asyncio.create_task(
                    self._terminate(entry, reason="task cancellation")
                )
                while True:
                    try:
                        await asyncio.shield(cleanup)
                        break
                    except asyncio.CancelledError:
                        if cleanup.cancelled():
                            raise cancellation
                raise cancellation
            except Exception:
                cleanup = asyncio.create_task(
                    self._terminate(entry, reason="supervisor status failure")
                )
                while True:
                    try:
                        await asyncio.shield(cleanup)
                        break
                    except asyncio.CancelledError:
                        if cleanup.cancelled():
                            raise
                raise

            if entry.root_status is None:
                stdout, stderr = command_result
                root_returncode = process.returncode
            else:
                root_returncode = command_result
                if root_returncode is None:
                    root_returncode = process.returncode
                stdout = stderr = b""
            forced = False
            if self._tree_is_alive(entry) or not communication.done():
                stdout, stderr, forced = await self._terminate(
                    entry,
                    reason="root process exit",
                )
            elif entry.root_status is not None:
                stdout, stderr = await self._read_output(entry)
            return ProcessResult(
                root_returncode,
                stdout,
                stderr,
                forced=forced,
            )
        finally:
            if communication.done() and not self._tree_is_alive(entry):
                await self._release_entry(entry)

    async def _spawn_and_register(
        self,
        spawn: Callable[..., Awaitable[asyncio.subprocess.Process]],
        args: tuple[str, ...],
        kwargs: dict[str, Any],
        input: bytes | None,
        *,
        status_read_fd: int | None,
        status_write_fd: int | None,
    ) -> _ProcessEntry:
        async with self._guard:
            try:
                if self._closing:
                    raise RuntimeError("subprocess owner is shutting down")
                process = await spawn(*args, **kwargs)
            except BaseException:
                if status_read_fd is not None:
                    os.close(status_read_fd)
                raise
            finally:
                if status_write_fd is not None:
                    os.close(status_write_fd)

            communication = asyncio.create_task(process.communicate(input))
            root_status = None
            root_status_stop = None
            if status_read_fd is not None:
                root_status_stop = threading.Event()
                root_status = asyncio.create_task(
                    asyncio.to_thread(
                        self._read_root_status,
                        status_read_fd,
                        root_status_stop,
                    )
                )
            windows_job = None
            if self.platform == "win32":
                try:
                    windows_job = self._windows_job_factory(process.pid)
                    self._windows_resume_factory(process.pid)
                except BaseException:
                    if windows_job is not None:
                        windows_job.close()
                    await self._cleanup_failed_windows_spawn(
                        process,
                        communication,
                    )
                    raise
            entry = _ProcessEntry(
                process=process,
                communication=communication,
                root_status=root_status,
                root_status_stop=root_status_stop,
                windows_job=windows_job,
            )
            self._entries[process.pid] = entry
            return entry

    @staticmethod
    def _read_root_status(
        status_fd: int,
        stop: threading.Event | None = None,
    ) -> int | None:
        try:
            chunks = []
            poller = select.poll()
            poller.register(
                status_fd,
                select.POLLIN | select.POLLHUP | select.POLLERR,
            )
            while True:
                if stop is not None and stop.is_set():
                    return None
                try:
                    events = poller.poll(50)
                except InterruptedError:
                    continue
                if not events:
                    continue
                chunk = os.read(status_fd, 32)
                if not chunk:
                    break
                chunks.append(chunk)
                if b"\n" in chunk:
                    break
        finally:
            os.close(status_fd)
        status = b"".join(chunks).strip()
        if not status:
            raise RuntimeError("subprocess supervisor exited without root status")
        return int(status)

    @staticmethod
    def _resolved_returncode(entry: _ProcessEntry) -> int | None:
        if entry.root_status is not None and entry.root_status.done():
            try:
                return entry.root_status.result()
            except (asyncio.CancelledError, Exception):
                pass
        return entry.process.returncode

    async def _cleanup_failed_windows_spawn(
        self,
        process: asyncio.subprocess.Process,
        communication: asyncio.Task[tuple[bytes, bytes]],
    ) -> None:
        await self._signal_windows_tree(process, force=True)
        try:
            await asyncio.wait_for(
                asyncio.shield(communication),
                timeout=self.force_wait,
            )
        except asyncio.TimeoutError:
            communication.cancel()
            await asyncio.gather(communication, return_exceptions=True)

    async def shutdown(self) -> ShutdownReport:
        """Stop every active tree within two bounded wait periods."""
        async with self._guard:
            self._closing = True
            entries = list(self._entries.values())

        outcomes = await asyncio.gather(
            *(self._terminate(entry, reason="worker shutdown") for entry in entries)
        )
        return ShutdownReport(
            terminated=len(entries),
            forced=sum(1 for _, _, forced in outcomes if forced),
        )

    def _apply_process_group(self, kwargs: dict[str, Any]) -> None:
        if self.platform == "win32":
            kwargs["creationflags"] = (
                kwargs.get("creationflags", 0)
                | _WINDOWS_NEW_PROCESS_GROUP
                | _WINDOWS_CREATE_SUSPENDED
            )
        else:
            kwargs.setdefault("start_new_session", True)

    async def _terminate(
        self,
        entry: _ProcessEntry,
        *,
        reason: str,
    ) -> tuple[bytes, bytes, bool]:
        async with self._guard:
            if entry.termination is None:
                entry.termination = asyncio.create_task(
                    self._terminate_tree(entry, reason=reason)
                )
            termination = entry.termination

        cancellation: asyncio.CancelledError | None = None
        try:
            while True:
                try:
                    result = await asyncio.shield(termination)
                    break
                except asyncio.CancelledError as error:
                    if termination.cancelled():
                        raise
                    cancellation = cancellation or error
            if cancellation is not None:
                raise cancellation
            return result
        finally:
            if termination.done():
                await self._release_entry(entry)

    async def _terminate_tree(
        self,
        entry: _ProcessEntry,
        *,
        reason: str,
    ) -> tuple[bytes, bytes, bool]:
        if not self._tree_is_alive(entry) and entry.communication.done():
            return (*await self._read_output(entry), False)

        await self._signal_tree(entry, force=False)
        if await self._wait_stopped(entry, timeout=self.grace_period):
            return (*await self._read_output(entry), False)

        self.logger.warning(
            "forcing subprocess tree %d to stop after %s",
            entry.process.pid,
            reason,
        )

        await self._signal_tree(entry, force=True)
        if not await self._wait_stopped(entry, timeout=self.force_wait):
            self.logger.error(
                "subprocess tree %d survived forced termination or kept pipes open",
                entry.process.pid,
            )
            entry.communication.cancel()
            await asyncio.gather(entry.communication, return_exceptions=True)
            return b"", b"", True
        return (*await self._read_output(entry), True)

    async def _wait_stopped(
        self,
        entry: _ProcessEntry,
        *,
        timeout: float,
    ) -> bool:
        deadline = asyncio.get_running_loop().time() + timeout
        while self._tree_is_alive(entry) or not entry.communication.done():
            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                return False
            await asyncio.sleep(min(0.05, remaining))
        return True

    async def _read_output(
        self,
        entry: _ProcessEntry,
    ) -> tuple[bytes, bytes]:
        if not entry.communication.done():
            await entry.communication
        if entry.communication.cancelled():
            return b"", b""
        exception = entry.communication.exception()
        if exception is not None:
            raise exception
        return entry.communication.result()

    def _tree_is_alive(self, entry: _ProcessEntry) -> bool:
        process = entry.process
        if self.platform == "win32":
            if entry.windows_job is not None:
                return entry.windows_job.active_processes() > 0
            return process.returncode is None
        try:
            os.killpg(process.pid, 0)
        except ProcessLookupError:
            return False
        except PermissionError:
            return True

        if self.platform.startswith("linux"):
            return self._linux_group_has_live_members(process.pid)
        return True

    @staticmethod
    def _linux_group_has_live_members(group_id: int) -> bool:
        for stat_path in Path("/proc").glob("[0-9]*/stat"):
            try:
                stat = stat_path.read_text()
                fields = stat[stat.rfind(")") + 2 :].split()
                state = fields[0]
                process_group = int(fields[2])
            except (OSError, ValueError, IndexError):
                continue
            if process_group == group_id and state != "Z":
                return True
        return False

    async def _signal_tree(
        self,
        entry: _ProcessEntry,
        *,
        force: bool,
    ) -> None:
        process = entry.process
        if self.platform == "win32":
            if force and entry.windows_job is not None:
                entry.windows_job.terminate()
                return
            await self._signal_windows_tree(process, force=force)
            return

        if self.platform.startswith("linux"):
            if force and _LINUX_FORCE_SIGNAL is None:
                raise RuntimeError("SIGUSR1 is required for Linux force cleanup")
            supervisor_signal = _LINUX_FORCE_SIGNAL if force else signal.SIGTERM
            try:
                os.kill(process.pid, supervisor_signal)
            except ProcessLookupError:
                pass
            except PermissionError:
                self.logger.exception(
                    "cannot signal subprocess supervisor %d",
                    process.pid,
                )
            return

        group_signal = signal.SIGKILL if force else signal.SIGTERM
        try:
            os.killpg(process.pid, group_signal)
        except ProcessLookupError:
            pass
        except PermissionError:
            self.logger.exception(
                "cannot signal subprocess group %d",
                process.pid,
            )
            if process.returncode is None:
                process.kill() if force else process.terminate()

    async def _release_entry(self, entry: _ProcessEntry) -> None:
        windows_job = None
        async with self._guard:
            if self._entries.get(entry.process.pid) is entry:
                self._entries.pop(entry.process.pid, None)
            windows_job, entry.windows_job = entry.windows_job, None
        if windows_job is not None:
            windows_job.close()
        if entry.root_status is not None:
            if not entry.root_status.done() and entry.root_status_stop is not None:
                entry.root_status_stop.set()
            try:
                await asyncio.wait_for(
                    asyncio.shield(entry.root_status),
                    timeout=self.force_wait,
                )
            except asyncio.TimeoutError:
                entry.root_status.cancel()
            await asyncio.gather(entry.root_status, return_exceptions=True)

    async def _signal_windows_tree(
        self,
        process: asyncio.subprocess.Process,
        *,
        force: bool,
    ) -> None:
        helper = None
        communication = None
        try:
            helper = await asyncio.create_subprocess_exec(
                *_windows_taskkill_command(process.pid, force=force),
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.PIPE,
            )
            communication = asyncio.create_task(helper.communicate())
            _, stderr = await asyncio.wait_for(
                asyncio.shield(communication),
                timeout=self.grace_period,
            )
            if helper.returncode not in (0, 128):
                self.logger.warning(
                    "taskkill failed for subprocess tree %d: %s",
                    process.pid,
                    stderr.decode(errors="replace").strip(),
                )
        except asyncio.TimeoutError:
            if helper is not None:
                try:
                    helper.kill()
                except ProcessLookupError:
                    pass
            if communication is not None:
                try:
                    await asyncio.wait_for(
                        asyncio.shield(communication),
                        timeout=self.force_wait,
                    )
                except asyncio.TimeoutError:
                    communication.cancel()
                await asyncio.gather(communication, return_exceptions=True)
            self.logger.exception(
                "taskkill timed out for subprocess tree %d",
                process.pid,
            )
            if process.returncode is None:
                process.kill() if force else process.terminate()
        except FileNotFoundError:
            self.logger.exception(
                "could not run taskkill for subprocess tree %d",
                process.pid,
            )
            if process.returncode is None:
                process.kill() if force else process.terminate()
