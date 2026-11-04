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
package io.github.loadup.merchantcenter.client.http;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.merchantcenter.client.command.MerchantActionGrantCommand;
import io.github.loadup.merchantcenter.client.command.MerchantActionGrantRevokeCommand;
import io.github.loadup.merchantcenter.client.command.MerchantCreateCommand;
import io.github.loadup.merchantcenter.client.command.MerchantRelationshipCreateCommand;
import io.github.loadup.merchantcenter.client.command.MerchantRelationshipRevokeCommand;
import io.github.loadup.merchantcenter.client.command.MerchantStatusCommand;
import io.github.loadup.merchantcenter.client.command.MerchantUpdateCommand;
import io.github.loadup.merchantcenter.client.dto.MerchantActionGrantDTO;
import io.github.loadup.merchantcenter.client.dto.MerchantDTO;
import io.github.loadup.merchantcenter.client.dto.MerchantManagementPage;
import io.github.loadup.merchantcenter.client.dto.MerchantRelationshipDTO;
import io.github.loadup.merchantcenter.client.query.MerchantPageQuery;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface MerchantManagementHttpService {
    String BASE_PATH = "/api/internal/v1/merchantcenter/management";

    @PostExchange(BASE_PATH + "/merchants/create")
    SuccessResponse<MerchantDTO> create(
            @RequestHeader("Authorization") String authorization, @RequestBody MerchantCreateCommand request);

    @PostExchange(BASE_PATH + "/merchants/update")
    SuccessResponse<MerchantDTO> update(
            @RequestHeader("Authorization") String authorization, @RequestBody MerchantUpdateCommand request);

    @PostExchange(BASE_PATH + "/merchants/page")
    MerchantManagementPage<MerchantDTO> page(
            @RequestHeader("Authorization") String authorization, @RequestBody MerchantPageQuery request);

    @PostExchange(BASE_PATH + "/merchants/detail")
    SuccessResponse<MerchantDTO> detail(
            @RequestHeader("Authorization") String authorization, @RequestBody IdQuery request);

    @PostExchange(BASE_PATH + "/merchants/status")
    SuccessResponse<MerchantDTO> status(
            @RequestHeader("Authorization") String authorization, @RequestBody MerchantStatusCommand request);

    @PostExchange(BASE_PATH + "/relationships/create")
    SuccessResponse<MerchantRelationshipDTO> createRelationship(
            @RequestHeader("Authorization") String authorization,
            @RequestBody MerchantRelationshipCreateCommand request);

    @PostExchange(BASE_PATH + "/relationships/revoke")
    SuccessResponse<MerchantRelationshipDTO> revokeRelationship(
            @RequestHeader("Authorization") String authorization,
            @RequestBody MerchantRelationshipRevokeCommand request);

    @PostExchange(BASE_PATH + "/grants/create")
    SuccessResponse<MerchantActionGrantDTO> grant(
            @RequestHeader("Authorization") String authorization, @RequestBody MerchantActionGrantCommand request);

    @PostExchange(BASE_PATH + "/grants/revoke")
    SuccessResponse<MerchantActionGrantDTO> revokeGrant(
            @RequestHeader("Authorization") String authorization,
            @RequestBody MerchantActionGrantRevokeCommand request);
}
