package com.echoe.backend.dto.vault;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VaultSettingsResponse(
        @JsonProperty("vault_enabled") boolean vaultEnabled,
        @JsonProperty("has_pin") boolean hasPin
) {}
