/* Copyright 2026 LoadUp Cloud. Licensed under the Apache License, Version 2.0. */
package io.github.loadup.pay.payment.client.autoconfigure;

import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** Registers this client's RPC converters wherever the client is used. */
@AutoConfiguration
@ComponentScan(basePackageClasses = PaymentRpcMapper.class)
public class PaymentRpcAutoConfiguration {}
