package com.echoe.backend.dto.vault;

import jakarta.validation.constraints.NotBlank;

public record VaultAccessRequest(
        @NotBlank String pin
) {}
