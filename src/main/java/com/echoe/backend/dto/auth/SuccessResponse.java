package com.echoe.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SuccessResponse(
        @JsonProperty("success") boolean success
) {}
