package com.echoe.backend.service;

import com.echoe.backend.dto.safety.CrisisResource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CrisisResourceProvider {

    private static final List<CrisisResource> INDIA_HELPLINES = List.of(
            new CrisisResource("iCall", "9152987821", "Mon-Sat 8am-10pm IST"),
            new CrisisResource("Vandrevala Foundation", "1860-2662-345", "24/7"),
            new CrisisResource("AASRA", "9820466726", "24/7")
    );

    public List<CrisisResource> getResources() {
        return INDIA_HELPLINES;
    }
}
