/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.components.payment.channel.alipay.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlipayNotificationPropertiesTest {
    private static final URI CALLBACK = URI.create("https://callbacks.example/payments/alipay/notify");

    private static void assertInvalid(String value) {
        assertThrows(
                RuntimeException.class,
                () -> new AlipayNotificationProperties(Map.of("binding-revision", URI.create(value))));
    }

    @Test
    void acceptsHttpsPathAndDefensivelyCopiesTheBindingMap() {
        var bindings = new HashMap<String, URI>();
        bindings.put("binding-revision", CALLBACK);

        var properties = new AlipayNotificationProperties(bindings);
        bindings.put("binding-revision", URI.create("https://attacker.example/changed"));

        assertEquals(CALLBACK, properties.notifyUrls().get("binding-revision"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> properties.notifyUrls().put("new-binding", CALLBACK));
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
    void rejectsInvalidFrozenBindingIdentifiers() {
        assertThrows(RuntimeException.class, () -> new AlipayNotificationProperties(Map.of("../binding", CALLBACK)));
    }
}
