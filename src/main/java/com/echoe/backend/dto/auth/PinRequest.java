package com.echoe.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PinRequest(
        @NotBlank @Pattern(regexp = "\\d{4}") @JsonProperty("pin") String pin
) {}
