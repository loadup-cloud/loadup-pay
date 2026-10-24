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
import io.github.loadup.pay.payment.client.facade.RefundChannelCallbackFacade;
import io.github.loadup.pay.channel.api.model.ChannelNotificationAcknowledgement;
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
    private final RefundChannelCallbackFacade refundCallbacks;
    public PaymentChannelCallbackController(PaymentChannelCallbackFacade callbacks, RefundChannelCallbackFacade refundCallbacks) {
        this.callbacks = callbacks; this.refundCallbacks = refundCallbacks;
    }

    @PostMapping(value = "/{endpointRef}", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Receive a signed channel notification", description = "Returns a native protocol acknowledgement only after committed processing.")
    public ResponseEntity<String> receive(@PathVariable String endpointRef, HttpServletRequest request) throws IOException {
        return receive(endpointRef, request, callbacks::receive);
    }

    @PostMapping(value = "/{endpointRef}/refund", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Receive a signed refund notification", description = "Returns native ACK only after committed original-refund query processing.")
    public ResponseEntity<String> receiveRefund(@PathVariable String endpointRef, HttpServletRequest request) throws IOException {
        return receive(endpointRef, request, refundCallbacks::receive);
    }

    private ResponseEntity<String> receive(String endpointRef, HttpServletRequest request,
            java.util.function.BiFunction<String, ChannelNotificationInput, ChannelNotificationAcknowledgement> receiver) throws IOException {
        byte[] body = request.getInputStream().readNBytes(ChannelNotificationInput.MAX_BODY_BYTES + 1);
        if (body.length > ChannelNotificationInput.MAX_BODY_BYTES) return ResponseEntity.status(413).build();
        if (body.length == 0 || request.getQueryString() != null) return ResponseEntity.badRequest().build();
        try {
            var headers = new java.util.HashMap<String, java.util.List<String>>();
            for (String name : java.util.List.of("Wechatpay-Serial", "Wechatpay-Timestamp", "Wechatpay-Nonce", "Wechatpay-Signature", "Wechatpay-Signature-Type")) {
                var values = request.getHeaders(name);
                if (values != null && values.hasMoreElements()) {
                    var captured = new java.util.ArrayList<String>();
                    while (values.hasMoreElements() && captured.size() <= 8) captured.add(values.nextElement());
                    headers.put(name, captured);
                }
            }
            var acknowledgement = receiver.apply(endpointRef, new ChannelNotificationInput(request.getContentType(), headers, body));
            return ResponseEntity.status(acknowledgement.httpStatus()).cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(acknowledgement.contentType())).body(acknowledgement.body());
        } catch (RuntimeException rejected) {
            // Unknown endpoints and unavailable protocol strategies cannot produce a successful acknowledgement.
            return ResponseEntity.badRequest().build();
        }
    }
}
