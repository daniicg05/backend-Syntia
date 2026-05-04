package com.syntia.ai.controller.api;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Favorito;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.ConvocatoriaPublicaDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.repository.FavoritoRepository;
import com.syntia.ai.service.UsuarioService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuario/favoritos")
public class FavoritoController {

    private final FavoritoRepository favoritoRepository;
    private final ConvocatoriaRepository convocatoriaRepository;
    private final UsuarioService usuarioService;

    public FavoritoController(FavoritoRepository favoritoRepository,
                              ConvocatoriaRepository convocatoriaRepository,
                              UsuarioService usuarioService) {
        this.favoritoRepository = favoritoRepository;
        this.convocatoriaRepository = convocatoriaRepository;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<List<ConvocatoriaPublicaDTO>> listar(Authentication auth) {
        Usuario usuario = resolverUsuario(auth);
        List<Favorito> favoritos = favoritoRepository.findByUsuarioIdOrderByAgregadoEnDesc(usuario.getId());
        List<ConvocatoriaPublicaDTO> dtos = favoritos.stream()
                .map(f -> toDTO(f.getConvocatoria()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/ids")
    public ResponseEntity<?> listarIds(Authentication auth) {
        Usuario usuario = resolverUsuario(auth);
        return ResponseEntity.ok(favoritoRepository.findConvocatoriaIdsByUsuarioId(usuario.getId()));
    }

    @PostMapping("/{convocatoriaId}")
    public ResponseEntity<?> agregar(@PathVariable Long convocatoriaId, Authentication auth) {
        Usuario usuario = resolverUsuario(auth);
        if (favoritoRepository.findByUsuarioIdAndConvocatoriaId(usuario.getId(), convocatoriaId).isPresent()) {
            return ResponseEntity.ok(Map.of("message", "Ya está en favoritos"));
        }
        Convocatoria conv = convocatoriaRepository.findById(convocatoriaId)
                .orElseThrow(() -> new EntityNotFoundException("Convocatoria no encontrada"));
        Favorito fav = Favorito.builder().usuario(usuario).convocatoria(conv).build();
        favoritoRepository.save(fav);
        return ResponseEntity.ok(Map.of("message", "Añadida a favoritos"));
    }

    @DeleteMapping("/{convocatoriaId}")
    @Transactional
    public ResponseEntity<?> eliminar(@PathVariable Long convocatoriaId, Authentication auth) {
        Usuario usuario = resolverUsuario(auth);
        favoritoRepository.deleteByUsuarioIdAndConvocatoriaId(usuario.getId(), convocatoriaId);
        return ResponseEntity.ok(Map.of("message", "Eliminada de favoritos"));
    }

    private ConvocatoriaPublicaDTO toDTO(Convocatoria c) {
        ConvocatoriaPublicaDTO dto = new ConvocatoriaPublicaDTO();
        dto.setId(c.getId());
        dto.setTitulo(c.getTitulo());
        dto.setTipo(c.getTipo());
        dto.setSector(c.getSector());
        dto.setUbicacion(c.getUbicacion());
        dto.setOrganismo(c.getOrganismo());
        dto.setFechaCierre(c.getFechaCierre());
        dto.setFechaPublicacion(c.getFechaPublicacion());
        dto.setAbierto(c.getAbierto());
        dto.setPresupuesto(c.getPresupuesto());
        dto.setIdBdns(c.getIdBdns());
        dto.setNumeroConvocatoria(c.getNumeroConvocatoria());
        dto.setRegionId(c.getRegionId());
        String numConv = c.getNumeroConvocatoria();
        dto.setUrlOficial(numConv != null && !numConv.isBlank()
                ? "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv
                : c.getUrlOficial());
        return dto;
    }

    private Usuario resolverUsuario(Authentication auth) {
        return usuarioService.buscarPorEmail(auth.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
