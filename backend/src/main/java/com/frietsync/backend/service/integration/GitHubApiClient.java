package com.frietsync.backend.service.integration;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

public class GitHubApiClient {
    private static final String API_URL = "https://api.github.com";
    private final RestTemplate restTemplate;

    public GitHubApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> getMap(String path, String token) {
        return restTemplate.exchange(API_URL + path, HttpMethod.GET, request(token),
                new ParameterizedTypeReference<Map<String, Object>>() { }).getBody();
    }

    public List<Map<String, Object>> getList(String path, String token) {
        return restTemplate.exchange(API_URL + path, HttpMethod.GET, request(token),
                new ParameterizedTypeReference<List<Map<String, Object>>>() { }).getBody();
    }

    public Map<String, Object> postMap(String path, Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.parseMediaType("application/vnd.github+json")));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return restTemplate.exchange(API_URL + path, HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<Map<String, Object>>() { }).getBody();
    }

    public Map<String, Object> postForm(String url, HttpEntity<?> request) {
        return restTemplate.exchange(url, HttpMethod.POST, request,
                new ParameterizedTypeReference<Map<String, Object>>() { }).getBody();
    }

    private HttpEntity<Void> request(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.parseMediaType("application/vnd.github+json")));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return new HttpEntity<>(headers);
    }
}
