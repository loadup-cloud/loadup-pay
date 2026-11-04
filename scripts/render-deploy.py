#!/usr/bin/env python3
"""Bind image/configuration versions without rewriting source manifests or reading secret values."""
import argparse
import json
import re
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("scope")
parser.add_argument("prefix")
parser.add_argument("tag")
parser.add_argument("config_version")
parser.add_argument("secret_version")
args = parser.parse_args()
scope, prefix, tag = args.scope, args.prefix, args.tag
for version in (args.config_version, args.secret_version):
    if not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?", version):
        raise SystemExit("Configuration versions must be DNS labels of at most 32 characters")
apps = {"authserver", "merchantcenter", "merchantmng", "productcenter", "productmng", "limitcenter", "payment", "acquire", "realtime", "reconciliation"}
if scope != "all" and scope not in apps:
    raise SystemExit("Unknown application")
if not re.fullmatch(r"[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}", tag):
    raise SystemExit("Invalid image tag")
selected = apps if scope == "all" else {scope}
callers = {"merchantcenter": ["productcenter", "merchantmng", "authserver"], "productcenter": ["acquire", "merchantmng", "productmng"],
           "merchantmng": [], "productmng": [], "limitcenter": ["productcenter", "payment"],
           "payment": ["acquire", "limitcenter", "reconciliation"], "acquire": ["payment"],
           "realtime": ["acquire", "payment"], "reconciliation": [],
           "authserver": ["merchantcenter", "productcenter", "limitcenter", "payment", "acquire", "realtime", "reconciliation"]}


def http_ingress_spec(app):
    peers = []
    if callers[app]:
        peers.append({"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "loadup-pay"}},
                      "podSelector": {"matchExpressions": [{"key": "app", "operator": "In", "values": callers[app]}]}})
    if app not in {"limitcenter", "authserver", "merchantcenter", "productcenter"}:
        peers.append({"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "envoy-gateway-system"}},
                      "podSelector": {"matchLabels": {"app.kubernetes.io/name": "envoy",
                          "gateway.envoyproxy.io/owning-gateway-namespace": "loadup-pay",
                          "gateway.envoyproxy.io/owning-gateway-name": "loadup-pay"}}})
    peers.append({"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "observability"}},
                  "podSelector": {"matchLabels": {"loadup.io/metrics-scraper": "true"}}})
    return {"podSelector": {"matchLabels": {"app": app}}, "policyTypes": ["Ingress"],
            "ingress": [{"from": peers, "ports": [{"protocol": "TCP", "port": 8080}]}]}


source = sys.stdin.read()
decoder = json.JSONDecoder()
resources = []
while source.strip():
    value, end = decoder.raw_decode(source.lstrip())
    resources.extend(value["items"] if value.get("kind") == "List" else [value])
    source = source.lstrip()[end:]
output = []
deployments, services, policies = set(), set(), set()
for resource in resources:
    kind = resource["kind"]
    name = resource["metadata"]["name"]
    if kind == "Namespace" and name == "loadup-pay":
        output.append(resource)
    elif kind == "Service" and name in {f"{app}-headless" for app in selected}:
        raise SystemExit("HTTP applications must not expose a legacy headless service")
    elif kind == "NetworkPolicy":
        app = name.removesuffix("-http-ingress")
        if name != f"{app}-http-ingress" or app not in apps:
            raise SystemExit("Unexpected application NetworkPolicy")
        if app in selected:
            if (app in policies or resource["metadata"].get("namespace") != "loadup-pay"
                    or resource["spec"] != http_ingress_spec(app)):
                raise SystemExit("HTTP ingress policy must match the declared caller, gateway and monitoring boundaries")
            policies.add(app)
            output.append(resource)
    elif name in selected and kind in {"Deployment", "Service"}:
        if resource["metadata"].get("namespace") != "loadup-pay":
            raise SystemExit("Unexpected resource namespace")
        if kind == "Deployment":
            template = resource["spec"]["template"]
            pod = template["spec"]
            containers = pod["containers"]
            matches = [container for container in containers if container["name"] == name]
            if len(matches) != 1 or name in deployments:
                raise SystemExit("Unexpected deployment container or duplicate")
            ports = matches[0].get("ports", [])
            if (len(ports) != 1 or ports[0].get("name") != "http"
                    or ports[0].get("containerPort") != 8080
                    or ports[0].get("protocol", "TCP") != "TCP"):
                raise SystemExit("Application deployment must expose only HTTP 8080")
            container = matches[0]
            environment = container.get("envFrom", [])
            if (len(environment) != 2 or any(entry.get("prefix") for entry in environment)
                    or environment[0].get("configMapRef", {}).get("name") != f"{name}-config"
                    or environment[1].get("secretRef", {}).get("name") != f"{name}-secret"
                    or environment[0]["configMapRef"].get("optional", False)
                    or environment[1]["secretRef"].get("optional", False)):
                raise SystemExit("Application must declare exactly the expected configuration references")
            if name == "authserver":
                volumes = pod.get("volumes", [])
                mounts = container.get("volumeMounts", [])
                if (len(volumes) != 1 or volumes[0].get("name") != "signing-keys"
                        or volumes[0].get("secret", {}).get("secretName") != "authserver-files"
                        or len(mounts) != 1 or mounts[0].get("name") != "signing-keys"
                        or mounts[0].get("mountPath") != "/var/run/loadup/secrets"
                        or mounts[0].get("readOnly") is not True or mounts[0].get("subPath")):
                    raise SystemExit("AuthServer requires a dedicated read-only signing KeyStore Secret")
                volumes[0]["secret"]["secretName"] = f"authserver-files-{args.secret_version}"
            elif pod.get("volumes") or container.get("volumeMounts"):
                raise SystemExit("Business applications must not mount private signing keys or legacy service JWT files")
            container["envFrom"][0]["configMapRef"]["name"] = f"{name}-config-{args.config_version}"
            container["envFrom"][1]["secretRef"]["name"] = f"{name}-secret-{args.secret_version}"
            annotations = template.setdefault("metadata", {}).setdefault("annotations", {})
            annotations.update({"loadup.io/image-tag": tag, "loadup.io/config-version": args.config_version,
                                "loadup.io/secret-version": args.secret_version})
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
if (deployments != selected or services != selected or policies != selected
        or sum(r["kind"] == "Namespace" for r in output) != 1):
    raise SystemExit("Incomplete application manifest")
# Install ingress boundaries before starting or replacing application pods.
output.sort(key=lambda resource: {"Namespace": 0, "NetworkPolicy": 1, "Deployment": 2, "Service": 3}[resource["kind"]])
json.dump({"apiVersion": "v1", "kind": "List", "items": output}, sys.stdout)
print()
