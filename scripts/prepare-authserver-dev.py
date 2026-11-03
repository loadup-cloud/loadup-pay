#!/usr/bin/env python3
"""Prepare development credentials locally without printing secrets or modifying Kubernetes."""
import importlib.util
import os
from pathlib import Path
import secrets
import subprocess
import sys

spec = importlib.util.spec_from_file_location("config", Path(__file__).with_name("manage-config.py"))
config = importlib.util.module_from_spec(spec)
spec.loader.exec_module(config)


def load(path):
    return config.env_file(path) if path.is_file() else {}


def save(path, values):
    existing = load(path)
    changed = {key: value for key, value in values.items() if key not in existing or not existing[key]}
    if not changed:
        return
    text = path.read_text() if path.exists() else "# Development secrets. Do not commit.\n"
    lines = text.splitlines()
    for key, value in changed.items():
        index = next((i for i, line in enumerate(lines) if line.startswith(key + "=")), None)
        if index is None:
            lines.append(key + "=" + value)
        else:
            lines[index] = key + "=" + value
    temporary = path.with_name(path.name + ".pending")
    with temporary.open("x") as output:
        output.write("\n".join(lines) + "\n")
    os.chmod(temporary, 0o600)
    temporary.replace(path)


def keytool(*args, password):
    tool = str(Path(os.environ["JAVA_HOME"]) / "bin/keytool") if os.environ.get("JAVA_HOME") else "keytool"
    env = os.environ.copy()
    env["LOADUP_LOCAL_STORE_PASSWORD"] = password
    result = subprocess.run([tool, *args, "-storepass:env", "LOADUP_LOCAL_STORE_PASSWORD"],
                            env=env, capture_output=True, timeout=30)
    if result.returncode:
        raise ValueError("KeyStore operation failed; check the file, alias and password (details suppressed).")


def main():
    if os.environ.get("APP_ENV", "dev") != "dev":
        raise ValueError("This helper is restricted to APP_ENV=dev.")
    directory = Path(os.environ.get("CONFIG_DIR", config.ROOT / "deploy/config/dev")).resolve()
    auth = directory / "authserver"
    auth.mkdir(mode=0o700, parents=True, exist_ok=True)
    server = load(auth / "secret.env")
    password = server.get("KMS_KEYSTORE_PASSWORD")
    store = auth / "authserver.p12"
    if store.exists() and not password:
        raise ValueError("Existing KeyStore has no configured password; refusing to replace either.")
    server.setdefault("KMS_KEYSTORE_PASSWORD", password or secrets.token_urlsafe(32))
    if not server.get("KMS_KEYSTORE_PASSWORD"):
        server["KMS_KEYSTORE_PASSWORD"] = secrets.token_urlsafe(32)
    source = load(directory / "customer/secret.env")
    if not server.get("AUTHSERVER_JDBC_PASSWORD"):
        server["AUTHSERVER_JDBC_PASSWORD"] = source.get("CUSTOMER_JDBC_PASSWORD", "")
    if not server["AUTHSERVER_JDBC_PASSWORD"]:
        raise ValueError("Fill authserver/secret.env AUTHSERVER_JDBC_PASSWORD; local PostgreSQL credentials were not found.")
    pending = []
    for app in sorted(config.SERVICE_CALLERS):
        path = directory / app / "secret.env"
        client = load(path)
        variable = app.upper() + "_OAUTH2_CLIENT_SECRET"
        left, right = server.get(variable), client.get("SERVICE_AUTH_CLIENT_SECRET")
        if left and right and left != right:
            raise ValueError(f"Credentials differ for {app}; reconcile the two secret.env files manually.")
        value = left or right or secrets.token_urlsafe(32)
        server[variable] = value
        pending.append((path, {"SERVICE_AUTH_CLIENT_SECRET": value}))
    # Persist passwords first so an interrupted key generation remains recoverable on retry.
    save(auth / "secret.env", server)
    for path, values in pending:
        save(path, values)
    alias = load(auth / "config.env").get("AUTHSERVER_SIGNING_KEY", "auth-jwt")
    if store.exists():
        keytool("-list", "-keystore", str(store), "-alias", alias, password=server["KMS_KEYSTORE_PASSWORD"])
    else:
        keytool("-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "3072",
                "-sigalg", "SHA256withRSA", "-storetype", "PKCS12", "-keystore", str(store),
                "-dname", "CN=LoadUp Local AuthServer", "-validity", "3650", "-noprompt",
                password=server["KMS_KEYSTORE_PASSWORD"])
        os.chmod(store, 0o600)
    print("Prepared local AuthServer KeyStore and matching per-application credentials; values hidden.")
    print("No database or Kubernetes changes. Create loadup_authserver before deploying.")


if __name__ == "__main__":
    os.umask(0o077)
    try:
        main()
    except (ValueError, OSError, subprocess.TimeoutExpired) as failure:
        print(f"AuthServer preparation failed: {failure}", file=sys.stderr)
        sys.exit(1)
