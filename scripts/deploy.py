#!/usr/bin/env python3
"""Build images, apply selected applications and wait for ready endpoints."""

from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

import common
import render_deploy
from render import source_manifest


def kubectl_jsonpath(reference: str) -> tuple[bool, str]:
    result = common.run(
        [
            "kubectl",
            "--context",
            common.kube_context(),
            "-n",
            common.NAMESPACE,
            "--request-timeout=10s",
            "get",
            reference,
            "-o",
            "jsonpath={.immutable}",
        ],
        capture=True,
        check=False,
    )
    return result.returncode == 0, result.stdout.strip()


def verify(scope: str) -> None:
    common.run([sys.executable, str(common.SCRIPT_DIR / "verify.py"), scope], cwd=common.PAY_ROOT)


def image_present(image: str) -> bool:
    result = subprocess.run(
        [
            "docker",
            "--context",
            common.docker_context(),
            "image",
            "inspect",
            image,
            "--format",
            "{{.Id}}",
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return result.returncode == 0


def write_release(release_file: Path, manifest: str) -> None:
    release_file.parent.mkdir(parents=True, exist_ok=True)
    if release_file.exists():
        if release_file.read_text(encoding="utf-8") != manifest:
            common.fail("An existing release record has different content.")
        return
    descriptor = os.open(release_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        output.write(manifest)


def apply_all(manifest: str, release_dir: Path, apps: list[str]) -> None:
    data = json.loads(manifest)
    first = [
        item
        for item in data["items"]
        if item["kind"] == "Namespace" or item["metadata"]["name"] in {"oauth2-server", "oauth2-server-http-ingress"}
    ]
    remaining = [item for item in data["items"] if item not in first]
    (release_dir / "oauth2-server.json").write_text(
        json.dumps({"apiVersion": "v1", "kind": "List", "items": first}), encoding="utf-8"
    )
    (release_dir / "business.json").write_text(
        json.dumps({"apiVersion": "v1", "kind": "List", "items": remaining}), encoding="utf-8"
    )
    common.run(
        ["kubectl", "--context", common.kube_context(), "apply", "-f", str(release_dir / "oauth2-server.json")]
    )
    verify("oauth2-server")
    # Roll consumers one at a time to bound startup CPU and database connections.
    for app in apps:
        if app == "oauth2-server":
            continue
        selected = [
            item
            for item in remaining
            if item["metadata"]["name"] in {app, f"{app}-http-ingress"}
        ]
        if len(selected) != 3:
            common.fail("Application rollout requires a NetworkPolicy, Deployment and Service")
        application = release_dir / "application.json"
        application.write_text(json.dumps({"apiVersion": "v1", "kind": "List", "items": selected}), encoding="utf-8")
        common.run(["kubectl", "--context", common.kube_context(), "apply", "-f", str(application)])
        verify(app)


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] in {"--help", "-h"}:
        print("Usage: scripts/deploy.py [all|application]")
        print("Builds JARs/images, applies only selected applications, then waits for ready endpoints.")
        print("Requires CONFIG_VERSION and SECRET_VERSION of existing immutable configuration snapshots.")
        print(
            "For previously built images: SKIP_BUILD=1 IMAGE_TAG=<existing-tag> "
            "CONFIG_VERSION=<version> SECRET_VERSION=<version> scripts/deploy.py [scope]"
        )
        return
    if len(args) > 1:
        common.fail("Expected one application name or all.")
    scope = args[0] if args else "all"
    apps = common.select_apps(scope)
    common.select_environment()
    common.require_environment_context()
    common.require_config_versions()
    common.require_command("kubectl")
    common.require_command("docker")
    if os.environ.get("SKIP_BUILD") == "1" and not os.environ.get("IMAGE_TAG"):
        common.fail("SKIP_BUILD=1 requires the existing IMAGE_TAG.")
    common.prepare_image_tag()
    common.require_supported_kubectl()
    with tempfile.TemporaryDirectory(prefix="pay-release-") as temporary:
        release_temp = Path(temporary)
        manifest = render_deploy.render(
            source_manifest(),
            scope,
            common.image_prefix(),
            os.environ["IMAGE_TAG"],
            os.environ["CONFIG_VERSION"],
            os.environ["SECRET_VERSION"],
        )
        (release_temp / "manifest.json").write_text(manifest, encoding="utf-8")
        common.run(
            [
                "kubectl",
                "--context",
                common.kube_context(),
                "--request-timeout=10s",
                "get",
                "--raw=/readyz",
            ],
            capture=True,
        )
        for app in apps:
            for reference in (
                f"configmap/{app}-config-{os.environ['CONFIG_VERSION']}",
                f"secret/{app}-secret-{os.environ['SECRET_VERSION']}",
            ):
                present, immutable = kubectl_jsonpath(reference)
                if not present:
                    print(
                        f"Release configuration is missing or unreadable: {reference} "
                        f"({common.kube_context()}/{common.NAMESPACE}).",
                        file=sys.stderr,
                    )
                    print(
                        "CONFIG_VERSION/SECRET_VERSION select existing snapshots; they do not create configuration.",
                        file=sys.stderr,
                    )
                    print(
                        "Prepare ConfigMaps, environment Secrets and AuthServer signing-key Secret for every "
                        "selected application; see deploy/CONFIGURATION.md.",
                        file=sys.stderr,
                    )
                    raise SystemExit(1)
                if immutable != "true":
                    common.fail(f"Release configuration must be immutable: {reference}")
            if app == "oauth2-server":
                present, immutable = kubectl_jsonpath(f"secret/oauth2-server-files-{os.environ['SECRET_VERSION']}")
                if not present or immutable != "true":
                    common.fail("AuthServer signing files must be an immutable versioned Secret.")
        if os.environ.get("SKIP_BUILD") != "1":
            common.run([sys.executable, str(common.SCRIPT_DIR / "build.py"), scope], cwd=common.PAY_ROOT)
        for app in apps:
            image = f"{common.image_prefix()}/{app}:{os.environ['IMAGE_TAG']}"
            if not image_present(image):
                common.fail(f"Image is unavailable: {image}")
        release_dir = Path(os.environ.get("RELEASE_DIR", str(common.PAY_ROOT / "deploy" / "releases")))
        release_file = release_dir / (
            f"{scope}-{os.environ['IMAGE_TAG']}-{os.environ['CONFIG_VERSION']}-{os.environ['SECRET_VERSION']}.json"
        )
        write_release(release_file, manifest)
        print(
            f"Deploying {scope} to {common.kube_context()}/{common.NAMESPACE} with "
            f"IMAGE_TAG={os.environ['IMAGE_TAG']} CONFIG_VERSION={os.environ['CONFIG_VERSION']} "
            f"SECRET_VERSION={os.environ['SECRET_VERSION']}"
        )
        print(f"Release manifest: {release_file} (not proof of successful rollout)")
        if scope == "all":
            apply_all(manifest, release_temp, apps)
        else:
            common.run(["kubectl", "--context", common.kube_context(), "apply", "-f", str(release_file)])
        verify(scope)


if __name__ == "__main__":
    main()
