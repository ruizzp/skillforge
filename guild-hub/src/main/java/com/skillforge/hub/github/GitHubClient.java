package com.skillforge.hub.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.hub.domain.GuildMember;
import com.skillforge.hub.domain.Quest;
import com.skillforge.hub.domain.QuestRarity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GitHubClient {

    private static final String API = "https://api.github.com";
    private static final int XP_PER_SKILL = 150;

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String owner;
    private final String repo;
    private final String token;

    public GitHubClient(
            @Value("${guild.github.owner}") String owner,
            @Value("${guild.github.repo}") String repo,
            @Value("${guild.github.token:}") String token) {
        this.owner = owner;
        this.repo = repo;
        this.token = token;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    public boolean hasToken() {
        return !token.isBlank();
    }

    // ── Heroes ──────────────────────────────────────────────────────────────

    public List<GuildMember> fetchHeroes() {
        try {
            String url = "%s/repos/%s/%s/issues?labels=hero,registered&state=open&per_page=100"
                    .formatted(API, owner, repo);
            JsonNode issues = get(url);
            List<GuildMember> members = new ArrayList<>();
            for (JsonNode issue : issues) {
                parseHero(issue).ifPresent(members::add);
            }
            return members;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Returns GitHub logins that already have a hero issue in "registered" or
     * "pending" (no verdict yet) state. Owners with only "invalid" issues are
     * NOT blocked — the ForkWatcher should retry them with the updated manifest.
     */
    public Set<String> fetchAllHeroOwners() {
        try {
            String url = "%s/repos/%s/%s/issues?labels=hero&state=all&per_page=100"
                    .formatted(API, owner, repo);
            JsonNode issues = get(url);
            Set<String> logins = new HashSet<>();
            for (JsonNode issue : issues) {
                boolean isRegistered = false;
                boolean isInvalidOnly = false;
                boolean hasPending = true;

                for (JsonNode label : issue.path("labels")) {
                    String name = label.path("name").asText();
                    if ("registered".equals(name)) { isRegistered = true; break; }
                    if ("invalid".equals(name)) { isInvalidOnly = true; hasPending = false; }
                }

                // skip only if registered or pending (not yet processed)
                if (!isRegistered && isInvalidOnly) continue;

                String login = extractLogin(issue);
                if (!login.isBlank()) logins.add(login.toLowerCase());
            }
            return logins;
        } catch (Exception e) {
            return Set.of();
        }
    }

    private String extractLogin(JsonNode issue) {
        String body = issue.path("body").asText("{}");
        try {
            JsonNode manifest = mapper.readTree(extractJson(body));
            String login = manifest.path("githubLogin").asText("");
            if (!login.isBlank()) return login;
        } catch (Exception ignored) {}
        return issue.path("user").path("login").asText("");
    }

    public List<JsonNode> fetchPendingRegistrations() {
        try {
            String url = "%s/repos/%s/%s/issues?labels=hero&state=open&per_page=100"
                    .formatted(API, owner, repo);
            JsonNode issues = get(url);
            List<JsonNode> pending = new ArrayList<>();
            for (JsonNode issue : issues) {
                boolean processed = false;
                for (JsonNode label : issue.path("labels")) {
                    String name = label.path("name").asText();
                    if ("registered".equals(name) || "invalid".equals(name)) {
                        processed = true;
                        break;
                    }
                }
                if (!processed) pending.add(issue);
            }
            return pending;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Forks ───────────────────────────────────────────────────────────────

    /**
     * Lists all forks of the main repo.
     */
    public List<JsonNode> fetchForks() {
        try {
            String url = "%s/repos/%s/%s/forks?per_page=100&sort=newest"
                    .formatted(API, owner, repo);
            JsonNode result = get(url);
            List<JsonNode> forks = new ArrayList<>();
            result.forEach(forks::add);
            return forks;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Tries to read a file from a fork repo. Returns the decoded content as a String.
     * Tries each path in order, returns empty if none found.
     */
    public Optional<String> readFileFromFork(String forkOwner, String forkRepo, List<String> paths) {
        for (String path : paths) {
            try {
                String url = "%s/repos/%s/%s/contents/%s".formatted(API, forkOwner, forkRepo, path);
                JsonNode response = get(url);
                String encoding = response.path("encoding").asText("");
                String content = response.path("content").asText("");
                if ("base64".equals(encoding) && !content.isBlank()) {
                    // GitHub wraps lines with \n inside the base64 block
                    String decoded = new String(Base64.getDecoder().decode(
                            content.replaceAll("\\s", "")));
                    return Optional.of(decoded);
                }
            } catch (Exception ignored) {}
        }
        return Optional.empty();
    }

    // ── Issue creation ──────────────────────────────────────────────────────

    /**
     * Creates a hero registration issue in the main repo. Returns the issue number.
     */
    public int createHeroIssue(String title, String manifestJson) throws Exception {
        requireToken("createHeroIssue");
        String url = "%s/repos/%s/%s/issues".formatted(API, owner, repo);
        String payload = mapper.writeValueAsString(Map.of(
                "title", title,
                "body", manifestJson,
                "labels", List.of("hero")
        ));
        String response = post(url, payload);
        return mapper.readTree(response).path("number").asInt();
    }

    // ── Quests ──────────────────────────────────────────────────────────────

    public List<Quest> fetchQuests() {
        try {
            String url = "%s/repos/%s/%s/issues?labels=quest&state=all&per_page=100"
                    .formatted(API, owner, repo);
            JsonNode issues = get(url);
            List<Quest> quests = new ArrayList<>();
            for (JsonNode issue : issues) {
                parseQuest(issue).ifPresent(quests::add);
            }
            return quests;
        } catch (Exception e) {
            return List.of();
        }
    }

    public int createQuestIssue(String title, String body, QuestRarity rarity,
                                int xpReward, List<String> requiredSkills) throws Exception {
        requireToken("createQuestIssue");
        String url = "%s/repos/%s/%s/issues".formatted(API, owner, repo);

        List<String> labels = new ArrayList<>();
        labels.add("quest");
        labels.add(rarity.name().toLowerCase());
        labels.add("xp:" + xpReward);
        requiredSkills.forEach(s -> labels.add("skill:" + s));

        String payload = mapper.writeValueAsString(Map.of(
                "title", "[QUEST] " + title,
                "body", body,
                "labels", labels
        ));
        String response = post(url, payload);
        return mapper.readTree(response).path("number").asInt();
    }

    // ── Quest comments ──────────────────────────────────────────────────────

    public record CommentEntry(String author, String body, String createdAt) {}

    public List<CommentEntry> fetchIssueComments(int issueNumber) {
        try {
            String url = "%s/repos/%s/%s/issues/%d/comments?per_page=100"
                .formatted(API, owner, repo, issueNumber);
            JsonNode comments = get(url);
            List<CommentEntry> result = new ArrayList<>();
            for (JsonNode c : comments) {
                result.add(new CommentEntry(
                    c.path("user").path("login").asText(""),
                    c.path("body").asText(""),
                    c.path("created_at").asText("")
                ));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Hero portfolio ──────────────────────────────────────────────────────

    public record ActivityEntry(
        String questId,
        String questTitle,
        List<String> skills,
        int xp,
        int confidencePct,
        String completedAt
    ) {}

    public void postQuestCompletionComment(int heroIssueNumber, String questId, String questTitle,
            List<String> skills, int xp, int confidencePct) throws Exception {
        requireToken("postQuestCompletionComment");
        String meta = mapper.writeValueAsString(Map.of(
            "questId", questId, "questTitle", questTitle,
            "skills", skills, "xp", xp,
            "confidence", confidencePct,
            "completedAt", Instant.now().toString()
        ));
        String skillsFormatted = skills.isEmpty() ? "—"
            : skills.stream().map(s -> "`" + s + "`").collect(Collectors.joining(", "));
        String body = """
            <!-- quest-completion: %s -->
            ## ⚔️ Quest Completada — %s

            | | |
            |---|---|
            | **Quest** | %s |
            | **Skills validadas** | %s |
            | **XP creditado** | +%d XP |
            | **Confiança** | %d%% |
            """.formatted(meta, questId, questTitle, skillsFormatted, xp, confidencePct);
        postComment(heroIssueNumber, body);
    }

    public List<ActivityEntry> fetchHeroActivity(int issueNumber) {
        try {
            String url = "%s/repos/%s/%s/issues/%d/comments?per_page=50"
                .formatted(API, owner, repo, issueNumber);
            JsonNode comments = get(url);
            List<ActivityEntry> result = new ArrayList<>();
            for (JsonNode comment : comments) {
                String body = comment.path("body").asText("");
                int marker = body.indexOf("<!-- quest-completion:");
                if (marker < 0) continue;
                int dataStart = marker + "<!-- quest-completion:".length();
                int dataEnd   = body.indexOf("-->", dataStart);
                if (dataEnd < 0) continue;
                try {
                    JsonNode data = mapper.readTree(body.substring(dataStart, dataEnd).trim());
                    List<String> skills = new ArrayList<>();
                    data.path("skills").forEach(s -> skills.add(s.asText()));
                    result.add(new ActivityEntry(
                        data.path("questId").asText(),
                        data.path("questTitle").asText(),
                        skills,
                        data.path("xp").asInt(),
                        data.path("confidence").asInt(),
                        data.path("completedAt").asText()
                    ));
                } catch (Exception ignored) {}
            }
            Collections.reverse(result); // mais recente primeiro
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Comments & labels ───────────────────────────────────────────────────

    public void postComment(int issueNumber, String body) throws Exception {
        requireToken("postComment");
        String url = "%s/repos/%s/%s/issues/%d/comments".formatted(API, owner, repo, issueNumber);
        post(url, mapper.writeValueAsString(Map.of("body", body)));
    }

    public void addLabel(int issueNumber, String label) throws Exception {
        requireToken("addLabel");
        String url = "%s/repos/%s/%s/issues/%d/labels".formatted(API, owner, repo, issueNumber);
        post(url, mapper.writeValueAsString(Map.of("labels", List.of(label))));
    }

    public synchronized void validateSkill(int issueNumber, String skill, String validatedBy) throws Exception {
        addLabel(issueNumber, "skill-validated:" + skill);
        updateXp(issueNumber, XP_PER_SKILL);
        String comment = "✓ **Skill `%s` validada** por @%s. +%d XP".formatted(skill, validatedBy, XP_PER_SKILL);
        postComment(issueNumber, comment);
    }

    public synchronized void removeSkillValidation(int issueNumber, String skill) throws Exception {
        requireToken("removeSkillValidation");
        String label = "skill-validated:" + skill;
        delete("%s/repos/%s/%s/issues/%d/labels/%s"
                .formatted(API, owner, repo, issueNumber, label.replace(":", "%3A")));
        updateXp(issueNumber, -XP_PER_SKILL);
    }

    /**
     * Credita XP de conclusão de quest de forma idempotente.
     * Usa label xp-source:{questId} como chave de deduplicação — segunda chamada é no-op.
     * Retorna true se o XP foi creditado agora, false se já havia sido creditado antes.
     */
    public synchronized boolean addQuestXp(int issueNumber, String questId, int amount) throws Exception {
        requireToken("addQuestXp");
        String idempotencyLabel = "xp-source:" + questId;

        JsonNode issue = get("%s/repos/%s/%s/issues/%d".formatted(API, owner, repo, issueNumber));
        for (JsonNode label : issue.path("labels")) {
            if (idempotencyLabel.equals(label.path("name").asText())) return false;
        }

        addLabel(issueNumber, idempotencyLabel);
        if (amount > 0) updateXpFromIssue(issue, amount);
        return true;
    }

    /**
     * Credita XP de revisão de forma idempotente.
     * Usa label reviewed:{questId} como chave de deduplicação.
     * Retorna true se o XP foi creditado agora, false se já havia sido creditado antes.
     */
    public synchronized boolean addReviewerXp(int issueNumber, String questId, int amount) throws Exception {
        requireToken("addReviewerXp");
        String idempotencyLabel = "reviewed:" + questId;

        JsonNode issue = get("%s/repos/%s/%s/issues/%d".formatted(API, owner, repo, issueNumber));
        for (JsonNode label : issue.path("labels")) {
            if (idempotencyLabel.equals(label.path("name").asText())) return false;
        }

        addLabel(issueNumber, idempotencyLabel);
        if (amount > 0) updateXpFromIssue(issue, amount);
        return true;
    }

    private static final List<String> QUEST_STATUS_LABELS =
        List.of("in-progress", "pending-review", "review-pending", "completed");

    public void setQuestStatus(int issueNumber, String status) throws Exception {
        requireToken("setQuestStatus");
        for (String old : QUEST_STATUS_LABELS) {
            if (!old.equals(status)) {
                try {
                    delete("%s/repos/%s/%s/issues/%d/labels/%s"
                        .formatted(API, owner, repo, issueNumber, old));
                } catch (Exception ignored) {}
            }
        }
        addLabel(issueNumber, status);
    }

    public void setRevisionCount(int issueNumber, int count) throws Exception {
        requireToken("setRevisionCount");
        JsonNode issue = get("%s/repos/%s/%s/issues/%d".formatted(API, owner, repo, issueNumber));
        for (JsonNode label : issue.path("labels")) {
            String name = label.path("name").asText();
            if (name.startsWith("revision-count:")) {
                try {
                    delete("%s/repos/%s/%s/issues/%d/labels/%s"
                        .formatted(API, owner, repo, issueNumber, name.replace(":", "%3A")));
                } catch (Exception ignored) {}
                break;
            }
        }
        addLabel(issueNumber, "revision-count:" + count);
    }

    private void updateXp(int issueNumber, int delta) throws Exception {
        requireToken("updateXp");
        JsonNode issue = get("%s/repos/%s/%s/issues/%d".formatted(API, owner, repo, issueNumber));
        updateXpFromIssue(issue, delta);
    }

    // Variante que reutiliza um issue já carregado — evita GET duplicado em addQuestXp/addReviewerXp.
    private void updateXpFromIssue(JsonNode issue, int delta) throws Exception {
        int issueNumber = issue.path("number").asInt();
        int current = 0;
        String existing = null;
        for (JsonNode label : issue.path("labels")) {
            String name = label.path("name").asText();
            if (name.startsWith("xp:")) {
                try { current = Integer.parseInt(name.substring(3)); } catch (NumberFormatException ignored) {}
                existing = name;
                break;
            }
        }

        int newXp = Math.max(0, current + delta);
        if (existing != null)
            delete("%s/repos/%s/%s/issues/%d/labels/%s".formatted(API, owner, repo, issueNumber, existing));
        if (newXp > 0)
            addLabel(issueNumber, "xp:" + newXp);
    }

    // ── Parsing ─────────────────────────────────────────────────────────────

    private Optional<GuildMember> parseHero(JsonNode issue) {
        try {
            String body = issue.path("body").asText("{}");
            JsonNode manifest = mapper.readTree(extractJson(body));

            String heroId = manifest.path("heroId").asText("");
            if (heroId.isBlank()) return Optional.empty();

            List<String> skills = new ArrayList<>();
            manifest.path("skills").forEach(s -> skills.add(s.asText()));

            // read skill-validated:* labels from the issue
            List<String> validatedSkills = new ArrayList<>();
            for (JsonNode label : issue.path("labels")) {
                String name = label.path("name").asText();
                if (name.startsWith("skill-validated:")) {
                    validatedSkills.add(name.substring("skill-validated:".length()));
                }
            }

            JsonNode opener = issue.path("user");
            String githubLogin = manifest.path("githubLogin").asText(
                    opener.path("login").asText(""));
            String avatarUrl = manifest.path("avatarUrl").asText(
                    opener.path("avatar_url").asText(""));

            int xp = manifest.path("xp").asInt(0);
            for (JsonNode label : issue.path("labels")) {
                String name = label.path("name").asText();
                if (name.startsWith("xp:")) {
                    try { xp = Integer.parseInt(name.substring(3)); } catch (NumberFormatException ignored) {}
                    break;
                }
            }

            return Optional.of(new GuildMember(
                    heroId,
                    manifest.path("heroName").asText(heroId),
                    manifest.path("heroClass").asText("Unknown"),
                    skills,
                    validatedSkills,
                    manifest.path("level").asInt(1),
                    xp,
                    manifest.path("specialty").asText(""),
                    githubLogin,
                    avatarUrl,
                    issue.path("number").asInt()
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Quest> parseQuest(JsonNode issue) {
        try {
            String title = issue.path("title").asText();
            int number = issue.path("number").asInt();
            String body = issue.path("body").asText("");
            String state = issue.path("state").asText("open");
            String url = issue.path("html_url").asText("");

            QuestRarity rarity = QuestRarity.COMMON;
            List<String> requiredSkills = new ArrayList<>();
            int xpReward = 100;
            List<String> solvers   = new ArrayList<>();
            String assignedTo = null;
            int revisionCount = 0;

            for (JsonNode label : issue.path("labels")) {
                String name = label.path("name").asText();
                try { rarity = QuestRarity.valueOf(name.toUpperCase()); } catch (IllegalArgumentException ignored) {}
                if (name.startsWith("skill:")) requiredSkills.add(name.substring(6));
                if (name.startsWith("xp:")) {
                    try { xpReward = Integer.parseInt(name.substring(3)); } catch (NumberFormatException ignored) {}
                }
                if (name.startsWith("solved-by:"))     solvers.add(name.substring("solved-by:".length()));
                if (name.startsWith("assigned-to:"))   assignedTo = name.substring("assigned-to:".length());
                if (name.startsWith("revision-count:")) {
                    try { revisionCount = Integer.parseInt(name.substring("revision-count:".length())); }
                    catch (NumberFormatException ignored) {}
                }
            }

            String assignee = Optional.ofNullable(issue.get("assignee"))
                    .filter(n -> !n.isNull())
                    .map(n -> n.path("login").asText(""))
                    .orElse("");

            String id = "QUEST-%03d".formatted(number);
            if (title.startsWith("[QUEST-")) {
                id = title.substring(1, title.indexOf(']'));
                title = title.substring(title.indexOf(']') + 2).trim();
            } else if (title.startsWith("[QUEST]")) {
                title = title.substring(7).trim();
            }

            return Optional.of(new Quest(id, title, body, rarity, state,
                    requiredSkills, xpReward, assignee, url, number, solvers, assignedTo, revisionCount));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void closeIssue(int issueNumber) throws Exception {
        requireToken("closeIssue");
        String url = "%s/repos/%s/%s/issues/%d".formatted(API, owner, repo, issueNumber);
        patch(url, mapper.writeValueAsString(Map.of("state", "closed")));
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private JsonNode get(String url) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
        if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }

    private void patch(String url, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();
        http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void delete(String url) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();
        http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String post(String url, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private void requireToken(String operation) {
        if (token.isBlank()) throw new IllegalStateException(
                "GITHUB_TOKEN required for " + operation + ". Set guild.github.token or export GITHUB_TOKEN.");
    }

    private static String extractJson(String body) {
        if (body == null) throw new IllegalArgumentException("body is null");
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        if (start < 0 || end < 0) throw new IllegalArgumentException("no JSON object found");
        return body.substring(start, end + 1);
    }
}