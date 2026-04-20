# PROMPT BACK — Parte 2: FiltrosBdns + BdnsFiltrosBuilder

## INSTRUCCIONES PARA EL AGENTE
Lee este archivo completo ANTES de escribir una sola línea de código.
Respeta el orden de los pasos numerados.
NO modifiques: CatalogosBdnsService.java, MotorMatchingService.java, RateLimitService.java.
Al finalizar, ejecuta los tests y reporta el resultado.

## Contexto
- Repo: backend-Syntia
- Rama: feature/filtros-bdns-completos
- Prerequisito: BACK_P1 completado (tablas existentes, logs de sincronizacion OK)
- Archivos a MODIFICAR: model/FiltrosBdns.java, service/BdnsFiltrosBuilder.java
- Archivos a CREAR: src/test/java/.../service/BdnsFiltrosBuilderTest.java

---

## Paso 1 — Reemplazar FiltrosBdns.java (19 campos completos)

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FiltrosBdns {

    // PAGINACION Y ORDEN
    @Builder.Default private Integer page     = 0;
    @Builder.Default private Integer pageSize = 15;
    @Builder.Default private String  vpd      = "GE";
    private String  order;
    private String  direccion;         // asc | desc

    // TEXTO LIBRE
    private String  descripcion;
    private Integer descripcionTipoBusqueda;  // 0=exacta 1=todas 2=alguna
    private String  numeroConvocatoria;
    private String  ayudaEstado;

    // FLAGS
    private Boolean mrr;
    private Boolean contribucion;

    // RANGO TEMPORAL
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;

    // TIPO ADMINISTRACION
    private String tipoAdministracion;   // C=Estado A=Autonomica L=Local O=Otro

    // IDs DE CATALOGO — poblar desde CatalogosBdnsService
    private List<Integer> regiones;
    private List<Integer> organos;
    private List<Integer> instrumentos;
    private Integer       finalidad;
    private List<Integer> tiposBeneficiario;
}
```

---

## Paso 2 — Reemplazar BdnsFiltrosBuilder.java

```java
@Component @RequiredArgsConstructor @Slf4j
public class BdnsFiltrosBuilder {

    private static final String BDNS_BASE =
        "https://www.infosubvenciones.es/bdnstrans/api";

    private final CatalogosBdnsService catalogos;

    /**
     * Construye FiltrosBdns desde proyecto+perfil resolviendo texto a IDs.
     * TODA la logica de normalizacion vive aqui, nunca en controller/.
     */
    public FiltrosBdns fromProyectoPerfil(Proyecto proyecto, Perfil perfil) {
        String ubicacion = coalesce(
            proyecto != null ? proyecto.getUbicacion() : null,
            perfil   != null ? perfil.getUbicacion()   : null
        );
        String sector = coalesce(
            proyecto != null ? proyecto.getSector() : null,
            perfil   != null ? perfil.getSector()   : null
        );

        List<Integer> regionIds = catalogos.resolverRegionIds(ubicacion);
        Integer finalidadId    = catalogos.resolverFinalidadId(sector);
        String tipoAdmon       = inferirTipoAdmon(ubicacion, regionIds);

        log.debug("[FILTROS] ubicacion='{}' regiones={} sector='{}' finalidad={}",
            ubicacion, regionIds, sector, finalidadId);

        return FiltrosBdns.builder()
            .pageSize(15)
            .descripcionTipoBusqueda(1)
            .regiones(regionIds.isEmpty() ? null : regionIds)
            .finalidad(finalidadId)
            .tipoAdministracion(tipoAdmon)
            .build();
    }

    /**
     * Construye URL completa para GET /convocatorias/busqueda.
     * Solo incluye parametros no nulos/vacios.
     */
    public String buildUrl(FiltrosBdns f) {
        UriComponentsBuilder b = UriComponentsBuilder
            .fromHttpUrl(BDNS_BASE + "/convocatorias/busqueda")
            .queryParam("vpd",      f.getVpd())
            .queryParam("page",     f.getPage())
            .queryParam("pageSize", f.getPageSize());

        if (hasText(f.getDescripcion()))
            b.queryParam("descripcion", f.getDescripcion());
        if (f.getDescripcionTipoBusqueda() != null)
            b.queryParam("descripcionTipoBusqueda", f.getDescripcionTipoBusqueda());
        if (hasText(f.getNumeroConvocatoria()))
            b.queryParam("numeroConvocatoria", f.getNumeroConvocatoria());
        if (hasText(f.getAyudaEstado()))
            b.queryParam("ayudaEstado", f.getAyudaEstado());
        if (Boolean.TRUE.equals(f.getMrr()))
            b.queryParam("mrr", true);
        if (Boolean.TRUE.equals(f.getContribucion()))
            b.queryParam("contribucion", true);
        if (f.getFechaDesde() != null)
            b.queryParam("fechaDesde", f.getFechaDesde().toString());
        if (f.getFechaHasta() != null)
            b.queryParam("fechaHasta", f.getFechaHasta().toString());
        if (hasText(f.getTipoAdministracion()))
            b.queryParam("tipoAdministracion", f.getTipoAdministracion());
        if (notEmpty(f.getRegiones()))
            b.queryParam("regiones", joinIds(f.getRegiones()));
        if (notEmpty(f.getOrganos()))
            b.queryParam("organos", joinIds(f.getOrganos()));
        if (notEmpty(f.getInstrumentos()))
            b.queryParam("instrumentos", joinIds(f.getInstrumentos()));
        if (f.getFinalidad() != null)
            b.queryParam("finalidad", f.getFinalidad());
        if (notEmpty(f.getTiposBeneficiario()))
            b.queryParam("tiposBeneficiario", joinIds(f.getTiposBeneficiario()));
        if (hasText(f.getOrder()))
            b.queryParam("order", f.getOrder());
        if (hasText(f.getDireccion()))
            b.queryParam("direccion", f.getDireccion());

        String url = b.toUriString();
        log.debug("[FILTROS] URL: {}", url);
        return url;
    }

    private String inferirTipoAdmon(String ubicacion, List<Integer> regionIds) {
        if (ubicacion == null || ubicacion.isBlank()) return null;
        String u = ubicacion.toLowerCase();
        if (u.contains("nacional") || u.contains("estado")) return "C";
        if (!regionIds.isEmpty()) return "A";
        return null;
    }

    private String joinIds(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
    private boolean hasText(String s)   { return s != null && !s.isBlank(); }
    private boolean notEmpty(List<?> l) { return l != null && !l.isEmpty(); }
    private String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
```

---

## Paso 3 — Test unitario BdnsFiltrosBuilderTest.java

```java
@SpringBootTest
class BdnsFiltrosBuilderTest {

    @Autowired BdnsFiltrosBuilder builder;

    @Test
    void buildUrl_conFiltrosCompletos_generaUrlCorrecta() {
        FiltrosBdns f = FiltrosBdns.builder()
            .regiones(List.of(55, 9))
            .finalidad(14)
            .tipoAdministracion("A")
            .descripcion("innovacion")
            .descripcionTipoBusqueda(1)
            .fechaDesde(LocalDate.of(2026, 1, 1))
            .mrr(true)
            .build();

        String url = builder.buildUrl(f);

        assertThat(url).contains("vpd=GE");
        assertThat(url).contains("finalidad=14");
        assertThat(url).contains("tipoAdministracion=A");
        assertThat(url).contains("mrr=true");
        assertThat(url).contains("fechaDesde=2026-01-01");
    }

    @Test
    void buildUrl_camposNulos_noApareceEnUrl() {
        FiltrosBdns f = FiltrosBdns.builder().build();
        String url = builder.buildUrl(f);

        assertThat(url).doesNotContain("regiones");
        assertThat(url).doesNotContain("finalidad");
        assertThat(url).doesNotContain("mrr");
        assertThat(url).contains("vpd=GE");
        assertThat(url).contains("page=0");
    }
}
```

---

## Verificacion

```bash
mvn compile
mvn test -Dtest=BdnsFiltrosBuilderTest
# Ambos tests deben pasar en verde
# En logs buscar: "[FILTROS] URL: https://www.infosubvenciones.es/bdnstrans/api/convocatorias/busqueda?vpd=GE&..."
```

PASA A BACK_P3 solo cuando los 2 tests pasen.
