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
package io.github.loadup.productcenter.client.http;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.productcenter.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.productcenter.client.dto.OrderAdmissionDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface ProductHttpService {
    String AUTHORIZE_PATH = "/api/internal/v1/productcenter/admissions/authorize";
    String DETAIL_PATH = "/api/internal/v1/productcenter/admissions/detail";

    @PostExchange(AUTHORIZE_PATH)
    SuccessResponse<OrderAdmissionDTO> authorize(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody OrderAdmissionAuthorizeCommand command);

    @PostExchange(DETAIL_PATH)
    SuccessResponse<OrderAdmissionDTO> detail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody IdQuery query);
}
