/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.channel.core;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Repository source/POM gate; bytecode and runtime wiring remain separate evidence. */
class PayModuleBoundaryTest {
    private static final List<String> SERVICES = List.of("acquire", "payment", "customer", "product", "limitcenter", "realtime", "reconciliation");
    private static final List<String> LAYERS = List.of("client", "domain", "app", "infrastructure", "adapter", "rpc", "bootstrap");
    private static final Pattern PAY_REFERENCE = Pattern.compile(
            "\\bio\\.github\\.loadup\\.pay\\.(acquire|payment|customer|product|limitcenter|realtime|reconciliation)"
                    + "\\.(client|domain|app|infrastructure|adapter|rpc|bootstrap)\\b(?:\\.([A-Za-z_$][\\w$]*))?");
    private static final Pattern CHANNEL_IMPLEMENTATION = Pattern.compile("\\bio\\.github\\.loadup\\.pay\\.channel\\.(?!api\\b)[A-Za-z_$][\\w$]*\\.");
    private static final Pattern TECHNICAL_IMPLEMENTATION = Pattern.compile(
            "\\b(?:org\\.springframework|com\\.mybatisflex|org\\.apache\\.ibatis|jakarta\\.persistence"
                    + "|io\\.grpc|com\\.google\\.protobuf|io\\.github\\.loadup\\.components)\\.");

    @Test
    void productionSourcesFollowServiceAndColaBoundaries() throws Exception {
        var violations = new ArrayList<String>();
        for (var module : modules()) {
            var root = module.directory().resolve("src/main/java");
            if (!Files.isDirectory(root)) continue;
            try (var sources = Files.walk(root)) {
                for (var source : sources.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).toList()) {
                    for (var violation : sourceViolations(module, module.directory().relativize(source).toString(), Files.readString(source)))
                        violations.add(source + ": " + violation);
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void productionPomDependenciesFollowTheSameBoundariesIncludingProfiles() throws Exception {
        var violations = new ArrayList<String>();
        for (var module : modules()) {
            for (var dependency : dependencies(module.directory())) {
                for (var violation : dependencyViolations(module, dependency)) violations.add(module.directory() + ": " + violation);
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void sourceRulesRejectBypassesButAllowClientPortsAndPureMapping() {
        assertRejected("payment", "domain", "model/Payment.java", "org.springframework.stereotype.Component marker;", "TECHNICAL_IMPLEMENTATION");
        assertRejected("acquire", "client", "dto/Order.java", "io.github.loadup.pay.acquire.domain.model.Order value;", "OWN_LAYER");
        assertRejected("payment", "app", "service/Payment.java", "io.github.loadup.pay.payment.infrastructure.repository.Payments value;", "OWN_LAYER");
        assertRejected("payment", "infrastructure", "client/Peer.java", "io.github.loadup.pay.acquire.app.service.OrderService value;", "CROSS_SERVICE");
        assertRejected("realtime", "app", "service/Apply.java", "io.github.loadup.pay.payment.rpc.PaymentRpc value;", "TRANSPORT_IN_BUSINESS");
        assertRejected("customer", "app", "service/Customer.java", "io.github.loadup.pay.channel.wechat.WechatChannelProvider value;", "CHANNEL_IMPLEMENTATION");
        assertRejected("payment", "infrastructure", "infrastructure/codec/Codec.java", "io.github.loadup.pay.payment.app.service.ResultService value;", "STORAGE_APP_ROLE");
        assertAllowed("payment", "infrastructure", "infrastructure/repository/Gateway.java", "io.github.loadup.pay.payment.app.port.Codec value;");
        assertAllowed("realtime", "infrastructure", "infrastructure/codec/Codec.java", "io.github.loadup.pay.realtime.app.converter.TradeProjectionConverter value;");
        assertAllowed("acquire", "app", "service/Order.java", "io.github.loadup.pay.payment.client.facade.PaymentFacade value;");
        assertAllowed("payment", "infrastructure", "client/Remote.java", "io.github.loadup.pay.acquire.rpc.AcquireRpc value;");
        assertAllowed("payment", "domain", "model/Payment.java", "io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity value;");
    }

    @Test
    void pomRulesRejectImplementationDependenciesButPreserveAssemblyAndTestAdapters() {
        assertFalse(dependencyViolations(module("payment", "app"), dep("payment-infrastructure", "")).isEmpty());
        assertFalse(dependencyViolations(module("customer", "app"), dep("product-app", "")).isEmpty());
        assertFalse(dependencyViolations(module("payment", "app"), dep("acquire-rpc", "")).isEmpty());
        assertFalse(dependencyViolations(module("payment", "infrastructure"), dep("channel-wechat", "runtime")).isEmpty());
        assertFalse(dependencyViolations(module("payment", "domain"), dep("loadup-components-database", "")).isEmpty());
        assertTrue(dependencyViolations(module("payment", "bootstrap"), dep("channel-wechat", "runtime")).isEmpty());
        assertTrue(dependencyViolations(module("payment", "infrastructure"), dep("payment-app", "")).isEmpty());
        assertTrue(dependencyViolations(module("acquire", "app"), dep("payment-client", "")).isEmpty());
        assertTrue(dependencyViolations(module("payment", "infrastructure"), dep("acquire-rpc", "")).isEmpty());
        assertTrue(dependencyViolations(module("payment", "app"), dep("payment-infrastructure", "test")).isEmpty());
    }

    private static List<String> sourceViolations(Module module, String path, String source) {
        var errors = new ArrayList<String>();
        if (Set.of("client", "domain").contains(module.layer()) && TECHNICAL_IMPLEMENTATION.matcher(source).find())
            errors.add("TECHNICAL_IMPLEMENTATION: client/domain cannot name Spring, ORM, RPC or component implementation types");
        if (CHANNEL_IMPLEMENTATION.matcher(source).find()) errors.add("CHANNEL_IMPLEMENTATION: business sources must use channel-api");
        var references = PAY_REFERENCE.matcher(source);
        while (references.find()) {
            String owner = references.group(1), layer = references.group(2), role = references.group(3);
            if (!owner.equals(module.service())) {
                if (!Set.of("client", "rpc").contains(layer)) errors.add("CROSS_SERVICE: " + owner + "." + layer);
                else if (layer.equals("rpc") && Set.of("client", "domain", "app").contains(module.layer()))
                    errors.add("TRANSPORT_IN_BUSINESS: gRPC belongs in adapters/ports");
            } else {
                if (!allowedOwnLayer(module.layer(), layer)) errors.add("OWN_LAYER: " + module.layer() + " -> " + layer);
                var normalized = "/" + path.replace('\\', '/');
                boolean storage = List.of("repository", "converter", "codec").stream()
                        .anyMatch(segment -> normalized.contains("/infrastructure/" + segment + "/"));
                if (module.layer().equals("infrastructure") && storage && layer.equals("app")
                        && !Set.of("port", "model", "converter").contains(role == null ? "" : role))
                    errors.add("STORAGE_APP_ROLE: data adapters may reference only app port/model/converter");
            }
        }
        return List.copyOf(errors);
    }

    private static boolean allowedOwnLayer(String source, String target) {
        return switch (source) {
            case "client" -> target.equals("client");
            case "domain" -> Set.of("client", "domain").contains(target);
            case "app" -> Set.of("client", "domain", "app").contains(target);
            case "infrastructure" -> !Set.of("adapter", "bootstrap").contains(target);
            default -> true;
        };
    }

    private static List<String> dependencyViolations(Module module, Dependency dependency) {
        if (dependency.scope().equals("test")) return List.of();
        var errors = new ArrayList<String>();
        if (Set.of("client", "domain").contains(module.layer()) && (dependency.group().startsWith("org.springframework")
                || dependency.group().startsWith("org.mybatis") || dependency.group().startsWith("com.mybatis-flex")
                || dependency.group().startsWith("io.grpc") || dependency.group().equals("com.google.protobuf")
                || dependency.group().equals("jakarta.persistence") || dependency.artifact().startsWith("loadup-components-")))
            errors.add("TECHNICAL_DEPENDENCY: " + dependency.artifact());
        if (!dependency.local()) return List.copyOf(errors);
        if (dependency.artifact().startsWith("channel-") && !dependency.artifact().equals("channel-api")
                && !(module.service().equals("payment") && module.layer().equals("bootstrap") && dependency.scope().equals("runtime")))
            errors.add("CHANNEL_IMPLEMENTATION: " + dependency.artifact());
        for (var owner : SERVICES) {
            for (var layer : LAYERS) {
                if (!dependency.artifact().equals(owner + "-" + layer)) continue;
                if (owner.equals(module.service())) {
                    if (!allowedOwnLayer(module.layer(), layer)) errors.add("OWN_LAYER: " + dependency.artifact());
                } else if (!Set.of("client", "rpc").contains(layer)) errors.add("CROSS_SERVICE: " + dependency.artifact());
                else if (layer.equals("rpc") && Set.of("client", "domain", "app").contains(module.layer()))
                    errors.add("TRANSPORT_IN_BUSINESS: " + dependency.artifact());
            }
        }
        return List.copyOf(errors);
    }

    private static void assertRejected(String owner, String layer, String path, String source, String rule) {
        assertTrue(sourceViolations(module(owner, layer), path, source).stream().anyMatch(value -> value.startsWith(rule)), rule);
    }

    private static void assertAllowed(String owner, String layer, String path, String source) {
        assertEquals(List.of(), sourceViolations(module(owner, layer), path, source));
    }

    private static Module module(String owner, String layer) { return new Module(owner, layer, Path.of(owner, owner + "-" + layer)); }
    private static Dependency dep(String artifact, String scope) { return new Dependency("io.github.loadup-cloud", artifact, scope); }

    private static List<Module> modules() {
        var root = repositoryRoot();
        var result = new ArrayList<Module>();
        for (String service : SERVICES) {
            for (String layer : service.equals("reconciliation") ? List.of("client", "domain", "app", "infrastructure") : LAYERS) {
                var directory = root.resolve(service).resolve(service + "-" + layer);
                assertTrue(Files.isRegularFile(directory.resolve("pom.xml")), "Missing expected module " + directory);
                result.add(new Module(service, layer, directory));
            }
        }
        return List.copyOf(result);
    }

    private static Path repositoryRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("channel/channel-core/pom.xml"))) return current;
            current = current.getParent();
        }
        throw new AssertionError("Run this source architecture test inside the loadup-pay checkout");
    }

    private static List<Dependency> dependencies(Path module) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(module.resolve("pom.xml").toFile());
        var xpath = XPathFactory.newInstance().newXPath();
        var nodes = (org.w3c.dom.NodeList) xpath.evaluate("/project/dependencies/dependency | /project/profiles/profile/dependencies/dependency", document, XPathConstants.NODESET);
        var result = new ArrayList<Dependency>();
        for (int index = 0; index < nodes.getLength(); index++) {
            var element = (Element) nodes.item(index);
            result.add(new Dependency(xpath.evaluate("groupId", element).trim(), xpath.evaluate("artifactId", element).trim(), xpath.evaluate("scope", element).trim()));
        }
        return List.copyOf(result);
    }

    private record Module(String service, String layer, Path directory) {}
    private record Dependency(String group, String artifact, String scope) {
        boolean local() { return Set.of("io.github.loadup-cloud", "${project.groupId}", "${project.parent.groupId}").contains(group); }
    }
}
