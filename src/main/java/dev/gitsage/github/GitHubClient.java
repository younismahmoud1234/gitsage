package dev.gitsage.github;

import dev.gitsage.config.GitSageConfiguration;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * GitHub API client for fetching org repositories and their content.
 */
@Singleton
public class GitHubClient {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubClient.class);
    private static final String GITHUB_API = "https://api.github.com";

    private final HttpClient httpClient;
    private final GitSageConfiguration config;
    private final ObjectMapper objectMapper;

    public GitHubClient(@Client(GITHUB_API) HttpClient httpClient,
                        GitSageConfiguration config,
                        ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all repositories in the configured organisation.
     */
    public List<Map<String, Object>> listOrgRepos() {
        var repos = new ArrayList<Map<String, Object>>();
        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            var uri = "/orgs/%s/repos?per_page=100&page=%d&type=all".formatted(config.github().org(), page);
            var request = HttpRequest.GET(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + config.github().token());

            try {
                var response = httpClient.toBlocking().retrieve(request, String.class);
                var nodes = objectMapper.readValue(response, List.class);

                if (nodes == null || nodes.isEmpty()) {
                    hasMore = false;
                } else {
                    @SuppressWarnings("unchecked")
                    var typedNodes = (List<Map<String, Object>>) nodes;
                    repos.addAll(typedNodes);
                    page++;
                    hasMore = typedNodes.size() == 100;
                }
            } catch (Exception e) {
                LOG.error("Failed to list repos for org '{}', page {}: {}", config.github().org(), page, e.getMessage());
                hasMore = false;
            }
        }

        LOG.info("Found {} repositories in org '{}'", repos.size(), config.github().org());
        return repos;
    }

    /**
     * Fetches the content of a file from a repository.
     */
    public Optional<String> fetchFileContent(String repoName, String filePath) {
        var uri = "/repos/%s/%s/contents/%s".formatted(config.github().org(), repoName, filePath);
        var request = HttpRequest.GET(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.github().token())
                .header(HttpHeaders.ACCEPT, "application/vnd.github.raw+json");

        try {
            var content = httpClient.toBlocking().retrieve(request, String.class);
            return Optional.ofNullable(content);
        } catch (Exception e) {
            LOG.debug("Could not fetch {}/{}: {}", repoName, filePath, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches the README content of a repository.
     */
    public Optional<String> fetchReadme(String repoName) {
        var uri = "/repos/%s/%s/readme".formatted(config.github().org(), repoName);
        var request = HttpRequest.GET(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.github().token())
                .header(HttpHeaders.ACCEPT, "application/vnd.github.raw+json");

        try {
            var content = httpClient.toBlocking().retrieve(request, String.class);
            return Optional.ofNullable(content);
        } catch (Exception e) {
            LOG.debug("No README found for {}: {}", repoName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lists repository tree (files) recursively.
     */
    public List<Map<String, Object>> listRepoTree(String repoName, String branch) {
        var uri = "/repos/%s/%s/git/trees/%s?recursive=1".formatted(config.github().org(), repoName, branch);
        var request = HttpRequest.GET(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + config.github().token());

        try {
            var response = httpClient.toBlocking().retrieve(request, String.class);
            var root = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            var tree = (List<Map<String, Object>>) root.get("tree");
            return tree != null ? tree : List.of();
        } catch (Exception e) {
            LOG.error("Failed to list tree for {}: {}", repoName, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches open issues from a repository.
     */
    public List<Map<String, Object>> fetchIssues(String repoName, int limit) {
        var uri = "/repos/%s/%s/issues?state=open&per_page=%d".formatted(config.github().org(), repoName, limit);
        var request = HttpRequest.GET(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + config.github().token());

        try {
            var response = httpClient.toBlocking().retrieve(request, String.class);
            @SuppressWarnings("unchecked")
            var issues = (List<Map<String, Object>>) objectMapper.readValue(response, List.class);
            return issues != null ? issues : List.of();
        } catch (Exception e) {
            LOG.debug("Failed to fetch issues for {}: {}", repoName, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches the default branch name for a repository.
     */
    public String getDefaultBranch(String repoName) {
        var uri = "/repos/%s/%s".formatted(config.github().org(), repoName);
        var request = HttpRequest.GET(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + config.github().token());

        try {
            var response = httpClient.toBlocking().retrieve(request, String.class);
            var repo = objectMapper.readValue(response, Map.class);
            return (String) repo.getOrDefault("default_branch", "main");
        } catch (Exception e) {
            LOG.debug("Could not determine default branch for {}, defaulting to 'main'", repoName);
            return "main";
        }
    }
}
