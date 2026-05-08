package com.skillforge.hero.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.hero.domain.GuildMember;
import com.skillforge.hero.domain.Quest;
import com.skillforge.hero.domain.QuestRarity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class GitHubClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String owner;
    private final String repo;
    private final String token;
    private static final String API = "https://api.github.com";

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

    public List<Quest> fetchQuests() {
        try {
            String url = "%s/repos/%s/%s/issues?labels=quest&state=all&per_page=100".formatted(API, owner, repo);
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

    public List<GuildMember> fetchHeroes() {
        try {
            String url = "%s/repos/%s/%s/issues?labels=hero&state=open&per_page=100".formatted(API, owner, repo);
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

    public int countCompletedQuests(String heroId) {
        try {
            String url = "%s/repos/%s/%s/issues?labels=quest,completed&state=closed&per_page=100".formatted(API, owner, repo);
            JsonNode issues = get(url);
            int count = 0;
            for (JsonNode issue : issues) {
                JsonNode assignee = issue.path("assignee");
                if (!assignee.isMissingNode() && !assignee.isNull()) {
                    String login = assignee.path("login").asText("");
                    if (login.equalsIgnoreCase(heroId)) count++;
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
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

            for (JsonNode label : issue.path("labels")) {
                String name = label.path("name").asText();
                try { rarity = QuestRarity.valueOf(name.toUpperCase()); } catch (IllegalArgumentException ignored) {}
                if (name.startsWith("skill:")) requiredSkills.add(name.substring(6));
                if (name.startsWith("xp:")) {
                    try { xpReward = Integer.parseInt(name.substring(3)); } catch (NumberFormatException ignored) {}
                }
            }

            String assigneeLogin = Optional.ofNullable(issue.get("assignee"))
                    .filter(n -> !n.isNull())
                    .map(n -> n.path("login").asText(""))
                    .orElse("");

            // Extract quest ID from title if present (e.g. [QUEST-001])
            String id = "QUEST-%03d".formatted(number);
            if (title.startsWith("[QUEST-")) {
                id = title.substring(1, title.indexOf(']'));
                title = title.substring(title.indexOf(']') + 2).trim();
            }

            return Optional.of(new Quest(id, title, body, rarity, state, requiredSkills, xpReward, assigneeLogin, url, number));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<GuildMember> parseHero(JsonNode issue) {
        try {
            String body = issue.path("body").asText("{}");
            JsonNode manifest = mapper.readTree(body);

            String heroId = manifest.path("heroId").asText("");
            if (heroId.isBlank()) return Optional.empty();

            List<String> skills = new ArrayList<>();
            manifest.path("skills").forEach(s -> skills.add(s.asText()));

            List<String> validatedSkills = new ArrayList<>();
            issue.path("labels").forEach(label -> {
                String name = label.path("name").asText();
                if (name.startsWith("skill-validated:"))
                    validatedSkills.add(name.substring("skill-validated:".length()));
            });

            JsonNode user = issue.path("user");
            String avatarUrl = user.path("avatar_url").asText("");
            String githubLogin = user.path("login").asText("");

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
                    avatarUrl
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<JsonNode> fetchPendingRegistrations() {
        try {
            String url = "%s/repos/%s/%s/issues?labels=hero&state=open&per_page=100".formatted(API, owner, repo);
            JsonNode issues = get(url);
            List<JsonNode> pending = new ArrayList<>();
            for (JsonNode issue : issues) {
                boolean alreadyProcessed = false;
                for (JsonNode label : issue.path("labels")) {
                    String name = label.path("name").asText();
                    if ("registered".equals(name) || "invalid".equals(name)) {
                        alreadyProcessed = true;
                        break;
                    }
                }
                if (!alreadyProcessed) pending.add(issue);
            }
            return pending;
        } catch (Exception e) {
            return List.of();
        }
    }

    public String fetchIssueBody(int issueNumber) {
        try {
            String url = "%s/repos/%s/%s/issues/%d".formatted(API, owner, repo, issueNumber);
            return get(url).path("body").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    public List<String> fetchIssueComments(int issueNumber) {
        try {
            String url = "%s/repos/%s/%s/issues/%d/comments?per_page=100".formatted(API, owner, repo, issueNumber);
            JsonNode comments = get(url);
            List<String> bodies = new ArrayList<>();
            for (JsonNode c : comments) bodies.add(c.path("body").asText(""));
            return bodies;
        } catch (Exception e) {
            return List.of();
        }
    }

    public void postComment(int issueNumber, String body) throws Exception {
        requireToken("postComment");
        String url = "%s/repos/%s/%s/issues/%d/comments".formatted(API, owner, repo, issueNumber);
        String payload = mapper.writeValueAsString(java.util.Map.of("body", body));
        post(url, payload);
    }

    public void addLabel(int issueNumber, String label) throws Exception {
        requireToken("addLabel");
        String url = "%s/repos/%s/%s/issues/%d/labels".formatted(API, owner, repo, issueNumber);
        String payload = mapper.writeValueAsString(java.util.Map.of("labels", List.of(label)));
        post(url, payload);
    }

    public boolean hasToken() {
        return !token.isBlank();
    }

    private void requireToken(String operation) {
        if (token.isBlank()) throw new IllegalStateException(
                "GITHUB_TOKEN required for " + operation + ". Set guild.github.token or export GITHUB_TOKEN.");
    }

    private void post(String url, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode get(String url) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();

        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }
}