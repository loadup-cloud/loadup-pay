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
package io.github.loadup.pay.channel.alipay.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlipayNotificationPropertiesTest {
    private static final URI CALLBACK = URI.create("https://callbacks.example/payments/alipay/notify");

    private static void assertInvalid(String value) {
        assertThrows(RuntimeException.class, () -> new AlipayNotificationProperties(
                Map.of("tenant", Map.of("binding-revision", URI.create(value)))));
    }

    @Test
    void acceptsHttpsPathAndDefensivelyCopiesTheNestedTenantAndBindingMaps() {
        var tenantBindings = new HashMap<String, URI>();
        tenantBindings.put("binding-revision", CALLBACK);
        var source = new HashMap<String, Map<String, URI>>();
        source.put("tenant", tenantBindings);

        var properties = new AlipayNotificationProperties(source);
        tenantBindings.put("binding-revision", URI.create("https://attacker.example/changed"));
        source.put("other-tenant", Map.of("other-binding", URI.create("https://other.example/path")));

        assertEquals(CALLBACK, properties.notifyUrls().get("tenant").get("binding-revision"));
        assertFalse(properties.notifyUrls().containsKey("other-tenant"));
        assertThrows(UnsupportedOperationException.class,
                () -> properties.notifyUrls().put("new-tenant", Map.of("new-binding", CALLBACK)));
        assertThrows(UnsupportedOperationException.class,
                () -> properties.notifyUrls().get("tenant").put("new-binding", CALLBACK));
    }

    @Test
    void rejectsUrlsWithoutTrustedHttpsHostAndNonemptyPathOrWithUserInfoQueryOrFragment() {
        assertInvalid("http://callbacks.example/path");
        assertInvalid("https://user@callbacks.example/path");
        assertInvalid("https://callbacks.example/path?tenant=other");
        assertInvalid("https://callbacks.example/path#fragment");
        assertInvalid("https://callbacks.example");
        assertInvalid("https:///path");
        assertInvalid("https://callbacks.example/" + "a".repeat(2048));
    }

    @Test
    void rejectsInvalidTenantAndFrozenBindingIdentifiers() {
        assertThrows(RuntimeException.class, () -> new AlipayNotificationProperties(
                Map.of("../tenant", Map.of("binding", CALLBACK))));
        assertThrows(RuntimeException.class, () -> new AlipayNotificationProperties(
                Map.of("tenant", Map.of("../binding", CALLBACK))));
    }
}
