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
                     "ports": [{"name": "http", "containerPort": 8080}],
                     "envFrom": [{"configMapRef": {"name": app + "-config"}}, {"secretRef": {"name": app + "-secret"}}],
                     "volumeMounts": [{"name": "service-secrets", "mountPath": "/var/run/loadup/secrets", "readOnly": True}]}],
                     "volumes": [{"name": "service-secrets", "secret": {"secretName": app + "-service-tokens"}}]}}}},
                {"kind": "Service", "metadata": {"name": app, "namespace": "loadup-pay"},
                 "spec": {"selector": {"app": app}, "ports": [{"name": "http", "port": 8080, "targetPort": "http"}]}},
            ])
        callers = {"customer": ["product"], "product": ["acquire"], "limitcenter": ["product", "payment"],
                   "payment": ["acquire", "limitcenter", "reconciliation"], "acquire": ["payment"],
                   "realtime": ["acquire", "payment"], "reconciliation": []}
        for app in self.apps:
            peers = []
            if callers[app]:
                peers.append({"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "loadup-pay"}},
                              "podSelector": {"matchExpressions": [{"key": "app", "operator": "In", "values": callers[app]}]}})
            if app != "limitcenter":
                peers.append({"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "envoy-gateway-system"}},
                              "podSelector": {"matchLabels": {"app.kubernetes.io/name": "envoy",
                                  "gateway.envoyproxy.io/owning-gateway-namespace": "loadup-pay",
                                  "gateway.envoyproxy.io/owning-gateway-name": "loadup-pay"}}})
            peers.append({"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "observability"}},
                          "podSelector": {"matchLabels": {"loadup.io/metrics-scraper": "true"}}})
            values.append({"kind": "NetworkPolicy", "metadata": {"name": app + "-http-ingress", "namespace": "loadup-pay"},
                           "spec": {"podSelector": {"matchLabels": {"app": app}}, "policyTypes": ["Ingress"],
                                    "ingress": [{"from": peers, "ports": [{"protocol": "TCP", "port": 8080}]}]}})
        return values

    def render(self, values, scope="all", config_version="cfg-001", secret_version="sec-001"):
        return subprocess.run([sys.executable, str(Path(__file__).resolve().parents[1] / "render-deploy.py"),
                               scope, "registry.example/pay", "test-v1", config_version, secret_version],
                              input=json.dumps({"kind": "List", "items": values}), text=True, capture_output=True)

    def test_all_and_single_application_keep_only_the_selected_http_resources(self):
        source = self.resources()
        original = copy.deepcopy(source)
        all_apps = self.render(source)
        self.assertEqual(all_apps.returncode, 0, all_apps.stderr)
        self.assertEqual(len(json.loads(all_apps.stdout)["items"]), 22)
        single = self.render(source, "payment")
        self.assertEqual(single.returncode, 0, single.stderr)
        items = json.loads(single.stdout)["items"]
        self.assertEqual(len(items), 4)
        deployment = next(value for value in items if value["kind"] == "Deployment")
        self.assertEqual(deployment["spec"]["template"]["spec"]["containers"][0]["image"],
                         "registry.example/pay/payment:test-v1")
        self.assertEqual(source, original)

    def test_configuration_versions_roll_the_template_but_never_change_the_projected_token_identity(self):
        first = next(item for item in json.loads(self.render(self.resources(), "payment").stdout)["items"] if item["kind"] == "Deployment")
        changed = next(item for item in json.loads(self.render(self.resources(), "payment", "cfg-002", "sec-003").stdout)["items"] if item["kind"] == "Deployment")
        template = changed["spec"]["template"]
        container = template["spec"]["containers"][0]
        self.assertEqual(container["envFrom"], [{"configMapRef": {"name": "payment-config-cfg-002"}},
                                              {"secretRef": {"name": "payment-secret-sec-003"}}])
        self.assertEqual(template["metadata"]["annotations"], {"loadup.io/image-tag": "test-v1",
                         "loadup.io/config-version": "cfg-002", "loadup.io/secret-version": "sec-003"})
        self.assertNotEqual(first["spec"]["template"], template)
        self.assertEqual(first["spec"]["template"]["spec"]["volumes"], template["spec"]["volumes"])

    def test_invalid_versions_are_rejected_before_output(self):
        for version in ("", "UPPER", "with.dots", "../escape", "a" * 33, "trailing-"):
            result = self.render(self.resources(), config_version=version)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(result.stdout, "")

    def test_kubernetes_optional_defaults_do_not_change_required_reference_semantics(self):
        source = self.resources()
        for resource in source:
            if resource["kind"] != "Deployment":
                continue
            pod = resource["spec"]["template"]["spec"]
            pod["volumes"][0]["secret"].update(defaultMode=420, optional=False)
            pod["containers"][0]["envFrom"][0]["configMapRef"]["optional"] = False
            pod["containers"][0]["envFrom"][1]["secretRef"]["optional"] = False
        result = self.render(source)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_unexpected_config_and_token_bindings_are_rejected_before_output(self):
        for change in (lambda pod: pod["containers"][0]["envFrom"].append({"secretRef": {"name": "unversioned"}}),
                       lambda pod: pod["volumes"][0]["secret"].update(secretName="customer-secret"),
                       lambda pod: pod["containers"][0]["volumeMounts"][0].update(subPath="payment.jwt")):
            source = self.resources()
            change(source[1]["spec"]["template"]["spec"])
            result = self.render(source)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(result.stdout, "")

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
        for change in (lambda values: values.remove(next(value for value in values if value["kind"] == "Service")), lambda values: values.append(copy.deepcopy(values[2])),
                       lambda values: values[2]["spec"]["ports"][0].update(targetPort="rpc")):
            with self.subTest(change=change):
                source = self.resources()
                change(source)
                result = self.render(source)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, "")

    def test_network_policy_is_required_and_cannot_open_all_peers_or_turn_namespace_and_pod_into_or(self):
        for change in (lambda values: values.pop(),
                       lambda values: values[-1]["spec"]["ingress"][0].update({"from": [{}]}),
                       lambda values: values[-1]["spec"]["ingress"][0]["from"].append({"namespaceSelector": {}})):
            source = self.resources()
            change(source)
            result = self.render(source)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(result.stdout, "")

    def test_policies_are_applied_before_deployments_and_limitcenter_has_no_gateway_peer(self):
        resources = json.loads(self.render(self.resources()).stdout)["items"]
        kinds = [value["kind"] for value in resources]
        self.assertLess(max(index for index, kind in enumerate(kinds) if kind == "NetworkPolicy"), kinds.index("Deployment"))
        limit = next(value for value in resources if value["kind"] == "NetworkPolicy" and value["metadata"]["name"] == "limitcenter-http-ingress")
        peers = limit["spec"]["ingress"][0]["from"]
        self.assertEqual({peer["namespaceSelector"]["matchLabels"]["kubernetes.io/metadata.name"] for peer in peers},
                         {"loadup-pay", "observability"})


if __name__ == "__main__":
    unittest.main()
