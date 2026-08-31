import importlib.util
import io
from pathlib import Path
import unittest
from unittest.mock import patch


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "asc.py"
SPEC = importlib.util.spec_from_file_location("keyjawn_asc", SCRIPT_PATH)
ASC = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(ASC)


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def raise_for_status(self):
        return None

    def json(self):
        return self.payload


class FakeSession:
    def __init__(self):
        self.calls = []

    def get(self, url, params=None):
        self.calls.append((url, params))
        if url == f"{ASC.BASE}/apps":
            return FakeResponse({"data": [{"id": "6759345867"}]})
        if url == f"{ASC.BASE}/builds":
            return FakeResponse({
                "data": [
                    {
                        "attributes": {
                            "version": "2",
                            "processingState": "VALID",
                            "uploadedDate": "2026-02-20T12:00:00Z",
                        }
                    },
                    {
                        "attributes": {
                            "version": "1",
                            "processingState": "VALID",
                            "uploadedDate": "2026-02-19T12:00:00Z",
                        }
                    },
                ]
            })
        raise AssertionError(f"Unexpected URL: {url}")


class ListBuildsTests(unittest.TestCase):
    def test_resolves_bundle_id_then_lists_the_apps_builds(self):
        session = FakeSession()
        with patch.object(ASC, "_session", return_value=(session, "token")):
            with patch("sys.stdout", new_callable=io.StringIO) as output:
                ASC.list_builds("com.keyjawn")

        self.assertEqual(session.calls, [
            (
                f"{ASC.BASE}/apps",
                {"filter[bundleId]": "com.keyjawn", "limit": 1},
            ),
            (
                f"{ASC.BASE}/builds",
                {
                    "filter[app]": "6759345867",
                    "sort": "-uploadedDate",
                    "limit": 200,
                },
            ),
        ])
        lines = output.getvalue().splitlines()
        self.assertIn("v2  VALID  2026-02-20", lines[0])
        self.assertIn("v1  VALID  2026-02-19", lines[1])


if __name__ == "__main__":
    unittest.main()
