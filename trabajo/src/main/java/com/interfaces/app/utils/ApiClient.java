package com.interfaces.app.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

public class ApiClient {

    private static ApiClient instance;
    private final HttpClient httpClient;
    private String baseUrl;
    private String jwtToken;

    private ApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        loadConfig();
    }

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    private void loadConfig() {
        try {
            URL configUrl = getClass().getResource("/configuration.properties");
            if (configUrl != null) {
                InputStream input = configUrl.openStream();
                Properties props = new Properties();
                props.load(input);
                this.baseUrl = props.getProperty("api.url", "http://52.201.91.206:3000");
                input.close();
            } else {
                this.baseUrl = "http://52.201.91.206:3000";
            }
        } catch (Exception e) {
            this.baseUrl = "http://52.201.91.206:3000";
        }
    }

    public boolean login() {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("nombre", "terminal");
            body.addProperty("password", "1234");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/usuarios/login"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                if (result.has("token")) {
                    this.jwtToken = result.get("token").getAsString();
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("API login error: " + e.getMessage());
            return false;
        }
    }

    public JsonObject findByTap(String tap) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/usuarios/by-tap/" + tap))
                .timeout(Duration.ofSeconds(10))
                .GET();

        if (jwtToken != null && !jwtToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }

        HttpRequest request = builder.build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }
        return null;
    }

    public boolean checkHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    public JsonObject registerRecycling(int idUsuario, String tipo, String numeroBarras) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("idUsuario", idUsuario);
        body.addProperty("tipo", tipo);
        body.addProperty("numeroBarras", numeroBarras);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/historial"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

        if (jwtToken != null && !jwtToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 201) {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }
        throw new Exception("Register failed: HTTP " + response.statusCode());
    }

    public String matchProduct(String imageBase64) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("image", imageBase64);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/clip/match"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

        if (jwtToken != null && !jwtToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }
        return "[]";
    }
}
