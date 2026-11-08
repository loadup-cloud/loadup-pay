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
SERVICE_CALLERS = {"productcenter", "limitcenter", "payment", "acquire", "reconciliation"}



def fail(message):
    raise ValueError(message)


def kube(*args, body=None):
    # Mutations otherwise return human-readable messages, which are not JSON.
    if args[0] in {"create", "replace"}:
        args = (*args, "-o", "json")
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


def file_data(app, directory):
    if app != "oauth2-server":
        return None
    key_store = directory / "oauth2-server.p12"
    if key_store.is_symlink() or not key_store.is_file() or not key_store.stat().st_size:
        fail("OAuth2 server requires its local oauth2-server.p12 KeyStore; run up.py oauth2-server for dev.")
    if key_store.stat().st_size > 1024 * 1024:
        fail("OAuth2 server KeyStore exceeds the Kubernetes Secret size limit.")
    return {"oauth2-server.p12": base64.b64encode(key_store.read_bytes()).decode("ascii")}


def catalog_data(directory, secret):
    path = directory.parent / "service-authorizations.json"
    catalog = json.loads(path.read_text())
    clients = catalog.get("clients")
    if not isinstance(clients, list) or not clients:
        fail("service-authorizations.json must contain service clients.")
    seen = set()
    for client in clients:
        client_id = client.get("clientId")
        if not isinstance(client_id, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,100}", client_id) or client_id in seen:
            fail("Invalid or duplicate authorization client id.")
        seen.add(client_id)
        for field, limit in (("tenantId", 64), ("serviceId", 64), ("audience", 128)):
            if not isinstance(client.get(field), str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1," + str(limit) + "}", client[field]):
                fail("Invalid authorization identity; logical tenant ids are distinct from database schemas.")
        permissions = client.get("permissions")
        if not isinstance(permissions, list) or not permissions or any(
                not isinstance(permission, str) or not re.fullmatch(r"(?:internal|pay:[a-z0-9:-]{1,120})", permission)
                for permission in permissions):
            fail("Invalid service permissions.")
        variable = client.get("secretEnv")
        if (not isinstance(variable, str) or not re.fullmatch(r"[A-Z][A-Z0-9_]*", variable)
                or not isinstance(client.get("enabled"), bool)):
            fail("Each service client requires an explicit enabled flag and secretEnv name.")
        if client["enabled"] and not re.fullmatch(r"[A-Za-z0-9_-]{32,72}", secret.get(variable, "")):
            fail(f"Missing/invalid client secret in oauth2-server/secret.env: {variable}")
    return json.dumps(catalog, separators=(",", ":"), sort_keys=True)


def resource(kind, name, data, immutable):
    obj = {"apiVersion": "v1", "kind": kind,
           "metadata": {"name": name, "namespace": NAMESPACE},
           "immutable": immutable, "data": data}
    if kind == "Secret":
        obj["type"] = "Opaque"
    return obj


def names(app):
    return [("ConfigMap", f"{app}-config-{os.environ['CONFIG_VERSION']}"),
            ("Secret", f"{app}-secret-{os.environ['SECRET_VERSION']}")] + (
                [("Secret", f"oauth2-server-files-{os.environ['SECRET_VERSION']}")] if app == "oauth2-server" else [])


def local_resources(app, directory):
    config = env_file(directory.parent / "common.env")
    app_config = env_file(directory / "config.env")
    config.update(app_config)
    secret = env_file(directory / "secret.env")
    prefix = app.upper().replace("-", "_")
    required_config = [f"{prefix}_{suffix}" for suffix in
                       ("JDBC_URL", "JDBC_USERNAME")]
    if not config.get(f"{prefix}_JWT_ISSUER_URI", config.get("JWT_ISSUER_URI", "")).strip():
        fail(f"Missing JWT_ISSUER_URI in common.env or {prefix}_JWT_ISSUER_URI for {app}.")
    if app != "oauth2-server":
        public_key = config.get(f"{prefix}_JWT_PUBLIC_KEY_LOCATION", config.get("JWT_PUBLIC_KEY_LOCATION", ""))
        jwk_set = config.get(f"{prefix}_JWT_JWK_SET_URI", config.get("JWT_JWK_SET_URI", ""))
        if public_key or not jwk_set:
            fail(f"AuthServer migration requires JWKS verification, without legacy public-key files: {app}.")
    required_secret = [f"{prefix}_JDBC_PASSWORD"]
    if app == "reconciliation":
        required_config.append("RECONCILIATION_S3_BUCKET")
        if bool(secret.get("RECONCILIATION_S3_ACCESS_KEY")) != bool(secret.get("RECONCILIATION_S3_SECRET_KEY")):
            fail("Reconciliation S3 access/secret credentials must be supplied together.")
    if app == "limitcenter":
        required_secret.append("LIMITCENTER_PERMIT_SECRET")
    if app == "payment":
        required_secret.append("PAYMENT_INPUT_FINGERPRINT_KEY")
    if app in SERVICE_CALLERS:
        required_config.append("SERVICE_AUTH_TOKEN_URI")
        required_secret.append("SERVICE_AUTH_CLIENT_SECRET")
        if not re.fullmatch(r"[A-Za-z0-9_-]{32,72}", secret.get("SERVICE_AUTH_CLIENT_SECRET", "")):
            fail(f"Missing/invalid SERVICE_AUTH_CLIENT_SECRET for {app} (32-72 URL-safe ASCII characters).")
    if app == "oauth2-server":
        required_secret.append("KMS_KEYSTORE_PASSWORD")
        config["AUTHSERVER_SERVICE_AUTHORIZATIONS"] = catalog_data(directory, secret)
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
    files = file_data(app, directory)
    return (config, encoded, files) if files is not None else (config, encoded)


def resolve_versions(apps, directory):
    values = {app: local_resources(app, directory / app) for app in apps}
    versions = []
    for index, variable, prefix in ((0, "CONFIG_VERSION", "cfg"), (1, "SECRET_VERSION", "sec")):
        content = json.dumps({app: (value[0] if index == 0 else value[1:])
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
    indexes = {"config": (0,), "secret": (1, 2), "all": (0, 1, 2)}
    if selection not in indexes:
        fail("Delete resource must be config, secret or all.")
    targets = [name for app in apps for index, name in enumerate(names(app)) if index in indexes[selection]]
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


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) < 3:
        fail("Expected an action, resource selection and at least one application.")
    action, selection, *apps = args
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
        values = local_resources(app, directory / app)
        if action != "check":
            desired.extend(resource(kind, name, data, True)
                           for (kind, name), data in zip(names(app), values))
        print(f"Validated {app} (values hidden; runtime authorization must be verified separately).")
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
            fail(f"Changed existing {kind}/{name}; use a new snapshot version.")
        elif existing:
            print(f"Reused {kind}/{name}")
        else:
            pending.append(("create", obj))
    if namespace is None:
        kube("create", "-f", "-", body={"apiVersion": "v1", "kind": "Namespace", "metadata": {"name": NAMESPACE}})
    for operation, obj in pending:
        kube(operation, "-f", "-", body=obj)
        print(f"{operation.title()}d {obj['kind']}/{obj['metadata']['name']}")


def execute(action, selection, apps):
    previous_umask = os.umask(0o077)
    try:
        main([action, selection, *apps])
    except UnicodeError:
        fail("Configuration operation failed: environment/token files must be valid UTF-8.")
    except (ValueError, OSError, KeyError) as error:
        fail(f"Configuration operation failed: {error}")
    finally:
        os.umask(previous_umask)
