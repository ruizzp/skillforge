package com.skillforge.marketing.domain;

import java.util.List;

public record ProjectBrief(
    String projectId,
    String projectName,
    String problem,
    List<String> audiences,
    String differentiator,
    String traction,
    String revenueModel,
    String cta,
    String context
) {}