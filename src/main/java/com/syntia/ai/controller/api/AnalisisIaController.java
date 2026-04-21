package com.syntia.ai.controller.api;

import com.syntia.ai.service.AnalisisIaService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/convocatorias/{bdnsId}/analisis-ia")
@PreAuthorize("hasRole('USUARIO')")
public class AnalisisIaController {

    private final AnalisisIaService analisisIaService;

    public AnalisisIaController(AnalisisIaService analisisIaService) {
        this.analisisIaService = analisisIaService;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analizar(@PathVariable Long bdnsId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        analisisIaService.analizar(bdnsId, emitter);
        return emitter;
    }
}

