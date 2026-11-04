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
package io.github.loadup.productcenter.client.http;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.productcenter.client.command.CatalogActionCommand;
import io.github.loadup.productcenter.client.command.CatalogSaveCommand;
import io.github.loadup.productcenter.client.command.ContractSignCommand;
import io.github.loadup.productcenter.client.command.ContractStatusCommand;
import io.github.loadup.productcenter.client.command.LimitPolicyPublishCommand;
import io.github.loadup.productcenter.client.command.LimitPolicyReplayCommand;
import io.github.loadup.productcenter.client.dto.CatalogVersionDTO;
import io.github.loadup.productcenter.client.dto.LimitPolicyPublicationDTO;
import io.github.loadup.productcenter.client.dto.MerchantContractDTO;
import io.github.loadup.productcenter.client.dto.ProductManagementPage;
import io.github.loadup.productcenter.client.query.CatalogPageQuery;
import io.github.loadup.productcenter.client.query.MerchantContractPageQuery;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface ProductManagementHttpService {
    String BASE_PATH = "/api/internal/v1/productcenter/management";

    @PostExchange(BASE_PATH + "/catalog/save")
    SuccessResponse<CatalogVersionDTO> save(
            @RequestHeader("Authorization") String authorization, @RequestBody CatalogSaveCommand request);

    @PostExchange(BASE_PATH + "/catalog/page")
    ProductManagementPage<CatalogVersionDTO> catalogPage(
            @RequestHeader("Authorization") String authorization, @RequestBody CatalogPageQuery request);

    @PostExchange(BASE_PATH + "/catalog/detail")
    SuccessResponse<CatalogVersionDTO> catalogDetail(
            @RequestHeader("Authorization") String authorization, @RequestBody IdQuery request);

    @PostExchange(BASE_PATH + "/catalog/publish")
    SuccessResponse<CatalogVersionDTO> publish(
            @RequestHeader("Authorization") String authorization, @RequestBody CatalogActionCommand request);

    @PostExchange(BASE_PATH + "/catalog/retire")
    SuccessResponse<CatalogVersionDTO> retire(
            @RequestHeader("Authorization") String authorization, @RequestBody CatalogActionCommand request);

    @PostExchange(BASE_PATH + "/merchant-contracts/preview")
    SuccessResponse<MerchantContractDTO> preview(
            @RequestHeader("Authorization") String authorization, @RequestBody ContractSignCommand request);

    @PostExchange(BASE_PATH + "/merchant-contracts/sign")
    SuccessResponse<MerchantContractDTO> sign(
            @RequestHeader("Authorization") String authorization, @RequestBody ContractSignCommand request);

    @PostExchange(BASE_PATH + "/merchant-contracts/page")
    ProductManagementPage<MerchantContractDTO> contractPage(
            @RequestHeader("Authorization") String authorization, @RequestBody MerchantContractPageQuery request);

    @PostExchange(BASE_PATH + "/merchant-contracts/detail")
    SuccessResponse<MerchantContractDTO> contractDetail(
            @RequestHeader("Authorization") String authorization, @RequestBody IdQuery request);

    @PostExchange(BASE_PATH + "/merchant-contracts/status")
    SuccessResponse<MerchantContractDTO> status(
            @RequestHeader("Authorization") String authorization, @RequestBody ContractStatusCommand request);

    @PostExchange(BASE_PATH + "/limit-publications/publish")
    SuccessResponse<LimitPolicyPublicationDTO> publishLimits(
            @RequestHeader("Authorization") String authorization, @RequestBody LimitPolicyPublishCommand request);

    @PostExchange(BASE_PATH + "/limit-publications/detail")
    SuccessResponse<LimitPolicyPublicationDTO> limitPublication(
            @RequestHeader("Authorization") String authorization, @RequestBody IdQuery request);

    @PostExchange(BASE_PATH + "/limit-publications/replay")
    SuccessResponse<LimitPolicyPublicationDTO> replayLimits(
            @RequestHeader("Authorization") String authorization, @RequestBody LimitPolicyReplayCommand request);
}
