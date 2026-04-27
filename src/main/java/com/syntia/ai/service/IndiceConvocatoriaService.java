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
 * Fase 2 del ETL BDNS: construye las tablas de indice catalogo <-> numero_convocatoria.
 * Para cada ID de catalogo, pagina el endpoint de busqueda y guarda las asociaciones.
 *
 * La cancelacion se respeta en TRES niveles:
 *   1) Entre fases (finalidades -> instrumentos -> ...)
 *   2) Entre IDs de catalogo dentro de una fase
 *   3) Entre paginas dentro de una llamada paginada (paginarYGuardar)
 * Esto garantiza que pulsar "Pausar" detenga el job en pocos segundos
 * en vez de tener que esperar a terminar la fase actual entera.
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
        log.info("=== INICIO construccion indices BDNS ===");

        int finalidades = 0, instrumentos = 0, beneficiarios = 0, organos = 0,
            tiposAdmin = 0, actividades = 0, reglamentos = 0, objetivos = 0, sectores = 0;

        if (cancelado.get()) return resultado(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        onProgreso.accept("Finalidades (1/9)");
        finalidades = construirIndiceFinalidades(cancelado);

        if (cancelado.get()) return resultado(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        onProgreso.accept("Instrumentos (2/9)");
        instrumentos = construirIndiceInstrumentos(cancelado);

        if (cancelado.get()) return resultado(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        onProgreso.accept("Beneficiarios (3/9)");
        beneficiarios = construirIndiceBeneficiarios(cancelado);

        if (cancelado.get()) return resultado(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        onProgreso.accept("Organos (4/9)");
        organos = construirIndiceOrganos(cancelado);

        if (cancelado.get()) return resultado(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        onProgreso.accept("Tipos administracion (5/9)");
        tiposAdmin = construirIndiceTiposAdmin(cancelado);

        if (cancelado.get()) return resultado(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        onProgreso.accept("Actividades (6/9)");
        actividades = construirIndiceActividades(cancelado);

        if (cancelado.get()) return resultado(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        onProgreso.accept("Reglamentos (7/9)");
        reglamentos = construirIndiceReglamentos(cancelado);

        if (cancelado.get()) return resultado(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        onProgreso.accept("Objetivos (8/9)");
        objetivos = construirIndiceObjetivos(cancelado);

        if (cancelado.get()) return resultado(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        onProgreso.accept("Sectores/Productos (9/9)");
        sectores = construirIndiceSectoresProducto(cancelado);

        log.info("=== FIN indices: finalidades={} instrumentos={} beneficiarios={} organos={} tiposAdmin={} actividades={} reglamentos={} objetivos={} sectores={}",
                finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        return resultado(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
    }

    private ResultadoIndices resultado(int finalidades, int instrumentos, int beneficiarios,
                                       int organos, int tiposAdmin, int actividades,
                                       int reglamentos, int objetivos, int sectores) {
        return new ResultadoIndices(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
    }

    private int construirIndiceFinalidades(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatFinalidad> cats = finalidadRepo.findAll();
        log.info("Construyendo indice finalidades para {} IDs", cats.size());
        for (CatFinalidad cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("finalidad", String.valueOf(cat.getId()), cancelado, numeros -> {
                for (String num : numeros) {
                    if (cancelado.get()) return;
                    if (!idxFinalidadRepo.existsByNumeroConvocatoriaAndFinalidadId(num, cat.getId())) {
                        idxFinalidadRepo.save(IdxConvocatoriaFinalidad.builder()
                                .numeroConvocatoria(num).finalidadId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            if (cancelado.get()) break;
            Thread.sleep(DELAY_MS);
        }
        log.info("Indice finalidades: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceInstrumentos(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatInstrumento> cats = instrumentoRepo.findAll();
        log.info("Construyendo indice instrumentos para {} IDs", cats.size());
        for (CatInstrumento cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("instrumentos", String.valueOf(cat.getId()), cancelado, numeros -> {
                for (String num : numeros) {
                    if (cancelado.get()) return;
                    if (!idxInstrumentoRepo.existsByNumeroConvocatoriaAndInstrumentoId(num, cat.getId())) {
                        idxInstrumentoRepo.save(IdxConvocatoriaInstrumento.builder()
                                .numeroConvocatoria(num).instrumentoId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            if (cancelado.get()) break;
            Thread.sleep(DELAY_MS);
        }
        log.info("Indice instrumentos: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceBeneficiarios(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatBeneficiario> cats = beneficiarioRepo.findAll();
        log.info("Construyendo indice beneficiarios para {} IDs", cats.size());
        for (CatBeneficiario cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("tiposBeneficiario", String.valueOf(cat.getId()), cancelado, numeros -> {
                for (String num : numeros) {
                    if (cancelado.get()) return;
                    if (!idxBeneficiarioRepo.existsByNumeroConvocatoriaAndBeneficiarioId(num, cat.getId())) {
                        idxBeneficiarioRepo.save(IdxConvocatoriaBeneficiario.builder()
                                .numeroConvocatoria(num).beneficiarioId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            if (cancelado.get()) break;
            Thread.sleep(DELAY_MS);
        }
        log.info("Indice beneficiarios: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceOrganos(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatOrgano> cats = organoRepo.findAll();
        log.info("Construyendo indice organos para {} IDs", cats.size());
        for (CatOrgano cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("organos", String.valueOf(cat.getId()), cancelado, numeros -> {
                for (String num : numeros) {
                    if (cancelado.get()) return;
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
            if (cancelado.get()) break;
            Thread.sleep(DELAY_MS);
        }
        log.info("Indice organos: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceTiposAdmin(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        for (String tipo : List.of("C", "A", "L", "O")) {
            if (cancelado.get()) break;
            paginarYGuardar("tipoAdministracion", tipo, cancelado, numeros -> {
                for (String num : numeros) {
                    if (cancelado.get()) return;
                    if (!idxTipoAdminRepo.existsByNumeroConvocatoriaAndTipoAdmin(num, tipo)) {
                        idxTipoAdminRepo.save(IdxConvocatoriaTipoAdmin.builder()
                                .numeroConvocatoria(num).tipoAdmin(tipo).build());
                        total.incrementAndGet();
                    }
                }
            });
            if (cancelado.get()) break;
            Thread.sleep(DELAY_MS);
        }
        log.info("Indice tiposAdmin: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceActividades(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatActividad> cats = actividadRepo.findAll();
        log.info("Construyendo indice actividades para {} IDs", cats.size());
        for (CatActividad cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("actividades", String.valueOf(cat.getId()), cancelado, numeros -> {
                for (String num : numeros) {
                    if (cancelado.get()) return;
                    if (!idxActividadRepo.existsByNumeroConvocatoriaAndActividadId(num, cat.getId())) {
                        idxActividadRepo.save(IdxConvocatoriaActividad.builder()
                                .numeroConvocatoria(num).actividadId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            if (cancelado.get()) break;
            Thread.sleep(DELAY_MS);
        }
        log.info("Indice actividades: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceReglamentos(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatReglamento> cats = reglamentoRepo.findAll();
        log.info("Construyendo indice reglamentos para {} IDs", cats.size());
        for (CatReglamento cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("reglamentos", String.valueOf(cat.getId()), cancelado, numeros -> {
                for (String num : numeros) {
                    if (cancelado.get()) return;
                    if (!idxReglamentoRepo.existsByNumeroConvocatoriaAndReglamentoId(num, cat.getId())) {
                        idxReglamentoRepo.save(IdxConvocatoriaReglamento.builder()
                                .numeroConvocatoria(num).reglamentoId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            if (cancelado.get()) break;
            Thread.sleep(DELAY_MS);
        }
        log.info("Indice reglamentos: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceObjetivos(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatObjetivo> cats = objetivoRepo.findAll();
        log.info("Construyendo indice objetivos para {} IDs", cats.size());
        for (CatObjetivo cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("objetivos", String.valueOf(cat.getId()), cancelado, numeros -> {
                for (String num : numeros) {
                    if (cancelado.get()) return;
                    if (!idxObjetivoRepo.existsByNumeroConvocatoriaAndObjetivoId(num, cat.getId())) {
                        idxObjetivoRepo.save(IdxConvocatoriaObjetivo.builder()
                                .numeroConvocatoria(num).objetivoId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            if (cancelado.get()) break;
            Thread.sleep(DELAY_MS);
        }
        log.info("Indice objetivos: {} registros", total.get());
        return total.get();
    }

    private int construirIndiceSectoresProducto(AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatSectorProducto> cats = sectorProductoRepo.findAll();
        log.info("Construyendo indice sectores/productos para {} IDs", cats.size());
        for (CatSectorProducto cat : cats) {
            if (cancelado.get()) break;
            paginarYGuardar("sectores", String.valueOf(cat.getId()), cancelado, numeros -> {
                for (String num : numeros) {
                    if (cancelado.get()) return;
                    if (!idxSectorProductoRepo.existsByNumeroConvocatoriaAndSectorProductoId(num, cat.getId())) {
                        idxSectorProductoRepo.save(IdxConvocatoriaSectorProducto.builder()
                                .numeroConvocatoria(num).sectorProductoId(cat.getId()).build());
                        total.incrementAndGet();
                    }
                }
            });
            if (cancelado.get()) break;
            Thread.sleep(DELAY_MS);
        }
        log.info("Indice sectores/productos: {} registros", total.get());
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

    /**
     * Pagina el endpoint de filtros consultando 'cancelado' entre paginas.
     * Esto permite que un Pausar abortado a mitad de un catalogo grande
     * tarde como mucho 'DELAY_PAGINAS' ms (200) en surtir efecto.
     */
    private void paginarYGuardar(String param, String valor, AtomicBoolean cancelado, NumerosConsumer consumer) {
        int pagina = 0;
        while (true) {
            if (cancelado.get()) return;
            BdnsCatalogoClient.PaginaIndice pag = bdnsCatalogoClient.buscarPorFiltro(param, valor, pagina);
            if (cancelado.get()) return;
            if (!pag.numerosConvocatoria().isEmpty()) {
                consumer.accept(pag.numerosConvocatoria());
            }
            if (pag.esUltima()) break;
            pagina++;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public record ResultadoIndices(int finalidades, int instrumentos, int beneficiarios,
                                   int organos, int tiposAdmin, int actividades,
                                   int reglamentos, int objetivos, int sectores) {}
}
