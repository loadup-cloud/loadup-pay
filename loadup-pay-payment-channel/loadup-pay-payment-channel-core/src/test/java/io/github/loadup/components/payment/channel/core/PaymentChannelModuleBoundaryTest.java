/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.components.payment.channel.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Source boundary gate: the API stays framework-neutral, core does not see vendor binders, and
 * binders do not reach into business services or each other.
 */
class PaymentChannelModuleBoundaryTest {
    private static final Pattern BUSINESS_REFERENCE = Pattern.compile(
            "\\bio\\.github\\.loadup\\.(pay|payment|acquire|realtime|reconciliation|merchantcenter|productcenter|limitcenter)\\.");
    private static final Pattern CORE_REFERENCE =
            Pattern.compile("(?m)^import\\s+io\\.github\\.loadup\\.components\\.payment\\.channel\\.core\\.");
    private static final Pattern API_REFERENCE =
            Pattern.compile("(?m)^import\\s+io\\.github\\.loadup\\.components\\.payment\\.channel\\.api\\.");
    private static final Pattern ALIPAY_REFERENCE =
            Pattern.compile("(?m)^import\\s+io\\.github\\.loadup\\.components\\.payment\\.channel\\.alipay\\.");
    private static final Pattern WECHAT_REFERENCE =
            Pattern.compile("(?m)^import\\s+io\\.github\\.loadup\\.components\\.payment\\.channel\\.wechat\\.");
    private static final Pattern SPRING_REFERENCE = Pattern.compile("\\borg\\.springframework\\.");
    private static final Pattern VENDOR_ACTION_REFERENCE = Pattern.compile(
            "(?m)^import\\s+io\\.github\\.loadup\\.components\\.payment\\.channel\\.api\\.model\\.(Alipay|Wechat)[A-Za-z]*;");
    private static final Pattern CROSS_MODULE_ORDERING = Pattern.compile("\\bafterName\\s*=|\\bbeforeName\\s*=");

    @Test
    void apiHasNoSpringOrVendorImplementationDependency() throws IOException {
        var api = productionSources(componentRoot().resolve("loadup-pay-payment-channel-api"));
        assertFalse(api.isEmpty());
        for (var source : api) {
            var text = Files.readString(source);
            assertFalse(SPRING_REFERENCE.matcher(text).find(), source + " must stay free of Spring");
            assertFalse(CORE_REFERENCE.matcher(text).find(), source + " must not depend on channel-core");
            assertFalse(ALIPAY_REFERENCE.matcher(text).find(), source + " must not depend on the Alipay binder");
            assertFalse(WECHAT_REFERENCE.matcher(text).find(), source + " must not depend on the WeChat binder");
            assertFalse(BUSINESS_REFERENCE.matcher(text).find(), source + " must not depend on a Pay service");
        }
    }

    @Test
    void coreOnlyDependsOnTheApiAndFrameworkComponents() throws IOException {
        var core = productionSources(componentRoot().resolve("loadup-pay-payment-channel-core"));
        assertFalse(core.isEmpty());
        for (var source : core) {
            var text = Files.readString(source);
            assertFalse(ALIPAY_REFERENCE.matcher(text).find(), source + " must not depend on the Alipay binder");
            assertFalse(WECHAT_REFERENCE.matcher(text).find(), source + " must not depend on the WeChat binder");
            assertFalse(VENDOR_ACTION_REFERENCE.matcher(text).find(), source + " must not encode vendor action types");
            assertFalse(BUSINESS_REFERENCE.matcher(text).find(), source + " must not depend on a Pay service");
        }
        assertTrue(core.stream().anyMatch(source -> {
            try {
                return API_REFERENCE.matcher(Files.readString(source)).find();
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }));
    }

    @Test
    void vendorBindersDoNotDependOnCoreOrEachOther() throws IOException {
        var binders = componentRoot().resolve("binders");
        var alipay = productionSources(binders.resolve("loadup-pay-payment-channel-alipay"));
        var wechat = productionSources(binders.resolve("loadup-pay-payment-channel-wechat"));
        assertFalse(alipay.isEmpty());
        assertFalse(wechat.isEmpty());
        for (var source : alipay) {
            var text = Files.readString(source);
            assertFalse(CORE_REFERENCE.matcher(text).find(), source + " must not depend on channel-core");
            assertFalse(WECHAT_REFERENCE.matcher(text).find(), source + " must not depend on the WeChat binder");
            assertFalse(CROSS_MODULE_ORDERING.matcher(text).find(), source + " must rely on provider collection");
            assertFalse(BUSINESS_REFERENCE.matcher(text).find(), source + " must not depend on a Pay service");
        }
        for (var source : wechat) {
            var text = Files.readString(source);
            assertFalse(CORE_REFERENCE.matcher(text).find(), source + " must not depend on channel-core");
            assertFalse(ALIPAY_REFERENCE.matcher(text).find(), source + " must not depend on the Alipay binder");
            assertFalse(CROSS_MODULE_ORDERING.matcher(text).find(), source + " must rely on provider collection");
            assertFalse(BUSINESS_REFERENCE.matcher(text).find(), source + " must not depend on a Pay service");
        }
    }

    @Test
    void autoConfigurationImportsStayInsideTheirOwnModule() throws IOException {
        assertAutoConfiguration(componentRoot().resolve("loadup-pay-payment-channel-core"), "core");
        assertAutoConfiguration(componentRoot().resolve("binders/loadup-pay-payment-channel-alipay"), "alipay");
        assertAutoConfiguration(componentRoot().resolve("binders/loadup-pay-payment-channel-wechat"), "wechat");
    }

    private static void assertAutoConfiguration(Path module, String packageSegment) throws IOException {
        var descriptor = module.resolve(
                "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        assertTrue(Files.isRegularFile(descriptor), descriptor + " must exist");
        var lines = Files.readAllLines(descriptor).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
        assertFalse(lines.isEmpty(), descriptor + " must register an auto-configuration");
        for (var line : lines) {
            assertTrue(
                    line.startsWith("io.github.loadup.components.payment.channel." + packageSegment + "."),
                    descriptor + " must only register its own module: " + line);
        }
    }

    private static List<Path> productionSources(Path module) throws IOException {
        var sourceRoot = module.resolve("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), sourceRoot + " must exist");
        try (var sources = Files.walk(sourceRoot)) {
            return sources.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        }
    }

    private static Path componentRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("loadup-pay-payment-channel-api/pom.xml"))
                    && Files.isRegularFile(current.resolve("loadup-pay-payment-channel-core/pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Run this test inside the loadup-pay-payment-channel module");
    }
}
