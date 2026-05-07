package com.skillforge.quest.web;

import com.skillforge.quest.domain.QuestDraft;
import com.skillforge.quest.domain.QuestRequest;
import com.skillforge.quest.domain.ValidationResult;
import com.skillforge.quest.service.ProfileValidatorService;
import com.skillforge.quest.service.QuestGuardianService;
import com.skillforge.quest.service.QuestPublisherService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class QuestController {

    private final ProfileValidatorService profileValidator;
    private final QuestGuardianService guardian;
    private final QuestPublisherService publisher;

    public QuestController(ProfileValidatorService profileValidator,
                           QuestGuardianService guardian,
                           QuestPublisherService publisher) {
        this.profileValidator = profileValidator;
        this.guardian = guardian;
        this.publisher = publisher;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        var resource = new ClassPathResource("manifest.json");
        String heroId = "guild-quest";
        try {
            heroId = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll(".*\"heroId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        } catch (Exception ignored) {}
        return Map.of("heroId", heroId, "status", "UP");
    }

    /** Valida se um domain profile está pronto para receber quests. */
    @GetMapping("/api/validate/{domain}")
    public ResponseEntity<ValidationResult> validateDomain(@PathVariable String domain) {
        ValidationResult result = profileValidator.validate(domain);
        int status = result.valid() ? 200 : 422;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Gera um rascunho de quest via HTTP — sem precisar do AMQP.
     * Útil para testes e desenvolvimento.
     */
    @PostMapping("/api/quest")
    public ResponseEntity<Map<String, Object>> createQuest(@RequestBody QuestRequest request) {
        QuestDraft draft = guardian.createQuest(request);

        if (draft.confidence() == 0.0) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                "error", "Quest não gerada",
                "reason", draft.markdown()
            ));
        }

        String issueUrl = publisher.publish(draft);

        return ResponseEntity.ok(Map.of(
            "title",      draft.title(),
            "rarity",     draft.rarity(),
            "xp",         draft.xpReward(),
            "skills",     draft.requiredSkills(),
            "confidence", draft.confidence(),
            "model",      draft.model(),
            "issueUrl",   issueUrl,
            "markdown",   draft.markdown()
        ));
    }
}