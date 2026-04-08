package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.model.dto.ResultadoPersistencia;
import com.syntia.ai.repository.ConvocatoriaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de lógica de negocio para la gestión de convocatorias.
 * Usado principalmente por el panel administrativo y el motor de matching.
 */
@Slf4j
@Service
public class ConvocatoriaService {

    private final ConvocatoriaRepository convocatoriaRepository;
    private final BdnsClientService bdnsClientService;
    private final ConvocatoriaValidador validador;

    public ConvocatoriaService(ConvocatoriaRepository convocatoriaRepository,
                               BdnsClientService bdnsClientService,
                               ConvocatoriaValidador validador) {
        this.convocatoriaRepository = convocatoriaRepository;
        this.bdnsClientService = bdnsClientService;
        this.validador = validador;
    }

    /** Corrige URLs antiguas /convocatoria/ → /convocatorias/ en toda la BD. Retorna el número de registros actualizados. */
    @Transactional
    public int corregirUrlsAntiguas() {
        List<Convocatoria> todas = convocatoriaRepository.findAll();
        int corregidas = 0;
        for (Convocatoria c : todas) {
            if (c.getUrlOficial() != null && c.getUrlOficial().contains("/bdnstrans/GE/es/convocatoria/")) {
                c.setUrlOficial(c.getUrlOficial().replace(
                        "/bdnstrans/GE/es/convocatoria/",
                        "/bdnstrans/GE/es/convocatorias/"));
                convocatoriaRepository.save(c);
                corregidas++;
            }
        }
        return corregidas;
    }

    /** Obtiene todas las convocatorias registradas. */
    public List<Convocatoria> obtenerTodas() {
        return convocatoriaRepository.findAll();
    }

    /**
     * Obtiene una convocatoria por ID.
     * @throws EntityNotFoundException si no existe
     */
    public Convocatoria obtenerPorId(Long id) {
        return convocatoriaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Convocatoria no encontrada: " + id));
    }

    /** Crea una nueva convocatoria. */
    @Transactional
    public Convocatoria crear(ConvocatoriaDTO dto) {
        Convocatoria c = new Convocatoria();
        c.setTitulo(dto.getTitulo());
        c.setTipo(dto.getTipo());
        c.setSector(dto.getSector());
        c.setUbicacion(dto.getUbicacion());
        c.setUrlOficial(dto.getUrlOficial());
        c.setFuente(dto.getFuente());
        c.setFechaCierre(dto.getFechaCierre());
        c.setIdBdns(dto.getIdBdns());
        c.setNumeroConvocatoria(dto.getNumeroConvocatoria());
        c.setOrganismo(dto.getOrganismo());
        c.setFechaPublicacion(dto.getFechaPublicacion());
        c.setDescripcion(dto.getDescripcion());
        c.setTextoCompleto(dto.getTextoCompleto());
        return convocatoriaRepository.save(c);
    }

    /**
     * Actualiza una convocatoria existente.
     * @throws EntityNotFoundException si no existe
     */
    @Transactional
    public Convocatoria actualizar(Long id, ConvocatoriaDTO dto) {
        Convocatoria c = obtenerPorId(id);
        c.setTitulo(dto.getTitulo());
        c.setTipo(dto.getTipo());
        c.setSector(dto.getSector());
        c.setUbicacion(dto.getUbicacion());
        c.setUrlOficial(dto.getUrlOficial());
        c.setFuente(dto.getFuente());
        c.setFechaCierre(dto.getFechaCierre());
        return convocatoriaRepository.save(c);
    }

    /**
     * Elimina una convocatoria por ID.
     * @throws EntityNotFoundException si no existe
     */
    @Transactional
    public void eliminar(Long id) {
        Convocatoria c = obtenerPorId(id);
        convocatoriaRepository.delete(c);
    }


    @Transactional
    public int importarDesdeBdns(int pagina, int tamano) {
        List<ConvocatoriaDTO> importadas = bdnsClientService.importar(pagina, tamano);
        return persistirNuevas(importadas).nuevas();
    }


    @Transactional
    public ResultadoPersistencia persistirNuevas(List<ConvocatoriaDTO> importadas) {
        int nuevas = 0;
        int duplicadas = 0;
        int rechazadas = 0;
        int actualizados = 0;

        for (ConvocatoriaDTO dto : importadas) {
            // Validar antes de intentar persistir
            ConvocatoriaValidador.ResultadoValidacion validacion = validador.validar(dto);
            if (!validacion.valida()) {
                log.debug("Convocatoria rechazada ({}): idBdns={}", validacion.razon(), dto.getIdBdns());
                rechazadas++;
                continue;
            }

            // Deduplicar por idBdns (clave oficial) si está disponible, si no por título+fuente
            if (dto.getIdBdns() != null && !dto.getIdBdns().isBlank()) {
                if (!convocatoriaRepository.existsByIdBdns(dto.getIdBdns())) {
                    crear(dto);
                    nuevas++;
                } else {
                    boolean actualizado = actualizarCamposNulos(dto);
                    if (actualizado) actualizados++;
                    else duplicadas++;
                }
            } else {
                boolean existe = convocatoriaRepository
                        .existsByTituloIgnoreCaseAndFuente(dto.getTitulo(), dto.getFuente());
                if (!existe) {
                    crear(dto);
                    nuevas++;
                } else {
                    duplicadas++;
                }
            }
        }

        log.info("BDNS import: {} procesadas — {} nuevas, {} actualizadas, {} duplicadas, {} rechazadas",
                importadas.size(), nuevas, actualizados, duplicadas, rechazadas);
        return new ResultadoPersistencia(nuevas, duplicadas, rechazadas, actualizados);
    }

    /**
     * Rellena solo los campos que están a null en un registro existente.
     * No sobreescribe datos ya presentes (protege ediciones manuales).
     * @return true si se modificó algún campo
     */
    private boolean actualizarCamposNulos(ConvocatoriaDTO dto) {
        return convocatoriaRepository.findByIdBdns(dto.getIdBdns()).map(c -> {
            boolean cambios = false;
            if (c.getOrganismo() == null && dto.getOrganismo() != null) {
                c.setOrganismo(dto.getOrganismo()); cambios = true;
            }
            if (c.getFechaPublicacion() == null && dto.getFechaPublicacion() != null) {
                c.setFechaPublicacion(dto.getFechaPublicacion()); cambios = true;
            }
            if (c.getDescripcion() == null && dto.getDescripcion() != null) {
                c.setDescripcion(dto.getDescripcion()); cambios = true;
            }
            if (c.getTextoCompleto() == null && dto.getTextoCompleto() != null) {
                c.setTextoCompleto(dto.getTextoCompleto()); cambios = true;
            }
            if (c.getFechaCierre() == null && dto.getFechaCierre() != null) {
                c.setFechaCierre(dto.getFechaCierre()); cambios = true;
            }
            if (cambios) convocatoriaRepository.save(c);
            return cambios;
        }).orElse(false);
    }

    /** Convierte una entidad a DTO para precargar formularios de edición. */
    public ConvocatoriaDTO toDTO(Convocatoria c) {
        ConvocatoriaDTO dto = new ConvocatoriaDTO();
        dto.setTitulo(c.getTitulo());
        dto.setTipo(c.getTipo());
        dto.setSector(c.getSector());
        dto.setUbicacion(c.getUbicacion());
        dto.setFuente(c.getFuente());
        dto.setFechaCierre(c.getFechaCierre());
        dto.setIdBdns(c.getIdBdns());
        dto.setNumeroConvocatoria(c.getNumeroConvocatoria());
        dto.setOrganismo(c.getOrganismo());
        dto.setFechaPublicacion(c.getFechaPublicacion());
        dto.setDescripcion(c.getDescripcion());
        dto.setTextoCompleto(c.getTextoCompleto());
        // Construir URL fiable: numConv > idBdns > url guardada
        String url;
        if (c.getNumeroConvocatoria() != null && !c.getNumeroConvocatoria().isBlank()) {
            url = "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias?numConv=" + c.getNumeroConvocatoria();
        } else if (c.getIdBdns() != null && !c.getIdBdns().isBlank()) {
            url = "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias/" + c.getIdBdns();
        } else {
            url = c.getUrlOficial();
            if (url != null) url = url.replace("/bdnstrans/GE/es/convocatoria/", "/bdnstrans/GE/es/convocatorias/");
        }
        dto.setUrlOficial(url);
        return dto;
    }
}

