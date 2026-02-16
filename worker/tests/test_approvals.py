import asyncio
import json

import pytest
import pytest_asyncio

from worker.config import Config
from worker.db import Database
from worker.approvals import ApprovalManager


@pytest_asyncio.fixture
async def db():
    database = Database(":memory:")
    await database.init()
    yield database
    await database.close()


@pytest.fixture
def config():
    return Config.for_testing()


@pytest.fixture
def manager(config, db):
    return ApprovalManager(config, db)


async def _create_pending_action(db: Database) -> str:
    """Helper: insert an action with status pending_approval."""
    return await db.log_action(
        action_type="post",
        platform="twitter",
        content="Test draft content",
        status="pending_approval",
    )


@pytest.mark.asyncio
async def test_process_decision_approve(manager, db):
    action_id = await _create_pending_action(db)
    message = json.dumps({
        "action_id": action_id,
        "decision": "approve",
        "timestamp": "2026-02-16T12:00:00Z",
    })

    result = await manager.process_decision(message)

    assert result == "approve"
    action = await db.get_action(action_id)
    assert action["status"] == "approved"
    assert action["approval_decision"] == "approve"
    assert action["approval_timestamp"] == "2026-02-16T12:00:00Z"


@pytest.mark.asyncio
async def test_process_decision_deny(manager, db):
    action_id = await _create_pending_action(db)
    message = json.dumps({
        "action_id": action_id,
        "decision": "deny",
        "timestamp": "2026-02-16T12:01:00Z",
    })

    result = await manager.process_decision(message)

    assert result == "deny"
    action = await db.get_action(action_id)
    assert action["status"] == "denied"
    assert action["approval_decision"] == "deny"
    assert action["approval_timestamp"] == "2026-02-16T12:01:00Z"


@pytest.mark.asyncio
async def test_process_decision_backlog(manager, db):
    action_id = await _create_pending_action(db)
    message = json.dumps({
        "action_id": action_id,
        "decision": "backlog",
        "timestamp": "2026-02-16T12:02:00Z",
    })

    result = await manager.process_decision(message)

    assert result == "backlog"
    action = await db.get_action(action_id)
    assert action["status"] == "backlogged"
    assert action["approval_decision"] == "backlog"
    assert action["approval_timestamp"] == "2026-02-16T12:02:00Z"


@pytest.mark.asyncio
async def test_process_decision_rethink(manager, db):
    action_id = await _create_pending_action(db)
    message = json.dumps({
        "action_id": action_id,
        "decision": "rethink",
        "timestamp": "2026-02-16T12:03:00Z",
    })

    result = await manager.process_decision(message)

    assert result == "rethink"
    action = await db.get_action(action_id)
    assert action["status"] == "pending_rethink"
    assert action["approval_decision"] == "rethink"
    assert action["approval_timestamp"] == "2026-02-16T12:03:00Z"


@pytest.mark.asyncio
async def test_process_decision_resolves_future(manager, db):
    action_id = await _create_pending_action(db)

    # Simulate a pending future (as request_approval would create)
    loop = asyncio.get_event_loop()
    future = loop.create_future()
    manager._pending[action_id] = future

    message = json.dumps({
        "action_id": action_id,
        "decision": "approve",
        "timestamp": "2026-02-16T12:04:00Z",
    })

    result = await manager.process_decision(message)

    assert result == "approve"
    assert future.done()
    assert future.result() == "approve"
    # Future should be cleaned up from the pending dict
    assert action_id not in manager._pending
