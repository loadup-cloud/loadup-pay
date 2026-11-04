#!/usr/bin/env python3
"""Validate/create configuration, then build and deploy with scripts/deploy.py."""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys

import authserver
import common
import manage_config


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] in {"--help", "-h"}:
        print("Usage: scripts/up.py [all|application]")
        print("Validate/create configuration, then build and deploy using scripts/deploy.py.")
        print("APP_ENV selects dev (default), test or prod; edit deploy/config/<APP_ENV> directly.")
        print("Snapshot versions are derived from content; explicit versions override them.")
        print("Use SKIP_BUILD=1 IMAGE_TAG=<existing-tag> to reuse existing images.")
        print("Dev all/authserver prepares missing local signing credentials; existing values are preserved.")
        print("Builds the required sibling framework components and waits for AuthServer before business applications.")
        print("Does not install the gateway controller, database or S3.")
        return
    if len(args) > 1:
        common.fail("Expected one application name or all.")
    scope = args[0] if args else "all"
    apps = common.select_apps(scope)
    common.select_environment()
    common.require_environment_context()
    if os.environ.get("SKIP_BUILD") == "1" and not os.environ.get("IMAGE_TAG"):
        common.fail("SKIP_BUILD=1 requires the existing IMAGE_TAG.")
    common.prepare_image_tag()
    if os.environ["APP_ENV"] == "dev" and scope in {"all", "authserver"}:
        common.prepare_java()
        try:
            authserver.prepare_dev()
        except (ValueError, OSError, subprocess.TimeoutExpired) as error:
            common.fail(f"AuthServer preparation failed: {error}")
    try:
        versions = manage_config.resolve_versions(apps, Path(os.environ["CONFIG_DIR"]).resolve())
    except (UnicodeError, ValueError, OSError, KeyError) as error:
        common.fail(f"Configuration operation failed: {error}")
    os.environ["CONFIG_VERSION"], os.environ["SECRET_VERSION"] = versions
    common.require_config_versions()
    manage_config.execute("create", "config", apps)
    common.run([sys.executable, str(common.SCRIPT_DIR / "deploy.py"), scope], cwd=common.PAY_ROOT)


if __name__ == "__main__":
    main()
