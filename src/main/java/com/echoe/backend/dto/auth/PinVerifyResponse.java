package com.echoe.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PinVerifyResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("attempts_left") Integer attemptsLeft,
        @JsonProperty("locked_until") String lockedUntil
) {}
