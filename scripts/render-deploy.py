#!/usr/bin/env python3
"""Select application resources and bind immutable image tags without rewriting source manifests."""
import json
import re
import sys

scope, prefix, tag = sys.argv[1:]
apps = {"customer", "product", "limitcenter", "payment", "acquire", "realtime", "reconciliation"}
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
deployments, services = set(), set()
for resource in resources:
    kind = resource["kind"]
    name = resource["metadata"]["name"]
    if kind == "Namespace" and name == "loadup-pay":
        output.append(resource)
    elif kind == "Service" and name in {f"{app}-headless" for app in selected}:
        raise SystemExit("HTTP applications must not expose a legacy headless service")
    elif name in selected and kind in {"Deployment", "Service"}:
        if resource["metadata"].get("namespace") != "loadup-pay":
            raise SystemExit("Unexpected resource namespace")
        if kind == "Deployment":
            containers = resource["spec"]["template"]["spec"]["containers"]
            matches = [container for container in containers if container["name"] == name]
            if len(matches) != 1 or name in deployments:
                raise SystemExit("Unexpected deployment container or duplicate")
            ports = matches[0].get("ports", [])
            if (len(ports) != 1 or ports[0].get("name") != "http"
                    or ports[0].get("containerPort") != 8080
                    or ports[0].get("protocol", "TCP") != "TCP"):
                raise SystemExit("Application deployment must expose only HTTP 8080")
            matches[0]["image"] = f"{prefix}/{name}:{tag}"
            deployments.add(name)
        else:
            if name in services:
                raise SystemExit("Duplicate service")
            spec = resource["spec"]
            ports = spec.get("ports", [])
            if (spec.get("clusterIP") == "None" or spec.get("selector") != {"app": name}
                    or spec.get("type", "ClusterIP") != "ClusterIP"
                    or len(ports) != 1 or ports[0].get("name") != "http"
                    or ports[0].get("port") != 8080 or ports[0].get("targetPort") != "http"
                    or ports[0].get("protocol", "TCP") != "TCP"):
                raise SystemExit("Application Service must route ClusterIP HTTP 8080")
            services.add(name)
        output.append(resource)
if (deployments != selected or services != selected
        or sum(r["kind"] == "Namespace" for r in output) != 1):
    raise SystemExit("Incomplete application manifest")
json.dump({"apiVersion": "v1", "kind": "List", "items": output}, sys.stdout)
print()
