"""Process-tree ownership tests for worker CLI calls."""

from __future__ import annotations

import asyncio
import importlib.util
import json
import os
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from worker.subprocesses import (
    SubprocessOwner,
    _ProcessEntry,
    _spawn_group_kwargs,
    _windows_taskkill_command,
)

pytestmark = pytest.mark.asyncio


async def test_input_bytes_reach_the_child_process() -> None:
    owner = SubprocessOwner()

    result = await owner.run_exec(
        sys.executable,
        "-c",
        "import sys;sys.stdout.buffer.write(sys.stdin.buffer.read())",
        input=b"curation prompt",
        timeout=3,
    )

    assert result.returncode == 0
    assert result.stdout == b"curation prompt"


async def test_completed_linux_supervisor_output_is_preserved(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def completed(value):
        return value

    communication = asyncio.create_task(completed((b"stdout", b"stderr")))
    root_status = asyncio.create_task(completed(0))
    await asyncio.gather(communication, root_status)
    entry = _ProcessEntry(
        process=SimpleNamespace(pid=4321, returncode=0),
        communication=communication,
        root_status=root_status,
    )
    owner = SubprocessOwner(platform="linux")
    monkeypatch.setattr(
        owner,
        "_spawn_and_register",
        AsyncMock(return_value=entry),
    )
    monkeypatch.setattr(owner, "_tree_is_alive", lambda _entry: False)

    result = await owner._run(
        AsyncMock(),
        "fake-command",
        timeout=1,
        input=None,
    )

    assert result.returncode == 0
    assert result.stdout == b"stdout"
    assert result.stderr == b"stderr"


async def test_forced_cleanup_bounds_unresponsive_root_status() -> None:
    blocker = asyncio.Event()
    communication = asyncio.create_task(
        asyncio.sleep(0, result=(b"", b""))
    )
    await communication
    root_status = asyncio.create_task(blocker.wait())
    entry = _ProcessEntry(
        process=SimpleNamespace(pid=4321, returncode=0),
        communication=communication,
        root_status=root_status,
    )
    owner = SubprocessOwner(grace_period=0.01, force_wait=0.02)
    owner._entries[4321] = entry

    await asyncio.wait_for(owner._release_entry(entry), timeout=0.2)

    assert root_status.done()
    assert owner.active_count == 0


async def test_release_stops_status_reader_without_failing_waiters() -> None:
    status_read_fd, status_write_fd = os.pipe()
    stop = threading.Event()
    owner = SubprocessOwner(grace_period=0.01, force_wait=0.2)
    communication = asyncio.create_task(asyncio.sleep(0, result=(b"", b"")))
    await communication
    root_status = asyncio.create_task(
        asyncio.to_thread(
            owner._read_root_status,
            status_read_fd,
            stop,
        )
    )
    entry = _ProcessEntry(
        process=SimpleNamespace(pid=4321, returncode=-signal.SIGTERM),
        communication=communication,
        root_status=root_status,
        root_status_stop=stop,
    )
    owner._entries[4321] = entry

    try:
        await asyncio.wait_for(owner._release_entry(entry), timeout=0.5)
        assert await root_status is None
        assert owner.active_count == 0
    finally:
        os.close(status_write_fd)


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux descriptors above FD_SETSIZE",
)
async def test_status_reader_accepts_high_numbered_file_descriptor() -> None:
    import resource

    soft_limit, hard_limit = resource.getrlimit(resource.RLIMIT_NOFILE)
    status_read_fd, status_write_fd = os.pipe()
    high_fd = 2048
    resource.setrlimit(
        resource.RLIMIT_NOFILE,
        (max(soft_limit, high_fd + 1), hard_limit),
    )
    try:
        os.dup2(status_read_fd, high_fd)
        os.close(status_read_fd)
        os.write(status_write_fd, b"0\n")
        os.close(status_write_fd)

        assert SubprocessOwner._read_root_status(high_fd) == 0
    finally:
        resource.setrlimit(
            resource.RLIMIT_NOFILE,
            (soft_limit, hard_limit),
        )


async def test_supervisor_status_failure_cleans_up_the_tree(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fail_status() -> int:
        raise RuntimeError("subprocess supervisor exited without root status")

    communication_blocker = asyncio.Event()

    async def communicate() -> tuple[bytes, bytes]:
        await communication_blocker.wait()
        return b"", b""

    communication = asyncio.create_task(communicate())
    root_status = asyncio.create_task(fail_status())
    entry = _ProcessEntry(
        process=SimpleNamespace(pid=4321, returncode=None),
        communication=communication,
        root_status=root_status,
    )
    owner = SubprocessOwner(
        platform="linux",
        grace_period=0.01,
        force_wait=0.02,
    )

    async def register(*_args, **_kwargs):
        owner._entries[4321] = entry
        return entry

    monkeypatch.setattr(owner, "_spawn_and_register", register)
    monkeypatch.setattr(owner, "_tree_is_alive", lambda _entry: True)
    owner._signal_tree = AsyncMock()

    try:
        with pytest.raises(
            RuntimeError,
            match="subprocess supervisor exited without root status",
        ):
            await owner._run(
                AsyncMock(),
                "fake-command",
                timeout=1,
                input=None,
            )
        assert owner._signal_tree.await_count == 2
        assert communication.done()
        assert owner.active_count == 0
    finally:
        communication.cancel()
        await asyncio.gather(communication, return_exceptions=True)


def _is_running(pid: int) -> bool:
    """Return false for absent processes and Linux zombies."""
    stat = Path(f"/proc/{pid}/stat")
    if stat.exists():
        fields = stat.read_text().split()
        if len(fields) > 2 and fields[2] == "Z":
            return False
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    return True


async def _wait_for_file(path: Path, timeout: float = 3.0) -> dict:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if path.exists():
            return json.loads(path.read_text())
        await asyncio.sleep(0.01)
    raise AssertionError(f"process fixture did not write {path}")


async def _wait_stopped(*pids: int, timeout: float = 3.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if all(not _is_running(pid) for pid in pids):
            return
        await asyncio.sleep(0.01)
    running = [pid for pid in pids if _is_running(pid)]
    raise AssertionError(f"processes still running: {running}")


def _tree_script(pid_file: Path, ignore_term: bool = False) -> str:
    child_setup = (
        "import signal,time;"
        + ("signal.signal(signal.SIGTERM, signal.SIG_IGN);" if ignore_term else "")
        + "time.sleep(60)"
    )
    parent_setup = (
        "signal.signal(signal.SIGTERM, signal.SIG_IGN);" if ignore_term else ""
    )
    return (
        "import json,os,signal,subprocess,sys,time;"
        f"{parent_setup}"
        f"child=subprocess.Popen([sys.executable,'-c',{child_setup!r}]);"
        f"open({str(pid_file)!r},'w').write("
        "json.dumps({'parent':os.getpid(),'child':child.pid}));"
        "sys.stdout.write(str(child.pid)+'\\n');sys.stdout.flush();"
        "time.sleep(60)"
    )


def _stubborn_redirected_child_script(pid_file: Path, parent_sleeps: bool) -> str:
    child_setup = (
        "import json,os,signal,time;"
        "signal.signal(signal.SIGTERM, signal.SIG_IGN);"
        f"open({str(pid_file)!r},'w').write("
        "json.dumps({'parent':os.getppid(),'child':os.getpid()}));"
        "time.sleep(60)"
    )
    parent_tail = "time.sleep(60)" if parent_sleeps else ""
    return (
        "import os,subprocess,sys,time\n"
        f"child=subprocess.Popen([sys.executable,'-c',{child_setup!r}],"
        "stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,"
        "stderr=subprocess.DEVNULL)\n"
        "deadline=time.monotonic()+3\n"
        f"while not os.path.exists({str(pid_file)!r}):\n"
        "    if time.monotonic() >= deadline:\n"
        "        raise RuntimeError('child did not become ready')\n"
        "    time.sleep(0.005)\n"
        f"{parent_tail}\n"
    )


def _detached_redirected_child_script(pid_file: Path) -> str:
    child_setup = (
        "import json,os,signal,time;"
        "signal.signal(signal.SIGTERM, signal.SIG_IGN);"
        f"open({str(pid_file)!r},'w').write(json.dumps({{'child':os.getpid()}}));"
        "time.sleep(60)"
    )
    return (
        "import subprocess,sys;"
        f"subprocess.Popen([sys.executable,'-c',{child_setup!r}],"
        "start_new_session=True,stdin=subprocess.DEVNULL,"
        "stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)"
    )


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subreaper integration test",
)
async def test_timeout_stops_child_and_grandchild_without_pipe_deadlock(
    tmp_path: Path,
) -> None:
    pid_file = tmp_path / "timeout-tree.json"
    owner = SubprocessOwner(grace_period=0.2)

    result = await owner.run_exec(
        sys.executable,
        "-c",
        _tree_script(pid_file),
        timeout=0.2,
    )

    pids = json.loads(pid_file.read_text())
    assert result.timed_out is True
    assert str(pids["child"]).encode() in result.stdout
    await _wait_stopped(pids["parent"], pids["child"])
    assert owner.active_count == 0


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subreaper integration test",
)
async def test_timeout_escalates_when_only_descendant_ignores_term(
    tmp_path: Path,
) -> None:
    pid_file = tmp_path / "split-tree.json"
    owner = SubprocessOwner(grace_period=0.1)

    result = await owner.run_exec(
        sys.executable,
        "-c",
        _stubborn_redirected_child_script(pid_file, parent_sleeps=True),
        timeout=0.2,
    )

    pids = json.loads(pid_file.read_text())
    assert result.timed_out is True
    assert result.forced is True
    await _wait_stopped(pids["parent"], pids["child"])
    assert owner.active_count == 0


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subreaper integration test",
)
async def test_successful_parent_cannot_leave_a_descendant_owned(
    tmp_path: Path,
) -> None:
    pid_file = tmp_path / "successful-parent-tree.json"
    owner = SubprocessOwner(grace_period=0.1)

    result = await owner.run_exec(
        sys.executable,
        "-c",
        _stubborn_redirected_child_script(pid_file, parent_sleeps=False),
        timeout=3,
    )

    pids = json.loads(pid_file.read_text())
    assert result.returncode == 0
    assert result.forced is True
    await _wait_stopped(pids["parent"], pids["child"])
    assert owner.active_count == 0


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subreaper integration test",
)
async def test_successful_parent_stops_detached_descendant_holding_output_pipe(
    tmp_path: Path,
) -> None:
    pid_file = tmp_path / "detached-child.json"
    child_setup = (
        "import json,os,time;"
        f"open({str(pid_file)!r},'w').write(json.dumps({{'child':os.getpid()}}));"
        "time.sleep(60)"
    )
    parent_setup = (
        "import subprocess,sys;"
        f"subprocess.Popen([sys.executable,'-c',{child_setup!r}],"
        "start_new_session=True)"
    )
    owner = SubprocessOwner(grace_period=0.1, force_wait=0.2)

    result = await asyncio.wait_for(
        owner.run_exec(
            sys.executable,
            "-c",
            parent_setup,
            timeout=0.2,
        ),
        timeout=2,
    )

    pids = await _wait_for_file(pid_file)
    assert result.timed_out is False
    await _wait_stopped(pids["child"])
    assert owner.active_count == 0


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subreaper integration test",
)
async def test_successful_parent_cannot_leave_detached_descendant_without_pipes(
    tmp_path: Path,
) -> None:
    pid_file = tmp_path / "detached-redirected-child.json"
    owner = SubprocessOwner(grace_period=0.1, force_wait=0.2)

    result = await asyncio.wait_for(
        owner.run_exec(
            sys.executable,
            "-c",
            _detached_redirected_child_script(pid_file),
            timeout=2,
        ),
        timeout=3,
    )

    pids = await _wait_for_file(pid_file)
    assert result.returncode == 0
    assert result.forced is True
    await _wait_stopped(pids["child"])
    assert owner.active_count == 0


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subreaper integration test",
)
async def test_cancellation_stops_child_and_grandchild(tmp_path: Path) -> None:
    pid_file = tmp_path / "cancel-tree.json"
    owner = SubprocessOwner(grace_period=0.2)
    task = asyncio.create_task(
        owner.run_exec(
            sys.executable,
            "-c",
            _tree_script(pid_file),
            timeout=60,
        )
    )
    pids = await _wait_for_file(pid_file)

    task.cancel()

    with pytest.raises(asyncio.CancelledError):
        await task
    await _wait_stopped(pids["parent"], pids["child"])
    assert owner.active_count == 0


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subreaper integration test",
)
async def test_repeated_cancellation_waits_for_spawn_registration(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    pid_file = tmp_path / "spawn-cancellation-tree.json"
    spawned = asyncio.Event()
    release_spawn = asyncio.Event()
    real_spawn = asyncio.create_subprocess_exec

    async def delayed_spawn(*args, **kwargs):
        process = await real_spawn(*args, **kwargs)
        spawned.set()
        await release_spawn.wait()
        return process

    monkeypatch.setattr(asyncio, "create_subprocess_exec", delayed_spawn)
    owner = SubprocessOwner(grace_period=0.1)
    run_task = asyncio.create_task(
        owner.run_exec(
            sys.executable,
            "-c",
            _tree_script(pid_file),
            timeout=60,
        )
    )
    await spawned.wait()
    pids = await _wait_for_file(pid_file)

    run_task.cancel()
    await asyncio.sleep(0)
    run_task.cancel()
    release_spawn.set()

    with pytest.raises(asyncio.CancelledError):
        await asyncio.wait_for(run_task, timeout=2)
    await _wait_stopped(pids["parent"], pids["child"])
    assert owner.active_count == 0


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subreaper integration test",
)
async def test_cancellation_during_timeout_cleanup_waits_for_termination(
    tmp_path: Path,
) -> None:
    pid_file = tmp_path / "timeout-cancellation-tree.json"
    owner = SubprocessOwner(grace_period=0.3, force_wait=0.2)
    run_task = asyncio.create_task(
        owner.run_exec(
            sys.executable,
            "-c",
            _tree_script(pid_file, ignore_term=True),
            timeout=0.05,
        )
    )
    pids = await _wait_for_file(pid_file)

    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        entries = list(owner._entries.values())
        if entries and entries[0].termination is not None:
            break
        await asyncio.sleep(0.01)
    else:
        raise AssertionError("timeout cleanup did not start")

    run_task.cancel()

    with pytest.raises(asyncio.CancelledError):
        await asyncio.wait_for(run_task, timeout=2)
    await _wait_stopped(pids["parent"], pids["child"])
    assert owner.active_count == 0


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subreaper integration test",
)
async def test_shutdown_escalates_and_reports_forced_tree_kill(
    tmp_path: Path,
) -> None:
    pid_file = tmp_path / "shutdown-tree.json"
    owner = SubprocessOwner(grace_period=0.1)
    run_task = asyncio.create_task(
        owner.run_exec(
            sys.executable,
            "-c",
            _tree_script(pid_file, ignore_term=True),
            timeout=60,
        )
    )
    pids = await _wait_for_file(pid_file)

    report = await owner.shutdown()
    result = await run_task

    assert report.terminated == 1
    assert report.forced == 1
    assert result.timed_out is False
    await _wait_stopped(pids["parent"], pids["child"])
    assert owner.active_count == 0


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subreaper integration test",
)
async def test_shutdown_checks_descendants_after_parent_exits(
    tmp_path: Path,
) -> None:
    pid_file = tmp_path / "shutdown-split-tree.json"
    owner = SubprocessOwner(grace_period=0.1)
    run_task = asyncio.create_task(
        owner.run_exec(
            sys.executable,
            "-c",
            _stubborn_redirected_child_script(pid_file, parent_sleeps=True),
            timeout=60,
        )
    )
    pids = await _wait_for_file(pid_file)

    report = await owner.shutdown()
    await run_task

    assert report.terminated == 1
    assert report.forced == 1
    await _wait_stopped(pids["parent"], pids["child"])
    assert owner.active_count == 0


async def test_windows_process_tree_commands_are_explicit() -> None:
    creation_flags = getattr(
        subprocess,
        "CREATE_NEW_PROCESS_GROUP",
        0x00000200,
    )

    assert _spawn_group_kwargs("win32") == {
        "creationflags": creation_flags | 0x00000004,
    }
    assert _windows_taskkill_command(4321, force=False) == (
        "taskkill",
        "/PID",
        "4321",
        "/T",
    )
    assert _windows_taskkill_command(4321, force=True) == (
        "taskkill",
        "/PID",
        "4321",
        "/T",
        "/F",
    )


async def test_windows_import_does_not_require_sigusr1(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    module_name = "worker._subprocesses_without_sigusr1"
    module_path = Path(__file__).parents[1] / "worker" / "subprocesses.py"
    monkeypatch.delattr(signal, "SIGUSR1")
    spec = importlib.util.spec_from_file_location(module_name, module_path)
    assert spec is not None
    assert spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    try:
        spec.loader.exec_module(module)
    finally:
        sys.modules.pop(module_name, None)

    assert module._LINUX_FORCE_SIGNAL is None


async def test_windows_job_is_attached_before_process_resumes() -> None:
    events: list[str] = []

    class FakeProcess:
        pid = 4321
        returncode = 0

        async def communicate(self, input=None):
            return b"", b""

    class FakeJob:
        def active_processes(self) -> int:
            return 0

        def close(self) -> None:
            events.append("close")

    async def spawn(*args, **kwargs):
        assert kwargs["creationflags"] & 0x00000004
        events.append("spawn-suspended")
        return FakeProcess()

    def attach_job(pid: int):
        events.append("attach-job")
        return FakeJob()

    def resume(pid: int) -> None:
        events.append("resume")

    owner = SubprocessOwner(
        platform="win32",
        windows_job_factory=attach_job,
        windows_resume_factory=resume,
    )

    result = await owner._run(
        spawn,
        "fake-command",
        timeout=1,
        input=None,
    )

    assert result.returncode == 0
    assert events == ["spawn-suspended", "attach-job", "resume", "close"]


async def test_windows_job_tracks_descendants_after_root_exit() -> None:
    communication = asyncio.create_task(asyncio.sleep(0, result=(b"", b"")))
    await communication

    class FakeJob:
        def active_processes(self) -> int:
            return 1

    entry = _ProcessEntry(
        process=SimpleNamespace(pid=4321, returncode=0),
        communication=communication,
        windows_job=FakeJob(),
    )
    owner = SubprocessOwner(platform="win32")

    assert owner._tree_is_alive(entry) is True


async def test_windows_job_setup_failure_has_bounded_cleanup() -> None:
    never_finishes = asyncio.Event()

    class FakeProcess:
        pid = 4321
        returncode = None

        async def communicate(self, input=None):
            await never_finishes.wait()
            return b"", b""

    async def spawn(*args, **kwargs):
        return FakeProcess()

    def fail_job_setup(pid: int):
        raise OSError("job setup failed")

    owner = SubprocessOwner(
        platform="win32",
        grace_period=0.01,
        force_wait=0.05,
        windows_job_factory=fail_job_setup,
    )
    owner._signal_windows_tree = AsyncMock()

    with pytest.raises(OSError, match="job setup failed"):
        await asyncio.wait_for(
            owner._run(
                spawn,
                "fake-command",
                timeout=1,
                input=None,
            ),
            timeout=0.5,
        )


async def test_timed_out_taskkill_helper_is_killed_and_reaped(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    stopped = asyncio.Event()

    class FakeHelper:
        returncode = None
        killed = False

        async def communicate(self):
            await stopped.wait()
            self.returncode = -9
            return b"", b""

        def kill(self) -> None:
            self.killed = True
            stopped.set()

    class FakeTarget:
        pid = 4321
        returncode = None
        terminated = False

        def terminate(self) -> None:
            self.terminated = True

        def kill(self) -> None:
            self.terminated = True

    helper = FakeHelper()

    async def spawn_helper(*_args, **_kwargs):
        return helper

    monkeypatch.setattr(asyncio, "create_subprocess_exec", spawn_helper)
    owner = SubprocessOwner(
        platform="win32",
        grace_period=0.01,
        force_wait=0.05,
    )
    target = FakeTarget()

    await owner._signal_windows_tree(target, force=False)

    assert helper.killed is True
    assert stopped.is_set()


@pytest.mark.skipif(
    not sys.platform.startswith("linux"),
    reason="Linux subprocess supervisor",
)
async def test_supervisor_restores_python_ignored_signals_before_exec(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from worker import subprocess_supervisor

    restored: list[tuple[int, object]] = []

    class ExecCalled(BaseException):
        pass

    monkeypatch.setattr(
        subprocess_supervisor.signal,
        "signal",
        lambda signum, handler: restored.append((signum, handler)),
    )
    monkeypatch.setattr(subprocess_supervisor.os, "close", lambda _fd: None)
    monkeypatch.setattr(
        subprocess_supervisor.os,
        "execvp",
        lambda *_args: (_ for _ in ()).throw(ExecCalled()),
    )

    with pytest.raises(ExecCalled):
        subprocess_supervisor._exec_child("exec", ("fake-command",), 9)

    assert (signal.SIGPIPE, signal.SIG_DFL) in restored
    assert (signal.SIGXFSZ, signal.SIG_DFL) in restored
