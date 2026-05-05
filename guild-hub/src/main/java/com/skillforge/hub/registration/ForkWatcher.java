package com.skillforge.hub.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillforge.hub.github.GitHubClient;
import com.skillforge.hub.web.HubDashboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Periodically scans forks of the main repo. When a fork has a configured
 * manifest.json (heroId != placeholder), and the fork owner is not yet
 * registered, creates a hero registration issue in the main repo so that
 * RegistrationWatcher can process it normally.
 *
 * Flow: fork pushed → ForkWatcher detects → creates issue → RegistrationWatcher validates
 *       → adds label "registered" → HeroRegistryService picks it up on next refresh.
 */
@Component
public class ForkWatcher {

    private static final Logger log = LoggerFactory.getLogger(ForkWatcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> MANIFEST_PATHS = List.of(
            "hero-template/src/main/resources/manifest.json",
            "src/main/resources/manifest.json",
            "manifest.json"
    );

    private static final Set<String> PLACEHOLDER_IDS = Set.of(
            "your-hero-id", "hero-unknown", ""
    );

    private final GitHubClient github;
    private final HubDashboardController dashboard;

    public ForkWatcher(GitHubClient github, HubDashboardController dashboard) {
        this.github = github;
        this.dashboard = dashboard;

        if (!github.hasToken()) {
            log.warn("GITHUB_TOKEN não configurado — ForkWatcher só detecta forks, não cria issues de registro.");
        } else {
            log.info("Fork Watcher ativo — escaneia forks a cada 10 minutos.");
        }
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 90_000)
    public void scan() {
        scanAndRegister(false);
    }

    /**
     * Scans all forks and returns a status report of what was found.
     * If register=true and GITHUB_TOKEN is set, creates registration issues for new heroes.
     */
    public List<ForkStatus> scanAndRegister(boolean register) {
        List<JsonNode> forks = github.fetchForks();
        if (forks.isEmpty()) {
            log.debug("Nenhum fork encontrado.");
            return List.of();
        }

        Set<String> alreadyRegistered = github.fetchAllHeroOwners();
        log.info("Escaneando {} fork(s). Já registrados: {}", forks.size(), alreadyRegistered.size());

        List<ForkStatus> report = new ArrayList<>();

        for (JsonNode fork : forks) {
            String forkOwner = fork.path("owner").path("login").asText("");
            String forkRepo  = fork.path("name").asText("");
            String avatarUrl = fork.path("owner").path("avatar_url").asText("");
            String forkUrl   = fork.path("html_url").asText("");

            if (forkOwner.isBlank()) continue;

            boolean alreadyDone = alreadyRegistered.contains(forkOwner.toLowerCase());

            Optional<String> rawManifest = github.readFileFromFork(forkOwner, forkRepo, MANIFEST_PATHS);

            if (rawManifest.isEmpty()) {
                report.add(new ForkStatus(forkOwner, forkUrl, null, "NO_MANIFEST", alreadyDone, -1));
                log.debug("Fork {}/{} sem manifest.json.", forkOwner, forkRepo);
                continue;
            }

            JsonNode manifest;
            try {
                manifest = MAPPER.readTree(rawManifest.get());
            } catch (Exception e) {
                report.add(new ForkStatus(forkOwner, forkUrl, null, "INVALID_JSON", alreadyDone, -1));
                log.warn("Fork {}/{} tem manifest.json com JSON inválido.", forkOwner, forkRepo);
                continue;
            }

            String heroId = manifest.path("heroId").asText("").trim();

            if (PLACEHOLDER_IDS.contains(heroId)) {
                report.add(new ForkStatus(forkOwner, forkUrl, heroId, "PLACEHOLDER", alreadyDone, -1));
                log.debug("Fork {}/{} ainda usa heroId placeholder.", forkOwner, forkRepo);
                continue;
            }

            if (alreadyDone) {
                report.add(new ForkStatus(forkOwner, forkUrl, heroId, "ALREADY_REGISTERED", true, -1));
                continue;
            }

            if (register && github.hasToken()) {
                try {
                    int issueNumber = registerHero(manifest, forkOwner, forkRepo, avatarUrl);
                    report.add(new ForkStatus(forkOwner, forkUrl, heroId, "REGISTERED", false, issueNumber));
                } catch (Exception e) {
                    report.add(new ForkStatus(forkOwner, forkUrl, heroId, "ERROR: " + e.getMessage(), false, -1));
                    log.error("Erro ao registrar fork {}/{}: {}", forkOwner, forkRepo, e.getMessage());
                }
            } else {
                report.add(new ForkStatus(forkOwner, forkUrl, heroId, "PENDING_REGISTRATION", false, -1));
                log.info("Fork {}/{} tem herói '{}' pronto para registro (aguardando próximo ciclo ou token).",
                        forkOwner, forkRepo, heroId);
            }
        }

        return report;
    }

    private int registerHero(JsonNode manifest, String forkOwner, String forkRepo, String avatarUrl) throws Exception {
        ObjectNode enriched = manifest.deepCopy();
        enriched.put("githubLogin", forkOwner);
        enriched.put("avatarUrl", avatarUrl);
        enriched.put("forkRepo", "https://github.com/" + forkOwner + "/" + forkRepo);

        String heroId   = manifest.path("heroId").asText();
        String heroName = manifest.path("heroName").asText(heroId);
        String title    = "[Hero Registration] " + heroName + " (@" + forkOwner + ")";
        String body     = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(enriched);

        int issueNumber = github.createHeroIssue(title, body);
        log.info("Issue #{} criada para '{}' — fork {}/{}.", issueNumber, heroId, forkOwner, forkRepo);

        dashboard.broadcast("FORK_DISCOVERED",
                "{\"heroId\":\"%s\",\"heroName\":\"%s\",\"forkOwner\":\"%s\",\"issueNumber\":%d}"
                        .formatted(heroId, heroName, forkOwner, issueNumber));
        return issueNumber;
    }

    public record ForkStatus(
            String forkOwner,
            String forkUrl,
            String heroId,
            String status,
            boolean alreadyRegistered,
            int issueNumber
    ) {}
}