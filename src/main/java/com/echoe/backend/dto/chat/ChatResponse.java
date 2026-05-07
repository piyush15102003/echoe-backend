package com.echoe.backend.dto.chat;

import com.echoe.backend.dto.ai.EchoResponse;
import com.echoe.backend.dto.safety.CrisisResource;

import java.util.List;

public record ChatResponse(
        EchoResponse echo,
        boolean crisisDetected,
        List<CrisisResource> crisisResources
) {}
