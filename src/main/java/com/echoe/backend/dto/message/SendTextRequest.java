package com.echoe.backend.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendTextRequest(
        @NotBlank @Size(max = 10000) String content
) {}
