package io.github.loadup.acquire.infrastructure.client;

import io.github.loadup.acquire.infrastructure.converter.RemoteContractConverter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackageClasses = RemoteContractConverter.class,
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RemoteContractConverter.class))
class RemoteContractMapperTestConfiguration {}
