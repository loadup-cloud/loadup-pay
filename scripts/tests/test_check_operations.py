"""Synthetic coverage for the offline operations checker."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import check_operations
import common
import render_deploy

STANDALONE = {"oauth2-server", "admin", "notificationcenter"}


class OperationsCheckerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for app in common.PAY_APPS:
            self._application(app)
            self._environment(app)
        self._manifest()
        (self.root / "deploy/prometheus-scrape.yml").write_text(yaml.safe_dump({
            "serverFiles": {"prometheus.yml": {"scrape_configs": [{
                "job_name": "loadup-pay", "metrics_path": "/actuator/prometheus",
                "relabel_configs": [{"source_labels": ["__meta_kubernetes_pod_label_app"], "target_label": "application"}]
            }]}}
        }))

    def tearDown(self):
        self.temp.cleanup()

    def _app_path(self, app):
        module = self.root / f"loadup-pay-{app}"
        base = module if app in STANDALONE else module / f"loadup-pay-{app}-bootstrap"
        return base / "src/main/resources/application.yml"

    def _application(self, app):
        path = self._app_path(app)
        path.parent.mkdir(parents=True)
        locations = [f"classpath:db/migration/pay/{app}"]
        if app == "oauth2-server":
            locations = ["classpath:db/loadup-oauth", "classpath:db/migration/pay/oauth2-server", "classpath:db/migration/framework/audit"]
        elif app == "admin":
            locations += ["classpath:db/migration/framework/upms", "classpath:db/migration/framework/audit"]
        elif app == "notificationcenter":
            locations += ["classpath:db/migration/modules/gotone", "classpath:db/migration/modules/inbox"]
        elif app == "reconciliation":
            locations += ["classpath:db/migration/framework/file", "classpath:db/migration/framework/transfer"]
        data = {
            "spring": {"application": {"name": app}, "lifecycle": {"timeout-per-shutdown-phase": "20s"}},
            "server": {"shutdown": "graceful"},
            "loadup": {
                "deployment": {"code": "${DEPLOYMENT_CODE:}"},
                "database": {"schema": "${DEPLOYMENT_DATABASE_SCHEMA:}"},
                "flyway": {"locations": locations},
            },
        }
        if app == "payment":
            data["loadup"]["http"] = {"clients": {"acquire": {"base-url": "http://acquire:8080", "internal-service": True},
                                                          "alipay": {"base-url": "https://external.example"}}}
        path.write_text(yaml.safe_dump(data, sort_keys=False), encoding="utf-8")

    def _environment(self, app):
        variable = app.upper().replace("-", "_")
        for environment in ("dev", "test", "prod"):
            path = self.root / "deploy/config" / environment / app / "config.env"
            path.parent.mkdir(parents=True)
            jdbc = f"{variable}_JDBC_URL=" + (f"jdbc:postgresql://db:5432/{app}" if environment == "dev" else "")
            username = f"{variable}_JDBC_USERNAME=admin" if environment == "dev" else f"{variable}_JDBC_USERNAME="
            path.write_text(f"DEPLOYMENT_CODE=shared\nDEPLOYMENT_DATABASE_SCHEMA=schema_{app}\n{jdbc}\n{username}\n")

    def _manifest(self):
        docs = []
        for app in common.PAY_APPS:
            docs.extend([
                {"apiVersion": "apps/v1", "kind": "Deployment", "metadata": {"name": app, "namespace": "loadup-pay"},
                 "spec": {"selector": {"matchLabels": {"app": app}}, "template": {"metadata": {"labels": {"app": app}, "annotations": {"prometheus.io/scrape": "true", "prometheus.io/path": "/actuator/prometheus", "prometheus.io/port": "8080"}},
                           "spec": {"terminationGracePeriodSeconds": 60, "containers": [{"name": app, "ports": [{"name": "http", "containerPort": 8080}],
                                   "lifecycle": {"preStop": {"exec": {"command": ["/bin/sh", "-c", "sleep 5"]}}},
                                   "startupProbe": {"httpGet": {"path": "/actuator/health/liveness", "port": "http"}},
                                   "readinessProbe": {"httpGet": {"path": "/actuator/health/readiness", "port": "http"}},
                                   "livenessProbe": {"httpGet": {"path": "/actuator/health/liveness", "port": "http"}}}]}}}},
                {"apiVersion": "v1", "kind": "Service", "metadata": {"name": app, "namespace": "loadup-pay"}, "spec": {"selector": {"app": app}, "ports": [{"name": "http", "port": 8080, "targetPort": "http"}]}},
                {"apiVersion": "networking.k8s.io/v1", "kind": "NetworkPolicy", "metadata": {"name": f"{app}-http-ingress", "namespace": "loadup-pay"}, "spec": render_deploy.http_ingress_spec(app)},
            ])
        path = self.root / "deploy/pay.yml"
        path.parent.mkdir(parents=True)
        path.write_text(yaml.safe_dump_all(docs), encoding="utf-8")

    def _documents(self):
        path = self.root / "deploy/pay.yml"
        docs = list(yaml.safe_load_all(path.read_text(encoding="utf-8")))
        return path, docs

    def _deployment(self, docs, app):
        return next(doc for doc in docs if doc.get("kind") == "Deployment" and doc["metadata"]["name"] == app)

    def test_synthetic_repository_passes_with_blank_template_jdbc_values(self):
        self.assertEqual([], check_operations.check_repository(self.root))

    def test_duplicate_yaml_keys_are_rejected_in_application_file(self):
        self._app_path("payment").write_text("kind: Service\nkind: Deployment\n", encoding="utf-8")
        self.assertTrue(any(f.startswith("APP001 payment") for f in check_operations.check_repository(self.root)))

    def test_roster_drift_is_rejected(self):
        with patch.object(render_deploy, "APPS", set(common.PAY_APPS) | {"rogue"}):
            self.assertTrue(any(f.startswith("ROSTER001") for f in check_operations.check_repository(self.root)))

    def test_shared_database_target_and_code_drift_are_rejected(self):
        path = self.root / "deploy/config/dev/admin/config.env"
        path.write_text(path.read_text().replace("shared", "other").replace("schema_admin", "schema_oauth2-server").replace("jdbc:postgresql://db:5432/admin", "jdbc:postgresql://db:5432/oauth2-server"))
        findings = check_operations.check_repository(self.root)
        self.assertTrue(any(f.startswith("ENV004 dev") for f in findings))
        self.assertTrue(any(f.startswith("ENV005 dev") for f in findings))

    def test_connection_options_do_not_hide_the_same_database_and_schema(self):
        admin = self.root / "deploy/config/dev/admin/config.env"
        admin.write_text(admin.read_text().replace("schema_admin", "schema_oauth2-server").replace(
            "jdbc:postgresql://db:5432/admin", "jdbc:postgresql://db:5432/oauth2-server?sslmode=require"))
        self.assertTrue(any(f.startswith("ENV004 dev") for f in check_operations.check_repository(self.root)))

    def test_extra_workload_and_conflicting_flattened_keys_fail_closed(self):
        manifest, docs = self._documents()
        docs.append({"kind": "Deployment", "metadata": {"name": "rogue"}})
        manifest.write_text(yaml.safe_dump_all(docs))
        self.assertTrue(any(f.startswith("ROSTER003") for f in check_operations.check_repository(self.root)))
        path = self._app_path("payment")
        path.write_text(path.read_text() + "\nloadup.database.schema: other\n")
        self.assertTrue(any(f.startswith("APP001 payment") for f in check_operations.check_repository(self.root)))

    def test_shared_jdbc_target_with_different_schemas_is_allowed(self):
        admin = self.root / "deploy/config/dev/admin/config.env"
        admin.write_text(admin.read_text().replace("jdbc:postgresql://db:5432/admin", "jdbc:postgresql://db:5432/shared"))
        oauth = self.root / "deploy/config/dev/oauth2-server/config.env"
        oauth.write_text(oauth.read_text().replace("jdbc:postgresql://db:5432/oauth2-server", "jdbc:postgresql://db:5432/shared"))
        findings = check_operations.check_repository(self.root)
        self.assertFalse(any(f.startswith("ENV004 dev") for f in findings))

    def test_foreign_flyway_path_and_missing_probe_are_rejected(self):
        path = self._app_path("payment")
        data = yaml.safe_load(path.read_text())
        data["loadup"]["flyway"]["locations"] = ["classpath:db/migration/pay/merchantcenter"]
        path.write_text(yaml.safe_dump(data, sort_keys=False))
        manifest, docs = self._documents()
        del self._deployment(docs, "payment")["spec"]["template"]["spec"]["containers"][0]["readinessProbe"]
        manifest.write_text(yaml.safe_dump_all(docs))
        findings = check_operations.check_repository(self.root)
        self.assertTrue(any(f.startswith("APP006 payment") for f in findings))
        self.assertTrue(any(f.startswith("K8S008 payment") for f in findings))

    def test_payment_port_and_shutdown_budget_are_rejected(self):
        manifest, docs = self._documents()
        deployment = self._deployment(docs, "payment")
        pod = deployment["spec"]["template"]["spec"]
        pod["containers"][0]["ports"][0]["containerPort"] = 9090
        pod["terminationGracePeriodSeconds"] = 10
        manifest.write_text(yaml.safe_dump_all(docs))
        findings = check_operations.check_repository(self.root)
        self.assertTrue(any(f.startswith("K8S004 payment") for f in findings))
        self.assertTrue(any(f.startswith("K8S010 payment") for f in findings))

    def test_effective_environment_rejects_shared_accounts_and_driver_overrides(self):
        directory = self.root / "deploy/config/dev"
        path = directory / "payment/config.env"
        path.write_text(path.read_text() + "SPRING_DATASOURCE_HIKARI_SCHEMA=rogue\n")
        findings = check_operations.check_repository(self.root, directory)
        self.assertTrue(any(f.startswith("ENV008") for f in findings))
        self.assertTrue(any(f.startswith("ENV009") for f in findings))

    def test_internal_profile_client_requires_deployment_header_flag(self):
        profile = self._app_path("payment").with_name("application-local.yml")
        profile.write_text("loadup.http.clients.acquire.base-url: http://acquire:8080\n")
        self.assertTrue(any(f.startswith("APP008 payment") for f in check_operations.check_repository(self.root)))

    def test_metrics_scraper_drift_is_rejected(self):
        path = self.root / "deploy/prometheus-scrape.yml"
        path.write_text(path.read_text().replace("/actuator/prometheus", "/wrong"))
        self.assertTrue(any(f.startswith("METRICS001") for f in check_operations.check_repository(self.root)))

    def test_effective_environment_requires_nonempty_database_settings(self):
        findings = check_operations.check_repository(self.root, self.root / "deploy/config/test")
        self.assertTrue(any(f.startswith("ENV007 effective/oauth2-server") for f in findings))


if __name__ == "__main__":
    unittest.main()
