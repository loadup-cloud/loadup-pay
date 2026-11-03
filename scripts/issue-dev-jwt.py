#!/usr/bin/env python3
"""Issue development service credentials offline, preserving caller identities."""

import base64
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import time


SPEC = importlib.util.spec_from_file_location("manage_config", Path(__file__).with_name("manage-config.py"))
config = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(config)


def openssl(*args, data=None):
    result = subprocess.run(["openssl", *args], input=data, capture_output=True, check=False)
    if result.returncode:
        raise ValueError("OpenSSL operation failed; check the local RSA key (details suppressed).")
    return result.stdout


def encode(value):
    return base64.urlsafe_b64encode(value).rstrip(b"=")


def main():
    if os.environ.get("APP_ENV", "dev") != "dev":
        raise ValueError("Offline development tokens are restricted to APP_ENV=dev.")
    directory = Path(os.environ.get("CONFIG_DIR", config.ROOT / "deploy/config/dev"))
    key = Path(os.environ.get("JWT_PRIVATE_KEY", config.ROOT / "deploy/local-config/local-development-pki/issuer-private.pem"))
    issuer = config.env_file(directory / "common.env").get("JWT_ISSUER_URI")
    if not issuer:
        raise ValueError("Set JWT_ISSUER_URI in the dev common.env file.")
    days = int(os.environ.get("JWT_TTL_DAYS", "30"))
    if not 1 <= days <= 365:
        raise ValueError("JWT_TTL_DAYS must be between 1 and 365 for local development.")
    public_key = (directory / "jwt-public.pem").read_bytes()
    if openssl("pkey", "-in", str(key), "-pubout").strip() != public_key.strip():
        raise ValueError("The signing key does not match dev/jwt-public.pem; no files changed.")
    policy = json.loads((directory / "service-token-policy.json").read_text())
    expected = {(caller, target) for caller, targets in config.TOKEN_TARGETS.items() for target in targets}
    if {(caller, target) for caller, targets in policy.items() for target in targets} != expected:
        raise ValueError("Development token policy must match the current outgoing service calls.")
    now = int(time.time())
    pending = []
    for caller, targets in policy.items():
        for target, permissions in targets.items():
            if not isinstance(permissions, list) or not permissions or not all(isinstance(p, str) and p for p in permissions):
                raise ValueError(f"Invalid development permissions for {caller}/{target}.")
            path = directory / caller / "service-tokens" / f"{target}.jwt"
            if path.exists() and os.environ.get("RENEW") != "1":
                print(f"Preserved {caller}/{target}.jwt")
                continue
            claims = {"iss": issuer, "sub": caller, "service_id": caller, "principal_type": "SERVICE",
                      "tenant_id": "__default__", "aud": [target], "token_use": "access", "permissions": permissions,
                      "iat": now, "nbf": now - 5, "exp": now + days * 86400}
            header = encode(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
            payload = encode(json.dumps(claims, separators=(",", ":")).encode())
            unsigned = header + b"." + payload
            token = unsigned + b"." + encode(openssl("dgst", "-sha256", "-sign", str(key), data=unsigned))
            pending.append((path, token))
    for path, token in pending:
        path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = path.with_suffix(".jwt.pending")
        with temporary.open("x", encoding="ascii") as output:
            output.write(token.decode("ascii") + "\n")
        temporary.replace(path)
        print(f"Issued {path.parent.parent.name}/{path.name} (valid {days} days; value hidden).")
    print("Offline files only; use configmap.sh tokens to explicitly publish credential changes.")


if __name__ == "__main__":
    os.umask(0o077)
    try:
        main()
    except UnicodeError:
        print("Development token issuance failed: invalid text encoding.", file=sys.stderr)
        sys.exit(1)
    except (ValueError, OSError, KeyError) as error:
        print(f"Development token issuance failed: {error}", file=sys.stderr)
        sys.exit(1)
