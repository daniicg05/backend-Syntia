package com.syntia.ai.service;

import com.syntia.ai.model.*;
import com.syntia.ai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

/**
 * Fase 1 del ETL BDNS: importa los catálogos de referencia.
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
        return importarTodos(null);
    }

    /**
     * Importa todos los catálogos con callback opcional de progreso.
     * @param onProgreso recibe un mensaje descriptivo por cada catálogo (ej: "Catálogos: importando finalidades (1/8)")
     */
    @Transactional
    public ResultadoCatalogos importarTodos(Consumer<String> onProgreso) {
        log.info("=== INICIO importación catálogos BDNS ===");

        notificar(onProgreso, "Catálogos: importando finalidades (1/8)");
        int finalidades   = importarFinalidades();

        notificar(onProgreso, "Catálogos: importando instrumentos (2/8)");
        int instrumentos  = importarInstrumentos();

        notificar(onProgreso, "Catálogos: importando beneficiarios (3/8)");
        int beneficiarios = importarBeneficiarios();

        notificar(onProgreso, "Catálogos: importando actividades (4/8)");
        int actividades   = importarActividades();

        notificar(onProgreso, "Catálogos: importando reglamentos (5/8)");
        int reglamentos   = importarReglamentos();

        notificar(onProgreso, "Catálogos: importando objetivos (6/8)");
        int objetivos     = importarObjetivos();

        notificar(onProgreso, "Catálogos: importando sectores (7/8)");
        int sectores      = importarSectoresProducto();

        notificar(onProgreso, "Catálogos: importando órganos (8/8)");
        int organos       = importarOrganos();

        log.info("=== FIN catálogos: finalidades={} instrumentos={} beneficiarios={} actividades={} reglamentos={} objetivos={} sectores={} organos={}",
                finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);
        return new ResultadoCatalogos(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);
    }

    private void notificar(Consumer<String> onProgreso, String mensaje) {
        if (onProgreso != null) onProgreso.accept(mensaje);
    }

    private int importarFinalidades() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlano("/finalidades");
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
        sectorProductoRepo.deleteAll();
        List<CatSectorProducto> entidades = items.stream()
                .map(i -> CatSectorProducto.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList();
        sectorProductoRepo.saveAll(entidades);
        log.info("SectoresProducto importados: {}", entidades.size());
        return entidades.size();
    }

    private int importarOrganos() {
        organoRepo.deleteAll();
        int total = 0;
        for (String tipo : List.of("C", "A", "L", "O")) {
            List<BdnsCatalogoClient.OrganoItem> items = bdnsCatalogoClient.fetchOrganos(tipo);
            List<CatOrgano> entidades = items.stream()
                    .map(i -> CatOrgano.builder()
                            .id(i.id())
                            .descripcion(i.descripcion())
                            .parentId(i.parentId())
                            .tipoAdmin(i.tipoAdmin())
                            .build())
                    .toList();
            organoRepo.saveAll(entidades);
            total += entidades.size();
            log.info("Organos tipo={} importados: {}", tipo, entidades.size());
        }
        return total;
    }

    /** Devuelve conteos actuales de todas las tablas cat_*. */
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
