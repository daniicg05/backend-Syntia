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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Slf4j
@Service
public class CatalogoImportService {

    private static final long PAUSA_ENTRE_CATALOGOS_MS = 2_000L;

    private final BdnsCatalogoClient bdnsCatalogoClient;
    private final CatFinalidadRepository finalidadRepo;
    private final CatInstrumentoRepository instrumentoRepo;
    private final CatBeneficiarioRepository beneficiarioRepo;
    private final CatActividadRepository actividadRepo;
    private final CatReglamentoRepository reglamentoRepo;
    private final CatObjetivoRepository objetivoRepo;
    private final CatSectorProductoRepository sectorProductoRepo;
    private final CatOrganoRepository organoRepo;
    private CatalogoImportService self;

    public CatalogoImportService(BdnsCatalogoClient bdnsCatalogoClient,
                                 CatFinalidadRepository finalidadRepo,
                                 CatInstrumentoRepository instrumentoRepo,
                                 CatBeneficiarioRepository beneficiarioRepo,
                                 CatActividadRepository actividadRepo,
                                 CatReglamentoRepository reglamentoRepo,
                                 CatObjetivoRepository objetivoRepo,
                                 CatSectorProductoRepository sectorProductoRepo,
                                 CatOrganoRepository organoRepo) {
        this.bdnsCatalogoClient = bdnsCatalogoClient;
        this.finalidadRepo = finalidadRepo;
        this.instrumentoRepo = instrumentoRepo;
        this.beneficiarioRepo = beneficiarioRepo;
        this.actividadRepo = actividadRepo;
        this.reglamentoRepo = reglamentoRepo;
        this.objetivoRepo = objetivoRepo;
        this.sectorProductoRepo = sectorProductoRepo;
        this.organoRepo = organoRepo;
    }

    @Autowired
    void setSelf(@Lazy CatalogoImportService self) {
        this.self = self;
    }

    @Scheduled(cron = "0 0 3 * * SUN")
    public ResultadoCatalogos importarTodos() {
        return importarTodos(null, null);
    }

    public ResultadoCatalogos importarTodos(Consumer<String> onProgreso) {
        return importarTodos(onProgreso, null);
    }

    public ResultadoCatalogos importarTodos(Consumer<String> onProgreso, BooleanSupplier cancelado) {
        log.info("=== INICIO importacion catalogos BDNS ===");

        int finalidades = 0;
        int instrumentos = 0;
        int beneficiarios = 0;
        int actividades = 0;
        int reglamentos = 0;
        int objetivos = 0;
        int sectores = 0;
        int organos = 0;

        if (estaCancelado(cancelado)) return parcial(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);

        notificar(onProgreso, "Catalogos: importando finalidades (1/8)");
        finalidades = self.importarFinalidades();
        if (estaCancelado(cancelado)) return parcial(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);

        notificar(onProgreso, "Catalogos: importando instrumentos (2/8)");
        instrumentos = self.importarInstrumentos();
        if (estaCancelado(cancelado)) return parcial(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);

        notificar(onProgreso, "Catalogos: importando beneficiarios (3/8)");
        beneficiarios = self.importarBeneficiarios();
        if (estaCancelado(cancelado)) return parcial(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);

        notificar(onProgreso, "Catalogos: importando actividades (4/8)");
        actividades = self.importarActividades();
        if (estaCancelado(cancelado)) return parcial(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);

        notificar(onProgreso, "Catalogos: importando reglamentos (5/8)");
        reglamentos = self.importarReglamentos();
        if (estaCancelado(cancelado)) return parcial(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);

        notificar(onProgreso, "Catalogos: importando objetivos (6/8)");
        objetivos = self.importarObjetivos();
        if (estaCancelado(cancelado)) return parcial(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);

        notificar(onProgreso, "Catalogos: importando sectores (7/8)");
        sectores = self.importarSectoresProducto();
        if (estaCancelado(cancelado)) return parcial(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);

        notificar(onProgreso, "Catalogos: importando organos (8/8)");
        organos = self.importarOrganos();

        log.info("=== FIN catalogos: finalidades={} instrumentos={} beneficiarios={} actividades={} reglamentos={} objetivos={} sectores={} organos={}",
                finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);
        return new ResultadoCatalogos(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);
    }

    private ResultadoCatalogos parcial(int finalidades, int instrumentos, int beneficiarios,
                                       int actividades, int reglamentos, int objetivos,
                                       int sectores, int organos) {
        log.info("Importacion catalogos cancelada; se conserva resultado parcial");
        return new ResultadoCatalogos(finalidades, instrumentos, beneficiarios, actividades, reglamentos, objetivos, sectores, organos);
    }

    private boolean estaCancelado(BooleanSupplier cancelado) {
        return cancelado != null && cancelado.getAsBoolean();
    }

    private void notificar(Consumer<String> onProgreso, String mensaje) {
        if (onProgreso != null) onProgreso.accept(mensaje);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int importarFinalidades() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlanoConRetry("/finalidades");
        if (items.isEmpty()) {
            log.warn("Finalidades: API devolvio 0 registros, se mantienen datos actuales");
            return (int) finalidadRepo.count();
        }
        finalidadRepo.deleteAll();
        finalidadRepo.saveAll(items.stream()
                .map(i -> CatFinalidad.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList());
        log.info("Finalidades importadas: {}", items.size());
        pausa();
        return items.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int importarInstrumentos() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlanoConRetry("/instrumentos");
        if (items.isEmpty()) {
            log.warn("Instrumentos: API devolvio 0 registros, se mantienen datos actuales");
            return (int) instrumentoRepo.count();
        }
        instrumentoRepo.deleteAll();
        instrumentoRepo.saveAll(items.stream()
                .map(i -> CatInstrumento.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList());
        log.info("Instrumentos importados: {}", items.size());
        pausa();
        return items.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int importarBeneficiarios() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlanoConRetry("/beneficiarios");
        if (items.isEmpty()) {
            log.warn("Beneficiarios: API devolvio 0 registros, se mantienen datos actuales");
            return (int) beneficiarioRepo.count();
        }
        beneficiarioRepo.deleteAll();
        beneficiarioRepo.saveAll(items.stream()
                .map(i -> CatBeneficiario.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList());
        log.info("Beneficiarios importados: {}", items.size());
        pausa();
        return items.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int importarActividades() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlanoConRetry("/actividades");
        if (items.isEmpty()) {
            log.warn("Actividades: API devolvio 0 registros, se mantienen datos actuales");
            return (int) actividadRepo.count();
        }
        actividadRepo.deleteAll();
        actividadRepo.saveAll(items.stream()
                .map(i -> CatActividad.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList());
        log.info("Actividades importadas: {}", items.size());
        pausa();
        return items.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int importarReglamentos() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlanoConRetry("/reglamentos");
        if (items.isEmpty()) {
            log.warn("Reglamentos: API devolvio 0 registros, se mantienen datos actuales");
            return (int) reglamentoRepo.count();
        }
        reglamentoRepo.deleteAll();
        reglamentoRepo.saveAll(items.stream()
                .map(i -> CatReglamento.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList());
        log.info("Reglamentos importados: {}", items.size());
        pausa();
        return items.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int importarObjetivos() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlanoConRetry("/objetivos");
        if (items.isEmpty()) {
            log.warn("Objetivos: API devolvio 0 registros, se mantienen datos actuales");
            return (int) objetivoRepo.count();
        }
        objetivoRepo.deleteAll();
        objetivoRepo.saveAll(items.stream()
                .map(i -> CatObjetivo.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList());
        log.info("Objetivos importados: {}", items.size());
        pausa();
        return items.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int importarSectoresProducto() {
        List<BdnsCatalogoClient.CatItem> items = bdnsCatalogoClient.fetchPlanoConRetry("/sectores");
        if (items.isEmpty()) {
            log.warn("SectoresProducto: API devolvio 0 registros, se mantienen datos actuales");
            return (int) sectorProductoRepo.count();
        }
        sectorProductoRepo.deleteAll();
        sectorProductoRepo.saveAll(items.stream()
                .map(i -> CatSectorProducto.builder().id(i.id()).descripcion(i.descripcion()).build())
                .toList());
        log.info("SectoresProducto importados: {}", items.size());
        pausa();
        return items.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int importarOrganos() {
        List<CatOrgano> todosOrganos = new ArrayList<>();
        for (String tipo : List.of("C", "A", "L", "O")) {
            log.info("Descargando organos tipo={}...", tipo);
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
            pausa();
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

    private void pausa() {
        try {
            Thread.sleep(PAUSA_ENTRE_CATALOGOS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
