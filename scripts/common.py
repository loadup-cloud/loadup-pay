"""Shared settings and command helpers for the Pay build and deployment scripts."""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import NoReturn, Sequence

PAY_ROOT = Path(__file__).resolve().parent.parent
SCRIPT_DIR = PAY_ROOT / "scripts"
NAMESPACE = "loadup-pay"
PAY_APPS = (
    "authserver",
    "merchantcenter",
    "merchantmng",
    "productcenter",
    "productmng",
    "limitcenter",
    "payment",
    "acquire",
    "realtime",
    "reconciliation",
    "notificationcenter",
)


def fail(message: str) -> NoReturn:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def run(
    args: Sequence[str],
    *,
    capture: bool = False,
    check: bool = True,
    cwd: Path | None = None,
    env: dict[str, str] | None = None,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        [str(arg) for arg in args],
        cwd=str(cwd) if cwd is not None else None,
        env=env,
        text=True,
        capture_output=capture,
        check=False,
    )
    if check and result.returncode:
        if capture and result.stderr:
            sys.stderr.write(result.stderr)
        raise SystemExit(result.returncode)
    return result


def select_apps(scope: str) -> list[str]:
    if scope == "all":
        return list(PAY_APPS)
    if scope in PAY_APPS:
        return [scope]
    fail(f"Unknown application: {scope} (expected all or {' '.join(PAY_APPS)})")


def select_environment() -> None:
    app_env = os.environ.get("APP_ENV", "dev")
    if app_env not in {"dev", "test", "prod"}:
        fail("APP_ENV must be dev, test or prod.")
    os.environ["APP_ENV"] = app_env
    os.environ.setdefault("CONFIG_DIR", str(PAY_ROOT / "deploy" / "config" / app_env))


def require_environment_context() -> None:
    if os.environ.get("APP_ENV", "dev") != "dev" and not os.environ.get("KUBE_CONTEXT"):
        fail("Set KUBE_CONTEXT explicitly for test/prod; environment selection does not switch clusters.")


def require_command(name: str) -> None:
    if shutil.which(name) is None:
        fail(f"Required command is unavailable: {name}")


def prepare_java() -> None:
    mise = shutil.which("mise")
    if mise is not None:
        java_home = run([mise, "where", "java"], capture=True, cwd=PAY_ROOT).stdout.strip()
        if java_home:
            os.environ["JAVA_HOME"] = java_home
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        os.environ["PATH"] = str(Path(java_home) / "bin") + os.pathsep + os.environ.get("PATH", "")
    require_command("java")
    output = run(["java", "-version"], capture=True).stderr
    match = re.search(r'version "([0-9]+)', output)
    if match is None or match.group(1) != "25":
        fail("Java 25 is required; install the pinned mise runtime or set JAVA_HOME.")


def prepare_image_tag() -> None:
    tag = os.environ.get("IMAGE_TAG") or time.strftime("%Y%m%d%H%M%S") + f"-{os.getpid()}"
    if re.fullmatch(r"[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}", tag) is None:
        fail("Invalid IMAGE_TAG.")
    os.environ["IMAGE_TAG"] = tag
    os.environ.setdefault("IMAGE_PREFIX", "loadup-pay")
    os.environ.setdefault("DOCKER_CONTEXT", "orbstack")
    os.environ.setdefault("KUBE_CONTEXT", "orbstack")


def require_config_versions() -> None:
    for variable in ("CONFIG_VERSION", "SECRET_VERSION"):
        version = os.environ.get(variable, "")
        if re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?", version) is None:
            fail(f"{variable} must be an explicit DNS label of at most 32 characters.")


def require_supported_kubectl() -> None:
    output = run(
        [
            "kubectl",
            "--context",
            os.environ.get("KUBE_CONTEXT", "orbstack"),
            "--request-timeout=10s",
            "version",
            "-o",
            "json",
        ],
        capture=True,
    ).stdout
    versions = json.loads(output)
    client, server = versions["clientVersion"], versions["serverVersion"]
    minor = lambda version: int(re.match(r"[0-9]+", version["minor"]).group())
    if client["major"] != server["major"] or abs(minor(client) - minor(server)) > 1:
        fail("kubectl must be within one minor version of the target API server; update the client before deployment")


def kube_context() -> str:
    return os.environ.get("KUBE_CONTEXT", "orbstack")


def docker_context() -> str:
    return os.environ.get("DOCKER_CONTEXT", "orbstack")


def image_prefix() -> str:
    return os.environ.get("IMAGE_PREFIX", "loadup-pay")


def bootstrap_project(app: str) -> str:
    return f"{app}/{app}-bootstrap"
