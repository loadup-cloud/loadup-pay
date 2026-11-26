#!/usr/bin/env python3
"""Read-only consistency checks for offline Pay deployment inputs."""

from __future__ import annotations

import argparse
import re
import sys
from urllib.parse import urlsplit, urlunsplit, parse_qsl
from pathlib import Path

import yaml

PAY_ROOT = Path(__file__).resolve().parent.parent
ENVIRONMENTS = ("dev", "test", "prod")


class UniqueKeyLoader(yaml.SafeLoader):
    """Safe YAML loader that rejects duplicate mapping keys."""


def _mapping(loader, node, deep=False):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in result:
            raise yaml.constructor.ConstructorError(
                "while constructing a mapping", node.start_mark,
                f"duplicate key {key!r}", key_node.start_mark,
            )
        result[key] = loader.construct_object(value_node, deep=deep)
    return result


UniqueKeyLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _mapping)


def _load(path: Path):
    with path.open(encoding="utf-8") as stream:
        return list(yaml.load_all(stream, Loader=UniqueKeyLoader))


def _flatten(value, prefix="", output=None):
    output = {} if output is None else output
    if isinstance(value, dict):
        for key, child in value.items():
            name = f"{prefix}.{key}" if prefix else str(key)
            if name in output:
                raise ValueError("duplicate flattened configuration key")
            output[name] = child
            _flatten(child, name, output)
    return output


def _env(path: Path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError("invalid ordinary environment entry")
        key, value = line.split("=", 1)
        key = key.strip()
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key) or key in values:
            raise ValueError("invalid or duplicate ordinary environment key")
        values[key] = value.strip()
    return values


def _app_configs(root: Path, apps):
    result = {}
    standalone = {"oauth2-server", "admin", "notificationcenter"}
    for app in apps:
        module = root / f"loadup-pay-{app}"
        base = module if app in standalone else module / f"loadup-pay-{app}-bootstrap"
        paths = sorted((base / "src/main/resources").glob("application*.yml"), key=lambda path: path.name != "application.yml")
        if paths:
            result[app] = paths
    return result


def _normalized_target(value):
    value = str(value).strip()
    if value.startswith("jdbc:"):
        value = value[5:]
    parsed = urlsplit(value)
    if parsed.scheme and parsed.netloc:
        return urlunsplit((parsed.scheme.lower(), parsed.netloc.lower(), parsed.path.rstrip("/"), "", ""))
    return value.rstrip("/").lower()


def _check_repository(root: Path, environment_dir: Path | None = None) -> list[str]:
    """Return stable, value-redacted findings for repository-level invariants."""
    root = Path(root).resolve()
    scripts = root / "scripts"
    sys.path.insert(0, str(scripts))
    try:
        import common
        import render_deploy
    finally:
        if sys.path[0] == str(scripts):
            sys.path.pop(0)
    findings = []

    try:
        manifest = [doc for doc in _load(root / "deploy/pay.yml") if isinstance(doc, dict)]
    except (OSError, yaml.YAMLError) as exc:
        return [f"YAML001 deploy/pay.yml cannot be loaded safely ({type(exc).__name__})"]
    roster = set(common.PAY_APPS)
    if roster != render_deploy.APPS or set(render_deploy.CALLERS) != roster:
        findings.append("ROSTER001 common and deployment application rosters differ")
    for app, callers in render_deploy.CALLERS.items():
        if any(caller not in roster for caller in callers):
            findings.append(f"ROSTER002 {app} names an unknown caller")

    resources = {}
    for doc in manifest:
        kind = doc.get("kind")
        name = doc.get("metadata", {}).get("name")
        if kind in {"Deployment", "Service", "NetworkPolicy"}:
            resources.setdefault((kind, name), []).append(doc)
    expected_resources = {(kind, name) for app in roster for kind, name in (
        ("Deployment", app), ("Service", app), ("NetworkPolicy", f"{app}-http-ingress"))}
    if set(resources) != expected_resources:
        findings.append("ROSTER003 manifest workloads/services/policies differ from the application roster")
    for app in sorted(roster):
        deployment = resources.get(("Deployment", app), [])
        service = resources.get(("Service", app), [])
        policy = resources.get(("NetworkPolicy", f"{app}-http-ingress"), [])
        if len(deployment) != 1 or len(service) != 1 or len(policy) != 1:
            findings.append(f"K8S001 {app} must have one Deployment, Service and ingress policy")
            continue
        dep, svc, net = deployment[0], service[0], policy[0]
        spec = dep.get("spec", {})
        pod = spec.get("template", {}).get("spec", {})
        containers = pod.get("containers", [])
        cs = [c for c in containers if c.get("name") == app]
        if dep.get("metadata", {}).get("namespace") != "loadup-pay" or len(cs) != 1:
            findings.append(f"K8S002 {app} deployment namespace/container is inconsistent")
            continue
        container = cs[0]
        labels = spec.get("template", {}).get("metadata", {}).get("labels", {})
        selector = spec.get("selector", {}).get("matchLabels", {})
        if labels.get("app") != app or selector.get("app") != app:
            findings.append(f"K8S003 {app} pod labels and Deployment selector must match")
        ports = container.get("ports", [])
        if (len(ports) != 1 or ports[0].get("name") != "http"
                or ports[0].get("containerPort") != 8080
                or ports[0].get("protocol", "TCP") != "TCP"):
            findings.append(f"K8S004 {app} container must expose HTTP port 8080")
        if svc.get("metadata", {}).get("namespace") != "loadup-pay" or svc.get("spec", {}).get("selector") != {"app": app}:
            findings.append(f"K8S005 {app} Service selector/namespace must match its pods")
        service_ports = svc.get("spec", {}).get("ports", [])
        if (len(service_ports) != 1 or service_ports[0].get("port") != 8080
                or service_ports[0].get("targetPort") not in ("http", 8080)):
            findings.append(f"K8S006 {app} Service must target its HTTP 8080 port")
        if (net.get("metadata", {}).get("namespace") != "loadup-pay"
                or net.get("spec") != render_deploy.http_ingress_spec(app)):
            findings.append(f"K8S007 {app} ingress policy differs from declared caller/gateway/metrics peers")
        for probe in ("startupProbe", "readinessProbe", "livenessProbe"):
            http = container.get(probe, {}).get("httpGet", {})
            expected_path = "/actuator/health/readiness" if probe == "readinessProbe" else "/actuator/health/liveness"
            if http.get("path") != expected_path or http.get("port") not in ("http", 8080):
                findings.append(f"K8S008 {app} is missing HTTP {probe}")
        annotations = dep.get("spec", {}).get("template", {}).get("metadata", {}).get("annotations", {})
        if annotations.get("prometheus.io/scrape") != "true" or annotations.get("prometheus.io/path") != "/actuator/prometheus" or str(annotations.get("prometheus.io/port")) != "8080":
            findings.append(f"K8S009 {app} Prometheus scrape annotations must target HTTP 8080 actuator metrics")
        prestop = container.get("lifecycle", {}).get("preStop", {}).get("exec", {}).get("command", [])
        sleep = 0
        for token in prestop:
            match = re.search(r"\bsleep\s+(\d+)\b", str(token))
            if match:
                sleep = max(sleep, int(match.group(1)))
        app_paths = _app_configs(root, roster).get(app, [])
        shutdown = 0
        if app_paths:
            try:
                data = _flatten(_load(app_paths[0])[0] or {})
                shutdown = _duration_seconds(data.get("spring.lifecycle.timeout-per-shutdown-phase", "0s"))
            except (OSError, yaml.YAMLError, IndexError, ValueError):
                findings.append(f"APP001 {app} application YAML is malformed")
        grace = pod.get("terminationGracePeriodSeconds", 0)
        if not sleep or not shutdown or sleep + shutdown >= grace:
            findings.append(f"K8S010 {app} preStop plus graceful shutdown must fit terminationGracePeriodSeconds")

    app_files = _app_configs(root, roster)
    internal_client_names = roster | {"oauth-management", "payment-statements"}
    for app in sorted(roster):
        paths = app_files.get(app, [])
        if not paths:
            findings.append(f"APP002 {app} application configuration is missing")
            continue
        config = None
        for path in paths:
            try:
                parsed = _load(path)
                if len(parsed) != 1 or not isinstance(parsed[0], dict):
                    raise ValueError("expected one application YAML mapping")
                flattened = _flatten(parsed[0])
            except (OSError, yaml.YAMLError, IndexError, ValueError):
                findings.append(f"APP001 {app} application YAML is malformed")
                continue
            if path.name == "application.yml":
                config = flattened
            for key in flattened:
                if re.search(r"(?:^|\.)(?:tenant-routing|tenant-routing-data-source|routing-data-source|tenant-data-source)(?:\.|$)", key, re.I):
                    findings.append(f"APP007 {app} contains a legacy tenant/dynamic datasource setting")
            override_keys = re.compile(
                r"spring\.datasource\.(?:hikari\.)?(?:schema|connection-init-sql|connection-init-sqls|connection-properties)$",
                re.I,
            )
            url_override = re.compile(r"(?:currentSchema|(?:^|[?&])options=)", re.I)
            if any(override_keys.search(key) for key in flattened) or any(
                key.endswith(".url") and isinstance(value, str) and url_override.search(value)
                for key, value in flattened.items()
            ):
                findings.append(f"APP007 {app} contains a datasource schema override or initialization SQL")
            client_base = "loadup.http.clients."
            for key in flattened:
                suffix = ".base-url"
                if key.startswith(client_base) and key.endswith(suffix):
                    name = key[len(client_base):-len(suffix)].split(".", 1)[0]
                    if name in internal_client_names and flattened.get(f"{client_base}{name}.internal-service") is not True:
                        findings.append(f"APP008 {app} named internal HTTP client lacks internal-service: true")
        if config is None:
            continue
        if config.get("spring.application.name") != app:
            findings.append(f"APP003 {app} spring.application.name must match its deployment name")
        if "loadup.deployment.code" not in config or "DEPLOYMENT_CODE" not in str(config["loadup.deployment.code"]):
            findings.append(f"APP004 {app} must use fixed loadup.deployment.code from DEPLOYMENT_CODE")
        if "loadup.database.schema" not in config or "DEPLOYMENT_DATABASE_SCHEMA" not in str(config.get("loadup.database.schema", "")):
            findings.append(f"APP005 {app} must source its fixed database schema from DEPLOYMENT_DATABASE_SCHEMA")
        if config.get("server.shutdown") != "graceful":
            findings.append(f"APP009 {app} must enable graceful server shutdown")
        locations = config.get("loadup.flyway.locations", [])
        allowed_locations = {f"classpath:db/migration/pay/{app}"}
        if app == "oauth2-server":
            allowed_locations |= {"classpath:db/loadup-oauth", "classpath:db/migration/framework/audit"}
        elif app == "admin":
            allowed_locations |= {"classpath:db/migration/framework/upms", "classpath:db/migration/framework/audit"}
        elif app == "notificationcenter":
            allowed_locations |= {"classpath:db/migration/modules/gotone", "classpath:db/migration/modules/inbox"}
        elif app == "reconciliation":
            allowed_locations |= {"classpath:db/migration/framework/file", "classpath:db/migration/framework/transfer"}
        if not isinstance(locations, list) or set(locations) != allowed_locations or len(locations) != len(allowed_locations):
            findings.append(f"APP006 {app} Flyway locations must use the application-specific allowlist")

    try:
        scrape = _load(root / "deploy/prometheus-scrape.yml")[0]
        jobs = scrape["serverFiles"]["prometheus.yml"]["scrape_configs"]
        pay_jobs = [job for job in jobs if job.get("job_name") == "loadup-pay"]
        if len(pay_jobs) != 1 or pay_jobs[0].get("metrics_path") != "/actuator/prometheus" or not any(
                rule.get("target_label") == "application" and rule.get("source_labels") == ["__meta_kubernetes_pod_label_app"]
                for rule in pay_jobs[0].get("relabel_configs", [])):
            findings.append("METRICS001 Prometheus scrape path/application label differs from workload/alert contracts")
    except (OSError, yaml.YAMLError, KeyError, IndexError, TypeError):
        findings.append("METRICS001 Prometheus scrape configuration is missing or malformed")

    selected_env_dir = Path(environment_dir).resolve() if environment_dir is not None else None
    environments = (("effective", selected_env_dir),) if selected_env_dir else tuple((env, root / "deploy/config" / env) for env in ENVIRONMENTS)
    for environment, env_root in environments:
        codes = set()
        db_targets = {}
        account_owners = {}
        for app in sorted(roster):
            path = env_root / app / "config.env"
            if not path.exists():
                findings.append(f"ENV001 {environment}/{app} config.env is missing")
                continue
            common_file = env_root / "common.env"
            values = _env(common_file) if common_file.exists() else {}
            values.update(_env(path))
            reserved = {re.sub(r"[^A-Z0-9]", "", key.upper()) for key in values}
            if any(key in reserved for key in (
                    "SPRINGDATASOURCEHIKARISCHEMA", "SPRINGDATASOURCESCHEMA",
                    "SPRINGDATASOURCEHIKARICONNECTIONINITSQL", "LOADUPDATABASEDEFAULTSCHEMA",
                    "LOADUPDATABASEDEFAULTTENANTID", "LOADUPTENANTENABLED")):
                findings.append(f"ENV008 {environment}/{app} overrides the fixed datasource/deployment contract")
            code = values.get("DEPLOYMENT_CODE", "")
            schema = values.get("DEPLOYMENT_DATABASE_SCHEMA", "")
            if code:
                codes.add(code)
            else:
                findings.append(f"ENV006 {environment}/{app} deployment code is empty")
            if not schema:
                findings.append(f"ENV002 {environment}/{app} database schema is empty")
            jdbc_values = [v for k, v in values.items() if k.endswith("_JDBC_URL") and v]
            usernames = [v for k, v in values.items() if k.endswith("_JDBC_USERNAME") and v]
            if selected_env_dir and (not jdbc_values or not usernames):
                findings.append(f"ENV007 {environment}/{app} effective JDBC URL or username is empty")
            if any(any(key.lower() in {"currentschema", "options"} for key, _ in parse_qsl(urlsplit(url.removeprefix("jdbc:")).query)) for url in jdbc_values):
                findings.append(f"ENV008 {environment}/{app} JDBC driver overrides its schema/search path")
            if selected_env_dir and jdbc_values and usernames:
                parsed_target = urlsplit(_normalized_target(jdbc_values[0]))
                account = (parsed_target.scheme, parsed_target.netloc, usernames[0])
                if account in account_owners:
                    findings.append(f"ENV009 {environment} apps {account_owners[account]} and {app} reuse a database account")
                else:
                    account_owners[account] = app
            if schema and jdbc_values:
                target = (_normalized_target(jdbc_values[0]), schema)
                if target in db_targets:
                    findings.append(f"ENV004 {environment} apps {db_targets[target]} and {app} share a JDBC target and schema")
                else:
                    db_targets[target] = app
        if len(codes) > 1:
            findings.append(f"ENV005 {environment} apps use inconsistent deployment codes")
    return findings


def check_repository(root: Path, environment_dir: Path | None = None) -> list[str]:
    """Fail closed without exposing configuration contents on structural errors."""
    try:
        return sorted(set(_check_repository(root, environment_dir)))
    except (OSError, ValueError, TypeError, KeyError, AttributeError, yaml.YAMLError):
        return ["STRUCT001 repository/configuration input has an invalid structure"]


def _duration_seconds(value):
    match = re.fullmatch(r"(\d+)(ms|s|m|h)", str(value).strip())
    if not match:
        raise ValueError("unsupported duration")
    amount, unit = int(match.group(1)), match.group(2)
    return amount * {"ms": 0.001, "s": 1, "m": 60, "h": 3600}[unit]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=PAY_ROOT, help="loadup-pay repository root")
    parser.add_argument("--environment-dir", type=Path, help="optional effective ordinary config.env directory (one file per app)")
    args = parser.parse_args(argv)
    findings = check_repository(args.root, args.environment_dir)
    if findings:
        print("\n".join(findings))
        return 1
    print("PASS: offline operations/configuration consistency checks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
