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
package io.github.loadup.pay.acquire.adapter.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CheckoutPageController {
    @GetMapping(value = "/checkout", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> page() {
        return resource("checkout.html", MediaType.TEXT_HTML);
    }

    @GetMapping(value = "/checkout.js", produces = "text/javascript")
    public ResponseEntity<Resource> script() {
        return resource("checkout.js", MediaType.valueOf("text/javascript"));
    }

    @GetMapping(value = "/checkout.css", produces = "text/css")
    public ResponseEntity<Resource> style() {
        return resource("checkout.css", MediaType.valueOf("text/css"));
    }

    private ResponseEntity<Resource> resource(String name, MediaType type) {
        return ResponseEntity.ok()
                .contentType(type)
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        "Content-Security-Policy",
                        "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none';"
                                + " form-action 'none'; frame-ancestors 'none'")
                .body(new ClassPathResource("cashier/" + name));
    }
}
