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
package io.github.loadup.components.payment.channel.core.codec;

import static io.github.loadup.components.payment.channel.api.error.ChannelResultCodeEnum.ACTION_SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.components.payment.channel.api.codec.ChannelActionCodec;
import io.github.loadup.components.payment.channel.api.codec.ChannelActionTypeCodec;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentAction;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable action codec registry. Vendor binders own strict wire codecs; this class enforces the common kind, size,
 * checksum and type rules.
 */
public final class RegistryChannelActionCodec implements ChannelActionCodec {
    static final int MAX_PAYLOAD_LENGTH = 8192;
    private static final String KIND_PATTERN = "[A-Z0-9_]{1,32}";

    private final Map<String, ChannelActionTypeCodec<?>> byKind;
    private final Map<Class<?>, ChannelActionTypeCodec<?>> byType;

    public RegistryChannelActionCodec(Collection<ChannelActionTypeCodec<?>> candidates) {
        if (candidates == null) {
            throw new CommonException(ACTION_SNAPSHOT_INVALID);
        }
        var kinds = new HashMap<String, ChannelActionTypeCodec<?>>();
        var types = new HashMap<Class<?>, ChannelActionTypeCodec<?>>();
        for (var candidate : candidates) {
            if (candidate == null) {
                throw new CommonException(ACTION_SNAPSHOT_INVALID);
            }
            var kind = candidate.kind();
            var type = candidate.actionType();
            if (kind == null || !kind.matches(KIND_PATTERN) || type == null) {
                throw new CommonException(ACTION_SNAPSHOT_INVALID);
            }
            if (kinds.putIfAbsent(kind, candidate) != null || types.putIfAbsent(type, candidate) != null) {
                throw new CommonException(ACTION_SNAPSHOT_INVALID);
            }
        }
        byKind = Map.copyOf(kinds);
        byType = Map.copyOf(types);
    }

    @Override
    public String kind(ChannelPaymentAction action) {
        return codecFor(action).kind();
    }

    @Override
    public String encode(ChannelPaymentAction action) {
        var codec = codecFor(action);
        var payload = encodeTyped(codec, action);
        if (payload == null || payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new CommonException(ACTION_SNAPSHOT_INVALID);
        }
        return payload;
    }

    @Override
    public String checksum(String payload) {
        return DigestUtils.sha256(payload);
    }

    @Override
    public ChannelPaymentAction decode(String kind, String payload, String payloadHash) {
        if (payload == null
                || payload.length() > MAX_PAYLOAD_LENGTH
                || payloadHash == null
                || kind == null
                || !payloadHash.equals(checksum(payload))) {
            throw new CommonException(ACTION_SNAPSHOT_INVALID);
        }
        var codec = byKind.get(kind);
        if (codec == null) {
            throw new CommonException(ACTION_SNAPSHOT_INVALID);
        }
        try {
            var result = decodeTyped(codec, payload);
            if (result == null || !codec.actionType().isInstance(result)) {
                throw new CommonException(ACTION_SNAPSHOT_INVALID);
            }
            return result;
        } catch (RuntimeException invalid) {
            throw new CommonException(ACTION_SNAPSHOT_INVALID);
        }
    }

    private ChannelActionTypeCodec<?> codecFor(ChannelPaymentAction action) {
        if (action == null) {
            throw new CommonException(ACTION_SNAPSHOT_INVALID);
        }
        var codec = byType.get(action.getClass());
        if (codec == null) {
            throw new CommonException(ACTION_SNAPSHOT_INVALID);
        }
        return codec;
    }

    private static String encodeTyped(ChannelActionTypeCodec<?> codec, ChannelPaymentAction action) {
        return encodeCaptured(codec, action);
    }

    private static <A extends ChannelPaymentAction> String encodeCaptured(
            ChannelActionTypeCodec<A> codec, ChannelPaymentAction action) {
        return codec.encode(codec.actionType().cast(action));
    }

    private static ChannelPaymentAction decodeTyped(ChannelActionTypeCodec<?> codec, String payload) {
        return decodeCaptured(codec, payload);
    }

    private static <A extends ChannelPaymentAction> ChannelPaymentAction decodeCaptured(
            ChannelActionTypeCodec<A> codec, String payload) {
        return codec.decode(payload);
    }
}
