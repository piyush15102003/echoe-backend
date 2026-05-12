package com.echoe.backend.dto.vault;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VaultSettingsRequest(
        boolean enable,
        @NotBlank @Size(min = 4, max = 6) String pin,
        @JsonProperty("confirm_pin") String confirmPin
) {}
