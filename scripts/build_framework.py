#!/usr/bin/env python3
"""Clean-install the framework artifacts required by the Pay applications."""

from __future__ import annotations

import os
from pathlib import Path
import sys

import common

TARGETED_PROJECTS = ",".join(
    [
        "loadup-dependencies",
        "loadup-components/integration/loadup-components-http",
        "loadup-components/security/loadup-components-resource-server",
        "loadup-components/security/loadup-components-authserver/loadup-components-authserver-binder-sas",
        "loadup-components/security/loadup-components-kms/loadup-components-kms-binder-local",
    ]
)
MVND_FLAGS = [
    "--batch-mode",
    "clean",
    "install",
    "-Dmaven.test.skip=true",
    "-Dskip.spotless=true",
    "-Dskip.spotbugs=true",
]


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] in {"--help", "-h"}:
        print("Usage: scripts/build_framework.py [extra mvnd arguments]")
        print("Clean-install the BOM and the HTTP, resource-server, SAS and local KMS components with their dependencies.")
        print("FRAMEWORK_SCOPE=all builds the complete framework reactor; use it to bootstrap a clean checkout.")
        print("FRAMEWORK_DIR defaults to the sibling loadup-framework repository; tests are skipped.")
        return
    common.prepare_java()
    common.require_command("mvnd")
    framework_dir = Path(os.environ.get("FRAMEWORK_DIR", str(common.PAY_ROOT.parent / "loadup-framework")))
    if not (framework_dir / "pom.xml").is_file():
        common.fail(
            "Framework checkout is unavailable; set FRAMEWORK_DIR or explicitly use SKIP_FRAMEWORK_BUILD=1 "
            "with installed artifacts."
        )
    if os.environ.get("FRAMEWORK_SCOPE", "targeted") == "all":
        common.run(["mvnd", *MVND_FLAGS, *args], cwd=framework_dir)
        return
    common.run(["mvnd", *MVND_FLAGS, "-pl", TARGETED_PROJECTS, "-am", *args], cwd=framework_dir)


if __name__ == "__main__":
    main()
