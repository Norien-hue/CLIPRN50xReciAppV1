package com.reciapp.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class ClipService {

    private final String clipUrl;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public ClipService(@Value("${app.clip.url}") String clipUrl) {
        this.clipUrl = clipUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    public List<Map<String, Object>> match(String image) {
        try {
            String json = mapper.writeValueAsString(Map.of("image", image, "top_k", 5));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(clipUrl + "/match"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return mapper.readValue(resp.body(), new TypeReference<List<Map<String, Object>>>() {});
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
