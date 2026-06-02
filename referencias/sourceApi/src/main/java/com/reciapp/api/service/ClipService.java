package com.reciapp.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    public String match(String image) {
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
                return resp.body();
            }
            String errBody = resp.body() != null ? resp.body().replace("\"", "'") : "no body";
            System.err.println("[ClipService] clip-service returned " + resp.statusCode() + ": " + errBody);
            return "{\"matches\":[],\"debug\":{\"error\":\"clip-service HTTP " + resp.statusCode() + "\"}}";
        } catch (Exception e) {
            System.err.println("[ClipService] error: " + e.getMessage());
            return "{\"matches\":[],\"debug\":{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}}";
        }
    }
}
