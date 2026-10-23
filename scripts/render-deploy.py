#!/usr/bin/env python3
"""Select application resources and bind immutable image tags without rewriting source manifests."""
import json
import re
import sys

scope, prefix, tag = sys.argv[1:]
apps = {"customer", "product", "limitcenter", "payment", "acquire", "realtime"}
if scope != "all" and scope not in apps:
    raise SystemExit("Unknown application")
if not re.fullmatch(r"[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}", tag):
    raise SystemExit("Invalid image tag")
selected = apps if scope == "all" else {scope}
source = sys.stdin.read()
decoder = json.JSONDecoder()
resources = []
while source.strip():
    value, end = decoder.raw_decode(source.lstrip())
    resources.extend(value["items"] if value.get("kind") == "List" else [value])
    source = source.lstrip()[end:]
output = []
deployments, services, headless_services = set(), set(), set()
for resource in resources:
    kind = resource["kind"]
    name = resource["metadata"]["name"]
    if kind == "Namespace" and name == "loadup-pay":
        output.append(resource)
    elif kind == "Service" and name in {f"{app}-headless" for app in selected}:
        app = name.removesuffix("-headless")
        spec = resource["spec"]
        if (resource["metadata"].get("namespace") != "loadup-pay"
                or spec.get("clusterIP") != "None"
                or spec.get("selector") != {"app": app}
                or spec.get("publishNotReadyAddresses", False)
                or spec.get("ports") != [{"name": "grpc", "port": 50051,
                                          "targetPort": "grpc", "protocol": "TCP"}]
                or app in headless_services):
            raise SystemExit("Unexpected headless service configuration or duplicate")
        headless_services.add(app)
        output.append(resource)
    elif name in selected and kind in {"Deployment", "Service"}:
        if resource["metadata"].get("namespace") != "loadup-pay":
            raise SystemExit("Unexpected resource namespace")
        if kind == "Deployment":
            containers = resource["spec"]["template"]["spec"]["containers"]
            matches = [container for container in containers if container["name"] == name]
            if len(matches) != 1 or name in deployments:
                raise SystemExit("Unexpected deployment container or duplicate")
            matches[0]["image"] = f"{prefix}/{name}:{tag}"
            deployments.add(name)
        else:
            if name in services:
                raise SystemExit("Duplicate service")
            services.add(name)
        output.append(resource)
if (deployments != selected or services != selected or headless_services != selected
        or sum(r["kind"] == "Namespace" for r in output) != 1):
    raise SystemExit("Incomplete application manifest")
json.dump({"apiVersion": "v1", "kind": "List", "items": output}, sys.stdout)
print()
