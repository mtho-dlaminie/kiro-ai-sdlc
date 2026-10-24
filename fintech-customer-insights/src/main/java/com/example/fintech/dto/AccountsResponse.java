package com.example.fintech.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Collection of accounts for a customer. Empty collections return an empty list, never 404.")
public record AccountsResponse(
        @Schema(example = "CUST-000001") String customerId,
        int count,
        List<AccountResponse> items) {
}
