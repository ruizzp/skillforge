package com.skillforge.marketing.domain;

public record PitchDraft(
    String guildPitch,
    String investorOnePager,
    double clareza,
    double dor,
    double diferencial,
    double credibilidade,
    boolean diferencialDefensavel,
    boolean ctaPresente,
    String veredicto,
    double confidence,
    boolean valid
) {}