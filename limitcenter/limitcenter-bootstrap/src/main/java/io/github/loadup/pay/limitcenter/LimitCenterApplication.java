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
package io.github.loadup.pay.limitcenter;

import io.github.loadup.pay.common.BusinessTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.TimeZone;

@SpringBootApplication
public class LimitCenterApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(BusinessTime.ZONE));
        SpringApplication.run(LimitCenterApplication.class, args);
    }

    @Bean
    Clock businessClock() {
        return Clock.system(BusinessTime.ZONE);
    }
}
