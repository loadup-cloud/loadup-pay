package io.github.loadup.productcenter.app.model.limitcenter.support;

import io.github.loadup.productcenter.app.model.limitcenter.command.LimitPolicyActivateCommand;
import io.github.loadup.productcenter.app.model.limitcenter.dto.LimitRuleDTO;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Canonical protocol v1: sorted rules, UTF-8 fields framed with a four-byte big-endian byte length.
 */
public final class LimitPolicyHash {
    private LimitPolicyHash() {}

    public static String compute(String tenant, LimitPolicyActivateCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, "loadup-pay-limit-policy-v1");
            add(digest, tenant);
            add(digest, command.policyId());
            add(digest, command.policyVersion());
            add(digest, command.payeeMerchantId());
            add(digest, command.refundAmountRestore());
            add(digest, command.refundCountRestore());
            var rules = command.rules().stream()
                    .sorted(Comparator.comparing(LimitPolicyHash::key))
                    .toList();
            add(digest, rules.size());
            for (var rule : rules) {
                add(digest, rule.ruleScopeId());
                add(digest, rule.merchantId());
                add(digest, rule.metric());
                add(digest, rule.currency());
                add(digest, rule.window());
                add(digest, rule.weekStart());
                add(digest, rule.threshold());
                add(digest, rule.enforcementEnabled());
                add(digest, rule.meteringEnabled());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String key(LimitRuleDTO rule) {
        return String.join(
                "\0",
                rule.ruleScopeId(),
                rule.merchantId(),
                rule.metric().name(),
                rule.currency(),
                rule.window().name());
    }

    private static void add(MessageDigest digest, Object field) {
        byte[] bytes = Objects.requireNonNull(field).toString().getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
