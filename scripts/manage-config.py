#!/usr/bin/env python3
"""Manage local configuration and Kubernetes snapshots without logging their values."""

import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
NAMESPACE = "loadup-pay"
KEY = re.compile(r"[A-Za-z_][A-Za-z0-9_]*\Z")
TOKEN_TARGETS = {"customer": (), "product": ("limitcenter",), "limitcenter": ("payment",),
                 "payment": ("acquire", "limitcenter", "realtime"), "acquire": ("payment", "realtime"),
                 "realtime": (), "reconciliation": ("payment",)}


def fail(message):
    raise ValueError(message)


def kube(*args, body=None):
    result = subprocess.run(
        ["kubectl", "--context", os.environ.get("KUBE_CONTEXT", "orbstack"),
         "--namespace", NAMESPACE, "--request-timeout=30s", *args],
        input=json.dumps(body) if body is not None else None,
        capture_output=True, text=True, check=False,
    )
    if result.returncode:
        # API validation errors may echo Secret data, so never forward stderr.
        fail(f"kubectl {args[0]} failed; check context, connectivity, permissions and resource state (details suppressed).")
    return json.loads(result.stdout) if result.stdout.strip() else None


def get(kind, name):
    return kube("get", kind, name, "--ignore-not-found", "-o", "json")


def env_file(path):
    data = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = raw.lstrip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or not KEY.fullmatch(key) or key in data:
            fail(f"Invalid or duplicate environment key: {path}:{number} (use literal KEY=value).")
        data[key] = value
    return data


def token_data(app, directory):
    required = {f"{target}.jwt" for target in TOKEN_TARGETS[app]}
    path = directory / "service-tokens"
    if not path.is_dir():
        fail(f"Missing token directory: {path}")
    data = {}
    for file in sorted(path.iterdir()):
        if file.name == ".gitkeep":
            continue
        if file.is_symlink() or not file.is_file() or not re.fullmatch(r"[a-z0-9-]+\.jwt", file.name):
            fail(f"Only regular <service>.jwt files are allowed: {path}")
        value = file.read_bytes()
        if not value.strip():
            fail(f"Empty token file: {file}")
        data[file.name] = base64.b64encode(value).decode("ascii")
    if required - data.keys():
        fail(f"Missing required token files for {app}: {', '.join(sorted(required - data.keys()))}")
    public_key = directory.parent / "jwt-public.pem"
    if public_key.is_file():
        pem = public_key.read_bytes()
        if not pem.startswith(b"-----BEGIN PUBLIC KEY-----") or b"PRIVATE" in pem:
            fail("jwt-public.pem must contain an X.509 public key, never a private key.")
        data["jwt-public.pem"] = base64.b64encode(pem).decode("ascii")
    return data


def resource(kind, name, data, immutable):
    obj = {"apiVersion": "v1", "kind": kind,
           "metadata": {"name": name, "namespace": NAMESPACE},
           "immutable": immutable, "data": data}
    if kind == "Secret":
        obj["type"] = "Opaque"
    return obj


def names(app):
    return [("ConfigMap", f"{app}-config-{os.environ['CONFIG_VERSION']}"),
            ("Secret", f"{app}-secret-{os.environ['SECRET_VERSION']}"),
            ("Secret", f"{app}-service-tokens")]


def local_resources(app, directory):
    config = env_file(directory.parent / "common.env")
    app_config = env_file(directory / "config.env")
    config.update(app_config)
    secret = env_file(directory / "secret.env")
    prefix = app.upper()
    required_config = [f"{prefix}_{suffix}" for suffix in
                       ("JDBC_URL", "JDBC_USERNAME")]
    if not config.get(f"{prefix}_JWT_ISSUER_URI", config.get("JWT_ISSUER_URI", "")).strip():
        fail(f"Missing JWT_ISSUER_URI in common.env or {prefix}_JWT_ISSUER_URI for {app}.")
    public_key = config.get(f"{prefix}_JWT_PUBLIC_KEY_LOCATION", config.get("JWT_PUBLIC_KEY_LOCATION", ""))
    jwk_set = config.get(f"{prefix}_JWT_JWK_SET_URI", config.get("JWT_JWK_SET_URI", ""))
    if bool(public_key) == bool(jwk_set):
        fail(f"Configure exactly one JWT_PUBLIC_KEY_LOCATION or JWT_JWK_SET_URI for {app}.")
    if public_key and (public_key != "file:/var/run/loadup/secrets/jwt-public.pem"
                       or not (directory.parent / "jwt-public.pem").is_file()):
        fail(f"Local JWT verification requires jwt-public.pem and the mounted file location for {app}.")
    required_secret = [f"{prefix}_JDBC_PASSWORD"]
    if app == "reconciliation":
        required_config.append("RECONCILIATION_S3_BUCKET")
        if bool(secret.get("RECONCILIATION_S3_ACCESS_KEY")) != bool(secret.get("RECONCILIATION_S3_SECRET_KEY")):
            fail("Reconciliation S3 access/secret credentials must be supplied together.")
    if app == "limitcenter":
        required_secret.append("LIMITCENTER_PERMIT_SECRET")
    if app == "payment":
        required_secret.append("PAYMENT_INPUT_FINGERPRINT_KEY")
    for data, required in ((config, required_config), (secret, required_secret)):
        for key in required:
            if not data.get(key, "").strip():
                fail(f"Missing required value for {app}: {key}")
    for key in ("LIMITCENTER_PERMIT_SECRET", "PAYMENT_INPUT_FINGERPRINT_KEY"):
        if key in required_secret and len(secret[key].encode("utf-8")) < 32:
            fail(f"{key} requires at least 32 UTF-8 bytes.")
    if config.keys() & secret.keys():
        fail(f"Duplicate config/secret environment keys for {app}.")
    if config.get("LOADUP_SECURITY_RESOURCE_SERVER_ENABLED", "true").lower() != "true":
        fail(f"Resource-server authentication must remain enabled for {app}.")
    for key in config:
        if any(word in key for word in ("PASSWORD", "SECRET", "PRIVATE_KEY", "ACCESS_KEY", "FINGERPRINT_KEY")):
            fail(f"Sensitive environment key belongs in secret.env: {app}/{key}")
    encoded = {key: base64.b64encode(value.encode("utf-8")).decode("ascii") for key, value in secret.items()}
    return config, encoded, token_data(app, directory)


def resolve_versions(apps, directory):
    values = {app: local_resources(app, directory / app) for app in apps}
    versions = []
    for index, variable, prefix in ((0, "CONFIG_VERSION", "cfg"), (1, "SECRET_VERSION", "sec")):
        content = json.dumps({app: ({"env": value[0], "publicKey": value[2].get("jwt-public.pem")}
                                    if index == 0 else value[index])
                              for app, value in values.items()}, sort_keys=True).encode("utf-8")
        version = os.environ.get(variable) or f"{prefix}-{os.environ.get('APP_ENV', 'dev')}-{hashlib.sha256(content).hexdigest()[:12]}"
        if not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?", version):
            fail(f"Invalid {variable}; expected a DNS label of at most 32 characters.")
        os.environ[variable] = version
        versions.append(version)
    return versions


def same(existing, desired):
    return (existing.get("data", {}) == desired["data"]
            and existing.get("binaryData", {}) == {}
            and existing.get("immutable", False) == desired["immutable"]
            and existing.get("type", "Opaque") == desired.get("type", "Opaque"))


def references(node):
    """Walk pod specs, projected volumes, env/envFrom and imagePullSecrets."""
    found = set()
    if isinstance(node, list):
        for item in node:
            found.update(references(item))
    elif isinstance(node, dict):
        for key, value in node.items():
            kind = {"configMap": "ConfigMap", "configMapRef": "ConfigMap", "configMapKeyRef": "ConfigMap",
                    "secret": "Secret", "secretRef": "Secret", "secretKeyRef": "Secret"}.get(key)
            if kind and isinstance(value, dict):
                name = value.get("name") or value.get("secretName")
                if name:
                    found.add((kind, name))
            if key == "imagePullSecrets":
                found.update(("Secret", item["name"]) for item in value)
            found.update(references(value))
    return found


def delete_resources(apps, selection):
    indexes = {"config": (0,), "secret": (1,), "tokens": (2,), "all": (0, 1, 2)}
    if selection not in indexes:
        fail("Delete resource must be config, secret, tokens or all.")
    targets = [names(app)[index] for app in apps for index in indexes[selection]]
    objects = [(kind, name, get(kind, name)) for kind, name in targets]
    # Fail closed if any workload type cannot be listed. Zero-replica history still matters.
    workloads = kube("get", "pods,deployments,replicasets,statefulsets,daemonsets,jobs,cronjobs", "-o", "json")
    protected = set()
    for item in workloads["items"]:
        protected.update(references(item.get("spec", {})))
    archive = Path(os.environ.get("RELEASE_DIR", str(ROOT / "deploy/releases")))
    for manifest in archive.glob("*.json"):
        protected.update(references(json.loads(manifest.read_text())))
    used = set(targets) & protected
    if used:
        fail("Deletion refused; workload/history/release archive references: " +
             ", ".join(f"{kind}/{name}" for kind, name in sorted(used)))
    for kind, name, obj in objects:
        if obj is None:
            print(f"Absent {kind}/{name}")
            continue
        plural = "configmaps" if kind == "ConfigMap" else "secrets"
        options = {"apiVersion": "v1", "kind": "DeleteOptions", "preconditions": {
            "uid": obj["metadata"]["uid"], "resourceVersion": obj["metadata"]["resourceVersion"]}}
        kube("delete", "--raw", f"/api/v1/namespaces/{NAMESPACE}/{plural}/{name}", "-f", "-", body=options)
        print(f"Deleted {kind}/{name}")


def main():
    action, selection, *apps = sys.argv[1:]
    directory = Path(os.environ["CONFIG_DIR"]).resolve()
    if action == "versions":
        print(*resolve_versions(apps, directory))
        return
    if action == "delete":
        delete_resources(apps, selection)
        return
    if action == "status":
        for app in apps:
            for kind, name in names(app):
                obj = get(kind, name)
                print(f"{kind}/{name}: " + (f"immutable={obj.get('immutable', False)}" if obj else "absent"))
        return
    desired = []
    if action in ("create", "update"):
        resolve_versions(apps, directory)
    # Validate every selected app before the first cluster mutation.
    for app in apps:
        if action == "tokens":
            desired.append(resource("Secret", f"{app}-service-tokens", token_data(app, directory / app), False))
        else:
            values = local_resources(app, directory / app)
            if action != "check":
                desired.extend(resource(kind, name, data, index != 2)
                               for index, ((kind, name), data) in enumerate(zip(names(app), values)))
        print(f"Validated {app} (values hidden; token claims/signatures require issuer verification).")
    if action == "check":
        return
    namespace = kube("get", "namespace", NAMESPACE, "--ignore-not-found", "-o", "json")
    pending = []
    for obj in desired:
        name, kind = obj["metadata"]["name"], obj["kind"]
        existing = get(kind, name) if namespace else None
        if existing and existing["metadata"].get("deletionTimestamp"):
            fail(f"Resource is being deleted: {kind}/{name}; wait and investigate before retrying.")
        if existing and not same(existing, obj):
            if action != "tokens" or existing.get("immutable", False) or existing.get("type", "Opaque") != "Opaque":
                fail(f"Changed existing {kind}/{name}; use a new snapshot version or explicit tokens rotation.")
            # Preserve labels/annotations/ownership and use resourceVersion for optimistic locking.
            existing["data"] = obj["data"]
            existing["immutable"] = False
            pending.append(("replace", existing))
        elif existing:
            print(f"Reused {kind}/{name}")
        else:
            pending.append(("create", obj))
    if namespace is None:
        kube("create", "-f", "-", body={"apiVersion": "v1", "kind": "Namespace", "metadata": {"name": NAMESPACE}})
    for operation, obj in pending:
        kube(operation, "-f", "-", body=obj)
        print(f"{operation.title()}d {obj['kind']}/{obj['metadata']['name']}")


if __name__ == "__main__":
    os.umask(0o077)
    try:
        main()
    except UnicodeError:
        print("Configuration operation failed: environment/token files must be valid UTF-8.", file=sys.stderr)
        sys.exit(1)
    except (ValueError, OSError, KeyError) as error:
        print(f"Configuration operation failed: {error}", file=sys.stderr)
        sys.exit(1)
