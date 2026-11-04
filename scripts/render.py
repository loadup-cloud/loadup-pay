#!/usr/bin/env python3
"""Render a reviewable manifest without building images or applying resources."""

from __future__ import annotations

import os
import sys

import common
import render_deploy


def source_manifest() -> str:
    return common.run(
        [
            "kubectl",
            "--context",
            common.kube_context(),
            "--request-timeout=15s",
            "create",
            "--dry-run=client",
            "--validate=false",
            "-f",
            str(common.PAY_ROOT / "deploy" / "pay.yml"),
            "-o",
            "json",
        ],
        capture=True,
    ).stdout


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] in {"--help", "-h"}:
        print(
            "Usage: IMAGE_TAG=<tag> CONFIG_VERSION=<version> SECRET_VERSION=<version> "
            "scripts/render.py [all|application]"
        )
        print("Renders a reviewable manifest; never builds images or applies resources.")
        return
    if len(args) > 1:
        common.fail("Expected one application name or all.")
    scope = args[0] if args else "all"
    common.select_apps(scope)
    common.require_config_versions()
    if not os.environ.get("IMAGE_TAG"):
        common.fail("Rendering requires an explicit IMAGE_TAG.")
    common.prepare_image_tag()
    common.require_command("kubectl")
    sys.stdout.write(
        render_deploy.render(
            source_manifest(),
            scope,
            common.image_prefix(),
            os.environ["IMAGE_TAG"],
            os.environ["CONFIG_VERSION"],
            os.environ["SECRET_VERSION"],
        )
    )


if __name__ == "__main__":
    main()
