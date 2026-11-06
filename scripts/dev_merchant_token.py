#!/usr/bin/env python3
"""Mint a development-only merchant/operator JWT with the local AuthServer KeyStore."""

from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import uuid

import common
import manage_config


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def env_file(path: Path) -> dict[str, str]:
    return manage_config.env_file(path) if path.is_file() else {}


def value(name: str, text: str | None, maximum: int) -> str:
    if text is None or not text.strip() or len(text) > maximum or any(ord(ch) < 32 for ch in text):
        common.fail(f"Invalid {name}.")
    return text


def csv(name: str, text: str, maximum: int) -> list[str]:
    values = [item.strip() for item in text.split(",") if item.strip()]
    if not values or any(len(item) > maximum for item in values):
        common.fail(f"Invalid {name}.")
    return values


def sign(p12: Path, password: str, signing_input: str) -> bytes:
    with tempfile.TemporaryDirectory(prefix="loadup-dev-token-") as temporary:
        key = Path(temporary) / "authserver-key.pem"
        environment = os.environ.copy()
        environment["LOADUP_P12_PASSWORD"] = password
        result = subprocess.run(
            [
                "openssl",
                "pkcs12",
                "-in",
                str(p12),
                "-nocerts",
                "-nodes",
                "-passin",
                "env:LOADUP_P12_PASSWORD",
                "-out",
                str(key),
            ],
            env=environment,
            capture_output=True,
            check=False,
        )
        if result.returncode != 0:
            common.fail("Unable to read the AuthServer KeyStore; check authserver.p12 and KMS_KEYSTORE_PASSWORD.")
        os.chmod(key, 0o600)
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", str(key)],
            input=signing_input.encode("utf-8"),
            capture_output=True,
            check=False,
        )
        if result.returncode != 0:
            common.fail("Unable to sign the development token.")
        return result.stdout


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Mint a development-only merchant/operator JWT from the AuthServer PKCS12 KeyStore. "
            "The token is written to stdout; secrets and the private key are never printed."
        )
    )
    parser.add_argument(
        "--merchant-id",
        default=os.environ.get("DEV_MERCHANT_ID"),
        help="Merchant id claim; defaults to DEV_MERCHANT_ID.",
    )
    parser.add_argument("--subject", help="Token subject/principal id; defaults to --merchant-id.")
    parser.add_argument(
        "--principal-type",
        choices=("MERCHANT", "MERCHANT_APP", "OPERATOR"),
        default="MERCHANT",
        help="principal_type claim (default: MERCHANT).",
    )
    parser.add_argument(
        "--audience",
        default="acquire,productcenter,merchantcenter",
        help="Comma-separated aud values (default: acquire,productcenter,merchantcenter).",
    )
    parser.add_argument(
        "--permissions",
        default="pay:acquire:create,pay:product:admit",
        help="Comma-separated permissions (default: pay:acquire:create,pay:product:admit).",
    )
    parser.add_argument("--tenant", default="__default__", help="tenant_id claim (default: __default__).")
    parser.add_argument("--ttl-seconds", type=int, default=3600, help="Token lifetime in seconds (default: 3600).")
    parser.add_argument("--issuer", help="Override iss; defaults to CONFIG_DIR/common.env JWT_ISSUER_URI.")
    parser.add_argument("--kid", help="Override JWT kid; defaults to <AUTHSERVER_SIGNING_KEY>-v1.")
    parser.add_argument("--config-dir", help="Environment directory; defaults to CONFIG_DIR or deploy/config/<APP_ENV>.")
    args = parser.parse_args(argv)

    if args.config_dir:
        os.environ["CONFIG_DIR"] = args.config_dir
    common.select_environment()
    if os.environ["APP_ENV"] != "dev":
        common.fail("dev_merchant_token.py only supports APP_ENV=dev.")
    if not args.merchant_id:
        common.fail("--merchant-id is required (or set DEV_MERCHANT_ID).")
    if args.ttl_seconds < 60 or args.ttl_seconds > 86400:
        common.fail("--ttl-seconds must be between 60 and 86400.")
    common.require_command("openssl")

    config_dir = Path(os.environ["CONFIG_DIR"]).resolve()
    auth = config_dir / "authserver"
    common_values = env_file(config_dir / "common.env")
    auth_config = env_file(auth / "config.env")
    auth_secret = env_file(auth / "secret.env")
    p12 = auth / "authserver.p12"
    if not p12.is_file():
        common.fail("AuthServer KeyStore is missing; run python3 scripts/up.py authserver for dev first.")
    password = auth_secret.get("KMS_KEYSTORE_PASSWORD", "")
    if not password:
        common.fail("KMS_KEYSTORE_PASSWORD is missing from authserver/secret.env.")

    issuer = args.issuer or os.environ.get("JWT_ISSUER_URI") or common_values.get("JWT_ISSUER_URI")
    issuer = value("issuer", issuer, 512)
    kid = args.kid or f"{auth_config.get('AUTHSERVER_SIGNING_KEY', 'auth-jwt')}-v1"
    kid = value("kid", kid, 128)
    merchant = value("merchant id", args.merchant_id, 64)
    subject = value("subject", args.subject or merchant, 64)
    if args.principal_type == "MERCHANT" and subject != merchant:
        common.fail("MERCHANT tokens require --subject to equal --merchant-id.")
    tenant = value("tenant", args.tenant, 64)
    audiences = csv("audience", args.audience, 128)
    permissions = csv("permissions", args.permissions, 128)

    if not re.fullmatch(r"[A-Za-z0-9_-]+", kid):
        common.fail("Invalid kid.")

    now = int(time.time())
    header = {"alg": "RS256", "typ": "JWT", "kid": kid}
    payload = {
        "iss": issuer,
        "sub": subject,
        "aud": audiences,
        "principal_type": args.principal_type,
        "merchant_id": merchant,
        "tenant_id": tenant,
        "token_use": "access",
        "permissions": permissions,
        "iat": now,
        "nbf": now - 60,
        "exp": now + args.ttl_seconds,
        "jti": str(uuid.uuid4()),
    }
    signing_input = (
        b64url(json.dumps(header, separators=(",", ":")).encode("utf-8"))
        + "."
        + b64url(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    )
    token = signing_input + "." + b64url(sign(p12, password, signing_input))
    print(token)


if __name__ == "__main__":
    main()
