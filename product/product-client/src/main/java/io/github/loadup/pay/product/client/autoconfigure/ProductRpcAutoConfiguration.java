/* Copyright 2026 LoadUp Cloud. Licensed under the Apache License, Version 2.0. */
package io.github.loadup.pay.product.client.autoconfigure;

import io.github.loadup.pay.product.rpc.ProductRpcMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** Registers this client's RPC converters wherever the client is used. */
@AutoConfiguration
@ComponentScan(basePackageClasses = ProductRpcMapper.class)
public class ProductRpcAutoConfiguration {}
