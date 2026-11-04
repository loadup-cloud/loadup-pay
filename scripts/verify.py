#!/usr/bin/env python3
"""Wait for selected deployments and their HTTP service endpoints."""

from __future__ import annotations

import json
import sys

import common


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] in {"--help", "-h"}:
        print("Usage: scripts/verify.py [all|application]")
        print("Checks selected Deployment rollout status and ready HTTP EndpointSlice addresses.")
        return
    scope = args[0] if args else "all"
    apps = common.select_apps(scope)
    common.require_command("kubectl")
    for app in apps:
        common.run(
            [
                "kubectl",
                "--context",
                common.kube_context(),
                "-n",
                common.NAMESPACE,
                "rollout",
                "status",
                f"deployment/{app}",
                "--timeout=660s",
            ]
        )
        output = common.run(
            [
                "kubectl",
                "--context",
                common.kube_context(),
                "-n",
                common.NAMESPACE,
                "get",
                "endpointslices",
                "-l",
                f"kubernetes.io/service-name={app}",
                "-o",
                "json",
            ],
            capture=True,
        ).stdout
        data = json.loads(output)
        ready = any(
            endpoint.get("conditions", {}).get("ready") and endpoint.get("addresses")
            for slice_ in data["items"]
            for endpoint in slice_.get("endpoints", [])
        )
        if not ready:
            common.fail("No ready service endpoint")
    print(f"PASS: {' '.join(apps)} are ready and their Services have ready endpoints.")


if __name__ == "__main__":
    main()
