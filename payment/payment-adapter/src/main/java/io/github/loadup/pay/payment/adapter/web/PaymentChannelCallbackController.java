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
package io.github.loadup.pay.payment.adapter.web;

import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.payment.client.facade.PaymentChannelCallbackFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/payment/channel-callbacks")
@Tag(name = "Payment channel callbacks")
public class PaymentChannelCallbackController {
    private final PaymentChannelCallbackFacade callbacks;
    public PaymentChannelCallbackController(PaymentChannelCallbackFacade callbacks) { this.callbacks = callbacks; }

    @PostMapping(value = "/{endpointRef}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Receive a signed channel notification", description = "Returns a native protocol acknowledgement only after committed processing.")
    public ResponseEntity<String> receive(@PathVariable String endpointRef, HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readNBytes(ChannelNotificationInput.MAX_BODY_BYTES + 1);
        if (body.length > ChannelNotificationInput.MAX_BODY_BYTES) return ResponseEntity.status(413).build();
        if (body.length == 0 || request.getQueryString() != null) return ResponseEntity.badRequest().build();
        try {
            var acknowledgement = callbacks.receive(endpointRef, new ChannelNotificationInput(request.getContentType(), body));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(acknowledgement.contentType())).body(acknowledgement.body());
        } catch (RuntimeException rejected) {
            // Unknown endpoints and unavailable protocol strategies cannot produce a successful acknowledgement.
            return ResponseEntity.badRequest().build();
        }
    }
}
