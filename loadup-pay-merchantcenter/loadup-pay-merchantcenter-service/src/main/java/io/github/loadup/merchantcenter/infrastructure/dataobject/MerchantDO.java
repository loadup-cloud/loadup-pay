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
package io.github.loadup.merchantcenter.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.components.database.dataobject.BaseDO;

@Table("merchant_profile")
public class MerchantDO extends BaseDO {
    private String merchantCode;
    private String name;
    private String shortName;
    private String type;
    private String industry;
    private String country;
    private String province;
    private String city;
    private String address;
    private String registrationNo;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String status;
    private Long rowVersion;
    private String createdBy;
    private String updatedBy;

    public String getMerchantCode() {
        return merchantCode;
    }

    public void setMerchantCode(String value) {
        merchantCode = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String value) {
        shortName = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String value) {
        type = value;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String value) {
        industry = value;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String value) {
        country = value;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String value) {
        province = value;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String value) {
        city = value;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String value) {
        address = value;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public void setRegistrationNo(String value) {
        registrationNo = value;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String value) {
        contactName = value;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String value) {
        contactPhone = value;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String value) {
        contactEmail = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String value) {
        createdBy = value;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String value) {
        updatedBy = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
