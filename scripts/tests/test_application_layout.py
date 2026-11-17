"""Verify build selection and artifact ownership against source POMs without building."""
import sys
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import common


class ApplicationLayoutTest(unittest.TestCase):
    def test_every_application_selects_its_executable_module(self):
        for app in common.PAY_APPS:
            with self.subTest(app=app):
                artifact = f"loadup-pay-{app}"
                expected = artifact if app in {"admin", "notificationcenter", "oauth2-server"} else f"{artifact}/{artifact}-bootstrap"
                selected = common.bootstrap_project(app)
                self.assertEqual(expected, selected)
                pom = ET.parse(common.PAY_ROOT / selected / "pom.xml").getroot()
                self.assertEqual(Path(selected).name, pom.findtext("{*}artifactId"))
                self.assertTrue(any(plugin.findtext("{*}artifactId") == "spring-boot-maven-plugin"
                                    for plugin in pom.findall("./{*}build/{*}plugins/{*}plugin")))

    def test_unknown_app_cannot_select_a_filesystem_path(self):
        with self.assertRaises(SystemExit):
            common.bootstrap_project("../another-repository")


if __name__ == "__main__":
    unittest.main()
