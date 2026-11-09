"""Structural ownership gates for the LoadUp Pay reactor.

These checks intentionally inspect source and Maven descriptors without invoking
Maven, so dependency-direction regressions are visible before compilation.
"""

from __future__ import annotations

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PAY_ROOT = ROOT
SERVICES = (
    "acquire",
    "limitcenter",
    "merchantcenter",
    "payment",
    "productcenter",
    "realtime",
    "reconciliation",
)
CLIENT_MODULES = {service: f"{service}/{service}-client" for service in SERVICES}
DOMAIN_ARTIFACTS = {f"{service}-domain" for service in SERVICES}
CLIENT_ARTIFACTS = {f"{service}-client" for service in SERVICES}
GROUP_ID = "io.github.loadup-cloud"
CONTRACT_PREFIX = "io.github.loadup.contracts."
XML_NS = "{http://maven.apache.org/POM/4.0.0}"


def _text(element: ET.Element | None) -> str:
    return (element.text or "").strip() if element is not None else ""


def _pom(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def _module_poms() -> dict[Path, ET.Element]:
    """Return modules reachable from the Pay root POM's declared module tree."""
    root_pom = PAY_ROOT / "pom.xml"
    pending = [PAY_ROOT]
    modules: dict[Path, ET.Element] = {}
    while pending:
        directory = pending.pop()
        pom_path = directory / "pom.xml"
        if not pom_path.is_file():
            continue
        resolved = pom_path.resolve()
        if resolved in modules:
            continue
        model = _pom(pom_path)
        modules[resolved] = model
        for module in model.findall(f"{XML_NS}modules/{XML_NS}module"):
            child = (directory / _text(module)).resolve()
            if child != root_pom.parent.resolve():
                pending.append(child)
    return modules


def _direct_production_dependencies(model: ET.Element) -> set[str]:
    dependencies: set[str] = set()
    for dependency in model.findall(f"{XML_NS}dependencies/{XML_NS}dependency"):
        group = _text(dependency.find(f"{XML_NS}groupId"))
        scope = _text(dependency.find(f"{XML_NS}scope"))
        if group != GROUP_ID or scope == "test":
            continue
        if scope not in ("", "compile", "runtime"):
            continue
        artifact = _text(dependency.find(f"{XML_NS}artifactId"))
        if artifact:
            dependencies.add(artifact)
    return dependencies


def _java_sources(module_path: Path) -> list[Path]:
    source_root = PAY_ROOT / module_path / "src/main/java"
    return sorted(source_root.rglob("*.java")) if source_root.is_dir() else []


def _package(source: str) -> str:
    match = re.search(r"(?m)^\s*package\s+([\w.]+)\s*;", source)
    return match.group(1) if match else ""


class ContractOwnershipTest(unittest.TestCase):
    def test_application_and_domain_sources_use_consumer_owned_models(self) -> None:
        problems: list[str] = []
        for service in SERVICES:
            for source_path in (PAY_ROOT / service).glob("*/src/main/java/**/*.java"):
                source = source_path.read_text(encoding="utf-8")
                namespace = _package(source)
                if not namespace.startswith((f"io.github.loadup.{service}.app.", f"io.github.loadup.{service}.domain.")):
                    continue
                for imported in re.findall(r"(?m)^\s*import\s+(?:static\s+)?([\w.]+)\s*;", source):
                    match = re.match(r"io\.github\.loadup\.(\w+)\.client\.", imported)
                    if match and match.group(1) in SERVICES and match.group(1) != service:
                        problems.append(f"{source_path.relative_to(PAY_ROOT)}: remote contract {imported}")
        self.assertEqual([], problems, "Remote contracts escaped their adapters:\n" + "\n".join(problems))

    def test_provider_clients_have_no_domain_or_peer_client_imports(self) -> None:
        problems: list[str] = []
        for service, module_path in CLIENT_MODULES.items():
            own_prefix = f"io.github.loadup.{service}.client."
            for source_path in _java_sources(Path(module_path)):
                relative = source_path.relative_to(ROOT)
                source = source_path.read_text(encoding="utf-8")
                for imported in re.findall(r"(?m)^\s*import\s+(?:static\s+)?([\w.]+)\s*;", source):
                    if re.match(r"io\.github\.loadup\.[\w]+\.domain\.", imported):
                        problems.append(f"{relative}: domain import {imported}")
                    if re.match(r"io\.github\.loadup\.[\w]+\.client\.", imported) and not imported.startswith(own_prefix):
                        problems.append(f"{relative}: peer-client import {imported}")

            model = _pom(PAY_ROOT / module_path / "pom.xml")
            bad_artifacts = _direct_production_dependencies(model) & (DOMAIN_ARTIFACTS | (CLIENT_ARTIFACTS - {f"{service}-client"}))
            problems.extend(f"{module_path}/pom.xml: production dependency {artifact}" for artifact in sorted(bad_artifacts))

        self.assertEqual([], problems, "Provider client ownership violations:\n" + "\n".join(problems))

    def test_event_artifact_contains_only_event_records_and_lifecycle_enums(self) -> None:
        event_module = Path("pay-contracts/pay-contracts-events")
        source_root = PAY_ROOT / event_module / "src/main/java"
        problems: list[str] = []
        for source_path in sorted(source_root.rglob("*.java")):
            relative = source_path.relative_to(ROOT)
            source = source_path.read_text(encoding="utf-8")
            package = _package(source)
            if package != f"{CONTRACT_PREFIX}event" and not package.startswith(f"{CONTRACT_PREFIX}event."):
                problems.append(f"{relative}: non-event package {package or '<missing>'}")
            imports = re.findall(r"(?m)^\s*import\s+(?:static\s+)?([\w.]+)\s*;", source)
            if any(re.match(r"io\.github\.loadup\.[\w]+\.client\.", imported) for imported in imports):
                problems.append(f"{relative}: event contract imports a service client")
            if any(re.match(r"io\.github\.loadup\.[\w]+\.domain\.", imported) for imported in imports):
                problems.append(f"{relative}: event contract imports a service domain")
            declarations = re.findall(
                r"(?m)^\s*public\s+(?:(?:final|sealed|abstract)\s+)*(record|enum|class|interface)\s+([A-Za-z_$][\w$]*)",
                source,
            )
            for kind, name in declarations:
                if kind not in ("record", "enum"):
                    problems.append(f"{relative}: public {kind} {name} is not an event record/lifecycle enum")

        self.assertEqual([], problems, "Event artifact ownership violations:\n" + "\n".join(problems))

    def test_contract_event_artifact_has_no_former_payment_product_or_model_packages(self) -> None:
        source_root = PAY_ROOT / "pay-contracts/pay-contracts-events/src/main/java"
        found = sorted(
            path.relative_to(ROOT).as_posix()
            for package in ("payment", "product", "model")
            for path in (source_root / "io/github/loadup/contracts" / package).rglob("*.java")
            if path.is_file()
        )
        self.assertEqual([], found, "Former provider contract packages remain in the event artifact:\n" + "\n".join(found))

    def test_root_reactor_compile_runtime_dependency_graph_is_acyclic(self) -> None:
        modules = _module_poms()
        artifact_to_path: dict[str, Path] = {}
        for pom_path, model in modules.items():
            artifact = _text(model.find(f"{XML_NS}artifactId"))
            if artifact:
                artifact_to_path[artifact] = pom_path.parent

        graph: dict[Path, set[Path]] = {path.parent: set() for path in modules}
        for pom_path, model in modules.items():
            owner = pom_path.parent
            for artifact in _direct_production_dependencies(model):
                target = artifact_to_path.get(artifact)
                if target is not None:
                    graph[owner].add(target)

        visiting: set[Path] = set()
        visited: set[Path] = set()
        stack: list[Path] = []
        cycles: list[str] = []

        def visit(node: Path) -> None:
            if node in visited:
                return
            if node in visiting:
                start = stack.index(node)
                cycles.append(" -> ".join(path.relative_to(PAY_ROOT).as_posix() for path in stack[start:] + [node]))
                return
            visiting.add(node)
            stack.append(node)
            for dependency in sorted(graph[node], key=str):
                visit(dependency)
            stack.pop()
            visiting.remove(node)
            visited.add(node)

        for module in sorted(graph, key=str):
            visit(module)

        self.assertEqual([], cycles, "Compile/runtime Maven dependency cycles:\n" + "\n".join(cycles))


if __name__ == "__main__":
    unittest.main()
