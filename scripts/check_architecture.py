#!/usr/bin/env python3
"""Offline source/POM dependency rules; never imports application code or invokes Maven."""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

PAY = re.compile(r"loadup-pay-(.+)-(client|domain|app|infrastructure|adapter|bootstrap|service)$")
JAVA_REF = re.compile(r"\b(?:io\.github\.loadup|org\.springframework|jakarta\.persistence)\.[\w.]+")
# Keep offsets/newlines so diagnostics refer to the original source. This is a lexical
# boundary check, not a replacement for Java compilation or bytecode analysis.
JAVA_NON_CODE = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')


def code_only(source):
    return JAVA_NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)


def java_violations(source):
    code = code_only(source)
    package = re.search(r"\bpackage\s+io\.github\.loadup\.([\w.]+)\s*;", code)
    if not package:
        return []
    parts = package[1].split(".")
    owner = parts[0]
    layer = parts[1] if len(parts) > 1 else ""
    contracts = owner == "contracts"
    findings = []
    for match in JAVA_REF.finditer(code):
        ref = match.group().rstrip(".")
        reason = None
        if (layer == "domain" or contracts) and ref.startswith((
                "org.springframework.", "jakarta.persistence.")):
            reason = "A5-TECH: domain/shared facts must not depend on Spring or ORM"
        if ref.startswith("io.github.loadup."):
            target = ref.removeprefix("io.github.loadup.").split(".")
            remote, target_layer = target[0], target[1] if len(target) > 1 else ""
            if contracts and target_layer in {"client", "domain", "app", "infrastructure", "adapter"}:
                reason = "A5-CONTRACT: shared contracts must not depend on applications"
            elif layer in {"domain", "app", "client"} and target_layer in {
                    "client", "domain", "app", "infrastructure", "adapter", "bootstrap"}:
                if remote != owner:
                    reason = "A5-REMOTE: consume provider contracts through infrastructure"
                elif layer == "domain" and target_layer != "domain":
                    if not (target_layer == "client" and len(target) > 2 and target[2] in {"error", "enums"}):
                        reason = "A5-DOMAIN: domain may use only its own pure client errors/enums"
                elif layer == "app" and target_layer in {"infrastructure", "adapter", "bootstrap"}:
                    reason = "A5-APP: app must not depend on outer implementations"
                elif layer == "client" and target_layer != "client":
                    reason = "A5-CLIENT: public client must not depend on application internals"
        if reason:
            findings.append(f"{code.count(chr(10), 0, match.start()) + 1}: {reason} ({ref})")
    return findings


def pom_violations(artifact, dependency, scope):
    if scope == "test":
        return []
    findings = []
    own, other = PAY.fullmatch(artifact), PAY.fullmatch(dependency)
    if artifact.startswith(("loadup-components-", "loadup-commons-", "loadup-framework")) and dependency.startswith(("loadup-modules-", "loadup-pay-")):
        findings.append("A5-REPOSITORY: Framework cannot depend on Modules/Pay")
    if artifact.startswith("loadup-modules-") and dependency.startswith("loadup-pay-"):
        findings.append("A5-REPOSITORY: Modules cannot depend on Pay")
    if "testify" in dependency:
        findings.append("A5-TEST: Testify must have test scope")
    if artifact.startswith("loadup-pay-contracts-") and other:
        findings.append("A5-CONTRACT: shared contract must not depend on application artifacts")
    if own and other:
        owner, layer = own.groups()
        provider, target = other.groups()
        if owner != provider and target != "client":
            findings.append("A5-OWNER: cross-application implementation dependency")
        elif owner != provider and layer in {"domain", "app", "client"}:
            findings.append("A5-REMOTE: provider client belongs in infrastructure")
        elif owner == provider:
            allowed = {"client": {"client"}, "domain": {"client", "domain"},
                       "app": {"client", "domain", "app"},
                       "infrastructure": {"client", "domain", "app", "infrastructure"},
                       "adapter": {"client", "domain", "app", "adapter"}}
            if layer in allowed and target not in allowed[layer]:
                findings.append("A5-LAYER: inverted module dependency")
    return findings


def check_repository(root):
    root = root.resolve()
    findings, projects = [], {}
    visited = set()

    def visit(path):
        path = path.resolve()
        if not path.is_relative_to(root):
            findings.append("pom.xml: A5-REACTOR: module escapes repository")
            return
        if path in visited:
            return
        visited.add(path)
        try:
            xml = ET.parse(path).getroot()
        except (OSError, ET.ParseError):
            findings.append(f"{path.relative_to(root)}: A5-REACTOR: missing or malformed POM")
            return
        artifact = xml.findtext("{*}artifactId")
        if not artifact or artifact in projects:
            findings.append(f"{path.relative_to(root)}: A5-REACTOR: missing/duplicate artifact")
            return
        projects[artifact] = (path, xml)
        for module in xml.findall("./{*}modules/{*}module") + xml.findall("./{*}profiles/{*}profile/{*}modules/{*}module"):
            visit(path.parent / (module.text or "") / "pom.xml")

    visit(root / "pom.xml")
    graph = {name: set() for name in projects}
    for artifact, (pom, xml) in projects.items():
        dependencies = xml.findall("./{*}dependencies/{*}dependency") + xml.findall("./{*}profiles/{*}profile/{*}dependencies/{*}dependency")
        for dep in dependencies:
            name, scope = dep.findtext("{*}artifactId", ""), dep.findtext("{*}scope", "compile")
            if "${" in name or "${" in scope:
                findings.append(f"{pom.relative_to(root)}: A5-POM: unresolved dependency coordinate/scope")
            for problem in pom_violations(artifact, name, scope):
                findings.append(f"{pom.relative_to(root)}: {problem} ({name})")
            if scope != "test" and name in graph:
                graph[artifact].add(name)
        for file in sorted((pom.parent / "src/main/java").rglob("*.java")):
            findings.extend(f"{file.relative_to(root)}:{problem}" for problem in java_violations(file.read_text()))
    active, done = [], set()

    def cycles(node):
        if node in active:
            findings.append("A5-CYCLE: " + " -> ".join(active[active.index(node):] + [node]))
            return
        if node in done:
            return
        active.append(node)
        for target in sorted(graph[node]):
            cycles(target)
        active.pop()
        done.add(node)

    for node in sorted(graph):
        cycles(node)
    return sorted(set(findings))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--workspace", type=Path,
                        help="Also check Framework/Modules/Pay reactors in this workspace; requires all three checkouts")
    args = parser.parse_args(argv)
    roots = [args.root] if args.workspace is None else [args.workspace / name for name in (
        "loadup-framework", "loadup-modules", "loadup-pay")]
    findings = [f"{root.name}/{finding}" for root in roots for finding in check_repository(root)]
    for finding in findings:
        print(finding, file=sys.stderr)
    if findings:
        return 1
    print("PASS: repository source/POM architecture rules (runtime behavior not verified).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
