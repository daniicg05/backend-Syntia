package com.syntia.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP ligero para la API de OpenAI (Chat Completions).
 * <p>
 * Usa {@link RestClient} de Spring 6, sin dependencias externas adicionales.
 * Si la API key no está configurada o la llamada falla, lanza
 * {@link OpenAiUnavailableException} para que {@link MotorMatchingService}
 * pueda hacer fallback al motor rule-based.
 */
@Slf4j
@Component
public class OpenAiClient {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4.1}")
    private String model;

    @Value("${openai.max-tokens:400}")
    private int maxTokens;

    /** Max tokens para respuestas largas (guías de solicitud con JSON complejo). */
    @Value("${openai.max-tokens-large:4000}")
    private int maxTokensLarge;

    /** Max tokens para análisis completo (10 slides con contenido rico). */
    @Value("${openai.max-tokens-analisis:8000}")
    private int maxTokensAnalisis;

    @Value("${openai.temperature:0.3}")
    private double temperature;

    private final RestClient restClient;

    /** RestClient dedicado para chatLarge con timeout de lectura ampliado (90s). */
    private final RestClient restClientLarge;

    /** Máximo de caracteres del userPrompt. Suficiente para detalle oficial + perfil + instrucción. */
    private static final int MAX_PROMPT_CHARS = 4000;

    /** Máximo de caracteres del userPrompt para guías enriquecidas (necesita más contexto). */
    private static final int MAX_PROMPT_CHARS_LARGE = 8000;

    /** Máximo de caracteres del userPrompt para análisis completo (incluye texto oficial + catálogos + perfil). */
    private static final int MAX_PROMPT_CHARS_ANALISIS = 25000;

    public OpenAiClient(RestClient.Builder builder) {
        // Timeout estándar: 10s conexión, 30s lectura — para matching y keywords
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restClient = builder.requestFactory(factory).build();

        // Timeout ampliado: 10s conexión, 90s lectura — para guías enriquecidas (JSON ~8000 tokens)
        SimpleClientHttpRequestFactory factoryLarge = new SimpleClientHttpRequestFactory();
        factoryLarge.setConnectTimeout(10_000);
        factoryLarge.setReadTimeout(90_000);
        this.restClientLarge = RestClient.builder().requestFactory(factoryLarge).build();
    }

    @jakarta.annotation.PostConstruct
    void logConfig() {
        if (apiKey != null && !apiKey.isBlank()) {
            log.info("OpenAI configurado: model={}, key={}...", model, apiKey.substring(0, Math.min(10, apiKey.length())));
        } else {
            log.warn("OpenAI API key NO configurada — se usará motor rule-based");
        }
    }

    /**
     * Envía un prompt al modelo configurado y devuelve el contenido de la respuesta.
     *
     * @param systemPrompt instrucción de sistema (rol del asistente)
     * @param userPrompt   consulta del usuario
     * @return texto de respuesta del modelo
     * @throws OpenAiUnavailableException si la API no está configurada o falla
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAiUnavailableException("openai.api-key no configurada");
        }

        // Truncar prompt largo para no gastar tokens innecesarios
        String promptFinal = userPrompt.length() > MAX_PROMPT_CHARS
                ? userPrompt.substring(0, MAX_PROMPT_CHARS)
                : userPrompt;

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "temperature", temperature,
                    // json_object → respuesta directa sin texto adicional, más rápido de parsear
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user",   "content", promptFinal)
                    )
            );

            ChatResponse response = restClient.post()
                    .uri(API_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new OpenAiUnavailableException("Respuesta vacía de OpenAI");
            }

            return response.choices().get(0).message().content().trim();

        } catch (OpenAiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error al llamar a OpenAI: {}", e.getMessage());
            throw new OpenAiUnavailableException("Error en la llamada a OpenAI: " + e.getMessage());
        }
    }

    /**
     * Envía un prompt al modelo con un límite de tokens mayor para respuestas JSON complejas.
     * Usa {@link #maxTokensLarge} (por defecto 4000) y un prompt más largo.
     * <p>
     * Diseñado para la generación de guías de solicitud de subvenciones con JSON enriquecido
     * que incluye workflows, guías visuales y disclaimers.
     *
     * @param systemPrompt instrucción de sistema (rol del asistente)
     * @param userPrompt   datos de la convocatoria y el solicitante
     * @return texto de respuesta del modelo (JSON)
     * @throws OpenAiUnavailableException si la API no está configurada o falla
     */
    public String chatLarge(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAiUnavailableException("openai.api-key no configurada");
        }

        String promptFinal = userPrompt.length() > MAX_PROMPT_CHARS_LARGE
                ? userPrompt.substring(0, MAX_PROMPT_CHARS_LARGE)
                : userPrompt;

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", maxTokensLarge,
                    "temperature", temperature,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user",   "content", promptFinal)
                    )
            );

            ChatResponse response = restClientLarge.post()
                    .uri(API_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new OpenAiUnavailableException("Respuesta vacía de OpenAI (chatLarge)");
            }

            return response.choices().get(0).message().content().trim();

        } catch (OpenAiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error al llamar a OpenAI (chatLarge): {}", e.getMessage());
            throw new OpenAiUnavailableException("Error en la llamada a OpenAI (chatLarge): " + e.getMessage());
        }
    }

    /**
     * Envía un prompt para análisis completo de convocatorias.
     * Usa {@link #maxTokensAnalisis} (por defecto 8000) y un prompt mucho más largo
     * que incluye texto oficial, catálogos BDNS, perfil y proyecto del usuario.
     */
    public String chatAnalisis(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAiUnavailableException("openai.api-key no configurada");
        }

        String promptFinal = userPrompt.length() > MAX_PROMPT_CHARS_ANALISIS
                ? userPrompt.substring(0, MAX_PROMPT_CHARS_ANALISIS)
                : userPrompt;

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", maxTokensAnalisis,
                    "temperature", 0.2,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user",   "content", promptFinal)
                    )
            );

            ChatResponse response = restClientLarge.post()
                    .uri(API_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new OpenAiUnavailableException("Respuesta vacía de OpenAI (chatAnalisis)");
            }

            return response.choices().get(0).message().content().trim();

        } catch (OpenAiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error al llamar a OpenAI (chatAnalisis): {}", e.getMessage());
            throw new OpenAiUnavailableException("Error en la llamada a OpenAI (chatAnalisis): " + e.getMessage());
        }
    }

    // ── Records internos para deserializar la respuesta JSON de OpenAI ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {}

    // ── Excepción de disponibilidad ──

    public static class OpenAiUnavailableException extends RuntimeException {
        public OpenAiUnavailableException(String msg) { super(msg); }
    }
}

