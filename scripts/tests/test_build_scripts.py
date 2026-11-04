"""Build-context contracts for the Docker allowlist; never invokes Docker or Maven."""
from pathlib import Path
import unittest

PAY_ROOT = Path(__file__).resolve().parents[2]


class DockerignoreTest(unittest.TestCase):
    def setUp(self):
        self.lines = (PAY_ROOT / ".dockerignore").read_text(encoding="utf-8").splitlines()

    def bootstrap_apps(self):
        return sorted(
            path.name.removesuffix("-bootstrap")
            for path in PAY_ROOT.glob("*/*-bootstrap")
            if path.is_dir()
        )

    def test_every_bootstrap_application_is_in_the_build_context(self):
        apps = self.bootstrap_apps()
        self.assertTrue(apps)
        for app in apps:
            for required in (
                f"!{app}/",
                f"!{app}/{app}-bootstrap/",
                f"!{app}/{app}-bootstrap/target/",
                f"!{app}/{app}-bootstrap/target/{app}-bootstrap-*.jar",
            ):
                self.assertIn(required, self.lines, f"{app} is missing from .dockerignore: {required}")

    def test_allowlist_does_not_reference_missing_applications(self):
        for line in self.lines:
            if not line.startswith("!") or line == "!Dockerfile":
                continue
            root = line[1:].split("/", 1)[0]
            self.assertTrue((PAY_ROOT / root).exists(), f"stale .dockerignore entry: {line}")


if __name__ == "__main__":
    unittest.main()
