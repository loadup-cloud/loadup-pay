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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_SIGNATURE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatV2CodecTest {
    private final WechatV2Codec codec = new WechatV2Codec();
    private final byte[] key = "192006250b4c09247ec02edce69f6a2d".getBytes(StandardCharsets.UTF_8);

    @Test
    void officialHmacVectorUsesSortedRawValuesAndAppendedKeyWithoutUrlEncoding() {
        var fields = Map.of("appid", "wxd930ea5d5a258f4f", "mch_id", "10000100", "device_info", "1000",
                "body", "test", "nonce_str", "ibuaiVcKdpRxkhJA");
        assertEquals("6A9AE1657590FD6257D693A078E1C3E4BB6BA4DC30B23E0EE2496E54170DACD6",
                java.util.HexFormat.of().withUpperCase().formatHex(codec.mac(fields, key)));
    }

    @Test
    void signedExtensionFieldsAndEscapedTextArePreservedAndTamperingOrWrongKeyFails() {
        var fields = new TreeMap<>(Map.of("return_code", "SUCCESS", "result_code", "SUCCESS", "appid", "wx-app",
                "mch_id", "1900000001", "sign_type", "HMAC-SHA256", "extension", "a & b < c 中文"));
        String xml = codec.encode(fields, key);
        assertTrue(xml.contains("&amp;"));
        var verified = codec.verify(WechatV2Method.MICROPAY, xml.getBytes(StandardCharsets.UTF_8), key);
        assertEquals(fields.get("extension"), verified.fields().get("extension"));
        assertEquals(64, verified.sourceDigest().length());
        assertFalse(verified.toString().contains("a & b"));
        assertThrows(UnsupportedOperationException.class, () -> verified.fields().put("result_code", "FAIL"));
        assertEquals(CHANNEL_SIGNATURE_INVALID, assertThrows(CommonException.class, () -> codec.verify(WechatV2Method.MICROPAY,
                xml.replace("中文", "改写").getBytes(StandardCharsets.UTF_8), key)).getResultCode());
        assertThrows(CommonException.class, () -> codec.verify(WechatV2Method.MICROPAY, xml.getBytes(StandardCharsets.UTF_8),
                "0".repeat(32).getBytes(StandardCharsets.UTF_8)));
        assertEquals(CHANNEL_SIGNATURE_INVALID, assertThrows(CommonException.class, () -> codec.verify(WechatV2Method.MICROPAY,
                xml.replace("HMAC-SHA256", "MD5").getBytes(StandardCharsets.UTF_8), key)).getResultCode());
    }

    @Test
    void responseMayOmitSignTypeButMustStillCarryTheSelectedHmacAndSignedExtensions() {
        var fields = Map.of("return_code", "SUCCESS", "result_code", "FAIL", "err_code", "USERPAYING", "FutureField", "extension");
        var signature = java.util.HexFormat.of().withUpperCase().formatHex(codec.mac(fields, key));
        String xml = "<xml><return_code>SUCCESS</return_code><result_code>FAIL</result_code><err_code>USERPAYING</err_code>"
                + "<FutureField>extension</FutureField><sign>" + signature + "</sign></xml>";
        assertEquals("extension", codec.verify(WechatV2Method.MICROPAY, xml.getBytes(StandardCharsets.UTF_8), key).fields().get("FutureField"));
        assertThrows(CommonException.class, () -> codec.verify(WechatV2Method.MICROPAY,
                xml.replace("extension", "tampered").getBytes(StandardCharsets.UTF_8), key));
    }

    @Test
    void untrustedXmlCannotReadEntitiesChangeEncodingNestDuplicateOrAppendAnotherRoot() {
        for (String invalid : java.util.List.of("<!DOCTYPE xml [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><xml><body>&x;</body></xml>",
                "<xml><value><nested>1</nested></value></xml>", "<xml><value>1</value><value>2</value></xml>",
                "<xml xmlns='urn:untrusted'><value>1</value></xml>", "<xml value='attribute'/>",
                "<xml><key>secret</key></xml>", "<xml/> <xml/>",
                "<?xml version='1.0' encoding='ISO-8859-1'?><xml/>", "<xml><return_code>FAIL</return_code></xml>")) {
            assertThrows(CommonException.class, () -> codec.verify(WechatV2Method.MICROPAY, invalid.getBytes(StandardCharsets.UTF_8), key));
        }
        assertThrows(CommonException.class, () -> codec.verify(WechatV2Method.MICROPAY, new byte[]{(byte) 0xC3, 0x28}, key));
        assertThrows(CommonException.class, () -> codec.verify(WechatV2Method.MICROPAY, " ".repeat(65537).getBytes(StandardCharsets.UTF_8), key));
        assertThrows(CommonException.class, () -> codec.encode(Map.of("sign_type", "MD5"), key));
        assertThrows(CommonException.class, () -> codec.encode(Map.of("sign_type", "HMAC-SHA256", "body", "four-byte \uD83D\uDE00"), key));
    }
}
