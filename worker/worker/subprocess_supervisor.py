"""Linux subreaper used by :mod:`worker.subprocesses`.

The worker process group is not a sufficient ownership boundary because a
descendant can call ``setsid()``. This helper stays alive as a Linux child
subreaper until every descendant has exited, including reparented daemons.
"""

from __future__ import annotations

import ctypes
import os
import signal
import sys
import time
from collections.abc import Sequence

_PR_SET_CHILD_SUBREAPER = 36
_FORCE_SIGNAL = signal.SIGUSR1
_requested_signal: int | None = None


def _become_subreaper() -> None:
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(_PR_SET_CHILD_SUBREAPER, 1, 0, 0, 0) != 0:
        error = ctypes.get_errno()
        raise OSError(error, os.strerror(error))


def _record_signal(signum: int, _frame: object) -> None:
    global _requested_signal
    if signum == _FORCE_SIGNAL:
        _requested_signal = signal.SIGKILL
    elif _requested_signal != signal.SIGKILL:
        _requested_signal = signum


def _direct_children(pid: int) -> list[int]:
    try:
        children = open(  # noqa: SIM115 - close explicitly around procfs races
            f"/proc/{pid}/task/{pid}/children",
            encoding="ascii",
        )
    except OSError:
        return []
    try:
        return [int(child) for child in children.read().split()]
    finally:
        children.close()


def _descendants(pid: int) -> list[int]:
    found: list[int] = []
    pending = _direct_children(pid)
    while pending:
        child = pending.pop()
        found.append(child)
        pending.extend(_direct_children(child))
    return found


def _signal_descendants(signum: int) -> None:
    for pid in reversed(_descendants(os.getpid())):
        try:
            os.kill(pid, signum)
        except ProcessLookupError:
            pass
        except PermissionError:
            os.write(
                2,
                f"cannot signal supervised descendant {pid}\n".encode(),
            )


def _exit_code(status: int) -> int:
    if os.WIFEXITED(status):
        return os.WEXITSTATUS(status)
    if os.WIFSIGNALED(status):
        return 128 + os.WTERMSIG(status)
    return 1


def _exec_child(mode: str, command: Sequence[str], status_fd: int) -> None:
    for signum in (
        signal.SIGTERM,
        signal.SIGINT,
        _FORCE_SIGNAL,
        signal.SIGPIPE,
        signal.SIGXFSZ,
    ):
        signal.signal(signum, signal.SIG_DFL)
    os.close(status_fd)

    try:
        if mode == "exec":
            os.execvp(command[0], list(command))
        os.execl("/bin/sh", "sh", "-c", command[0])
    except OSError as error:
        os.write(2, f"cannot execute {command[0]}: {error}\n".encode())
        os._exit(127)


def supervise(mode: str, status_fd: int, command: Sequence[str]) -> int:
    """Run one command and remain its subreaper until its tree is gone."""
    _become_subreaper()
    for signum in (signal.SIGTERM, signal.SIGINT, _FORCE_SIGNAL):
        signal.signal(signum, _record_signal)

    root_pid = os.fork()
    if root_pid == 0:
        _exec_child(mode, command, status_fd)

    root_status: int | None = None
    status_reported = False
    while True:
        if _requested_signal is not None:
            _signal_descendants(_requested_signal)

        try:
            waited_pid, waited_status = os.waitpid(-1, os.WNOHANG)
        except ChildProcessError:
            break

        if waited_pid == 0:
            time.sleep(0.02)
            continue

        if waited_pid == root_pid:
            root_status = waited_status
            os.write(status_fd, f"{_exit_code(waited_status)}\n".encode())
            os.close(status_fd)
            status_reported = True

    if not status_reported:
        os.close(status_fd)
    return _exit_code(root_status) if root_status is not None else 1


def main(argv: Sequence[str] | None = None) -> int:
    args = list(argv if argv is not None else sys.argv[1:])
    if len(args) < 3 or args[0] not in {"exec", "shell"}:
        raise SystemExit(
            "usage: subprocess_supervisor.py (exec|shell) STATUS_FD COMMAND..."
        )
    mode = args[0]
    status_fd = int(args[1])
    command = args[2:]
    if mode == "shell" and len(command) != 1:
        raise SystemExit("shell mode accepts exactly one command")
    return supervise(mode, status_fd, command)


if __name__ == "__main__":
    raise SystemExit(main())
