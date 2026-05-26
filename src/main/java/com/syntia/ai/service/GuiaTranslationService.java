package com.syntia.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.model.dto.GuiaUsuarioDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GuiaTranslationService {

    private static final Set<String> SUPPORTED_LANGS = Set.of("es", "en", "ca", "gl", "eu");

    private static final Map<String, String> LANG_NAMES = Map.of(
            "en", "English",
            "ca", "Catalan (Català)",
            "gl", "Galician (Galego)",
            "eu", "Basque (Euskara)"
    );

    private static final String TRANSLATION_SYSTEM_PROMPT = """
            You are a professional translator specializing in Spanish public grant documentation. \
            Your task: translate the entire JSON below into the target language. \
            Rules: \
            1. Maintain EXACTLY the same JSON structure, keys, and data types. \
            2. Translate ALL text values: titles, descriptions, objectives, method names, document names, \
               legal disclaimers, step titles, step descriptions, phase names, user_action, portal_action, \
               who_can_apply, legal_basis, and every other human-readable string. \
            3. DO NOT translate: URLs (official_link, official_portal), ISO dates, numbers, \
               legal acronyms (LGS, BOE, BDNS, SEPE, AEAT, TGSS, IVA, IRPF, FNMT). \
            4. Use formal, administrative tone appropriate for government documentation. \
            5. Return ONLY the translated JSON. No markdown, no comments, no explanations.""";

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    private final Cache<String, GuiaUsuarioDTO> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(7, TimeUnit.DAYS)
            .build();

    public GuiaTranslationService(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    public static String normalizeLang(String lang) {
        if (lang == null || lang.isBlank()) return "es";
        String normalized = lang.trim().toLowerCase();
        return SUPPORTED_LANGS.contains(normalized) ? normalized : "es";
    }

    public GuiaUsuarioDTO translate(GuiaUsuarioDTO dto, String lang) {
        if ("es".equals(lang)) return dto;

        String cacheKey = dto.getId() + "_" + dto.getOrigen() + "_" + lang;
        GuiaUsuarioDTO cached = cache.getIfPresent(cacheKey);
        if (cached != null) return cached;

        try {
            GuiaUsuarioDTO translated = doTranslate(dto, lang);
            cache.put(cacheKey, translated);
            return translated;
        } catch (Exception e) {
            log.warn("Traduccion fallida para guia id={} lang={}: {}. Usando original.", dto.getId(), lang, e.getMessage());
            return dto;
        }
    }

    public void evict(Long guiaId, String origen) {
        for (String lang : SUPPORTED_LANGS) {
            cache.invalidate(guiaId + "_" + origen + "_" + lang);
        }
    }

    Cache<String, GuiaUsuarioDTO> getCache() {
        return cache;
    }

    private GuiaUsuarioDTO doTranslate(GuiaUsuarioDTO dto, String lang) throws Exception {
        String langName = LANG_NAMES.getOrDefault(lang, lang);

        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("titulo", nvl(dto.getTitulo()));
        meta.put("organismo", nvl(dto.getOrganismo()));
        meta.put("sector", nvl(dto.getSector()));
        meta.put("ubicacion", nvl(dto.getUbicacion()));
        meta.put("proyectoNombre", nvl(dto.getProyectoNombre()));
        payload.put("metadata", meta);
        payload.put("guia", dto.getGuia());

        String jsonInput = objectMapper.writeValueAsString(payload);
        String userPrompt = "Target language: " + langName + "\n\nJSON to translate:\n" + jsonInput;

        String response = openAiClient.chatAnalisis(TRANSLATION_SYSTEM_PROMPT, userPrompt);

        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);

        GuiaUsuarioDTO result = GuiaUsuarioDTO.builder()
                .id(dto.getId())
                .origen(dto.getOrigen())
                .convocatoriaId(dto.getConvocatoriaId())
                .fechaCierre(dto.getFechaCierre())
                .abierto(dto.getAbierto())
                .urlOficial(dto.getUrlOficial())
                .numeroConvocatoria(dto.getNumeroConvocatoria())
                .proyectoId(dto.getProyectoId())
                .creadoEn(dto.getCreadoEn())
                .puntuacion(dto.getPuntuacion())
                .build();

        if (parsed.get("metadata") instanceof Map<?, ?> metaResp) {
            result.setTitulo(strOr(metaResp, "titulo", dto.getTitulo()));
            result.setOrganismo(strOr(metaResp, "organismo", dto.getOrganismo()));
            result.setSector(strOr(metaResp, "sector", dto.getSector()));
            result.setUbicacion(strOr(metaResp, "ubicacion", dto.getUbicacion()));
            result.setProyectoNombre(strOr(metaResp, "proyectoNombre", dto.getProyectoNombre()));
        } else {
            result.setTitulo(dto.getTitulo());
            result.setOrganismo(dto.getOrganismo());
            result.setSector(dto.getSector());
            result.setUbicacion(dto.getUbicacion());
            result.setProyectoNombre(dto.getProyectoNombre());
        }

        if (parsed.containsKey("guia")) {
            String guiaJson = objectMapper.writeValueAsString(parsed.get("guia"));
            GuiaSubvencionDTO translatedGuia = objectMapper.readValue(guiaJson, GuiaSubvencionDTO.class);
            result.setGuia(translatedGuia);
        } else {
            result.setGuia(dto.getGuia());
        }

        return result;
    }

    private static String strOr(Map<?, ?> map, String key, String fallback) {
        Object v = map.get(key);
        return v instanceof String s && !s.isBlank() ? s : fallback;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
