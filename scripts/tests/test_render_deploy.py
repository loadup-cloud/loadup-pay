"""Protocol/resource selection checks; no cluster, build, or financial request is made."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import unittest


class RenderDeployTest(unittest.TestCase):
    apps = ("customer", "product", "limitcenter", "payment", "acquire", "realtime", "reconciliation")

    def resources(self):
        values = [{"kind": "Namespace", "metadata": {"name": "loadup-pay"}}]
        for app in self.apps:
            values.extend([
                {"kind": "Deployment", "metadata": {"name": app, "namespace": "loadup-pay"},
                 "spec": {"template": {"spec": {"containers": [{"name": app, "image": "old",
                     "ports": [{"name": "http", "containerPort": 8080}]}]}}}},
                {"kind": "Service", "metadata": {"name": app, "namespace": "loadup-pay"},
                 "spec": {"selector": {"app": app}, "ports": [{"name": "http", "port": 8080, "targetPort": "http"}]}},
            ])
        return values

    def render(self, values, scope="all"):
        return subprocess.run([sys.executable, str(Path(__file__).resolve().parents[1] / "render-deploy.py"),
                               scope, "registry.example/pay", "test-v1"],
                              input=json.dumps({"kind": "List", "items": values}), text=True, capture_output=True)

    def test_all_and_single_application_keep_only_the_selected_http_resources(self):
        source = self.resources()
        original = copy.deepcopy(source)
        all_apps = self.render(source)
        self.assertEqual(all_apps.returncode, 0, all_apps.stderr)
        self.assertEqual(len(json.loads(all_apps.stdout)["items"]), 15)
        single = self.render(source, "payment")
        self.assertEqual(single.returncode, 0, single.stderr)
        items = json.loads(single.stdout)["items"]
        self.assertEqual(len(items), 3)
        deployment = next(value for value in items if value["kind"] == "Deployment")
        self.assertEqual(deployment["spec"]["template"]["spec"]["containers"][0]["image"],
                         "registry.example/pay/payment:test-v1")
        self.assertEqual(source, original)

    def test_legacy_headless_or_rpc_ports_are_rejected_before_any_output(self):
        source = self.resources()
        source.append({"kind": "Service", "metadata": {"name": "payment-headless"}})
        legacy = self.render(source)
        self.assertNotEqual(legacy.returncode, 0)
        self.assertEqual(legacy.stdout, "")
        source = self.resources()
        source[1]["spec"]["template"]["spec"]["containers"][0]["ports"].append(
            {"name": "dubbo", "containerPort": 50051})
        invalid_port = self.render(source)
        self.assertNotEqual(invalid_port.returncode, 0)
        self.assertEqual(invalid_port.stdout, "")

    def test_missing_duplicate_or_wrongly_routed_service_never_produces_a_partial_manifest(self):
        for change in (lambda values: values.pop(), lambda values: values.append(copy.deepcopy(values[2])),
                       lambda values: values[2]["spec"]["ports"][0].update(targetPort="rpc")):
            with self.subTest(change=change):
                source = self.resources()
                change(source)
                result = self.render(source)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, "")


if __name__ == "__main__":
    unittest.main()
