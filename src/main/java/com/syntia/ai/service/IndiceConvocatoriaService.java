package com.syntia.ai.service;

import com.syntia.ai.model.*;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private static final String EJE_SYNC_INDICES_DETALLE = "INDICES_BDNS_DETALLE";

    @Value("${bdns.indices.detalle.page-size:50}")
    private int detallePageSize;

    @Value("${bdns.indices.detalle.limite-convocatorias:5000}")
    private int detalleLimiteConvocatorias;

    @Value("${bdns.indices.detalle.delay-ms:150}")
    private long detalleDelayMs;

    private final BdnsCatalogoClient bdnsCatalogoClient;
    private final BdnsClientService bdnsClientService;
    private final SyncStateRepository syncStateRepo;

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
        return construirTodos(onProgreso, cancelado, null);
    }

    public ResultadoIndices construirTodos(Consumer<String> onProgreso, AtomicBoolean cancelado, Integer limiteConvocatorias) throws InterruptedException {
        return construirPorDetallePaginado(onProgreso, cancelado, limiteConvocatorias);
    }

    public ResultadoIndices construirPorCatalogos(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        log.info("=== INICIO construcción índices BDNS ===");
        onProgreso.accept("Finalidades (1/9)");
        int finalidades   = construirIndiceFinalidades(onProgreso, cancelado);
        onProgreso.accept("Instrumentos (2/9)");
        int instrumentos  = cancelado.get() ? 0 : construirIndiceInstrumentos(onProgreso, cancelado);
        onProgreso.accept("Beneficiarios (3/9)");
        int beneficiarios = cancelado.get() ? 0 : construirIndiceBeneficiarios(onProgreso, cancelado);
        onProgreso.accept("Órganos (4/9)");
        int organos       = cancelado.get() ? 0 : construirIndiceOrganos(onProgreso, cancelado);
        onProgreso.accept("Tipos administración (5/9)");
        int tiposAdmin    = cancelado.get() ? 0 : construirIndiceTiposAdmin(onProgreso, cancelado);
        onProgreso.accept("Actividades (6/9)");
        int actividades   = cancelado.get() ? 0 : construirIndiceActividades(onProgreso, cancelado);
        onProgreso.accept("Reglamentos (7/9)");
        int reglamentos   = cancelado.get() ? 0 : construirIndiceReglamentos(onProgreso, cancelado);
        onProgreso.accept("Objetivos (8/9)");
        int objetivos     = cancelado.get() ? 0 : construirIndiceObjetivos(onProgreso, cancelado);
        onProgreso.accept("Sectores/Productos (9/9)");
        int sectores      = cancelado.get() ? 0 : construirIndiceSectoresProducto(onProgreso, cancelado);
        log.info("=== FIN índices: finalidades={} instrumentos={} beneficiarios={} organos={} tiposAdmin={} actividades={} reglamentos={} objetivos={} sectores={}",
                finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
        return new ResultadoIndices(finalidades, instrumentos, beneficiarios, organos, tiposAdmin, actividades, reglamentos, objetivos, sectores);
    }

    private ResultadoIndices construirPorDetallePaginado(Consumer<String> onProgreso, AtomicBoolean cancelado, Integer limiteConvocatorias) throws InterruptedException {
        int pageSize = Math.max(1, Math.min(detallePageSize, 50));
        int limiteBase = limiteConvocatorias != null ? limiteConvocatorias : detalleLimiteConvocatorias;
        int limite = Math.max(1, limiteBase);

        notificar(onProgreso, "Preparando estado de reanudacion...");
        SyncState syncState = syncStateRepo.findByEje(EJE_SYNC_INDICES_DETALLE)
                .orElse(SyncState.builder()
                        .eje(EJE_SYNC_INDICES_DETALLE)
                        .ultimaPaginaOk(-1)
                        .estado(SyncState.Estado.PENDIENTE)
                        .build());

        int pagina = Math.max(0, syncState.getUltimaPaginaOk() + 1);
        syncState.setEstado(SyncState.Estado.EN_PROGRESO);
        if (syncState.getTsInicio() == null) syncState.setTsInicio(Instant.now());
        syncState.setTsUltimaCarga(Instant.now());
        syncState = syncStateRepo.save(syncState);

        notificar(onProgreso, "Cargando catalogos...");
        CatalogoMaps catalogos = cargarCatalogosNormalizados();
        AcumuladorIndices acumulado = new AcumuladorIndices();
        int procesadas = 0;

        log.info("=== INICIO indices BDNS por detalle: paginaInicio={} pageSize={} limite={} ===", pagina, pageSize, limite);
        notificar(onProgreso, "Consultando convocatorias recientes...");

        while (!cancelado.get() && procesadas < limite) {
            BdnsClientService.PaginaBdns paginaBdns = bdnsClientService.importarPorEje(null, null, pagina, pageSize);
            if (syncState.getTotalPaginas() == null && paginaBdns.totalElements() > 0) {
                syncState.setTotalPaginas((int) Math.ceil((double) paginaBdns.totalElements() / pageSize));
            }

            List<ConvocatoriaDTO> convocatorias = paginaBdns.items();
            if (convocatorias.isEmpty()) break;

            int enPagina = 0;
            int totalAntesPagina = acumulado.total();
            for (ConvocatoriaDTO convocatoria : convocatorias) {
                if (cancelado.get() || procesadas >= limite) break;

                String numero = convocatoria.getNumeroConvocatoria();
                if (numero == null || numero.isBlank()) continue;

                Map<String, Object> detalle = bdnsClientService.obtenerDetalleLive(numero);
                if (detalle != null) {
                    AcumuladorIndices insertados = indexarDetalle(numero, detalle, catalogos);
                    acumulado.sumar(insertados);
                }

                procesadas++;
                enPagina++;
                if (procesadas == 1 || procesadas % 25 == 0 || procesadas == limite) {
                    notificar(onProgreso, faseDetalle(pagina, enPagina, procesadas, limite, acumulado.total()));
                }

                if (detalleDelayMs > 0) Thread.sleep(detalleDelayMs);
            }

            syncState.setUltimaPaginaOk(pagina);
            syncState.setRegistrosNuevos(syncState.getRegistrosNuevos() + (acumulado.total() - totalAntesPagina));
            syncState.setTsUltimaCarga(Instant.now());
            syncStateRepo.save(syncState);

            if (paginaBdns.esUltima()) break;
            pagina++;
        }

        syncState.setEstado(cancelado.get() ? SyncState.Estado.EN_PROGRESO : SyncState.Estado.COMPLETADO);
        syncState.setTsUltimaCarga(Instant.now());
        syncStateRepo.save(syncState);

        log.info("=== FIN indices BDNS por detalle: procesadas={} insertados={} paginaOk={} cancelado={} ===",
                procesadas, acumulado.total(), syncState.getUltimaPaginaOk(), cancelado.get());

        return acumulado.toResultado();
    }

    private int construirIndiceFinalidades(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatFinalidad> cats = finalidadRepo.findAll();
        log.info("Construyendo índice finalidades para {} IDs", cats.size());
        int actual = 0;
        for (CatFinalidad cat : cats) {
            if (cancelado.get()) break;
            actual++;
            notificar(onProgreso, "Finalidades (1/9): " + actual + "/" + cats.size() + " - " + total.get() + " nuevos");
            paginarYGuardar(cancelado, "finalidad", String.valueOf(cat.getId()), numeros -> {
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

    private int construirIndiceInstrumentos(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatInstrumento> cats = instrumentoRepo.findAll();
        log.info("Construyendo índice instrumentos para {} IDs", cats.size());
        int actual = 0;
        for (CatInstrumento cat : cats) {
            if (cancelado.get()) break;
            actual++;
            notificar(onProgreso, "Instrumentos (2/9): " + actual + "/" + cats.size() + " - " + total.get() + " nuevos");
            paginarYGuardar(cancelado, "instrumentos", String.valueOf(cat.getId()), numeros -> {
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

    private int construirIndiceBeneficiarios(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatBeneficiario> cats = beneficiarioRepo.findAll();
        log.info("Construyendo índice beneficiarios para {} IDs", cats.size());
        int actual = 0;
        for (CatBeneficiario cat : cats) {
            if (cancelado.get()) break;
            actual++;
            notificar(onProgreso, "Beneficiarios (3/9): " + actual + "/" + cats.size() + " - " + total.get() + " nuevos");
            paginarYGuardar(cancelado, "tiposBeneficiario", String.valueOf(cat.getId()), numeros -> {
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

    private int construirIndiceOrganos(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatOrgano> cats = organoRepo.findAll();
        log.info("Construyendo índice organos para {} IDs", cats.size());
        int actual = 0;
        for (CatOrgano cat : cats) {
            if (cancelado.get()) break;
            actual++;
            notificar(onProgreso, "Organos (4/9): " + actual + "/" + cats.size() + " - " + total.get() + " nuevos");
            paginarYGuardar(cancelado, "organos", String.valueOf(cat.getId()), numeros -> {
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

    private int construirIndiceTiposAdmin(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<String> tipos = List.of("C", "A", "L", "O");
        int actual = 0;
        for (String tipo : tipos) {
            if (cancelado.get()) break;
            actual++;
            notificar(onProgreso, "Tipos administracion (5/9): " + actual + "/" + tipos.size() + " - " + total.get() + " nuevos");
            paginarYGuardar(cancelado, "tipoAdministracion", tipo, numeros -> {
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

    private int construirIndiceActividades(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatActividad> cats = actividadRepo.findAll();
        log.info("Construyendo índice actividades para {} IDs", cats.size());
        int actual = 0;
        for (CatActividad cat : cats) {
            if (cancelado.get()) break;
            actual++;
            notificar(onProgreso, "Actividades (6/9): " + actual + "/" + cats.size() + " - " + total.get() + " nuevos");
            paginarYGuardar(cancelado, "actividades", String.valueOf(cat.getId()), numeros -> {
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

    private int construirIndiceReglamentos(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatReglamento> cats = reglamentoRepo.findAll();
        log.info("Construyendo índice reglamentos para {} IDs", cats.size());
        int actual = 0;
        for (CatReglamento cat : cats) {
            if (cancelado.get()) break;
            actual++;
            notificar(onProgreso, "Reglamentos (7/9): " + actual + "/" + cats.size() + " - " + total.get() + " nuevos");
            paginarYGuardar(cancelado, "reglamentos", String.valueOf(cat.getId()), numeros -> {
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

    private int construirIndiceObjetivos(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatObjetivo> cats = objetivoRepo.findAll();
        log.info("Construyendo índice objetivos para {} IDs", cats.size());
        int actual = 0;
        for (CatObjetivo cat : cats) {
            if (cancelado.get()) break;
            actual++;
            notificar(onProgreso, "Objetivos (8/9): " + actual + "/" + cats.size() + " - " + total.get() + " nuevos");
            paginarYGuardar(cancelado, "objetivos", String.valueOf(cat.getId()), numeros -> {
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

    private int construirIndiceSectoresProducto(Consumer<String> onProgreso, AtomicBoolean cancelado) throws InterruptedException {
        AtomicInteger total = new AtomicInteger();
        List<CatSectorProducto> cats = sectorProductoRepo.findAll();
        log.info("Construyendo índice sectores/productos para {} IDs", cats.size());
        int actual = 0;
        for (CatSectorProducto cat : cats) {
            if (cancelado.get()) break;
            actual++;
            notificar(onProgreso, "Sectores/Productos (9/9): " + actual + "/" + cats.size() + " - " + total.get() + " nuevos");
            paginarYGuardar(cancelado, "sectores", String.valueOf(cat.getId()), numeros -> {
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

    private AcumuladorIndices indexarDetalle(String numeroConvocatoria, Map<String, Object> detalle, CatalogoMaps catalogos) {
        AcumuladorIndices res = new AcumuladorIndices();

        Integer finalidadId = catalogos.finalidades.get(normalizar(stringField(detalle, "descripcionFinalidad")));
        if (finalidadId != null && !idxFinalidadRepo.existsByNumeroConvocatoriaAndFinalidadId(numeroConvocatoria, finalidadId)) {
            idxFinalidadRepo.save(IdxConvocatoriaFinalidad.builder().numeroConvocatoria(numeroConvocatoria).finalidadId(finalidadId).build());
            res.finalidades++;
        }

        for (String descripcion : descripciones(detalle.get("instrumentos"))) {
            Integer id = catalogos.instrumentos.get(normalizar(descripcion));
            if (id != null && !idxInstrumentoRepo.existsByNumeroConvocatoriaAndInstrumentoId(numeroConvocatoria, id)) {
                idxInstrumentoRepo.save(IdxConvocatoriaInstrumento.builder().numeroConvocatoria(numeroConvocatoria).instrumentoId(id).build());
                res.instrumentos++;
            }
        }

        for (String descripcion : descripciones(detalle.get("tiposBeneficiarios"))) {
            Integer id = catalogos.beneficiarios.get(normalizar(descripcion));
            if (id != null && !idxBeneficiarioRepo.existsByNumeroConvocatoriaAndBeneficiarioId(numeroConvocatoria, id)) {
                idxBeneficiarioRepo.save(IdxConvocatoriaBeneficiario.builder().numeroConvocatoria(numeroConvocatoria).beneficiarioId(id).build());
                res.beneficiarios++;
            }
        }

        Object organoObj = detalle.get("organo");
        if (organoObj instanceof Map<?, ?> organo) {
            String tipoAdmin = tipoAdmin(stringFromMap(organo, "nivel1"));
            if (tipoAdmin != null && !idxTipoAdminRepo.existsByNumeroConvocatoriaAndTipoAdmin(numeroConvocatoria, tipoAdmin)) {
                idxTipoAdminRepo.save(IdxConvocatoriaTipoAdmin.builder().numeroConvocatoria(numeroConvocatoria).tipoAdmin(tipoAdmin).build());
                res.tiposAdmin++;
            }

            Integer organoId = resolverOrgano(catalogos, tipoAdmin,
                    stringFromMap(organo, "nivel3"),
                    stringFromMap(organo, "nivel2"),
                    stringFromMap(organo, "nivel1"));
            if (organoId != null && !idxOrganoRepo.existsByNumeroConvocatoriaAndOrganoId(numeroConvocatoria, organoId)) {
                idxOrganoRepo.save(IdxConvocatoriaOrgano.builder()
                        .numeroConvocatoria(numeroConvocatoria).organoId(organoId).tipoAdmin(tipoAdmin).build());
                res.organos++;
            }
        }

        for (String descripcion : descripciones(detalle.get("sectores"))) {
            Integer id = catalogos.actividades.get(normalizar(descripcion));
            if (id != null && !idxActividadRepo.existsByNumeroConvocatoriaAndActividadId(numeroConvocatoria, id)) {
                idxActividadRepo.save(IdxConvocatoriaActividad.builder().numeroConvocatoria(numeroConvocatoria).actividadId(id).build());
                res.actividades++;
            }
        }

        for (String descripcion : extraerReglamentosDesdeDetalle(detalle)) {
            Integer id = catalogos.reglamentos.get(normalizar(descripcion));
            if (id != null && !idxReglamentoRepo.existsByNumeroConvocatoriaAndReglamentoId(numeroConvocatoria, id)) {
                idxReglamentoRepo.save(IdxConvocatoriaReglamento.builder().numeroConvocatoria(numeroConvocatoria).reglamentoId(id).build());
                res.reglamentos++;
            }
        }

        for (String descripcion : descripciones(detalle.get("objetivos"))) {
            Integer id = catalogos.objetivos.get(normalizar(descripcion));
            if (id != null && !idxObjetivoRepo.existsByNumeroConvocatoriaAndObjetivoId(numeroConvocatoria, id)) {
                idxObjetivoRepo.save(IdxConvocatoriaObjetivo.builder().numeroConvocatoria(numeroConvocatoria).objetivoId(id).build());
                res.objetivos++;
            }
        }

        for (String descripcion : descripciones(detalle.get("sectoresProductos"))) {
            Integer id = catalogos.sectores.get(normalizar(descripcion));
            if (id != null && !idxSectorProductoRepo.existsByNumeroConvocatoriaAndSectorProductoId(numeroConvocatoria, id)) {
                idxSectorProductoRepo.save(IdxConvocatoriaSectorProducto.builder().numeroConvocatoria(numeroConvocatoria).sectorProductoId(id).build());
                res.sectores++;
            }
        }

        return res;
    }

    private CatalogoMaps cargarCatalogosNormalizados() {
        CatalogoMaps maps = new CatalogoMaps();
        finalidadRepo.findAll().forEach(c -> maps.finalidades.putIfAbsent(normalizar(c.getDescripcion()), c.getId()));
        instrumentoRepo.findAll().forEach(c -> maps.instrumentos.putIfAbsent(normalizar(c.getDescripcion()), c.getId()));
        beneficiarioRepo.findAll().forEach(c -> maps.beneficiarios.putIfAbsent(normalizar(c.getDescripcion()), c.getId()));
        actividadRepo.findAll().forEach(c -> maps.actividades.putIfAbsent(normalizar(c.getDescripcion()), c.getId()));
        reglamentoRepo.findAll().forEach(c -> maps.reglamentos.putIfAbsent(normalizar(c.getDescripcion()), c.getId()));
        objetivoRepo.findAll().forEach(c -> maps.objetivos.putIfAbsent(normalizar(c.getDescripcion()), c.getId()));
        sectorProductoRepo.findAll().forEach(c -> maps.sectores.putIfAbsent(normalizar(c.getDescripcion()), c.getId()));
        organoRepo.findAll().forEach(c -> {
            String key = normalizar(c.getDescripcion());
            maps.organos.putIfAbsent(key, c.getId());
            if (c.getTipoAdmin() != null) {
                maps.organosPorTipo.putIfAbsent(c.getTipoAdmin() + "|" + key, c.getId());
            }
        });
        return maps;
    }

    private Integer resolverOrgano(CatalogoMaps catalogos, String tipoAdmin, String... descripciones) {
        for (String descripcion : descripciones) {
            String key = normalizar(descripcion);
            if (key.isBlank()) continue;
            if (tipoAdmin != null) {
                Integer porTipo = catalogos.organosPorTipo.get(tipoAdmin + "|" + key);
                if (porTipo != null) return porTipo;
            }
            Integer general = catalogos.organos.get(key);
            if (general != null) return general;
        }
        return null;
    }

    private List<String> descripciones(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(this::descripcion).filter(Objects::nonNull).filter(s -> !s.isBlank()).toList();
        }
        String descripcion = descripcion(obj);
        return descripcion == null || descripcion.isBlank() ? List.of() : List.of(descripcion);
    }

    /**
     * Extrae una lista de descripciones que pueden representar reglamentos desde el Map de detalle.
     * Acepta varias claves y estructuras (singular/plural, String, List, Map) y devuelve la primera
     * lista encontrada no vacía.
     */
    @SuppressWarnings("unchecked")
    private List<String> extraerReglamentosDesdeDetalle(Map<String, Object> detalle) {
        if (detalle == null) return List.of();

        List<Object> candidates = List.of(
                detalle.get("reglamento"),
                detalle.get("reglamentos"),
                detalle.get("descripcionBasesReguladoras"),
                detalle.get("basesReguladoras"),
                detalle.get("descripcionBases")
        );

        for (Object cand : candidates) {
            if (cand == null) continue;
            List<String> out = descripciones(cand);
            if (!out.isEmpty()) return out;
        }

        // Fallback: intentar extraer texto del primer anuncio
        Object anunciosObj = detalle.get("anuncios");
        if (anunciosObj instanceof List<?> anList && !anList.isEmpty()) {
            Object first = anList.get(0);
            if (first instanceof Map<?, ?> am) {
                String texto = stringFromMap((Map<?, ?>) am, "texto");
                if (texto != null && !texto.isBlank()) {
                    return List.of(texto);
                }
            }
        }

        return List.of();
    }

    private String descripcion(Object obj) {
        if (obj instanceof String s) return s;
        if (obj instanceof Map<?, ?> map) return stringFromMap(map, "descripcion");
        return null;
    }

    private String stringField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    private String stringFromMap(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    private String tipoAdmin(String nivel1) {
        if (nivel1 == null) return null;
        return switch (normalizar(nivel1)) {
            case "ESTADO" -> "C";
            case "AUTONOMICA" -> "A";
            case "LOCAL" -> "L";
            case "OTROS" -> "O";
            default -> null;
        };
    }

    private String normalizar(String value) {
        if (value == null) return "";
        String limpio = value.replace('\u00A0', ' ').trim();
        limpio = Normalizer.normalize(limpio, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return limpio.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String faseDetalle(int pagina, int enPagina, int procesadas, int limite, int totalInsertados) {
        return "Convocatorias recientes (1/1): " + procesadas + "/" + limite
                + " - pagina " + pagina
                + " (" + enPagina + "/" + Math.max(1, detallePageSize) + ")"
                + " - " + totalInsertados + " nuevos";
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

    private void notificar(Consumer<String> onProgreso, String mensaje) {
        if (onProgreso != null) onProgreso.accept(mensaje);
    }

    private void paginarYGuardar(AtomicBoolean cancelado, String param, String valor, NumerosConsumer consumer) {
        int pagina = 0;
        while (!cancelado.get()) {
            BdnsCatalogoClient.PaginaIndice pag = bdnsCatalogoClient.buscarPorFiltro(param, valor, pagina);
            if (!pag.numerosConvocatoria().isEmpty()) {
                consumer.accept(pag.numerosConvocatoria());
            }
            if (pag.esUltima()) break;
            pagina++;
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    private static class CatalogoMaps {
        final Map<String, Integer> finalidades = new HashMap<>();
        final Map<String, Integer> instrumentos = new HashMap<>();
        final Map<String, Integer> beneficiarios = new HashMap<>();
        final Map<String, Integer> organos = new HashMap<>();
        final Map<String, Integer> organosPorTipo = new HashMap<>();
        final Map<String, Integer> actividades = new HashMap<>();
        final Map<String, Integer> reglamentos = new HashMap<>();
        final Map<String, Integer> objetivos = new HashMap<>();
        final Map<String, Integer> sectores = new HashMap<>();
    }

    private static class AcumuladorIndices {
        int finalidades;
        int instrumentos;
        int beneficiarios;
        int organos;
        int tiposAdmin;
        int actividades;
        int reglamentos;
        int objetivos;
        int sectores;

        void sumar(AcumuladorIndices other) {
            finalidades += other.finalidades;
            instrumentos += other.instrumentos;
            beneficiarios += other.beneficiarios;
            organos += other.organos;
            tiposAdmin += other.tiposAdmin;
            actividades += other.actividades;
            reglamentos += other.reglamentos;
            objetivos += other.objetivos;
            sectores += other.sectores;
        }

        int total() {
            return finalidades + instrumentos + beneficiarios + organos + tiposAdmin
                    + actividades + reglamentos + objetivos + sectores;
        }

        ResultadoIndices toResultado() {
            return new ResultadoIndices(finalidades, instrumentos, beneficiarios, organos, tiposAdmin,
                    actividades, reglamentos, objetivos, sectores);
        }
    }

    public record ResultadoIndices(int finalidades, int instrumentos, int beneficiarios,
                                   int organos, int tiposAdmin, int actividades,
                                   int reglamentos, int objetivos, int sectores) {}
}
