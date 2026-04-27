package com.syntia.ai.service;

import com.syntia.ai.model.CatActividad;
import com.syntia.ai.model.CatBeneficiario;
import com.syntia.ai.model.CatFinalidad;
import com.syntia.ai.model.CatInstrumento;
import com.syntia.ai.model.CatObjetivo;
import com.syntia.ai.model.CatOrgano;
import com.syntia.ai.model.CatReglamento;
import com.syntia.ai.model.CatSectorProducto;
import com.syntia.ai.repository.CatActividadRepository;
import com.syntia.ai.repository.CatBeneficiarioRepository;
import com.syntia.ai.repository.CatFinalidadRepository;
import com.syntia.ai.repository.CatInstrumentoRepository;
import com.syntia.ai.repository.CatObjetivoRepository;
import com.syntia.ai.repository.CatOrganoRepository;
import com.syntia.ai.repository.CatReglamentoRepository;
import com.syntia.ai.repository.CatSectorProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Fase 1 del ETL BDNS: importa los catalogos de referencia.
 * Scheduled cada domingo a las 3AM. Ejecutable manualmente desde el admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogoImportService {

    private final BdnsCatalogoClient bdnsCatalogoClient;
    private final CatFinalidadRepository finalidadRepo;
    private final CatInstrumentoRepository instrumentoRepo;
    private final CatBeneficiarioRepository beneficiarioRepo;
    private final CatActividadRepository actividadRepo;
    private final CatReglamentoRepository reglamentoRepo;
    private final CatObjetivoRepository objetivoRepo;
    private final CatSectorProductoRepository sectorProductoRepo;
    private final CatOrganoRepository organoRepo;

    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public ResultadoCatalogos importarTodos() {
        return importarTodos(null, null);
    }

    /**
     * Importa todos los catalogos con callback opcional de progreso.
     * @param onProgreso recibe un mensaje descriptivo por cada catalogo
     */
    @Transactional
    public ResultadoCatalogos importarTodos(Consumer<String> onProgreso) {
        return importarTodos(onProgreso, null);
    }

    /**
     * Importa todos los catalogos con soporte de cancelacion cooperativa entre fases.
     */
    @Transactional
    public ResultadoCatalogos importarTodos(Consumer<String> onProgreso, BooleanSupplier cancelado) {
        log.info("=== INICIO importacion catalogos BDNS ===");

        comprobarCancelacion(cancelado);
        notificar(onProgreso, "Catalogos: importando finalidades (1/8)");
        int finalidades = importarFinalidades();

        comprobarCancelacion(cancelado);
        notificar(onProgreso, "Catalogos: importando instrumentos (2/8)");
        int instrumentos = importarInstrumentos();

        comprobarCancelacion(cancelado);
        notificar(onProgreso, "Catalogos: importando beneficiarios (3/8)");
        int beneficiarios = importarBeneficiarios();

        comprobarCancelacion(cancelado);
        notificar(onProgreso, "Catalogos: importando actividades (4/8)");
        int actividades = importarActividades();

        comprobarCancelacion(cancelado);
        notificar(onProgreso, "Catalogos: importando reglamentos (5/8)");
        int reglamentos = importarReglamentos();

        comprobarCancelacion(cancelado);
        notificar(onProgreso, "Catalogos: importando objetivos (6/8)");
        int objetivos = importarObjetivos();

        comprobarCancelacion(cancelado);
        notificar(onProgreso, "Catalogos: importando sectores (7/8)");
        int sectores = importarSectoresProducto();

        comprobarCancelacion(cancelado);
        notificar(onProgreso, "Catalogos: importando organos (8/8)");
        int organos = importarOrganos();

        log.info("=== FIN catalogos: finalidades={} instrumentos={} beneficiarios={} actividades={} reglamentos={} objetivos={} sectores={} organos={}",
                finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);
        return new ResultadoCatalogos(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);
    }

    private void notificar(Consumer<String> onProgreso, String mensaje) {
        if (onProgreso != null) {
            onProgreso.accept(mensaje);
        }
    }

    private void comprobarCancelacion(BooleanSupplier cancelado) {
        if (cancelado != null && cancelado.getAsBoolean()) {
            throw new CancellationException("Importacion de catalogos cancelada");
        }
    }

    private int importarFinalidades() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlano("/finalidades");
        if (items.isEmpty()) {
            log.warn("Finalidades: API devolvio 0 registros, se mantienen datos actuales");
            return (int) finalidadRepo.count();
        }
        finalidadRepo.deleteAll();
        List<CatFinalidad> entidades = items.stream()
                .map(i -> CatFinalidad.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList();
        finalidadRepo.saveAll(entidades);
        log.info("Finalidades importadas: {}", entidades.size());
        return entidades.size();
    }

    private int importarInstrumentos() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlano("/instrumentos");
        if (items.isEmpty()) {
            log.warn("Instrumentos: API devolvio 0 registros, se mantienen datos actuales");
            return (int) instrumentoRepo.count();
        }
        instrumentoRepo.deleteAll();
        List<CatInstrumento> entidades = items.stream()
                .map(i -> CatInstrumento.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList();
        instrumentoRepo.saveAll(entidades);
        log.info("Instrumentos importados: {}", entidades.size());
        return entidades.size();
    }

    private int importarBeneficiarios() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlano("/beneficiarios");
        if (items.isEmpty()) {
            log.warn("Beneficiarios: API devolvio 0 registros, se mantienen datos actuales");
            return (int) beneficiarioRepo.count();
        }
        beneficiarioRepo.deleteAll();
        List<CatBeneficiario> entidades = items.stream()
                .map(i -> CatBeneficiario.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList();
        beneficiarioRepo.saveAll(entidades);
        log.info("Beneficiarios importados: {}", entidades.size());
        return entidades.size();
    }

    private int importarActividades() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlano("/actividades");
        if (items.isEmpty()) {
            log.warn("Actividades: API devolvio 0 registros, se mantienen datos actuales");
            return (int) actividadRepo.count();
        }
        actividadRepo.deleteAll();
        List<CatActividad> entidades = items.stream()
                .map(i -> CatActividad.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList();
        actividadRepo.saveAll(entidades);
        log.info("Actividades importadas: {}", entidades.size());
        return entidades.size();
    }

    private int importarReglamentos() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlano("/reglamentos");
        if (items.isEmpty()) {
            log.warn("Reglamentos: API devolvio 0 registros, se mantienen datos actuales");
            return (int) reglamentoRepo.count();
        }
        reglamentoRepo.deleteAll();
        List<CatReglamento> entidades = items.stream()
                .map(i -> CatReglamento.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList();
        reglamentoRepo.saveAll(entidades);
        log.info("Reglamentos importados: {}", entidades.size());
        return entidades.size();
    }

    private int importarObjetivos() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlano("/objetivos");
        if (items.isEmpty()) {
            log.warn("Objetivos: API devolvio 0 registros, se mantienen datos actuales");
            return (int) objetivoRepo.count();
        }
        objetivoRepo.deleteAll();
        List<CatObjetivo> entidades = items.stream()
                .map(i -> CatObjetivo.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList();
        objetivoRepo.saveAll(entidades);
        log.info("Objetivos importados: {}", entidades.size());
        return entidades.size();
    }

    private int importarSectoresProducto() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlano("/sectores");
        if (items.isEmpty()) {
            log.warn("SectoresProducto: API devolvio 0 registros, se mantienen datos actuales");
            return (int) sectorProductoRepo.count();
        }
        sectorProductoRepo.deleteAll();
        List<CatSectorProducto> entidades = items.stream()
                .map(i -> CatSectorProducto.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList();
        sectorProductoRepo.saveAll(entidades);
        log.info("SectoresProducto importados: {}", entidades.size());
        return entidades.size();
    }

    private int importarOrganos() {
        List<CatOrgano> todosOrganos = new ArrayList<>();
        for (String tipo : List.of("C", "A", "L", "O")) {
            List<BdnsCatalogoClient.OrganoItem> items = bdnsCatalogoClient.fetchOrganos(tipo);
            for (BdnsCatalogoClient.OrganoItem i : items) {
                todosOrganos.add(CatOrgano.builder()
                        .id(i.id())
                        .descripcion(i.descripcion())
                        .parentId(i.parentId())
                        .tipoAdmin(i.tipoAdmin())
                        .build());
            }
            log.info("Organos tipo={} descargados: {}", tipo, items.size());
        }
        if (todosOrganos.isEmpty()) {
            log.warn("Organos: API devolvio 0 registros en total, se mantienen datos actuales");
            return (int) organoRepo.count();
        }
        organoRepo.deleteAll();
        organoRepo.saveAll(todosOrganos);
        log.info("Organos importados total: {}", todosOrganos.size());
        return todosOrganos.size();
    }

    public ConteoCatalogos contarTodos() {
        return new ConteoCatalogos(
                (int) finalidadRepo.count(),
                (int) instrumentoRepo.count(),
                (int) beneficiarioRepo.count(),
                (int) actividadRepo.count(),
                (int) reglamentoRepo.count(),
                (int) objetivoRepo.count(),
                (int) sectorProductoRepo.count(),
                (int) organoRepo.count()
        );
    }

    public record ResultadoCatalogos(int finalidades, int instrumentos, int beneficiarios,
                                     int actividades, int reglamentos, int objetivos,
                                     int sectores, int organos) {}

    public record ConteoCatalogos(int finalidades, int instrumentos, int beneficiarios,
                                  int actividades, int reglamentos, int objetivos,
                                  int sectores, int organos) {}
}
