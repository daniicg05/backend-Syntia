package com.syntia.ai.service;

import com.syntia.ai.model.*;
import com.syntia.ai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Fase 2 del ETL BDNS: construye las tablas de índice catálogo ↔ numero_convocatoria.
 * Para cada ID de catálogo, pagina el endpoint de búsqueda y guarda las asociaciones.
 * Cada save() corre en su propia transacción (sin @Transactional en el método largo
 * para evitar mantener una transacción abierta durante horas).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndiceConvocatoriaService {

    private static final int DELAY_MS = 300;

    private final BdnsCatalogoClient bdnsCatalogoClient;

    private final CatFinalidadRepository finalidadRepo;
    private final CatInstrumentoRepository instrumentoRepo;
    private final CatBeneficiarioRepository beneficiarioRepo;
    private final CatOrganoRepository organoRepo;
    private final CatActividadRepository actividadRepo;
    private final CatReglamentoRepository reglamentoRepo;
    private final CatObjetivoRepository objetivoRepo;
    private final CatSectorProductoRepository sectorProductoRepo;

    private final IdxConvocatoriaFinalidadRepository idxFinalidadRepo;
    private final IdxConvocatoriaInstrumentoRepository idxInstrumentoRepo;
    private final IdxConvocatoriaBeneficiarioRepository idxBeneficiarioRepo;
    private final IdxConvocatoriaOrganoRepository idxOrganoRepo;
    private final IdxConvocatoriaTipoAdminRepository idxTipoAdminRepo;
    private final IdxConvocatoriaActividadRepository idxActividadRepo;
    private final IdxConvocatoriaReglamentoRepository idxReglamentoRepo;
    private final IdxConvocatoriaObjetivoRepository idxObjetivoRepo;
    private final IdxConvocatoriaSectorProductoRepository idxSectorProductoRepo;

    public ResultadoIndices construirTodos(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        log.info("=== INICIO construcción índices BDNS ===");
        onProgreso.accept("Finalidades (1/9)");
        int finalidades   = construirIndiceFinalidades(cancelado);
        onProgreso.accept("Instrumentos (2/9)");
        int instrumentos  = cancelado.get() ? 0 : construirIndiceInstrumentos(cancelado);
        onProgreso.accept("Beneficiarios (3/9)");
        int beneficiarios = cancelado.get() ? 0 : construirIndiceBeneficiarios(cancelado);
        onProgreso.accept("Órganos (4/9)");
        int organos       = cancelado.get() ? 0 : construirIndiceOrganos(cancelado);
        onProgreso.accept("Tipos administración (5/9)");
        int tiposAdmin    = cancelado.get() ? 0 : construirIndiceTiposAdmin(cancelado);
        onProgreso.accept("Actividades (6/9)");
        int actividades   = cancelado.get() ? 0 : construirIndiceActividades(cancelado);
        onProgreso.accept("Reglamentos (7/9)");
        int reglamentos   = cancelado.get() ? 0 : construirIndiceReglamentos(cancelado);
        onProgreso.accept("Objetivos (8/9)");
        int objetivos     = cancelado.get() ? 0 : construirIndiceObjetivos(cancelado);
        onProgreso.accept("Sectores/Productos (9/9)");
        int sectores      = cancelado.get() ? 0 : construirIndiceSectoresProducto(cancelado);
        log.info("=== FIN índices: finalidades={} instrumentos={} beneficiarios={} organos={} tiposAdmin={} actividades={} reglamentos={} objetivos={} sectores={}",
                finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        return new ResultadoIndices(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
    }

    private int construirIndiceFinalidades(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatFinalidad> cats = finalidadRepo.findAll();
        log.info("Construyendo índice finalidades para {} IDs", cats.size());
        for (CatFinalidad cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("finalidad", String.valueOf(cat.getId()), numeros -> {
                for (String num : numeros) {
                    if (!idxFinalidadRepo.existsByNumeroConvocatoriaAndFinalidadId(num, cat.getId())) {
                        idxFinalidadRepo.save(IdxConvocatoriaFinalidad.builder()
                                .numeroConvocatoria(num).finalidadId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            Thread.sleep(DELAY_MS);
        }
        log.info("Índice finalidades: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceInstrumentos(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatInstrumento> cats = instrumentoRepo.findAll();
        log.info("Construyendo índice instrumentos para {} IDs", cats.size());
        for (CatInstrumento cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("instrumentos", String.valueOf(cat.getId()), numeros -> {
                for (String num : numeros) {
                    if (!idxInstrumentoRepo.existsByNumeroConvocatoriaAndInstrumentoId(num, cat.getId())) {
                        idxInstrumentoRepo.save(IdxConvocatoriaInstrumento.builder()
                                .numeroConvocatoria(num).instrumentoId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            Thread.sleep(DELAY_MS);
        }
        log.info("Índice instrumentos: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceBeneficiarios(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatBeneficiario> cats = beneficiarioRepo.findAll();
        log.info("Construyendo índice beneficiarios para {} IDs", cats.size());
        for (CatBeneficiario cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("tiposBeneficiario", String.valueOf(cat.getId()), numeros -> {
                for (String num : numeros) {
                    if (!idxBeneficiarioRepo.existsByNumeroConvocatoriaAndBeneficiarioId(num, cat.getId())) {
                        idxBeneficiarioRepo.save(IdxConvocatoriaBeneficiario.builder()
                                .numeroConvocatoria(num).beneficiarioId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            Thread.sleep(DELAY_MS);
        }
        log.info("Índice beneficiarios: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceOrganos(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatOrgano> cats = organoRepo.findAll();
        log.info("Construyendo índice organos para {} IDs", cats.size());
        for (CatOrgano cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("organos", String.valueOf(cat.getId()), numeros -> {
                for (String num : numeros) {
                    if (!idxOrganoRepo.existsByNumeroConvocatoriaAndOrganoId(num, cat.getId())) {
                        idxOrganoRepo.save(IdxConvocatoriaOrgano.builder()
                                .numeroConvocatoria(num)
                                .organoId(cat.getId())
                                .tipoAdmin(cat.getTipoAdmin())
                                .build());
                        total.incrementAndGet();
                    }
                }
            });
            Thread.sleep(DELAY_MS);
        }
        log.info("Índice organos: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceTiposAdmin(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        for (String tipo : List.of("C", "A", "L", "O")) {
            if (cancelado.get()) break;
            paginarYGuardar("tipoAdministracion", tipo, numeros -> {
                for (String num : numeros) {
                    if (!idxTipoAdminRepo.existsByNumeroConvocatoriaAndTipoAdmin(num, tipo)) {
                        idxTipoAdminRepo.save(IdxConvocatoriaTipoAdmin.builder()
                                .numeroConvocatoria(num).tipoAdmin(tipo).build());
                        total.incrementAndGet();
                    }
                }
            });
            Thread.sleep(DELAY_MS);
        }
        log.info("Índice tiposAdmin: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceActividades(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatActividad> cats = actividadRepo.findAll();
        log.info("Construyendo índice actividades para {} IDs", cats.size());
        for (CatActividad cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("actividades", String.valueOf(cat.getId()), numeros -> {
                for (String num : numeros) {
                    if (!idxActividadRepo.existsByNumeroConvocatoriaAndActividadId(num, cat.getId())) {
                        idxActividadRepo.save(IdxConvocatoriaActividad.builder()
                                .numeroConvocatoria(num).actividadId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            Thread.sleep(DELAY_MS);
        }
        log.info("Índice actividades: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceReglamentos(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatReglamento> cats = reglamentoRepo.findAll();
        log.info("Construyendo índice reglamentos para {} IDs", cats.size());
        for (CatReglamento cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("reglamentos", String.valueOf(cat.getId()), numeros -> {
                for (String num : numeros) {
                    if (!idxReglamentoRepo.existsByNumeroConvocatoriaAndReglamentoId(num, cat.getId())) {
                        idxReglamentoRepo.save(IdxConvocatoriaReglamento.builder()
                                .numeroConvocatoria(num).reglamentoId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            Thread.sleep(DELAY_MS);
        }
        log.info("Índice reglamentos: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceObjetivos(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatObjetivo> cats = objetivoRepo.findAll();
        log.info("Construyendo índice objetivos para {} IDs", cats.size());
        for (CatObjetivo cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("objetivos", String.valueOf(cat.getId()), numeros -> {
                for (String num : numeros) {
                    if (!idxObjetivoRepo.existsByNumeroConvocatoriaAndObjetivoId(num, cat.getId())) {
                        idxObjetivoRepo.save(IdxConvocatoriaObjetivo.builder()
                                .numeroConvocatoria(num).objetivoId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            Thread.sleep(DELAY_MS);
        }
        log.info("Índice objetivos: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceSectoresProducto(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatSectorProducto> cats = sectorProductoRepo.findAll();
        log.info("Construyendo índice sectores/productos para {} IDs", cats.size());
        for (CatSectorProducto cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("sectores", String.valueOf(cat.getId()), numeros -> {
                for (String num : numeros) {
                    if (!idxSectorProductoRepo.existsByNumeroConvocatoriaAndSectorProductoId(num, cat.getId())) {
                        idxSectorProductoRepo.save(IdxConvocatoriaSectorProducto.builder()
                                .numeroConvocatoria(num).sectorProductoId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            Thread.sleep(DELAY_MS);
        }
        log.info("Índice sectores/productos: {} registros", total.get());
        return total.get();
    }

    /** Devuelve conteos actuales de todas las tablas idx_*. */
    public ConteoIndices contarTodos() {
        return new ConteoIndices(
                idxFinalidadRepo.count(),
                idxInstrumentoRepo.count(),
                idxBeneficiarioRepo.count(),
                idxOrganoRepo.count(),
                idxTipoAdminRepo.count(),
                idxActividadRepo.count(),
                idxReglamentoRepo.count(),
                idxObjetivoRepo.count(),
                idxSectorProductoRepo.count()
        );
    }

    public record ConteoIndices(long finalidades, long instrumentos, long beneficiarios,
                                long organos, long tiposAdmin, long actividades,
                                long reglamentos, long objetivos, long sectores) {}

    @FunctionalInterface
    interface NumerosConsumer { void accept(List<String> numeros); }

    private void paginarYGuardar(String param, String valor, NumerosConsumer consumer) {
        int pagina = 0;
        while (true) {
            BdnsCatalogoClient.PaginaIndice pag = bdnsCatalogoClient.buscarPorFiltro(param, valor, pagina);
            if (!pag.numerosConvocatoria().isEmpty()) {
                consumer.accept(pag.numerosConvocatoria());
            }
            if (pag.esUltima()) break;
            pagina++;
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    public record ResultadoIndices(int finalidades, int instrumentos, int beneficiarios,
                                   int organos, int tiposAdmin, int actividades,
                                   int reglamentos, int objetivos, int sectores) {}
}
