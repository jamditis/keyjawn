"""Worker shutdown tests."""

import signal
from unittest.mock import AsyncMock, MagicMock

import pytest
from worker.main import _install_shutdown_handlers
from worker.runner import WorkerRunner


@pytest.mark.asyncio
async def test_runner_shutdown_reaps_owned_subprocesses() -> None:
    runner = WorkerRunner.__new__(WorkerRunner)
    runner.subprocesses = AsyncMock()
    runner._redis_sub = None
    runner.db = None

    await runner.stop()

    runner.subprocesses.shutdown.assert_awaited_once_with()


def test_systemd_sigterm_requests_async_shutdown() -> None:
    callbacks = {}

    class RecordingLoop:
        def add_signal_handler(self, signum, callback):
            callbacks[signum] = callback

    shutdown_requested = MagicMock()

    installed = _install_shutdown_handlers(
        RecordingLoop(),
        shutdown_requested,
    )

    assert signal.SIGTERM in installed
    callbacks[signal.SIGTERM]()
    shutdown_requested.set.assert_called_once_with()
