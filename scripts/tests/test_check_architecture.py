"""Mutation fixtures for source and Maven ownership rules; no compiler or cluster required."""
import sys
import tempfile
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_architecture as gate


class ArchitectureRulesTest(unittest.TestCase):
    def test_business_layer_rejects_fully_qualified_remote_and_outer_types(self):
        source = '''package io.github.loadup.payment.app.service;
            class Example {
                io.github.loadup.limitcenter.client.dto.LimitReservationDTO value;
                io.github.loadup.payment.infrastructure.client.PaymentLimitHttpClient client;
            }'''
        findings = gate.java_violations(source)
        self.assertTrue(any("A5-REMOTE" in item for item in findings))
        self.assertTrue(any("A5-APP" in item for item in findings))

    def test_literals_and_comments_are_not_dependencies(self):
        source = '''package io.github.loadup.payment.app.service;
            // import io.github.loadup.limitcenter.client.Bad;
            class Example {
                String value = "io.github.loadup.payment.infrastructure.Bad";
                String text = """\norg.springframework.stereotype.Service\n""";
                /* io.github.loadup.limitcenter.client.Bad ignored; */
            }'''
        self.assertEqual([], gate.java_violations(source))

    def test_domain_keeps_only_own_pure_enums_and_errors(self):
        permitted = '''package io.github.loadup.payment.domain.model;
            import io.github.loadup.payment.client.error.PaymentResultCodeEnum;
            import io.github.loadup.payment.client.enums.PaymentAttemptState;
            import io.github.loadup.components.payment.channel.contract.Channel;'''
        self.assertEqual([], gate.java_violations(permitted))
        self.assertTrue(gate.java_violations(permitted + '\nimport org.springframework.stereotype.Service;'))
        self.assertTrue(gate.java_violations(permitted + '\nimport io.github.loadup.payment.client.dto.PaymentDTO;'))

    def test_merged_service_packages_keep_the_same_rules(self):
        self.assertTrue(gate.java_violations('''package io.github.loadup.realtime.domain.model;
            import org.springframework.data.relational.core.mapping.Table;'''))
        self.assertTrue(gate.java_violations('''package io.github.loadup.realtime.app.service;
            import io.github.loadup.payment.client.dto.PaymentDTO;'''))
        self.assertEqual([], gate.java_violations('''package io.github.loadup.realtime.infrastructure.client;
            import io.github.loadup.payment.client.dto.PaymentDTO;'''))

    def test_shared_contracts_and_clients_cannot_see_implementations(self):
        for package in ("contracts.event.payment", "payment.client.dto"):
            with self.subTest(package=package):
                self.assertTrue(gate.java_violations(f'''package io.github.loadup.{package};
                    import io.github.loadup.payment.domain.model.Payment;'''))

    def test_pom_rules_distinguish_test_fixture_dependencies(self):
        self.assertEqual([], gate.pom_violations("loadup-pay-productcenter-infrastructure",
                                               "loadup-pay-merchantcenter-service", "test"))
        self.assertTrue(gate.pom_violations("loadup-pay-productcenter-infrastructure",
                                           "loadup-pay-merchantcenter-service", "compile"))
        self.assertTrue(gate.pom_violations("loadup-pay-payment-app", "loadup-pay-limitcenter-client", "compile"))
        self.assertEqual([], gate.pom_violations("loadup-pay-payment-infrastructure",
                                               "loadup-pay-limitcenter-client", "compile"))
        self.assertTrue(gate.pom_violations("loadup-pay-payment-app", "loadup-testify-core", "runtime"))
        self.assertTrue(gate.pom_violations("loadup-pay-payment-client", "loadup-pay-payment-app", "compile"))

    def test_lower_repositories_cannot_depend_on_business_applications(self):
        self.assertTrue(gate.pom_violations("loadup-components-http", "loadup-pay-payment-client", "compile"))
        self.assertTrue(gate.pom_violations("loadup-modules-upms-app", "loadup-pay-payment-client", "compile"))
        self.assertEqual([], gate.pom_violations("loadup-pay-payment-app", "loadup-components-task", "compile"))

    def write_pom(self, root, folder, artifact, modules=(), dependencies=()):
        path = root / folder / "pom.xml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>' + artifact
                        + '</artifactId><modules>' + ''.join(f'<module>{m}</module>' for m in modules)
                        + '</modules><dependencies>' + ''.join(
                            f'<dependency><artifactId>{d}</artifactId></dependency>' for d in dependencies)
                        + '</dependencies></project>')

    def test_reactor_cycle_and_missing_module_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_pom(root, "", "root", ("a", "b"))
            self.write_pom(root, "a", "a", dependencies=("b",))
            self.write_pom(root, "b", "b", dependencies=("a",))
            self.assertTrue(any("A5-CYCLE" in item for item in gate.check_repository(root)))
            (root / "b/pom.xml").unlink()
            self.assertTrue(any("A5-REACTOR" in item for item in gate.check_repository(root)))

    def test_scan_includes_untracked_production_source_but_not_test_fixtures(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_pom(root, "", "loadup-pay-payment-app")
            source = 'package io.github.loadup.payment.app; import io.github.loadup.limitcenter.client.Bad;'
            for kind in ("main", "test"):
                file = root / f"src/{kind}/java/Example.java"
                file.parent.mkdir(parents=True)
                file.write_text(source)
            findings = gate.check_repository(root)
            self.assertEqual(1, len(findings))
            self.assertIn("src/main/java/Example.java", findings[0])


if __name__ == "__main__":
    unittest.main()
