package com.syntia.ai.controller.api;

import com.syntia.ai.model.dto.CatalogoItemDTO;
import com.syntia.ai.service.CatalogosBdnsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CatalogosController {

    private final CatalogosBdnsService catalogos;

    @GetMapping("/regiones")
    public ResponseEntity<List<CatalogoItemDTO>> getRegiones() {
        return ResponseEntity.ok(catalogos.getAllRegiones().stream()
                .map(r -> new CatalogoItemDTO(r.getId(), r.getNombre()))
                .sorted(Comparator.comparing(CatalogoItemDTO::getNombre))
                .toList());
    }

    @GetMapping("/finalidades")
    public ResponseEntity<List<CatalogoItemDTO>> getFinalidades() {
        return ResponseEntity.ok(catalogos.getAllFinalidades().stream()
                .map(f -> new CatalogoItemDTO(f.getId(), f.getNombre()))
                .sorted(Comparator.comparing(CatalogoItemDTO::getNombre))
                .toList());
    }

    @GetMapping("/instrumentos")
    public ResponseEntity<List<CatalogoItemDTO>> getInstrumentos() {
        return ResponseEntity.ok(catalogos.getAllInstrumentos().stream()
                .map(i -> new CatalogoItemDTO(i.getId(), i.getNombre()))
                .sorted(Comparator.comparing(CatalogoItemDTO::getNombre))
                .toList());
    }

    @GetMapping("/organos")
    public ResponseEntity<List<CatalogoItemDTO>> getOrganos(@RequestParam(required = false) String tipoAdmon) {
        return ResponseEntity.ok(catalogos.getOrganos(tipoAdmon).stream()
                .map(o -> new CatalogoItemDTO(o.getId(), o.getNombre()))
                .sorted(Comparator.comparing(CatalogoItemDTO::getNombre))
                .toList());
    }
}

