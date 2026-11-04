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
package io.github.loadup.productmng;

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.ResultCode;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.client.error.ProductResultCodeEnum;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class ManagementResponses {
    public <T> T required(Supplier<SuccessResponse<T>> request) {
        try {
            return HttpServiceResponses.requiredData(request.get());
        } catch (RuntimeException failure) {
            String code = HttpServiceResponses.resultCode(failure);
            Stream<ResultCode> allowed = Stream.of(
                            Arrays.stream(ContractResultCodeEnum.values()),
                            Arrays.stream(ProductResultCodeEnum.values()))
                    .flatMap(values -> values.map(value -> (ResultCode) value));
            var known = allowed.filter(value -> value.getCode().equals(code)).findFirst();
            if (known.isPresent()) {
                throw new CommonException(known.orElseThrow());
            }
            throw new CommonException(CommonResultCodeEnum.SYS_ERROR);
        }
    }
}
