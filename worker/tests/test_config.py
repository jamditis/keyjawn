from worker.config import Config


def test_test_config_uses_memory_db():
    config = Config.for_testing()
    assert config.db_path == ":memory:"


def test_default_schedule_values():
    config = Config.for_testing()
    assert config.action_window_start_hour == 18
    assert config.action_window_end_hour == 21
    assert config.max_actions_per_day == 3
    assert config.approval_timeout_seconds == 7200


def test_max_posts_per_platform():
    config = Config.for_testing()
    assert config.max_posts_per_platform == 3
