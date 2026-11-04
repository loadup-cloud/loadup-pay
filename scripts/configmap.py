#!/usr/bin/env python3
"""Manage local configuration and Kubernetes snapshots without logging their values."""

from __future__ import annotations

import os
import sys

import common
import manage_config

ACTIONS = {"check", "versions", "create", "update", "status", "delete"}
HELP = """Usage: scripts/configmap.py <action> [all|application] [config|secret|all]
Actions:
  check    Validate environment files, application grants and signing KeyStore (offline).
  versions Print content-derived CONFIG_VERSION and SECRET_VERSION (offline).
  create   Create immutable configuration, credential and signing-key snapshots; identical objects are reused.
  update   Publish snapshots under NEW version names (same safety checks as create).
  status   Show resource names, existence and immutability; never values.
  delete   Delete unreferenced resources; final argument selects type (default: config).

APP_ENV selects dev (default), test or prod; CONFIG_DIR defaults to deploy/config/<APP_ENV>.
create/update derive immutable versions from content unless explicitly supplied.
status/delete require explicit CONFIG_VERSION and SECRET_VERSION.
KUBE_CONTEXT defaults to orbstack; namespace is loadup-pay.
create/update refuse changed existing snapshots; roll out a new snapshot version to rotate credentials.
delete checks workloads (including historical ReplicaSets) and RELEASE_DIR archives.
"""


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if not args or args[0] in {"--help", "-h"}:
        print(HELP, end="")
        return
    if len(args) > 3:
        common.fail("Too many arguments; use --help.")
    action = args[0]
    scope = args[1] if len(args) > 1 else "all"
    selection = args[2] if len(args) > 2 else "config"
    apps = common.select_apps(scope)
    if action not in ACTIONS:
        common.fail("Unknown action; use --help.")
    if action != "delete" and len(args) > 2:
        common.fail("Resource selection is only supported for delete.")
    common.select_environment()
    if action in {"status", "delete"}:
        common.require_config_versions()
    os.environ.setdefault("KUBE_CONTEXT", "orbstack")
    if action not in {"check", "versions"}:
        common.require_environment_context()
        common.require_command("kubectl")
        common.require_supported_kubectl()
    manage_config.execute(action, selection, apps)


if __name__ == "__main__":
    main()
