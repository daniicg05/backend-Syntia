package com.syntia.ai.controller.api;

import com.syntia.ai.model.Recomendacion;
import com.syntia.ai.model.dto.ConvocatoriaDetalleDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import com.syntia.ai.service.BdnsClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;

@RestController
@RequestMapping("/api/convocatorias")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ConvocatoriaDetalleController {

    private final BdnsClientService bdnsClient;
    private final RecomendacionRepository recomendacionRepo;
    private final ConvocatoriaRepository convocatoriaRepo;

    @GetMapping("/{idBdns}/detalle")
    public ResponseEntity<ConvocatoriaDetalleDTO> getDetalle(@PathVariable String idBdns) {
        var convocatoriaOpt = convocatoriaRepo.findByIdBdns(idBdns);
        String numeroConvocatoria = convocatoriaOpt
                .map(conv -> conv.getNumeroConvocatoria())
                .filter(num -> num != null && !num.isBlank())
                .orElse(idBdns);

        ConvocatoriaDetalleDTO detalle = bdnsClient.obtenerDetalleCompleto(numeroConvocatoria);

        // Enriquecer con datos IA de Syntia si ya fue analizada.
        convocatoriaOpt.ifPresent(conv -> {
            detalle.setSector(conv.getSector());
            detalle.setInstrumento(conv.getTipo());

            recomendacionRepo.findAll().stream()
                    .filter(rec -> rec.getConvocatoria() != null
                            && rec.getConvocatoria().getId() != null
                            && rec.getConvocatoria().getId().equals(conv.getId()))
                    .max(Comparator.comparingInt(Recomendacion::getPuntuacion))
                    .ifPresent(rec -> {
                        detalle.setPuntuacion(rec.getPuntuacion());
                        detalle.setExplicacion(rec.getExplicacion());
                        detalle.setGuia(rec.getGuia());
                        detalle.setFechaAnalisis(rec.getGeneradaEn());
                    });
        });

        return ResponseEntity.ok(detalle);
    }
}
