package com.syntia.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
public class FacebookAuthService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${facebook.app-id:}")
    private String appId;

    @Value("${facebook.app-secret:}")
    private String appSecret;

    public FacebookUserInfo verificarToken(String accessToken) {
        try {
            // 1. Verificar que el token es válido y pertenece a nuestra app
            String debugUrl = "https://graph.facebook.com/debug_token?input_token="
                    + accessToken + "&access_token=" + appId + "|" + appSecret;

            HttpRequest debugRequest = HttpRequest.newBuilder()
                    .uri(URI.create(debugUrl))
                    .GET()
                    .build();

            HttpResponse<String> debugResponse = httpClient.send(debugRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode debugData = objectMapper.readTree(debugResponse.body()).get("data");

            if (debugData == null || !debugData.has("is_valid") || !debugData.get("is_valid").asBoolean()) {
                log.warn("Token de Facebook invalido");
                return null;
            }

            String tokenAppId = debugData.has("app_id") ? debugData.get("app_id").asText() : "";
            if (!appId.equals(tokenAppId)) {
                log.warn("Token de Facebook no pertenece a esta app");
                return null;
            }

            // 2. Obtener datos del usuario
            String meUrl = "https://graph.facebook.com/me?fields=id,email&access_token=" + accessToken;

            HttpRequest meRequest = HttpRequest.newBuilder()
                    .uri(URI.create(meUrl))
                    .GET()
                    .build();

            HttpResponse<String> meResponse = httpClient.send(meRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode userData = objectMapper.readTree(meResponse.body());

            String email = userData.has("email") ? userData.get("email").asText() : null;
            String facebookId = userData.has("id") ? userData.get("id").asText() : null;

            if (email == null || email.isBlank()) {
                log.warn("El usuario de Facebook no tiene email asociado");
                return null;
            }

            return new FacebookUserInfo(facebookId, email);

        } catch (Exception e) {
            log.error("Error verificando token de Facebook: {}", e.getMessage());
            return null;
        }
    }

    public record FacebookUserInfo(String facebookId, String email) {}
}
