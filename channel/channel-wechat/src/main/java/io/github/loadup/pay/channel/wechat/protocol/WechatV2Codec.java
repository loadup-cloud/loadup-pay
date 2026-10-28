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
package io.github.loadup.pay.channel.wechat.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_CREDENTIAL_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_SIGNATURE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;

/**
 * Strict bounded flat XML and HMAC-SHA256 only. No MD5 downgrade, DTD, entities or unsigned error facts.
 */
public class WechatV2Codec {
    private static final int MAX_BYTES = 65536;

    public String encode(Map<String, String> original, byte[] key) {
        if (original == null || original.isEmpty() || original.size() > 128 || original.containsKey("sign")
                || original.containsKey("key") || !"HMAC-SHA256".equals(original.get("sign_type")))
            throw new CommonException(INVALID_ARGUMENT);
        var fields = new TreeMap<>(original);
        for (var field : fields.entrySet()) {
            if (!validName(field.getKey()) || !validText(field.getValue())) throw new CommonException(INVALID_ARGUMENT);
        }
        fields.put("sign", HexFormat.of().withUpperCase().formatHex(mac(fields, key)));
        try {
            var output = new StringWriter();
            var writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            writer.writeStartElement("xml");
            for (var field : fields.entrySet()) {
                writer.writeStartElement(field.getKey());
                writer.writeCharacters(field.getValue());
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.close();
            var value = output.toString();
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new CommonException(INVALID_ARGUMENT);
            return value;
        } catch (CommonException failure) {
            throw failure;
        } catch (Exception invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    public WechatV2VerifiedResponse verify(WechatV2Method method, byte[] response, byte[] key) {
        if (method == null) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        var fields = parse(response);
        var signature = fields.get("sign");
        if (!"SUCCESS".equals(fields.get("return_code")) || signature == null || !signature.matches("[0-9A-F]{64}")
                || fields.containsKey("sign_type") && !"HMAC-SHA256".equals(fields.get("sign_type")))
            throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(signature), mac(fields, key)))
            throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        return new WechatV2VerifiedResponse(method, fields,
                DigestUtils.sha256(new String(response, StandardCharsets.UTF_8)));
    }

    private Map<String, String> parse(byte[] response) {
        if (response == null || response.length == 0 || response.length > MAX_BYTES)
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        try {
            // Reject malformed UTF-8 before parsing; no encoding declaration can reinterpret the signed bytes.
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response));
            var factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setXMLResolver((publicId, systemId, base, namespace) -> {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            });
            var reader = factory.createXMLStreamReader(new ByteArrayInputStream(response), "UTF-8");
            try {
                if (reader.getCharacterEncodingScheme() != null
                        && !"UTF-8".equalsIgnoreCase(reader.getCharacterEncodingScheme()))
                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
                var fields = new TreeMap<String, String>();
                int depth = 0;
                boolean rootSeen = false, ended = false;
                String name = null;
                var value = new StringBuilder();
                while (reader.hasNext()) {
                    int event = reader.next();
                    switch (event) {
                        case XMLStreamConstants.START_ELEMENT -> {
                            if (ended || reader.getAttributeCount() != 0 || reader.getNamespaceCount() != 0
                                    || reader.getPrefix() != null && !reader.getPrefix().isEmpty()
                                    || reader.getNamespaceURI() != null && !reader.getNamespaceURI().isEmpty())
                                throw new CommonException(CHANNEL_RESPONSE_INVALID);
                            depth++;
                            if (depth == 1) {
                                if (rootSeen || !"xml".equals(reader.getLocalName()))
                                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
                                rootSeen = true;
                            } else if (depth == 2) {
                                name = reader.getLocalName();
                                if (!validName(name) || "key".equals(name) || fields.containsKey(name) || fields.size() >= 128)
                                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
                                value.setLength(0);
                            } else throw new CommonException(CHANNEL_RESPONSE_INVALID);
                        }
                        case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
                            if (depth == 2) {
                                value.append(reader.getText());
                                if (value.length() > 32768) throw new CommonException(CHANNEL_RESPONSE_INVALID);
                            } else if (!reader.isWhiteSpace()) throw new CommonException(CHANNEL_RESPONSE_INVALID);
                        }
                        case XMLStreamConstants.END_ELEMENT -> {
                            if (depth == 2) {
                                if (!validText(value.toString())) throw new CommonException(CHANNEL_RESPONSE_INVALID);
                                fields.put(name, value.toString());
                            } else if (depth == 1) ended = true;
                            else throw new CommonException(CHANNEL_RESPONSE_INVALID);
                            depth--;
                        }
                        case XMLStreamConstants.END_DOCUMENT -> {
                        }
                        default -> throw new CommonException(CHANNEL_RESPONSE_INVALID);
                    }
                }
                if (!rootSeen || !ended || depth != 0) throw new CommonException(CHANNEL_RESPONSE_INVALID);
                return fields;
            } finally {
                reader.close();
            }
        } catch (CommonException failure) {
            throw failure;
        } catch (Exception invalid) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
    }

    byte[] mac(Map<String, String> fields, byte[] key) {
        if (key == null || key.length != 32) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        byte[] prefix = fields.entrySet().stream().filter(e -> !"sign".equals(e.getKey()) && !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&", "", "&key=")).getBytes(StandardCharsets.UTF_8);
        var message = Arrays.copyOf(prefix, prefix.length + key.length);
        System.arraycopy(key, 0, message, prefix.length, key.length);
        try {
            var hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            return hmac.doFinal(message);
        } catch (Exception failure) {
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        } finally {
            Arrays.fill(message, (byte) 0);
            Arrays.fill(prefix, (byte) 0);
        }
    }

    private boolean validName(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]{0,63}");
    }

    private boolean validText(String value) {
        return value != null && value.codePoints().allMatch(c -> !Character.isISOControl(c) && c >= 32 && c <= 0xFFFF && c != 0xFFFE && c != 0xFFFF
                && !Character.isSurrogate((char) c));
    }
}
