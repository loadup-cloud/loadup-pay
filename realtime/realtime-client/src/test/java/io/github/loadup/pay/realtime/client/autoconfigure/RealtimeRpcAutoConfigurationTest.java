/* Copyright 2026 LoadUp Cloud. Licensed under the Apache License, Version 2.0. */
package io.github.loadup.pay.realtime.client.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.loadup.pay.acquire.rpc.AcquireRpcMapper;
import io.github.loadup.pay.payment.rpc.PaymentCaptureRpcMapper;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import io.github.loadup.pay.payment.rpc.PaymentStatementRpcMapper;
import io.github.loadup.pay.payment.rpc.PaymentVerificationRpcMapper;
import io.github.loadup.pay.product.rpc.ProductRpcMapper;
import io.github.loadup.pay.realtime.rpc.RealtimeRpcMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

class RealtimeRpcAutoConfigurationTest {
    private ApplicationContextRunner clients() {
        var loader = getClass().getClassLoader();
        var candidates = ImportCandidates.load(AutoConfiguration.class, loader).getCandidates().stream()
                .filter(name -> name.startsWith("io.github.loadup.pay.") && name.endsWith("RpcAutoConfiguration"))
                .toList();
        assertThat(candidates).contains(
                "io.github.loadup.pay.realtime.client.autoconfigure.RealtimeRpcAutoConfiguration",
                "io.github.loadup.pay.acquire.client.autoconfigure.AcquireRpcAutoConfiguration",
                "io.github.loadup.pay.payment.client.autoconfigure.PaymentRpcAutoConfiguration",
                "io.github.loadup.pay.product.client.autoconfigure.ProductRpcAutoConfiguration");
        var configurations = candidates.stream()
                .map(name -> ClassUtils.resolveClassName(name, loader))
                .toArray(Class<?>[]::new);
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(configurations));
    }

    @Test
    void clientDependenciesRegisterTheCompleteMapperChainWithoutApplicationScanning() {
        clients().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RealtimeRpcMapper.class);
            assertThat(context).hasSingleBean(AcquireRpcMapper.class);
            assertThat(context).hasSingleBean(PaymentRpcMapper.class);
            assertThat(context).hasSingleBean(ProductRpcMapper.class);
            assertThat(context).hasSingleBean(PaymentCaptureRpcMapper.class);
            assertThat(context).hasSingleBean(PaymentStatementRpcMapper.class);
            assertThat(context).hasSingleBean(PaymentVerificationRpcMapper.class);
        });
    }

    @Test
    void applicationScanningAndClientAutoConfigurationDoNotDuplicateConverters() {
        clients().withUserConfiguration(LocalRpcScan.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RealtimeRpcMapper.class);
            assertThat(context).hasSingleBean(AcquireRpcMapper.class);
            assertThat(context).hasSingleBean(PaymentRpcMapper.class);
            assertThat(context).hasSingleBean(ProductRpcMapper.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = RealtimeRpcMapper.class)
    static class LocalRpcScan {}
}
