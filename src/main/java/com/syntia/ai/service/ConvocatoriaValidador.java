package com.syntia.ai.service;

import com.syntia.ai.model.dto.ConvocatoriaDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Valida un ConvocatoriaDTO antes de persistirlo.
 * Las convocatorias inválidas se registran en métricas pero no se lanzan excepciones
 * para no interrumpir la importación masiva.
 */
@Slf4j
@Component
public class ConvocatoriaValidador {

    private static final int MAX_TITULO = 500;

    public record ResultadoValidacion(boolean valida, String razon) {}

    public ResultadoValidacion validar(ConvocatoriaDTO dto) {
        if (dto.getTitulo() == null || dto.getTitulo().isBlank()) {
            return new ResultadoValidacion(false, "titulo vacío");
        }
        if (dto.getTitulo().length() > MAX_TITULO) {
            log.debug("Convocatoria rechazada: titulo demasiado largo ({} chars) idBdns={}",
                    dto.getTitulo().length(), dto.getIdBdns());
            return new ResultadoValidacion(false, "titulo supera " + MAX_TITULO + " caracteres");
        }
        if (dto.getIdBdns() == null || dto.getIdBdns().isBlank()) {
            // No rechazar, pero registrar: puede provenir de fuentes sin idBdns
            log.debug("Convocatoria sin idBdns: titulo='{}'", dto.getTitulo());
        }
        return new ResultadoValidacion(true, null);
    }
}