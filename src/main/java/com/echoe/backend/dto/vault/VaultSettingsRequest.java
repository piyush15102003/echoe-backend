package com.echoe.backend.dto.vault;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VaultSettingsRequest(
        @JsonProperty("vault_mode_enabled") boolean enable
) {}
