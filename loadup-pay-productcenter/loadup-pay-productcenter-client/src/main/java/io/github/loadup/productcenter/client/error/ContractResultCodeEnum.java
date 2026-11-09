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
package io.github.loadup.productcenter.client.error;

import io.github.loadup.commons.enums.ResultStatusEnum;
import io.github.loadup.commons.result.ResultCode;

public enum ContractResultCodeEnum implements ResultCode {
    NOT_FOUND("CONTRACT_NOT_FOUND", "Contract resource not found"),
    CONFLICT("CONTRACT_CONFLICT", "Contract resource changed or already exists"),
    INVALID_STATE("CONTRACT_INVALID_STATE", "Operation not allowed in current state"),
    MERCHANT_UNVERIFIED("CONTRACT_MERCHANT_UNVERIFIED", "Verified active merchant is required"),
    PARAMETER_KEY_MISMATCH("CONTRACT_PARAMETER_KEY_MISMATCH", "Parameter key mismatch"),
    CONFLICTING_PRODUCT_RELATIONSHIPS(
            "CONTRACT_CONFLICTING_PRODUCT_RELATIONSHIPS", "Conflicting product relationships"),
    PLAN_OUTSIDE_SALE_WINDOW("CONTRACT_PLAN_OUTSIDE_SALE_WINDOW", "Plan outside sale window"),
    MERCHANT_ELIGIBILITY_NOT_ESTABLISHED(
            "CONTRACT_MERCHANT_ELIGIBILITY_NOT_ESTABLISHED", "Merchant eligibility not established"),
    OVERRIDE_FOR_UNSELECTED_PRODUCT("CONTRACT_OVERRIDE_FOR_UNSELECTED_PRODUCT", "Override for unselected product"),
    GENERATION_MUST_BE_POSITIVE("CONTRACT_GENERATION_MUST_BE_POSITIVE", "Generation must be positive"),
    INVALID_REVISION_COUNT("CONTRACT_INVALID_REVISION_COUNT", "Invalid revision count"),
    CONTRACT_IDENTITY_MISMATCH("CONTRACT_CONTRACT_IDENTITY_MISMATCH", "Contract identity mismatch"),
    DUPLICATE_REVISION("CONTRACT_DUPLICATE_REVISION", "Duplicate revision"),
    OVERLAPPING_REVISIONS("CONTRACT_OVERLAPPING_REVISIONS", "Overlapping revisions"),
    VALUE_EXCEEDS_SIZE_LIMIT("CONTRACT_VALUE_EXCEEDS_SIZE_LIMIT", "Value exceeds size limit"),
    DECIMAL_EXCEEDS_PRECISION_LIMIT("CONTRACT_DECIMAL_EXCEEDS_PRECISION_LIMIT", "Decimal exceeds precision limit"),
    BOOLEAN_MUST_BE_TRUE_OR_FALSE("CONTRACT_BOOLEAN_MUST_BE_TRUE_OR_FALSE", "Boolean must be true or false"),
    NOT_A_NUMERIC_VALUE("CONTRACT_NOT_A_NUMERIC_VALUE", "Not a numeric value"),
    PRODUCT_OVERRIDE_FORBIDDEN("CONTRACT_PRODUCT_OVERRIDE_FORBIDDEN", "Product defaults are not an override layer"),
    TOO_MANY_ALLOWED_VALUES("CONTRACT_TOO_MANY_ALLOWED_VALUES", "Too many allowed values"),
    ALLOWED_VALUE_TYPE_MISMATCH("CONTRACT_ALLOWED_VALUE_TYPE_MISMATCH", "Allowed value type mismatch"),
    BOUNDS_REQUIRE_NUMERIC_PARAMETERS(
            "CONTRACT_BOUNDS_REQUIRE_NUMERIC_PARAMETERS", "Bounds require numeric parameters"),
    INTEGER_RANGE_CONTAINS_NO_VALUE("CONTRACT_INTEGER_RANGE_CONTAINS_NO_VALUE", "Integer range contains no value"),
    INVERTED_BOUNDS("CONTRACT_INVERTED_BOUNDS", "Inverted bounds"),
    PARAMETER_TYPE_MISMATCH("CONTRACT_PARAMETER_TYPE_MISMATCH", "Parameter type mismatch"),
    VALUE_NOT_ALLOWED("CONTRACT_VALUE_NOT_ALLOWED", "Value not allowed"),
    BELOW_MINIMUM("CONTRACT_BELOW_MINIMUM", "Below minimum"),
    ABOVE_MAXIMUM("CONTRACT_ABOVE_MAXIMUM", "Above maximum"),
    BACKDATED_ACTIVATION_UNSUPPORTED("CONTRACT_BACKDATED_ACTIVATION_UNSUPPORTED", "Backdated activation unsupported"),
    EMPTY_SIGNED_CONTRACT("CONTRACT_EMPTY_SIGNED_CONTRACT", "Empty signed contract"),
    INVALID_BUNDLE_SIZE("CONTRACT_INVALID_BUNDLE_SIZE", "Invalid bundle size"),
    DUPLICATE_BUNDLE_ITEM("CONTRACT_DUPLICATE_BUNDLE_ITEM", "Duplicate bundle item"),
    REQUIRED_ITEM_NOT_SELECTED("CONTRACT_REQUIRED_ITEM_NOT_SELECTED", "Required item must be selected by default"),
    DUPLICATE_BUNDLE_ALIAS("CONTRACT_DUPLICATE_BUNDLE_ALIAS", "Duplicate bundle alias"),
    DUPLICATE_QUALIFIED_ITEM("CONTRACT_DUPLICATE_QUALIFIED_ITEM", "Duplicate qualified item"),
    UNKNOWN_PERMISSION_FIELD("CONTRACT_UNKNOWN_PERMISSION_FIELD", "Unknown permission field"),
    REQUIRED_FIELD_CANNOT_BE_FILLED("CONTRACT_REQUIRED_FIELD_CANNOT_BE_FILLED", "Required field cannot be filled: "),
    TOO_MANY_PLAN_ITEMS("CONTRACT_TOO_MANY_PLAN_ITEMS", "Too many plan items"),
    UNKNOWN_PLAN_ITEM("CONTRACT_UNKNOWN_PLAN_ITEM", "Unknown plan item"),
    INVALID_BUNDLE_ORIGIN("CONTRACT_INVALID_BUNDLE_ORIGIN", "Invalid bundle origin"),
    INVALID_BUNDLE_COUNT("CONTRACT_INVALID_BUNDLE_COUNT", "Invalid bundle count"),
    MERCHANT_OVERRIDE_FORBIDDEN("CONTRACT_MERCHANT_OVERRIDE_FORBIDDEN", "Merchant override forbidden: "),
    MERCHANT_MINIMUM_WIDENS_PRODUCT_BOUNDS(
            "CONTRACT_MERCHANT_MINIMUM_WIDENS_PRODUCT_BOUNDS", "Merchant minimum widens product bounds"),
    MERCHANT_MAXIMUM_WIDENS_PRODUCT_BOUNDS(
            "CONTRACT_MERCHANT_MAXIMUM_WIDENS_PRODUCT_BOUNDS", "Merchant maximum widens product bounds"),
    EMPTY_MERCHANT_CONSTRAINT("CONTRACT_EMPTY_MERCHANT_CONSTRAINT", "Merchant constraints have no allowed value"),
    INVALID_OVERRIDE_LAYER("CONTRACT_INVALID_OVERRIDE_LAYER", "Invalid override layer"),
    UNKNOWN_PARAMETER("CONTRACT_UNKNOWN_PARAMETER", "Unknown parameter: "),
    OVERRIDE_FORBIDDEN("CONTRACT_OVERRIDE_FORBIDDEN", "Override forbidden: "),
    MERCHANT_PERMISSION_MISSING("CONTRACT_MERCHANT_PERMISSION_MISSING", "Merchant permission missing: "),
    UNKNOWN_RESOLVED_PARAMETER("CONTRACT_UNKNOWN_RESOLVED_PARAMETER", "Unknown resolved parameter"),
    MISSING_REQUIRED_PARAMETER("CONTRACT_MISSING_REQUIRED_PARAMETER", "Missing required parameter: "),
    TOO_MANY_CHILDREN("CONTRACT_TOO_MANY_CHILDREN", "Too many children"),
    INVALID_OR_CHILDREN("CONTRACT_INVALID_OR_CHILDREN", "Invalid OR children"),
    INVALID_CONFIGURATION_OPERAND("CONTRACT_INVALID_CONFIGURATION_OPERAND", "Invalid configuration operand"),
    INVALID_SET_SIZE("CONTRACT_INVALID_SET_SIZE", "Invalid set size"),
    INVALID_OPERAND_COUNT("CONTRACT_INVALID_OPERAND_COUNT", "Invalid operand count"),
    MIXED_OPERAND_TYPES("CONTRACT_MIXED_OPERAND_TYPES", "Mixed operand types"),
    BOOLEAN_ORDERING_UNSUPPORTED("CONTRACT_BOOLEAN_ORDERING_UNSUPPORTED", "Boolean ordering unsupported"),
    INVERTED_COMPARISON_RANGE("CONTRACT_INVERTED_COMPARISON_RANGE", "Inverted comparison range"),
    UNKNOWN_RULE_CONFIGURATION_REFERENCE(
            "CONTRACT_UNKNOWN_RULE_CONFIGURATION_REFERENCE", "Unknown rule configuration reference"),
    RULE_TREE_EXCEEDS_BUDGET("CONTRACT_RULE_TREE_EXCEEDS_BUDGET", "Rule tree exceeds budget"),
    INVALID_PRODUCT_SELECTION("CONTRACT_INVALID_PRODUCT_SELECTION", "Invalid product selection"),
    REQUIRED_PRODUCT_OMITTED("CONTRACT_REQUIRED_PRODUCT_OMITTED", "Required product omitted"),
    MISSING_PRODUCT_DEPENDENCY("CONTRACT_MISSING_PRODUCT_DEPENDENCY", "Missing product dependency"),
    MUTUALLY_EXCLUSIVE_PRODUCTS("CONTRACT_MUTUALLY_EXCLUSIVE_PRODUCTS", "Mutually exclusive products"),
    INVALID_IDENTIFIER("CONTRACT_INVALID_IDENTIFIER", "Invalid identifier: "),
    VERSION_MUST_BE_POSITIVE("CONTRACT_VERSION_MUST_BE_POSITIVE", "Version must be positive"),
    INVALID_INTERVAL("CONTRACT_INVALID_INTERVAL", "Invalid interval"),
    TOO_MANY_ENTRIES("CONTRACT_TOO_MANY_ENTRIES", "Too many entries"),
    MISSING_VALUE_PROVENANCE("CONTRACT_MISSING_VALUE_PROVENANCE", "Missing value provenance"),
    INVALID_PAGE("CONTRACT_INVALID_PAGE", "Page must be positive and size must be between 1 and 100"),
    MERCHANT_LOOKUP_ID_REQUIRED("CONTRACT_MERCHANT_LOOKUP_ID_REQUIRED", "Tenant and lookup identifiers are required"),
    CONTRACT_STATE_CHANGED_OR_TERMINATED(
            "CONTRACT_CONTRACT_STATE_CHANGED_OR_TERMINATED", "Contract state changed or terminated"),
    CATALOG_VERSION_CHANGED("CONTRACT_CATALOG_VERSION_CHANGED", "Catalog version changed"),
    MERCHANT_PROVIDER_UNAVAILABLE(
            "CONTRACT_MERCHANT_PROVIDER_UNAVAILABLE", "Configure a trusted MerchantFactsProvider"),
    TOO_MANY_MERCHANT_FACTS("CONTRACT_TOO_MANY_MERCHANT_FACTS", "Too many merchant facts"),
    UNTRUSTED_FACT_NAMESPACE("CONTRACT_UNTRUSTED_FACT_NAMESPACE", "Untrusted fact namespace"),
    AUTHENTICATED_ACTOR_REQUIRED("CONTRACT_AUTHENTICATED_ACTOR_REQUIRED", "Authenticated actor required"),
    INVALID_IDENTIFIER_2E6368("CONTRACT_INVALID_IDENTIFIER_2E6368", "Invalid identifier"),
    CONTRACT_CONTENT_EXCEEDS_LIMIT("CONTRACT_CONTRACT_CONTENT_EXCEEDS_LIMIT", "Contract content exceeds limit"),
    INVALID_CATALOG_REFERENCE(
            "CONTRACT_INVALID_CATALOG_REFERENCE", "Reference must be a published version of the expected kind"),
    INVALID_CONDITION_CLAUSES("CONTRACT_INVALID_CONDITION_CLAUSES", "Invalid condition clauses"),
    UNSUPPORTED_FACT_NAMESPACE("CONTRACT_UNSUPPORTED_FACT_NAMESPACE", "Unsupported fact namespace"),
    UNKNOWN_CLAUSE_KIND("CONTRACT_UNKNOWN_CLAUSE_KIND", "Unknown clause kind"),
    UNKNOWN_CONDITION_MODE("CONTRACT_UNKNOWN_CONDITION_MODE", "Unknown condition mode"),
    INVALID_PARAMETER_COUNT("CONTRACT_INVALID_PARAMETER_COUNT", "Invalid parameter count"),
    DUPLICATE_PARAMETER("CONTRACT_DUPLICATE_PARAMETER", "Duplicate parameter"),
    INVALID_BUNDLE_ITEMS("CONTRACT_INVALID_BUNDLE_ITEMS", "Invalid bundle items"),
    INVALID_PLAN_BUNDLES("CONTRACT_INVALID_PLAN_BUNDLES", "Invalid plan bundles"),
    DUPLICATE_PLAN_ITEM("CONTRACT_DUPLICATE_PLAN_ITEM", "Duplicate plan item"),
    UNKNOWN_POLICY_FIELD("CONTRACT_UNKNOWN_POLICY_FIELD", "Unknown policy field"),
    ELIGIBILITY_REQUIRES_MERCHANT_FACTS(
            "CONTRACT_ELIGIBILITY_REQUIRES_MERCHANT_FACTS", "Eligibility requires merchant facts"),
    UNKNOWN_OR_NULL_PARAMETER("CONTRACT_UNKNOWN_OR_NULL_PARAMETER", "Unknown or null parameter"),
    VALUES_REQUIRED("CONTRACT_VALUES_REQUIRED", "Values required"),
    UNKNOWN_SIGNED_ITEM("CONTRACT_UNKNOWN_SIGNED_ITEM", "Unknown signed item"),
    INVALID_FACTS("CONTRACT_INVALID_FACTS", "Invalid facts"),
    TRANSACTION_FACT_NAMESPACE_REQUIRED(
            "CONTRACT_TRANSACTION_FACT_NAMESPACE_REQUIRED", "Transaction fact namespace required"),
    SELECTION_AND_VALUES_REQUIRED("CONTRACT_SELECTION_AND_VALUES_REQUIRED", "Selection and values required"),
    PLAN_MUST_BE_PUBLISHED_AND_ON_SALE(
            "CONTRACT_PLAN_MUST_BE_PUBLISHED_AND_ON_SALE", "Plan must be published and on sale"),
    IDEMPOTENCY_CONFLICT("CONTRACT_IDEMPOTENCY_CONFLICT", "Request key reused with different payload"),
    INVALID_CATALOG_DEFINITION("CONTRACT_INVALID_CATALOG_DEFINITION", "Invalid catalog definition"),
    DRAFT_NOT_EDITABLE(
            "CONTRACT_DRAFT_NOT_EDITABLE",
            "Only the expected draft can be edited; copy to a new version to change identity");
    private final String code;
    private final String message;

    ContractResultCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getStatus() {
        return ResultStatusEnum.FAIL.getCode();
    }
}
