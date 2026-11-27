/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.loadup.modules.upms.app.autoconfigure.UpmsAutoConfiguration;
import io.github.loadup.modules.upms.authserver.UpmsAuthServerAutoConfiguration;
import io.github.loadup.modules.upms.authserver.UpmsAuthenticationProvider;
import io.github.loadup.modules.upms.infrastructure.autoconfigure.UpmsPersistenceAutoConfiguration;
import io.github.loadup.modules.upms.web.AuthenticationController;
import io.github.loadup.modules.upms.web.UpmsWebAutoConfiguration;
import io.github.loadup.modules.upms.web.UserController;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

class UpmsAssemblyTest {
    @Test
    void adminClasspathDiscoversAllUpmsAssemblyOwners() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, AdminApplication.class.getClassLoader())
                        .getCandidates())
                .contains(
                        UpmsPersistenceAutoConfiguration.class.getName(),
                        UpmsAutoConfiguration.class.getName(),
                        UpmsWebAutoConfiguration.class.getName(),
                        UpmsAuthServerAutoConfiguration.class.getName());
    }

    @Test
    void adminUpmsCompositionRegistersDefaultPersistenceWebAndAuthenticationOnce() {
        runner(UpmsWebAutoConfiguration.class, UpmsAuthServerAutoConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AuthenticationController.class);
                    assertThat(context)
                            .hasSingleBean(UserController.class)
                            .hasSingleBean(UpmsAuthenticationProvider.class);
                    for (String gateway : new String[] {
                        "userGatewayImpl",
                        "roleGatewayImpl",
                        "permissionGatewayImpl",
                        "departmentGatewayImpl",
                        "loginLogGatewayImpl",
                        "userOAuthBindingGatewayImpl"
                    }) {
                        assertThat(context).hasBean(gateway);
                    }
                });
    }

    @Test
    void webAdapterAssemblesWithoutAuthServerConfiguration() {
        runner(UpmsWebAutoConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AuthenticationController.class);
            assertThat(context).hasSingleBean(UserController.class).doesNotHaveBean(UpmsAuthenticationProvider.class);
        });
    }

    @Test
    void authServerAdapterAssemblesWithoutWebAdapterConfiguration() {
        runner(UpmsAuthServerAutoConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(UpmsAuthenticationProvider.class);
            assertThat(context).doesNotHaveBean(AuthenticationController.class);
        });
    }

    private ApplicationContextRunner runner(Class<?>... adapters) {
        var configurations = new ArrayList<Class<?>>();
        configurations.add(UpmsAutoConfiguration.class);
        configurations.add(UpmsPersistenceAutoConfiguration.class);
        configurations.addAll(List.of(adapters));
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(configurations.toArray(Class<?>[]::new)))
                .withPropertyValues("loadup.security.auth-server.protocol-endpoints-enabled=false")
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
                .withBean(JdbcClient.class, () -> mock(JdbcClient.class));
    }
}
