"""Configuration lifecycle guards; uses in-memory Kubernetes responses only."""

import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("manage_config", Path(__file__).resolve().parents[1] / "manage-config.py")
config = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(config)


class ManageConfigTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name)
        self.environment = patch.dict(os.environ, CONFIG_DIR=str(self.path), CONFIG_VERSION="cfg-old",
                                      SECRET_VERSION="sec-old", RELEASE_DIR=str(self.path / "releases"))
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def test_environment_values_are_literal_and_duplicates_are_rejected_without_values(self):
        source = self.path / "secret.env"
        source.write_text("KEY=$(do-not-run)\n")
        self.assertEqual(config.env_file(source), {"KEY": "$(do-not-run)"})
        source.write_text("KEY=private-value\nKEY=another-private-value\n")
        with self.assertRaises(ValueError) as error:
            config.env_file(source)
        self.assertNotIn("private-value", str(error.exception))

    def test_environment_common_values_are_merged_with_explicit_application_overrides(self):
        app = self.path / "customer"
        app.mkdir()
        (self.path / "common.env").write_text("JWT_ISSUER_URI=https://issuer.test\nJWT_JWK_SET_URI=https://issuer.test/jwks\nMETRICS_ENABLED=false\n")
        (app / "config.env").write_text("CUSTOMER_JDBC_URL=jdbc:postgresql://db/customer\nCUSTOMER_JDBC_USERNAME=customer\nMETRICS_ENABLED=true\n")
        (app / "secret.env").write_text("CUSTOMER_JDBC_PASSWORD=keep-existing\n")
        with patch.object(config, "token_data", return_value={}):
            values = config.local_resources("customer", app)
        self.assertEqual(values[0]["JWT_ISSUER_URI"], "https://issuer.test")
        self.assertEqual(values[0]["METRICS_ENABLED"], "true")

    def test_content_versions_change_for_public_key_and_secret_independently(self):
        def versions(values):
            os.environ.pop("CONFIG_VERSION", None)
            os.environ.pop("SECRET_VERSION", None)
            with patch.object(config, "local_resources", return_value=values):
                return config.resolve_versions(["customer"], self.path)
        initial = versions(({"KEY": "value"}, {"PASSWORD": "old"}, {"jwt-public.pem": "key1"}))
        changed_key = versions(({"KEY": "value"}, {"PASSWORD": "old"}, {"jwt-public.pem": "key2"}))
        changed_secret = versions(({"KEY": "value"}, {"PASSWORD": "new"}, {"jwt-public.pem": "key1"}))
        self.assertNotEqual(initial[0], changed_key[0])
        self.assertEqual(initial[1], changed_key[1])
        self.assertEqual(initial[0], changed_secret[0])
        self.assertNotEqual(initial[1], changed_secret[1])

    def test_conflict_in_later_resource_prevents_all_mutations(self):
        existing = config.resource("Secret", "customer-secret-sec-old", {"KEY": "different"}, True)
        with patch.object(config, "local_resources", return_value=({"KEY": "new"}, {}, {})), \
                patch.object(config, "get", side_effect=[None, existing]), \
                patch.object(config, "kube", return_value={"kind": "Namespace"}) as kube, \
                patch("sys.argv", ["manage-config.py", "create", "config", "customer"]):
            with self.assertRaisesRegex(ValueError, "Changed existing"):
                config.main()
        self.assertTrue(all(call.args[0] == "get" for call in kube.call_args_list))

    def test_historical_replicaset_reference_blocks_delete(self):
        history = {"items": [{"spec": {"replicas": 0, "template": {"spec": {"containers": [{
            "envFrom": [{"configMapRef": {"name": "customer-config-cfg-old"}}]}]}}}}]}
        with patch.object(config, "get", return_value=None), patch.object(config, "kube", return_value=history) as kube:
            with self.assertRaisesRegex(ValueError, "Deletion refused"):
                config.delete_resources(["customer"], "config")
        self.assertEqual(kube.call_count, 1)

    def test_release_archive_reference_blocks_delete(self):
        archive = self.path / "releases"
        archive.mkdir()
        (archive / "old.json").write_text(json.dumps({"envFrom": [{"secretRef": {"name": "customer-secret-sec-old"}}]}))
        with patch.object(config, "get", return_value=None), patch.object(config, "kube", return_value={"items": []}):
            with self.assertRaisesRegex(ValueError, "Deletion refused"):
                config.delete_resources(["customer"], "secret")

    def test_unused_delete_uses_uid_and_resource_version_preconditions(self):
        existing = {"metadata": {"uid": "original-uid", "resourceVersion": "42"}}
        with patch.object(config, "get", return_value=existing), \
                patch.object(config, "kube", side_effect=[{"items": []}, {}]) as kube:
            config.delete_resources(["customer"], "config")
        delete = kube.call_args
        self.assertEqual(delete.args[0], "delete")
        self.assertEqual(delete.kwargs["body"]["preconditions"], {"uid": "original-uid", "resourceVersion": "42"})


if __name__ == "__main__":
    unittest.main()
