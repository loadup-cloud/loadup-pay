#!/usr/bin/env python3
"""Build clean application JARs and local Docker images."""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys

import common


def image_exists(image: str) -> bool:
    result = subprocess.run(
        ["docker", "--context", common.docker_context(), "image", "inspect", image],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return result.returncode == 0


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] in {"--help", "-h"}:
        print("Usage: scripts/build.py [all|application] [extra mvnd arguments]")
        print("Installs the complete sibling framework reactor, then builds clean JARs and local Docker images;")
        print("tests are skipped. Set SKIP_FRAMEWORK_BUILD=1 only when matching framework artifacts are installed.")
        return
    scope = args.pop(0) if args else "all"
    apps = common.select_apps(scope)
    common.prepare_java()
    common.prepare_image_tag()
    common.require_command("mvnd")
    common.require_command("docker")
    common.run(["docker", "--context", common.docker_context(), "info", "--format", "{{.OSType}}"], capture=True)
    for app in apps:
        image = f"{common.image_prefix()}/{app}:{os.environ['IMAGE_TAG']}"
        if image_exists(image):
            common.fail(f"Image tag already exists: {image}. Choose a new tag or deploy with SKIP_BUILD=1.")
    if os.environ.get("SKIP_FRAMEWORK_BUILD") != "1":
        common.run([sys.executable, str(common.SCRIPT_DIR / "build_framework.py")], cwd=common.PAY_ROOT)
    projects = ",".join(common.bootstrap_project(app) for app in apps)
    common.run(
        [
            "mvnd",
            "--batch-mode",
            "clean",
            "package",
            "-pl",
            projects,
            "-am",
            "-Dmaven.test.skip=true",
            "-Dspotless.skip=true",
            "-Dspotbugs.skip=true",
            *args,
        ],
        cwd=common.PAY_ROOT,
    )
    for app in apps:
        module = common.PAY_ROOT / ("loadup-pay-oauth2-server" if app == "oauth2-server" else app)
        artifact = "loadup-pay-oauth2-server" if app == "oauth2-server" else f"{app}-bootstrap"
        target = module / ("target" if app == "oauth2-server" else f"{app}-bootstrap/target")
        jars = sorted(target.glob(f"{artifact}-*.jar"))
        if len(jars) != 1:
            common.fail(f"Expected one executable JAR for {app}; found {len(jars)}.")
        os.chmod(jars[0], 0o644)
        image = f"{common.image_prefix()}/{app}:{os.environ['IMAGE_TAG']}"
        common.run(
            [
                "docker",
                "--context",
                common.docker_context(),
                "build",
                "--build-arg",
                f"JAR_FILE={jars[0].relative_to(common.PAY_ROOT)}",
                "--tag",
                image,
                ".",
            ],
            cwd=common.PAY_ROOT,
        )
        print(f"Built {image}")
    print(f"Build complete: IMAGE_TAG={os.environ['IMAGE_TAG']}")


if __name__ == "__main__":
    main()
