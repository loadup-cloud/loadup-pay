/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Source architecture gate: only the executable assembly may depend on channel implementations. */
class ChannelModuleBoundaryTest {
    private static final Pattern CHANNEL_IMPLEMENTATION_REFERENCE = Pattern.compile(
            "\\bio\\.github\\.loadup\\.pay\\.channel\\.(?!api\\.)[A-Za-z_$][\\w$]*\\.");
    private static final Pattern BUSINESS_REFERENCE = Pattern.compile(
            "\\bio\\.github\\.loadup\\.pay\\.(acquire|payment|customer|product|limitcenter|realtime)\\.");

    @Test
    void paymentProductionSourcesCannotBypassTheChannelApi() throws Exception {
        for (var module : modules(repositoryRoot().resolve("payment"))) {
            for (var source : productionSources(module)) {
                assertFalse(CHANNEL_IMPLEMENTATION_REFERENCE.matcher(Files.readString(source)).find(),
                        source + " must call channel-api instead of importing or naming a plugin implementation");
            }
        }
    }

    @Test
    void channelProductionSourcesCannotReachBackIntoBusinessServices() throws Exception {
        for (var module : modules(repositoryRoot().resolve("channel"))) {
            for (var source : productionSources(module)) {
                assertFalse(BUSINESS_REFERENCE.matcher(Files.readString(source)).find(),
                        source + " must receive channel-api values instead of accessing business internals");
            }
        }
    }

    @Test
    void paymentBusinessModulesOnlyDependOnChannelApi() throws Exception {
        var root = repositoryRoot();
        for (var module : modules(root.resolve("payment"))) {
            if (module.getFileName().toString().equals("payment-bootstrap")) continue;
            for (var dependency : dependencies(module)) {
                if (dependency.local() && dependency.artifact().startsWith("channel-")) {
                    assertEquals("channel-api", dependency.artifact(), module.toString());
                }
            }
        }
        for (var name : List.of("payment-domain", "payment-app", "payment-infrastructure")) {
            assertTrue(dependencies(root.resolve("payment").resolve(name)).stream()
                    .anyMatch(dependency -> dependency.local() && dependency.artifact().equals("channel-api")), name);
        }
    }

    @Test
    void channelModulesCannotDependOnPaymentOrOtherImplementations() throws Exception {
        for (var module : modules(repositoryRoot().resolve("channel"))) {
            for (var dependency : dependencies(module)) {
                if (!dependency.local()) continue;
                assertFalse(dependency.artifact().startsWith("payment-"), module + " -> " + dependency.artifact());
                if (dependency.artifact().startsWith("channel-")) {
                    assertEquals("channel-api", dependency.artifact(), module.toString());
                }
            }
        }
    }

    @Test
    void bootstrapLoadsTheEngineAndVendorPluginsAtRuntime() throws Exception {
        var dependencies = dependencies(repositoryRoot().resolve("payment/payment-bootstrap"));
        var implementationArtifacts = dependencies.stream()
                .filter(dependency -> dependency.local() && dependency.artifact().startsWith("channel-")
                        && !dependency.artifact().equals("channel-api"))
                .toList();
        assertTrue(implementationArtifacts.stream().anyMatch(dependency -> dependency.artifact().equals("channel-core")));
        assertTrue(implementationArtifacts.stream().anyMatch(dependency -> dependency.artifact().equals("channel-alipay")));
        assertTrue(implementationArtifacts.stream().anyMatch(dependency -> dependency.artifact().equals("channel-wechat")));
        for (var dependency : implementationArtifacts) {
            assertEquals("runtime", dependency.scope(), dependency.artifact());
        }
    }

    private static Path repositoryRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("channel/channel-core/pom.xml"))) return current;
            current = current.getParent();
        }
        throw new AssertionError("Run this source architecture test inside the loadup-pay checkout");
    }

    private static List<Path> modules(Path parent) throws Exception {
        try (var children = Files.list(parent)) {
            return children.filter(path -> Files.isRegularFile(path.resolve("pom.xml"))).toList();
        }
    }

    private static List<Path> productionSources(Path module) throws Exception {
        var sourceRoot = module.resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) return List.of();
        try (var sources = Files.walk(sourceRoot)) {
            return sources.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        }
    }

    private static List<Dependency> dependencies(Path module) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(module.resolve("pom.xml").toFile());
        var xpath = XPathFactory.newInstance().newXPath();
        var nodes = (org.w3c.dom.NodeList) xpath.evaluate(
                "/project/dependencies/dependency | /project/profiles/profile/dependencies/dependency",
                document, XPathConstants.NODESET);
        var result = new ArrayList<Dependency>();
        for (int i = 0; i < nodes.getLength(); i++) {
            var element = (Element) nodes.item(i);
            result.add(new Dependency(xpath.evaluate("groupId", element).trim(),
                    xpath.evaluate("artifactId", element).trim(), xpath.evaluate("scope", element).trim()));
        }
        return List.copyOf(result);
    }

    private record Dependency(String group, String artifact, String scope) {
        boolean local() {
            return group.equals("io.github.loadup-cloud");
        }
    }
}
