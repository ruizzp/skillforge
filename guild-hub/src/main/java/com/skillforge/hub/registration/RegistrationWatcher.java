package com.skillforge.hub.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillforge.hub.github.GitHubClient;
import com.skillforge.hub.service.HeroRegistryService;
import com.skillforge.hub.web.HubDashboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;

@Component
public class RegistrationWatcher {

    private static final Logger log = LoggerFactory.getLogger(RegistrationWatcher.class);

    private final GitHubClient github;
    private final HeroRegistryService registry;
    private final HubDashboardController dashboard;

    public RegistrationWatcher(GitHubClient github,
                                HeroRegistryService registry,
                                HubDashboardController dashboard) {
        this.github = github;
        this.registry = registry;
        this.dashboard = dashboard;

        if (!github.hasToken()) {
            log.warn("GITHUB_TOKEN não configurado — o watcher não conseguirá postar comentários nem aplicar labels.");
        } else {
            log.info("Guild Hub Registration Watcher ativo — polling a cada 2 minutos.");
        }
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 5_000)
    public void watch() {
        if (!github.hasToken()) return;

        List<JsonNode> pending = github.fetchPendingRegistrations();
        if (pending.isEmpty()) return;

        log.info("Encontradas {} issues pendentes de registro.", pending.size());
        pending.forEach(this::process);
    }

    private void process(JsonNode issue) {
        int number = issue.path("number").asInt();
        String title = issue.path("title").asText();
        String body = issue.path("body").asText("");
        String opener = issue.path("user").path("login").asText("?");

        log.info("Processando issue #{} — '{}' (aberta por @{})", number, title, opener);

        var result = HeroValidator.validate(body);
        try {
            if (result.valid()) {
                registerHero(number, result.manifest(), opener);
            } else {
                rejectHero(number, result.errors(), opener);
            }
        } catch (Exception e) {
            log.error("Erro ao processar issue #{}: {}", number, e.getMessage());
        }
    }

    private void registerHero(int issueNumber, JsonNode manifest, String opener) throws Exception {
        String heroId    = manifest.path("heroId").asText();
        String heroName  = manifest.path("heroName").asText(heroId);
        String heroClass = manifest.path("heroClass").asText("Unknown");
        String specialty = manifest.path("specialty").asText("—");

        StringJoiner skills = new StringJoiner(", ");
        manifest.path("skills").forEach(s -> skills.add("`" + s.asText() + "`"));

        String comment = """
                ⚡ **%s entrou na guilda!**

                | | |
                |---|---|
                | **Hero ID** | `%s` |
                | **Classe** | %s |
                | **Skills declaradas** | %s |
                | **Specialty** | %s |
                | **Nível inicial** | 1 — Apprentice |

                Bem-vindo, @%s. Seus próximos passos:
                1. Clone o repositório e configure `hero-template/src/main/resources/manifest.json`
                2. Suba seu nó: `cd hero-template && mvn spring-boot:run`
                3. Escolha sua primeira quest em [QUEST_BOARD.md](../blob/main/QUEST_BOARD.md)

                *Registrado pelo Guild Hub · %s*
                """.formatted(heroName, heroId, heroClass, skills, specialty, opener, Instant.now());

        github.postComment(issueNumber, comment);
        github.addLabel(issueNumber, "registered");

        registry.refresh();
        dashboard.broadcast("HERO_JOINED",
                "{\"heroId\":\"%s\",\"heroName\":\"%s\",\"issueNumber\":%d}"
                        .formatted(heroId, heroName, issueNumber));

        log.info("Herói '{}' registrado com sucesso (issue #{}).", heroId, issueNumber);
    }

    private void rejectHero(int issueNumber, List<String> errors, String opener) throws Exception {
        StringJoiner errorList = new StringJoiner("\n");
        errors.forEach(e -> errorList.add("- " + e));

        String comment = """
                ❌ **Registro inválido**, @%s.

                O manifesto enviado tem os seguintes problemas:

                %s

                **Como corrigir:**
                1. Edite o corpo desta issue com um JSON válido
                2. Consulte [SKILL_MANIFEST_GUIDE.md](../blob/main/SKILL_MANIFEST_GUIDE.md) para o formato completo

                **Template mínimo:**
                ```json
                {
                  "heroId": "seu-hero-id",
                  "heroName": "Seu Nome",
                  "heroClass": "Backend",
                  "skills": ["java", "spring-boot"],
                  "endpoint": "http://localhost:8081",
                  "model": "phi3:mini",
                  "specialty": "Descreva sua especialidade"
                }
                ```

                Após corrigir, edite a issue para reprocessar automaticamente.

                *Validado pelo Guild Hub · %s*
                """.formatted(opener, errorList, Instant.now());

        github.postComment(issueNumber, comment);
        github.addLabel(issueNumber, "invalid");

        log.warn("Issue #{} rejeitada: {}", issueNumber, errors);
    }
}