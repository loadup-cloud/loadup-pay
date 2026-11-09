"""Guard frozen migration history and explicit application migration locations."""

import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import yaml

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("gen_migrations", ROOT / "scripts/gen-migrations.py")
migrations = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migrations)


class FrozenSnapshotsTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.source = self.root / "framework/schema.sql"
        self.source.parent.mkdir()
        self.source.write_text("CREATE TABLE sample (id INTEGER);\n")
        self.lock = self.root / "snapshots.json"
        self.lock.write_text(json.dumps({"format": 1, "files": {}}))
        self.patch = patch.multiple(
            migrations, PAY_ROOT=self.root, FRAMEWORK_ROOT=self.source.parent, LOCK_PATH=self.lock,
            APP_DIRS={"sample": "resources/db/migration/pay/sample"}, APP_SCHEMAS={"sample": ["sample"]},
            SCHEMAS={"sample": ("20260101000001", "schema.sql")},
        )
        self.patch.start()
        self.addCleanup(self.patch.stop)
        self.target = self.root / "resources/db/migration/pay/sample/V20260101000001__sample.sql"

    def run_generator(self, *args):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return migrations.main(list(args))

    def test_check_missing_snapshot_does_not_create_directories_or_change_lock(self):
        original = self.lock.read_bytes()
        self.assertEqual(1, self.run_generator("--check"))
        self.assertFalse((self.root / "resources").exists())
        self.assertEqual(original, self.lock.read_bytes())

    def test_template_upgrade_preserves_frozen_snapshot_and_lock(self):
        self.assertEqual(0, self.run_generator())
        original, lock = self.target.read_bytes(), self.lock.read_bytes()
        self.source.write_text("CREATE TABLE sample (id INTEGER, added TEXT);\n")
        self.assertEqual(0, self.run_generator())
        self.assertEqual(0, self.run_generator("--check"))
        self.assertEqual(original, self.target.read_bytes())
        self.assertEqual(lock, self.lock.read_bytes())

    def test_removing_dependency_preserves_its_history(self):
        self.assertEqual(0, self.run_generator())
        original = self.target.read_bytes()
        with patch.dict(migrations.APP_SCHEMAS, {"sample": []}, clear=True):
            self.assertEqual(0, self.run_generator())
            self.assertEqual(0, self.run_generator("--check"))
        self.assertEqual(original, self.target.read_bytes())

    def test_modified_or_deleted_history_fails_without_repairing_it(self):
        self.assertEqual(0, self.run_generator())
        self.target.write_text("-- changed migration\n")
        changed = self.target.read_bytes()
        self.assertEqual(1, self.run_generator())
        self.assertEqual(changed, self.target.read_bytes())
        self.target.unlink()
        self.assertEqual(1, self.run_generator())
        self.assertFalse(self.target.exists())

    def test_existing_unregistered_migration_is_never_overwritten(self):
        self.target.parent.mkdir(parents=True)
        self.target.write_text("-- independently owned\n")
        self.assertEqual(1, self.run_generator())
        self.assertEqual("-- independently owned\n", self.target.read_text())

    def test_new_component_cannot_backdate_an_existing_history(self):
        self.target.parent.mkdir(parents=True)
        existing = self.target.parent / "V20261001000000__application.sql"
        existing.write_text("CREATE TABLE business (id INTEGER);\n")
        original = self.lock.read_bytes()
        self.assertEqual(1, self.run_generator())
        self.assertFalse(self.target.exists())
        self.assertEqual(original, self.lock.read_bytes())


class ApplicationLocationsTest(unittest.TestCase):
    def test_each_bootstrap_selects_only_its_approved_migration_namespaces(self):
        modules = {"admin": ["upms"], "notificationcenter": ["notification"], "reconciliation": ["file", "transfer"]}
        for path in ROOT.glob("*/*-bootstrap/src/main/resources/application.yml"):
            app = path.parts[-6]
            document = yaml.safe_load(path.read_text())
            config = document.get("loadup", {}).get("flyway") or document.get("spring", {}).get("flyway", {})
            expected = (["classpath:db/migration/oauth2-server"] if app == "oauth2-server" else
                        [f"classpath:db/migration/pay/{app}"] +
                        [f"classpath:db/migration/framework/{name}" for name in modules.get(app, [])])
            actual = config.get("locations", [])
            if isinstance(actual, str):
                actual = actual.split(",")
            with self.subTest(app=app):
                self.assertTrue(config.get("enabled"))
                self.assertCountEqual(expected, actual)

    def test_all_frozen_snapshots_still_match_the_committed_hashes(self):
        lock = json.loads((ROOT / "scripts/component-migrations.lock.json").read_text())
        for relative, entry in lock["files"].items():
            with self.subTest(path=relative):
                self.assertEqual(entry["sha256"], hashlib.sha256((ROOT / relative).read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
