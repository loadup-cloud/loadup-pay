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
package io.github.loadup.pay.channel.api.model;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;

import java.time.LocalDate;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;

/**
 * A collecting account/day/profile, never a caller-supplied download location or financial execution ticket.
 */
public record ChannelStatementScope(Channel channel, String accountScopeHash, LocalDate billDate,
                                    String statementProfile) {
    public ChannelStatementScope {
        if (channel == null || accountScopeHash == null || !accountScopeHash.matches("[0-9a-f]{64}") || billDate == null
                || billDate.getYear() < 1970 || billDate.getYear() > 9998 || statementProfile == null || statementProfile.isBlank()
                || statementProfile.length() > 64 || statementProfile.chars().anyMatch(Character::isISOControl))
            throw new CommonException(INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
