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
package io.github.loadup.realtime.infrastructure.repository;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Clears every business table before each test because integration tests share one schema. */
final class DatabaseResetExtension implements BeforeEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        String[] candidates = applicationContext.getBeanNamesForType(JdbcTemplate.class);
        if (candidates.length == 0) {
            return;
        }
        JdbcTemplate jdbc = applicationContext.getBean(candidates[0], JdbcTemplate.class);
        String tables = jdbc.queryForObject(
                "SELECT coalesce(string_agg(format('%I', tablename), ', '), '') FROM pg_tables"
                        + " WHERE schemaname = current_schema() AND tablename NOT LIKE 'flyway%'",
                String.class);
        if (tables != null && !tables.isBlank()) {
            jdbc.execute("TRUNCATE TABLE " + tables + " CASCADE");
        }
    }
}
