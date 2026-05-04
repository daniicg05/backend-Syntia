package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Recomendacion;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MotorMatchingService {

    /** Puntuación mínima (0-100) para que una convocatoria se guarde como recomendación. */
    private static final int UMBRAL_RECOMENDACION = 20;


    private final ConvocatoriaRepository convocatoriaRepository;
    private final RecomendacionRepository recomendacionRepository;
    private final PerfilService perfilService;
    private final OpenAiMatchingService openAiMatchingService;
    private final BdnsClientService bdnsClientService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public MotorMatchingService(ConvocatoriaRepository convocatoriaRepository,
                                RecomendacionRepository recomendacionRepository,
                                PerfilService perfilService,
                                OpenAiMatchingService openAiMatchingService,
                                BdnsClientService bdnsClientService,
                                PlatformTransactionManager transactionManager) {
        this.convocatoriaRepository = convocatoriaRepository;
        this.recomendacionRepository = recomendacionRepository;
        this.perfilService = perfilService;
        this.openAiMatchingService = openAiMatchingService;
        this.bdnsClientService = bdnsClientService;
        this.objectMapper = new ObjectMapper();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public List<Recomendacion> generarRecomendaciones(Proyecto proyecto) {
        // 1. Cargar perfil del usuario
        Perfil perfil = perfilService.obtenerPerfil(proyecto.getUsuario().getId()).orElse(null);

        // 2. Obtener candidatas sin evaluar de la BD (guardadas previamente por BusquedaRapidaService)
        List<Recomendacion> candidatasBd = recomendacionRepository.findByProyectoIdAndUsadaIaFalse(proyecto.getId());
        log.info("Candidatas sin evaluar en BD para proyecto {}: {}", proyecto.getId(), candidatasBd.size());

        if (candidatasBd.isEmpty()) {
            log.warn("⚠ No hay candidatas sin evaluar. El usuario debe pulsar 'Buscar convocatorias' primero.");
            return new ArrayList<>();
        }

        // 3. Convertir a DTOs para el pipeline de evaluación
        List<ConvocatoriaDTO> aEvaluar = candidatasBd.stream()
                .map(rec -> entidadADto(rec.getConvocatoria()))
                .toList();

        // 4. Obtener detalles BDNS en paralelo
        Map<String, String> detallesPorId = obtenerDetallesEnParalelo(aEvaluar);

        // 5. Evaluar cada candidata con OpenAI y actualizar la recomendación existente
        List<Recomendacion> recomendaciones = new ArrayList<>();
        int fallosOpenAi = 0;
        int descartadasPorUmbral = 0;
        for (int i = 0; i < candidatasBd.size(); i++) {
            Recomendacion recExistente = candidatasBd.get(i);
            ConvocatoriaDTO dto = aEvaluar.get(i);
            try {
                String detalleTexto = dto.getNumeroConvocatoria() != null
                        ? detallesPorId.get(dto.getNumeroConvocatoria())
                        : null;

                Convocatoria temporal = recExistente.getConvocatoria();
                OpenAiMatchingService.ResultadoIA resultado =
                        openAiMatchingService.analizar(proyecto, perfil, temporal, detalleTexto);

                if (resultado.puntuacion() >= UMBRAL_RECOMENDACION) {
                    // Enriquecer sector si la IA lo infirió
                    if (resultado.sector() != null && (temporal.getSector() == null || temporal.getSector().isBlank())) {
                        temporal.setSector(resultado.sector());
                        convocatoriaRepository.save(temporal);
                    }
                    // Actualizar la recomendación existente con los resultados IA
                    recExistente.setPuntuacion(resultado.puntuacion());
                    recExistente.setExplicacion(resultado.explicacion());
                    recExistente.setGuia(resultado.guia());
                    recExistente.setUsadaIa(true);
                    recomendacionRepository.save(recExistente);
                    recomendaciones.add(recExistente);
                    log.info("Recomendación actualizada: puntuacion={} titulo='{}'",
                            resultado.puntuacion(), dto.getTitulo());
                } else {
                    descartadasPorUmbral++;
                    log.debug("Bajo umbral ({}< {}): '{}' — se mantiene como candidata",
                            resultado.puntuacion(), UMBRAL_RECOMENDACION, dto.getTitulo());
                }
            } catch (OpenAiClient.OpenAiUnavailableException e) {
                fallosOpenAi++;
                log.warn("OpenAI no disponible para '{}': {}", dto.getTitulo(), e.getMessage());
            } catch (Exception e) {
                log.warn("Error evaluando convocatoria '{}': {}", dto.getTitulo(), e.getMessage());
            }
        }

        if (fallosOpenAi > 0) {
            log.error("⚠ OpenAI falló en {}/{} evaluaciones.", fallosOpenAi, candidatasBd.size());
        }
        if (descartadasPorUmbral > 0) {
            log.info("Bajo umbral (<{}): {} — mantenidas como candidatas", UMBRAL_RECOMENDACION, descartadasPorUmbral);
        }

        if (recomendaciones.isEmpty() && fallosOpenAi > 0 && fallosOpenAi == candidatasBd.size()) {
            throw new OpenAiClient.OpenAiUnavailableException(
                    "OpenAI no disponible: falló en las " + fallosOpenAi + " evaluaciones. " +
                            "Verifica que la variable OPENAI_API_KEY esté configurada correctamente.");
        }

        recomendaciones.sort((a, b) -> Integer.compare(b.getPuntuacion(), a.getPuntuacion()));
        log.info("Matching completado: proyecto={} candidatas={} evaluadasIA={}",
                proyecto.getId(), candidatasBd.size(), recomendaciones.size());

        return recomendaciones;
    }

    // ── Helpers ─────────────────────────────────────────

    /**
     * Obtiene en paralelo el detalle BDNS de todas las candidatas.
     * Reduce la latencia de O(n×t) a O(t) donde t es el tiempo de una sola llamada.
     *
     * @param candidatas lista de DTOs a enriquecer con detalle
     * @return mapa idBdns → texto de detalle (null si no disponible)
     */
    private Map<String, String> obtenerDetallesEnParalelo(List<ConvocatoriaDTO> candidatas) {
        Map<String, String> detalles = new ConcurrentHashMap<>();
        if (candidatas.isEmpty()) return detalles;
        int nHilos = Math.min(candidatas.size(), 10);
        // En Java 17 ExecutorService no implementa AutoCloseable; el finally garantiza el shutdown
        @SuppressWarnings("resource")
        ExecutorService executor = Executors.newFixedThreadPool(nHilos);
        try {
            List<CompletableFuture<Void>> futuros = candidatas.stream()
                    .filter(dto -> dto.getNumeroConvocatoria() != null && !dto.getNumeroConvocatoria().isBlank())
                    .map(dto -> CompletableFuture.runAsync(() -> {
                        String texto = bdnsClientService.obtenerDetalleTexto(dto.getNumeroConvocatoria());
                        if (texto != null && !texto.isBlank()) {
                            detalles.put(dto.getNumeroConvocatoria(), texto);
                        }
                    }, executor))
                    .toList();
            CompletableFuture.allOf(futuros.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.warn("Error en obtención paralela de detalles BDNS: {}", e.getMessage());
        } finally {
            executor.shutdown(); // siempre liberar el pool
        }
        log.info("Detalles BDNS obtenidos en paralelo: {}/{} con contenido", detalles.size(), candidatas.size());
        return detalles;
    }

    // ── Conversión de entidades ────────────────────────────────────────────────

    private Convocatoria dtoAEntidad(ConvocatoriaDTO dto) {
        return Convocatoria.builder()
                .titulo(dto.getTitulo())
                .tipo(dto.getTipo())
                .sector(dto.getSector())
                .ubicacion(dto.getUbicacion())
                .urlOficial(dto.getUrlOficial())
                .fuente(dto.getFuente())
                .idBdns(dto.getIdBdns())
                .numeroConvocatoria(dto.getNumeroConvocatoria())
                .fechaCierre(dto.getFechaCierre())
                .mrr(Boolean.TRUE.equals(dto.getMrr()))
                .presupuesto(dto.getPresupuesto())
                .abierto(dto.getAbierto())
                .finalidad(dto.getFinalidad())
                .fechaInicio(dto.getFechaInicio())
                .organismo(dto.getOrganismo())
                .fechaPublicacion(dto.getFechaPublicacion())
                .descripcion(dto.getDescripcion())
                .textoCompleto(dto.getTextoCompleto())
                .build();
    }

    /**
     * Convierte una entidad Convocatoria (de BD) a ConvocatoriaDTO para reutilizar en el pipeline de evaluación.
     */
    private ConvocatoriaDTO entidadADto(Convocatoria conv) {
        ConvocatoriaDTO dto = new ConvocatoriaDTO();
        dto.setTitulo(conv.getTitulo());
        dto.setTipo(conv.getTipo());
        dto.setSector(conv.getSector());
        dto.setUbicacion(conv.getUbicacion());
        dto.setUrlOficial(conv.getUrlOficial());
        dto.setFuente(conv.getFuente());
        dto.setIdBdns(conv.getIdBdns());
        dto.setNumeroConvocatoria(conv.getNumeroConvocatoria());
        dto.setFechaCierre(conv.getFechaCierre());
        dto.setMrr(conv.getMrr());
        dto.setPresupuesto(conv.getPresupuesto());
        dto.setAbierto(conv.getAbierto());
        dto.setFinalidad(conv.getFinalidad());
        dto.setFechaInicio(conv.getFechaInicio());
        dto.setOrganismo(conv.getOrganismo());
        dto.setFechaPublicacion(conv.getFechaPublicacion());
        dto.setDescripcion(conv.getDescripcion());
        dto.setTextoCompleto(conv.getTextoCompleto());
        return dto;
    }

    // ── Streaming con SSE ────────────────────────────────────────────────────

    /**
     * Genera recomendaciones emitiendo eventos SSE en tiempo real.
     * <p>
     * v5.0.0 — Evalúa las candidatas existentes en BD (guardadas por BusquedaRapidaService)
     * en lugar de hacer una búsqueda BDNS fresca.
     *
     * @param proyecto proyecto del usuario
     * @param emitter  SseEmitter para enviar eventos al navegador
     */
    public void generarRecomendacionesStream(Proyecto proyecto, SseEmitter emitter) {
        try {
            enviarEvento(emitter, "estado", "🔍 Preparando análisis IA...");

            // 1. Cargar perfil
            Perfil perfil = perfilService.obtenerPerfil(proyecto.getUsuario().getId()).orElse(null);

            // 2. Obtener candidatas sin evaluar de BD
            List<Recomendacion> candidatasBd = transactionTemplate.execute(status ->
                    recomendacionRepository.findByProyectoIdAndUsadaIaFalse(proyecto.getId()));

            if (candidatasBd == null || candidatasBd.isEmpty()) {
                enviarEvento(emitter, "estado",
                        "⚠️ No hay candidatas para analizar. Pulsa primero 'Buscar convocatorias'.");
                enviarEvento(emitter, "completado",
                        Map.of("totalRecomendaciones", 0, "totalEvaluadas", 0,
                                "descartadas", 0, "errores", 0));
                return;
            }

            log.info("SSE: {} candidatas sin evaluar para proyecto {}", candidatasBd.size(), proyecto.getId());
            enviarEvento(emitter, "busqueda",
                    Map.of("candidatas", candidatasBd.size()));
            enviarEvento(emitter, "estado",
                    "🤖 Evaluando " + candidatasBd.size() + " convocatorias con IA...");

            // 3. Convertir a DTOs para obtener detalles
            List<ConvocatoriaDTO> dtos = candidatasBd.stream()
                    .map(rec -> entidadADto(rec.getConvocatoria()))
                    .toList();

            // 4. Obtener detalles BDNS en PARALELO
            enviarEvento(emitter, "estado", "📄 Descargando detalles de convocatorias en paralelo...");
            Map<String, String> detallesPorId = obtenerDetallesEnParalelo(dtos);

            // 5. Evaluar cada candidata, emitiendo resultados parciales
            List<Recomendacion> recomendaciones = new ArrayList<>();
            int procesadas = 0;
            int fallosOpenAi = 0;
            int descartadasPorUmbral = 0;

            for (int i = 0; i < candidatasBd.size(); i++) {
                Recomendacion recExistente = candidatasBd.get(i);
                ConvocatoriaDTO dto = dtos.get(i);
                procesadas++;
                enviarEvento(emitter, "progreso", Map.of(
                        "actual", procesadas,
                        "total", candidatasBd.size(),
                        "titulo", dto.getTitulo() != null ? dto.getTitulo() : "Sin título"
                ));

                try {
                    String detalleTexto = dto.getNumeroConvocatoria() != null
                            ? detallesPorId.get(dto.getNumeroConvocatoria())
                            : null;

                    // Evaluar con OpenAI
                    Convocatoria convocatoria = recExistente.getConvocatoria();
                    OpenAiMatchingService.ResultadoIA resultado =
                            openAiMatchingService.analizar(proyecto, perfil, convocatoria, detalleTexto);

                    if (resultado.puntuacion() >= UMBRAL_RECOMENDACION) {
                        if (resultado.sector() != null && (convocatoria.getSector() == null || convocatoria.getSector().isBlank())) {
                            convocatoria.setSector(resultado.sector());
                        }
                        // Actualizar la recomendación existente en transacción programática
                        final String explicacion = resultado.explicacion();
                        final String guia = resultado.guia();
                        final int puntuacion = resultado.puntuacion();
                        final String sectorIA = resultado.sector();
                        Recomendacion rec = transactionTemplate.execute(status -> {
                            if (sectorIA != null && (recExistente.getConvocatoria().getSector() == null || recExistente.getConvocatoria().getSector().isBlank())) {
                                recExistente.getConvocatoria().setSector(sectorIA);
                                convocatoriaRepository.save(recExistente.getConvocatoria());
                            }
                            recExistente.setPuntuacion(puntuacion);
                            recExistente.setExplicacion(explicacion);
                            recExistente.setGuia(guia);
                            recExistente.setUsadaIa(true);
                            return recomendacionRepository.save(recExistente);
                        });
                        recomendaciones.add(rec);

                        // Enviar resultado parcial al navegador
                        Map<String, Object> resultadoEvento = new LinkedHashMap<>();
                        resultadoEvento.put("titulo", dto.getTitulo() != null ? dto.getTitulo() : "Sin título");
                        resultadoEvento.put("puntuacion", resultado.puntuacion());
                        resultadoEvento.put("explicacion", resultado.explicacion() != null ? resultado.explicacion() : "");
                        resultadoEvento.put("tipo", dto.getTipo() != null ? dto.getTipo() : "");
                        resultadoEvento.put("sector", dto.getSector() != null ? dto.getSector() : "");
                        resultadoEvento.put("ubicacion", dto.getUbicacion() != null ? dto.getUbicacion() : "");
                        resultadoEvento.put("urlOficial", dto.getUrlOficial() != null ? dto.getUrlOficial() : "");
                        resultadoEvento.put("fuente", dto.getFuente() != null ? dto.getFuente() : "");
                        resultadoEvento.put("guia", resultado.guia() != null ? resultado.guia() : "");
                        resultadoEvento.put("fechaCierre", dto.getFechaCierre() != null ? dto.getFechaCierre().toString() : "");
                        resultadoEvento.put("totalEncontradas", recomendaciones.size());
                        enviarEvento(emitter, "resultado", resultadoEvento);
                        log.info("SSE resultado: puntuacion={} titulo='{}'",
                                resultado.puntuacion(), dto.getTitulo());
                    } else {
                        descartadasPorUmbral++;
                    }
                } catch (OpenAiClient.OpenAiUnavailableException e) {
                    fallosOpenAi++;
                    log.warn("SSE: OpenAI no disponible para '{}': {}", dto.getTitulo(), e.getMessage());
                } catch (Exception e) {
                    log.warn("SSE: Error evaluando '{}': {}", dto.getTitulo(), e.getMessage());
                }
            }

            // Resumen final
            enviarEvento(emitter, "completado", Map.of(
                    "totalRecomendaciones", recomendaciones.size(),
                    "totalEvaluadas", candidatasBd.size(),
                    "descartadas", descartadasPorUmbral,
                    "errores", fallosOpenAi
            ));

            log.info("SSE matching completado: proyecto={} recomendaciones={}",
                    proyecto.getId(), recomendaciones.size());

        } catch (Exception e) {
            log.error("SSE: Error en generarRecomendacionesStream: {}", e.getMessage(), e);
            enviarEvento(emitter, "error", "Error interno: " + e.getMessage());
        }
    }

    /**
     * Envía un evento SSE al cliente. Silencia errores si la conexión ya se cerró.
     */
    private void enviarEvento(SseEmitter emitter, String tipo, Object datos) {
        try {
            String json;
            if (datos instanceof String s) {
                json = "\"" + s.replace("\"", "\\\"") + "\"";
            } else {
                json = objectMapper.writeValueAsString(datos);
            }
            emitter.send(SseEmitter.event()
                    .name(tipo)
                    .data(json, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.debug("Error enviando evento SSE '{}': {}", tipo, e.getMessage());
        }
    }
}
