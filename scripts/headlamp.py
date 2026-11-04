#!/usr/bin/env python3
"""Manage the optional local Headlamp UI through the OrbStack Kubernetes context."""

from __future__ import annotations

import os
import sys

import common


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] in {"--help", "-h"}:
        print("Usage: scripts/headlamp.py {install|token|open}")
        print("Manages the optional local Headlamp UI through the selected Kubernetes context.")
        return
    command = args[0] if args else "open"
    context = common.kube_context()
    if command == "install":
        common.run(["kubectl", "--context", context, "apply", "-f", str(common.PAY_ROOT / "deploy" / "headlamp.yml")])
        common.run(
            [
                "kubectl",
                "--context",
                context,
                "-n",
                "headlamp",
                "rollout",
                "status",
                "deployment/headlamp",
                "--timeout=300s",
            ]
        )
    elif command == "token":
        common.run(
            ["kubectl", "--context", context, "-n", "headlamp", "create", "token", "pay-operator", "--duration=8h"]
        )
    elif command == "open":
        print("Headlamp: http://127.0.0.1:14466 (keep this process running)")
        common.run(
            [
                "kubectl",
                "--context",
                context,
                "-n",
                "headlamp",
                "port-forward",
                "--address=127.0.0.1",
                "service/headlamp",
                "14466:80",
            ]
        )
    else:
        common.fail("Usage: scripts/headlamp.py {install|token|open}")


if __name__ == "__main__":
    main()
