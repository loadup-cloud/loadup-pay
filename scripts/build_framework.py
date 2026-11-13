#!/usr/bin/env python3
"""Clean-install the complete framework reactor so no component jar is stale."""

from __future__ import annotations

import os
from pathlib import Path
import sys

import common

TARGETED_PROJECTS = ",".join(
    [
        "loadup-framework-bom",
        "loadup-components/integration/loadup-components-http",
        "loadup-components/security/loadup-components-service-auth",
        "loadup-components/security/loadup-components-oauth-server/loadup-components-oauth-server-api",
        "loadup-components/security/loadup-components-oauth-server/loadup-components-oauth-server-core",
        "loadup-components/security/loadup-components-kms/loadup-components-kms-binder-local",
    ]
)
MVND_FLAGS = [
    "--batch-mode",
    "clean",
    "install",
    "-Dmaven.test.skip=true",
    "-Dspotless.skip=true",
    "-Dspotbugs.skip=true",
]


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] in {"--help", "-h"}:
        print("Usage: scripts/build_framework.py [extra mvnd arguments]")
        print("Clean-install the complete framework reactor by default; tests are skipped.")
        print("FRAMEWORK_SCOPE=targeted installs only the components the Pay applications use.")
        print("FRAMEWORK_DIR defaults to the sibling loadup-framework repository.")
        return
    common.prepare_java()
    common.require_command("mvnd")
    framework_dir = Path(os.environ.get("FRAMEWORK_DIR", str(common.PAY_ROOT.parent / "loadup-framework")))
    if not (framework_dir / "pom.xml").is_file():
        common.fail(
            "Framework checkout is unavailable; set FRAMEWORK_DIR or explicitly use SKIP_FRAMEWORK_BUILD=1 "
            "with installed artifacts."
        )
    scope = os.environ.get("FRAMEWORK_SCOPE", "all")
    if scope == "all":
        common.run(["mvnd", *MVND_FLAGS, *args], cwd=framework_dir)
        return
    if scope != "targeted":
        common.fail("FRAMEWORK_SCOPE must be all or targeted.")
    common.run(["mvnd", *MVND_FLAGS, "-pl", TARGETED_PROJECTS, "-am", *args], cwd=framework_dir)


if __name__ == "__main__":
    main()
