package com.skillforge.quest.domain;

import java.util.List;

public record ValidationResult(
    boolean valid,
    List<String> missing,
    List<String> warnings
) {
    public static ValidationResult ok() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult blocked(List<String> missing) {
        return new ValidationResult(false, missing, List.of());
    }
}