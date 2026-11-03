"""Release preflight/recording contracts with fake tools only; never accesses a cluster or Docker daemon."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import test_render_deploy as renderer_tests


class DeployReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "source.json").write_text(json.dumps({"kind": "List", "items": renderer_tests.RenderDeployTest().resources()}))
        self.environment = dict(os.environ, PATH=str(self.root) + os.pathsep + os.environ["PATH"],
                                SKIP_BUILD="1", IMAGE_TAG="test-v1", IMAGE_PREFIX="registry.example/pay",
                                CONFIG_VERSION="cfg-001", SECRET_VERSION="sec-001",
                                KUBE_CONTEXT="fake-context", DOCKER_CONTEXT="fake-context",
                                RELEASE_DIR=str(self.root / "releases"), FAKE_ROOT=str(self.root), FAKE_MODE="ok")
        self.tool("docker", 'import sys; print("fake-image-id")\n')
        self.tool("kubectl", '''import json, os, pathlib, sys
root = pathlib.Path(os.environ["FAKE_ROOT"])
args = sys.argv[1:]
mode = os.environ["FAKE_MODE"]
with (root / "calls").open("a") as output:
    output.write(json.dumps(args) + "\\n")
if "version" in args:
    print(json.dumps({"clientVersion": {"major": "1", "minor": "33" if mode == "old-client" else "36"},
                      "serverVersion": {"major": "1", "minor": "36", "gitVersion": "v1.36.5"}}))
elif "create" in args and "--dry-run=client" in args:
    print((root / "source.json").read_text())
elif "apply" in args:
    with (root / "applied").open("a") as output:
        output.write("apply\\n")
elif "rollout" in args:
    sys.exit(1 if mode == "rollout-failure" else 0)
elif "endpointslices" in args:
    print(json.dumps({"items": [{"endpoints": [{"addresses": ["10.0.0.1"], "conditions": {"ready": True}}]}]}))
elif "get" in args and "--raw=/readyz" in args:
    print("ok")
elif "get" in args:
    reference = args[args.index("get") + 1]
    if reference.endswith("-service-tokens"):
        print("true" if mode == "immutable-tokens" else "false")
    elif mode == "missing-secret" and reference.startswith("secret/"):
        sys.exit(1)
    else:
        print("false" if mode == "mutable-config" else "true")
else:
    sys.exit("Unexpected fake kubectl operation")
''')

    def tool(self, name, body):
        file = self.root / name
        file.write_text("#!" + sys.executable + "\n" + body)
        file.chmod(0o700)

    def deploy(self):
        return subprocess.run(["bash", str(Path(__file__).resolve().parents[1] / "deploy.sh"), "payment"],
                              env=self.environment, text=True, capture_output=True)

    def test_success_records_exact_image_and_config_references_without_secret_values(self):
        result = self.deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        record = next((self.root / "releases").glob("*.json"))
        resources = json.loads(record.read_text())["items"]
        self.assertEqual(len(resources), 4)
        container = next(value for value in resources if value["kind"] == "Deployment")["spec"]["template"]["spec"]["containers"][0]
        self.assertEqual(container["image"], "registry.example/pay/payment:test-v1")
        self.assertEqual(container["envFrom"][1]["secretRef"]["name"], "payment-secret-sec-001")
        self.assertTrue((self.root / "applied").exists())
        self.assertNotIn('"data"', record.read_text())

    def test_mutable_snapshots_immutable_tokens_and_missing_references_never_apply_resources(self):
        for mode in ("mutable-config", "immutable-tokens", "missing-secret", "old-client"):
            with self.subTest(mode=mode):
                self.environment["FAKE_MODE"] = mode
                result = self.deploy()
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse((self.root / "applied").exists())
                self.assertFalse((self.root / "releases").exists())

    def test_missing_version_is_rejected_before_any_cluster_call(self):
        del self.environment["CONFIG_VERSION"]
        result = self.deploy()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / "calls").exists())

    def test_failed_rollout_retains_the_candidate_and_cannot_report_success(self):
        self.environment["FAKE_MODE"] = "rollout-failure"
        result = self.deploy()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(list((self.root / "releases").glob("*.json"))), 1)
        self.assertNotIn("PASS:", result.stdout)

    def test_different_candidate_cannot_overwrite_an_existing_release_record_or_apply_again(self):
        self.assertEqual(self.deploy().returncode, 0)
        original = next((self.root / "releases").glob("*.json")).read_text()
        source = json.loads((self.root / "source.json").read_text())
        deployment = next(value for value in source["items"] if value["kind"] == "Deployment" and value["metadata"]["name"] == "payment")
        deployment["spec"]["template"]["metadata"] = {"annotations": {"test": "different"}}
        (self.root / "source.json").write_text(json.dumps(source))
        result = self.deploy()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(next((self.root / "releases").glob("*.json")).read_text(), original)
        self.assertEqual((self.root / "applied").read_text(), "apply\n")
