#!/usr/bin/env python3
"""Check that kubectl and the target API server are within one minor version."""

from __future__ import annotations

import sys

import common


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] in {"--help", "-h"}:
        print("Usage: scripts/check_kubernetes.py")
        print("Checks that kubectl and the selected API server are within one minor version.")
        return
    common.require_command("kubectl")
    common.require_supported_kubectl()
    print("PASS: kubectl and the selected API server are within the supported minor-version difference.")


if __name__ == "__main__":
    main()
