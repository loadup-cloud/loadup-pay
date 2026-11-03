"""Render contracts only: no cluster, chart installation, container, or financial request."""
from pathlib import Path
import re
import subprocess
import unittest

import yaml


class GatewayChartTest(unittest.TestCase):
    chart = Path(__file__).resolve().parents[2] / "deploy" / "gateway"

    def render(self, *settings, namespace="loadup-pay", version="1.36.5"):
        args = ["helm", "template", "loadup-pay-gateway", str(self.chart), "--namespace", namespace,
                "--kube-version", version]
        for setting in settings:
            args.extend(["--set-string", setting])
        return subprocess.run(args, text=True, capture_output=True)

    def resources(self, *settings):
        result = self.render("hostname=pay.example.com", "tlsSecret.name=pay-tls", *settings)
        self.assertEqual(result.returncode, 0, result.stderr)
        return [value for value in yaml.safe_load_all(result.stdout) if value]

    def test_required_inputs_namespace_and_supported_kubernetes_range_are_enforced(self):
        for settings in ((), ("hostname=https://example.com", "tlsSecret.name=pay-tls"),
                         ("hostname=bad..example", "tlsSecret.name=pay-tls"),
                         ("hostname=example.com", "tlsSecret.name=../secret")):
            self.assertNotEqual(self.render(*settings).returncode, 0)
        for arguments in ({"namespace": "other"}, {"version": "1.32.9"}, {"version": "1.37.0"}):
            self.assertNotEqual(self.render("hostname=pay.example.com", "tlsSecret.name=pay-tls", **arguments).returncode, 0)

    def test_public_paths_route_to_their_owner_and_every_internal_or_actuator_path_has_no_https_backend(self):
        resources = self.resources()
        routes = [value for value in resources if value["kind"] == "HTTPRoute"
                  and value["spec"]["parentRefs"][0]["sectionName"] == "https"]

        def backends(path, method="POST"):
            result = set()
            for route in routes:
                for rule in route["spec"]["rules"]:
                    for match in rule["matches"]:
                        prefix = match["path"]["value"]
                        matched = path == prefix or (match["path"]["type"] == "PathPrefix" and path.startswith(prefix + "/"))
                        if matched and match["method"] == method:
                            result.update(backend["name"] for backend in rule["backendRefs"])
            return result

        for path, owner in {"/api/customer/authorize": "customer", "/api/product/limit-publications/detail": "product",
                            "/api/acquire/orders/create": "acquire", "/api/cashier/order/summary": "acquire",
                            "/api/payment/channel-callbacks/endpoint/refund": "payment",
                            "/api/realtime/trades/detail": "realtime", "/api/reconciliation/batches/detail": "reconciliation"}.items():
            self.assertEqual(backends(path), {owner})
        self.assertEqual(backends("/checkout.js", "GET"), {"acquire"})
        for path in ("/", "/actuator/health", "/api/internal", "/api/limitcenter/reserve", "/api/customer-other", "/checkout.js/extra"):
            self.assertFalse(backends(path))
            self.assertFalse(backends(path, "GET"))
        contracts = self.chart.parents[1].glob("*/*-client/src/main/java/io/github/loadup/pay/*/client/http/*HttpService.java")
        paths = [path for contract in contracts for path in re.findall(r'"(/api/internal/[^"\n]+)"', contract.read_text())]
        self.assertEqual(len(paths), 44)
        for path in paths:
            self.assertFalse(backends(path), path)

    def test_policies_preserve_native_callback_identity_and_define_bounded_transport_without_retries(self):
        resources = self.resources()
        policies = {value["metadata"]["name"]: value for value in resources if value["kind"] == "BackendTrafficPolicy"}
        callbacks = next(value for value in resources if value["kind"] == "HTTPRoute" and value["metadata"]["name"] == "loadup-pay-callbacks")
        rule = callbacks["spec"]["rules"][0]
        self.assertEqual([value["type"] for value in rule["filters"]], ["RequestHeaderModifier"])
        modifier = rule["filters"][0]["requestHeaderModifier"]
        self.assertEqual(set(modifier), {"remove"})
        self.assertTrue(all(name.startswith("x-envoy-") for name in modifier["remove"]))
        self.assertIn("x-envoy-retry-on", modifier["remove"])
        self.assertEqual(rule["timeouts"], {"request": "15s", "backendRequest": "15s"})
        self.assertEqual(policies["loadup-pay-callbacks"]["spec"]["requestBuffer"]["limit"], "64Ki")
        for policy in policies.values():
            self.assertIn("requestBuffer", policy["spec"])
            self.assertEqual(policy["spec"]["rateLimit"]["local"]["rules"][0]["limit"]["unit"], "Second")
        self.assertFalse(any(value["kind"] == "Secret" for value in resources))
        serialized = yaml.safe_dump_all(resources).lower()
        for setting in ("retry:", "retries:", "urlrewrite", "requestmirror", "extproc"):
            self.assertNotIn(setting, serialized)
        proxy = next(value for value in resources if value["kind"] == "EnvoyProxy")
        log = proxy["spec"]["telemetry"]["accessLog"]["settings"][0]["format"]["text"]
        self.assertNotIn("%REQ(", log)
        self.assertNotIn("%RESP(", log)
        redirect = next(value for value in resources if value["kind"] == "HTTPRoute" and value["metadata"]["name"].endswith("https-redirect"))
        self.assertNotIn("backendRefs", redirect["spec"]["rules"][0])
        self.assertEqual(redirect["spec"]["rules"][0]["filters"][0]["requestRedirect"]["statusCode"], 308)

    def test_cross_namespace_certificate_requires_a_grant_for_only_the_named_secret(self):
        self.assertFalse(any(value["kind"] == "ReferenceGrant" for value in self.resources()))
        grant = next(value for value in self.resources("tlsSecret.namespace=certificates") if value["kind"] == "ReferenceGrant")
        self.assertEqual(grant["metadata"]["namespace"], "certificates")
        self.assertEqual(grant["spec"]["from"], [{"group": "gateway.networking.k8s.io", "kind": "Gateway", "namespace": "loadup-pay"}])
        self.assertEqual(grant["spec"]["to"], [{"group": "", "kind": "Secret", "name": "pay-tls"}])
