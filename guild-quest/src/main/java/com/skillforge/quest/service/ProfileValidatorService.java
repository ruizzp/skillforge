package com.skillforge.quest.service;

import com.skillforge.quest.domain.ValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Guardrail puro — lê o estado do repositório e retorna aprovado/bloqueado.
 * Não armazena estado. Mesma entrada → mesmo resultado sempre.
 */
@Service
public class ProfileValidatorService {

    @Value("${skillforge.quests.root:quests}")
    private String questsRoot;

    public ValidationResult validate(String domain) {
        Path domainPath = Path.of(questsRoot, "domains", domain);
        List<String> missing = new ArrayList<>();

        if (!Files.exists(domainPath)) {
            return ValidationResult.blocked(List.of(
                "Domínio '%s' não encontrado em %s".formatted(domain, domainPath)
            ));
        }

        Path profile = domainPath.resolve("DOMAIN_PROFILE.md");
        if (!Files.exists(profile)) {
            missing.add("DOMAIN_PROFILE.md ausente em " + domainPath);
        } else {
            checkOpenItems(profile, missing);
        }

        Path validators = domainPath.resolve("validators").resolve("profile-validator.md");
        if (!Files.exists(validators)) {
            missing.add("validators/profile-validator.md ausente — contrato de validação não implementado");
        }

        Path fixtures = domainPath.resolve("fixtures");
        if (!Files.exists(fixtures)) {
            missing.add("fixtures/ ausente — mínimo 5 casos com gabarito necessários");
        } else {
            countFixtures(fixtures, missing);
        }

        return missing.isEmpty() ? ValidationResult.ok() : ValidationResult.blocked(missing);
    }

    private void checkOpenItems(Path profile, List<String> missing) {
        try {
            long openItems = Files.lines(profile)
                .filter(line -> line.contains("- [ ]"))
                .count();
            if (openItems > 0) {
                missing.add("DOMAIN_PROFILE.md tem %d item(ns) bloqueante(s) em aberto".formatted(openItems));
            }
        } catch (Exception ignored) {}
    }

    private void countFixtures(Path fixtures, List<String> missing) {
        try {
            long count = Files.list(fixtures)
                .filter(p -> p.toString().endsWith(".json"))
                .count();
            if (count < 5) {
                missing.add("fixtures/ tem %d arquivo(s) JSON — mínimo necessário: 5".formatted(count));
            }
        } catch (Exception ignored) {}
    }
}