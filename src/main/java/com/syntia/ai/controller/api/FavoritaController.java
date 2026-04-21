package com.syntia.ai.controller.api;

import com.syntia.ai.model.EstadoSolicitudFavorita;
import com.syntia.ai.model.dto.FavoritaEstadoRequestDTO;
import com.syntia.ai.model.dto.FavoritaImportRequestDTO;
import com.syntia.ai.model.dto.FavoritaResponseDTO;
import com.syntia.ai.model.dto.FavoritaUpsertRequestDTO;
import com.syntia.ai.service.FavoritaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/usuario/favoritas")
@PreAuthorize("hasRole('USUARIO')")
public class FavoritaController {

    private final FavoritaService favoritaService;

    public FavoritaController(FavoritaService favoritaService) {
        this.favoritaService = favoritaService;
    }

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) EstadoSolicitudFavorita estadoSolicitud,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(defaultValue = "guardadaEn,desc") String sort,
                                    Authentication authentication) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Long usuarioId = favoritaService.resolverUsuarioId(authentication.getName());

        String[] sortParts = sort.split(",", 2);
        String sortField = sortParts[0].isBlank() ? "guardadaEn" : sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Page<FavoritaResponseDTO> resultado = favoritaService.listar(
                usuarioId,
                estadoSolicitud,
                q,
                PageRequest.of(safePage, safeSize, Sort.by(direction, sortField))
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                "content", resultado.getContent(),
                "page", resultado.getNumber(),
                "size", resultado.getSize(),
                "totalElements", resultado.getTotalElements(),
                "totalPages", resultado.getTotalPages(),
                "hasNext", resultado.hasNext()
        ));
    }

    @GetMapping("/{convocatoriaId}")
    public ResponseEntity<FavoritaResponseDTO> obtener(@PathVariable Long convocatoriaId,
                                                       Authentication authentication) {
        Long usuarioId = favoritaService.resolverUsuarioId(authentication.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(favoritaService.obtenerPorConvocatoria(usuarioId, convocatoriaId));
    }

    @PostMapping
    public ResponseEntity<FavoritaResponseDTO> upsert(@Valid @RequestBody FavoritaUpsertRequestDTO request,
                                                      Authentication authentication) {
        Long usuarioId = favoritaService.resolverUsuarioId(authentication.getName());
        FavoritaService.UpsertResult result = favoritaService.upsert(usuarioId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .body(result.favorita());
    }

    @DeleteMapping("/{convocatoriaId}")
    public ResponseEntity<Void> eliminar(@PathVariable Long convocatoriaId,
                                         Authentication authentication) {
        Long usuarioId = favoritaService.resolverUsuarioId(authentication.getName());
        favoritaService.eliminar(usuarioId, convocatoriaId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{convocatoriaId}/estado")
    public ResponseEntity<FavoritaResponseDTO> actualizarEstado(@PathVariable Long convocatoriaId,
                                                                @Valid @RequestBody FavoritaEstadoRequestDTO request,
                                                                Authentication authentication) {
        Long usuarioId = favoritaService.resolverUsuarioId(authentication.getName());
        FavoritaResponseDTO actualizada = favoritaService.actualizarEstado(
                usuarioId,
                convocatoriaId,
                request.getEstadoSolicitud()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(actualizada);
    }

    @PostMapping("/import")
    public ResponseEntity<?> importar(@Valid @RequestBody FavoritaImportRequestDTO request,
                                      Authentication authentication) {
        Long usuarioId = favoritaService.resolverUsuarioId(authentication.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(favoritaService.importar(usuarioId, request.getFavoritas()));
    }
}
