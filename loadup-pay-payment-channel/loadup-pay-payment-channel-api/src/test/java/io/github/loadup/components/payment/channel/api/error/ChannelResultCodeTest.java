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
package io.github.loadup.components.payment.channel.api.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.loadup.commons.enums.ResultStatusEnum;
import org.junit.jupiter.api.Test;

class ChannelResultCodeTest {
    @Test
    void everyChannelErrorCarriesAStableCodeMessageAndFailureStatus() {
        for (var value : ChannelResultCodeEnum.values()) {
            assertFalse(value.getCode().isBlank());
            assertTrue(value.getCode().startsWith("CHANNEL_"));
            assertTrue(value.getCode().length() <= 64);
            assertFalse(value.getMessage().isBlank());
            assertTrue(value.getMessage().length() <= 256);
            assertEquals(ResultStatusEnum.FAIL.getCode(), value.getStatus());
        }
    }
}
