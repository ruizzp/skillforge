package com.skillforge.hub.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class HeroValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern HERO_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{1,38}$");

    public record ValidationResult(boolean valid, JsonNode manifest, List<String> errors) {
        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, null, errors);
        }
        public static ValidationResult ok(JsonNode manifest) {
            return new ValidationResult(true, manifest, List.of());
        }
    }

    public static ValidationResult validate(String issueBody) {
        List<String> errors = new ArrayList<>();

        JsonNode manifest;
        try {
            manifest = MAPPER.readTree(extractJson(issueBody));
        } catch (Exception e) {
            return ValidationResult.fail(List.of(
                    "O corpo da issue não contém um JSON válido.",
                    "Consulte [SKILL_MANIFEST_GUIDE.md](SKILL_MANIFEST_GUIDE.md) para o formato correto."
            ));
        }

        String heroId = manifest.path("heroId").asText("").trim();
        if (heroId.isBlank()) {
            errors.add("`heroId` é obrigatório.");
        } else if (!HERO_ID.matcher(heroId).matches()) {
            errors.add("`heroId` deve conter apenas letras minúsculas, números e hífens (ex: `meu-heroi-01`).");
        }

        if (manifest.path("heroName").asText("").isBlank()) errors.add("`heroName` é obrigatório.");

        JsonNode skills = manifest.path("skills");
        if (skills.isMissingNode() || !skills.isArray() || skills.isEmpty()) {
            errors.add("`skills` é obrigatório e deve ter ao menos uma skill declarada.");
        }

        String endpoint = manifest.path("endpoint").asText("").trim();
        if (endpoint.isBlank()) {
            errors.add("`endpoint` é obrigatório (ex: `http://localhost:8081`).");
        } else if (!endpoint.startsWith("http")) {
            errors.add("`endpoint` deve começar com `http://` ou `https://`.");
        }

        return errors.isEmpty() ? ValidationResult.ok(manifest) : ValidationResult.fail(errors);
    }

    private static String extractJson(String body) {
        if (body == null) throw new IllegalArgumentException("body is null");
        String trimmed = body.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < 0) throw new IllegalArgumentException("no JSON object found");
        return trimmed.substring(start, end + 1);
    }
}