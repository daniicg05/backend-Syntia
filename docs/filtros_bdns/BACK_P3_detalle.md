# PROMPT BACK — Parte 3: Controllers + DetalleDTO

## INSTRUCCIONES PARA EL AGENTE
Lee este archivo completo ANTES de escribir una sola línea de código.
Respeta el orden de los pasos numerados.
NO modifiques: MotorMatchingService.java, RateLimitService.java, OpenAiMatchingService.java.
Solo AÑADE metodos a BdnsClientService.java, no borres nada existente.
Al finalizar, ejecuta la verificacion curl y reporta cada respuesta.

## Contexto
- Repo: backend-Syntia
- Rama: feature/filtros-bdns-completos
- Prerequisito: BACK_P1 y BACK_P2 completados y verificados
- Archivos a CREAR: dto/ConvocatoriaDetalleDTO.java, dto/CatalogoItemDTO.java,
  controller/api/CatalogosController.java, controller/api/ConvocatoriaDetalleController.java
- Archivos a MODIFICAR (SOLO ANADIR): service/BdnsClientService.java

---

## Paso 1 — CatalogoItemDTO.java

```java
@Data @AllArgsConstructor @NoArgsConstructor
public class CatalogoItemDTO {
    private Integer id;
    private String  nombre;
}
```

---

## Paso 2 — ConvocatoriaDetalleDTO.java

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ConvocatoriaDetalleDTO {
    // Identificacion
    private String    idBdns;
    private String    numeroConvocatoria;
    private String    titulo;
    private String    tituloAlternativo;
    // Clasificacion
    private String    tipo;
    private String    ubicacion;
    private String    sector;
    private String    finalidad;
    private String    instrumento;
    // Organismo
    private String    nivel1;
    private String    nivel2;
    private String    nivel3;
    private String    fuente;
    // Contenido
    private String    objeto;
    private String    beneficiarios;
    private String    requisitos;
    private String    documentacion;
    // Financiero
    private String    dotacion;
    private String    ayudaEstado;
    private Boolean   mrr;
    private Boolean   contribucion;
    // Plazos
    private LocalDate fechaRecepcion;
    private LocalDate fechaFinSolicitud;
    private LocalDate fechaCierre;
    private String    plazoSolicitudes;
    // Procedimiento
    private String    procedimiento;
    private String    basesReguladoras;
    private String    urlOficial;
    // Datos IA Syntia (opcionales)
    private Integer       puntuacion;
    private String        explicacion;
    private String        guia;
    private LocalDateTime fechaAnalisis;
}
```

---

## Paso 3 — Anadir en BdnsClientService.java (NO borrar nada existente)

```java
@Cacheable(value = "bdns-detalle", key = "#idBdns")
public ConvocatoriaDetalleDTO obtenerDetalleCompleto(String idBdns) {
    String url = BDNS_BASE_URL + "/convocatorias/" + idBdns;
    try {
        String json = restClient.get().uri(url).retrieve().body(String.class);
        Map<String, Object> raw = new ObjectMapper().readValue(json, new TypeReference<>() {});

        String numConv = getString(raw, "numeroConvocatoria");
        String nivel1  = getString(raw, "nivel1");
        String nivel2  = getString(raw, "nivel2");
        String nivel3  = getString(raw, "nivel3");

        return ConvocatoriaDetalleDTO.builder()
            .idBdns(idBdns)
            .numeroConvocatoria(numConv)
            .titulo(coalesce(getString(raw, "descripcion"), getString(raw, "descripcionLeng")))
            .tituloAlternativo(getString(raw, "descripcionLeng"))
            .nivel1(nivel1).nivel2(nivel2).nivel3(nivel3)
            .tipo(normalizarNivel1(nivel1))
            .ubicacion("ESTADO".equals(nivel1) ? "Nacional" : nivel2)
            .fuente("BDNS - " + coalesce(nivel3, nivel2))
            .objeto(coalesce(getString(raw, "objeto"),
                    coalesce(getString(raw, "descripcionObjeto"), getString(raw, "finalidad"))))
            .beneficiarios(coalesce(getString(raw, "beneficiarios"), getString(raw, "tiposBeneficiarios")))
            .requisitos(coalesce(getString(raw, "requisitos"),
                        coalesce(getString(raw, "condicionesAcceso"), getString(raw, "requisitosParticipacion"))))
            .documentacion(coalesce(getString(raw, "documentacion"), getString(raw, "documentosRequeridos")))
            .dotacion(coalesce(getString(raw, "dotacion"),
                      coalesce(getString(raw, "presupuestoTotal"), getString(raw, "importeTotal"))))
            .ayudaEstado(getString(raw, "ayudaEstado"))
            .mrr(getBoolean(raw, "mrr"))
            .contribucion(getBoolean(raw, "contribucion"))
            .fechaRecepcion(parseDate(getString(raw, "fechaRecepcion")))
            .fechaFinSolicitud(parseDate(coalesce(
                getString(raw, "fechaFinSolicitud"), getString(raw, "fechaCierre"))))
            .plazoSolicitudes(coalesce(getString(raw, "plazoSolicitudes"), getString(raw, "plazoPresentacion")))
            .procedimiento(coalesce(getString(raw, "procedimiento"), getString(raw, "formaPresentacion")))
            .basesReguladoras(coalesce(getString(raw, "basesReguladoras"), getString(raw, "normativa")))
            .urlOficial("https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv)
            .build();
    } catch (Exception e) {
        log.warn("[BDNS] Error detalle idBdns={}: {}", idBdns, e.getMessage());
        throw new BdnsException("No se pudo obtener el detalle: " + idBdns);
    }
}

// Helpers privados — anadir solo si NO existen ya en la clase
private String getString(Map<String, Object> m, String k) {
    Object v = m.get(k); return v != null ? v.toString() : null;
}
private Boolean getBoolean(Map<String, Object> m, String k) {
    Object v = m.get(k);
    if (v instanceof Boolean b) return b;
    if (v instanceof String s)  return Boolean.parseBoolean(s);
    return null;
}
private LocalDate parseDate(String s) {
    if (s == null || s.isBlank()) return null;
    try { return LocalDate.parse(s.substring(0, 10)); } catch (Exception e) { return null; }
}
private String normalizarNivel1(String n) {
    if (n == null) return "Desconocido";
    return switch (n.toUpperCase()) {
        case "ESTADO"     -> "Estatal";
        case "AUTONOMICA" -> "Autonomica";
        case "LOCAL"      -> "Local";
        default           -> n;
    };
}
private String coalesce(String a, String b) {
    return (a != null && !a.isBlank()) ? a : b;
}
```

---

## Paso 4 — CatalogosController.java

```java
@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CatalogosController {

    private final CatalogosBdnsService catalogos;

    @GetMapping("/regiones")
    public ResponseEntity<List<CatalogoItemDTO>> getRegiones() {
        return ResponseEntity.ok(catalogos.getAllRegiones().stream()
            .map(r -> new CatalogoItemDTO(r.getId(), r.getNombre()))
            .sorted(Comparator.comparing(CatalogoItemDTO::getNombre)).toList());
    }

    @GetMapping("/finalidades")
    public ResponseEntity<List<CatalogoItemDTO>> getFinalidades() {
        return ResponseEntity.ok(catalogos.getAllFinalidades().stream()
            .map(f -> new CatalogoItemDTO(f.getId(), f.getNombre()))
            .sorted(Comparator.comparing(CatalogoItemDTO::getNombre)).toList());
    }

    @GetMapping("/instrumentos")
    public ResponseEntity<List<CatalogoItemDTO>> getInstrumentos() {
        return ResponseEntity.ok(catalogos.getAllInstrumentos().stream()
            .map(i -> new CatalogoItemDTO(i.getId(), i.getNombre()))
            .sorted(Comparator.comparing(CatalogoItemDTO::getNombre)).toList());
    }

    @GetMapping("/organos")
    public ResponseEntity<List<CatalogoItemDTO>> getOrganos(
            @RequestParam(required = false) String tipoAdmon) {
        return ResponseEntity.ok(catalogos.getOrganos(tipoAdmon).stream()
            .map(o -> new CatalogoItemDTO(o.getId(), o.getNombre()))
            .sorted(Comparator.comparing(CatalogoItemDTO::getNombre)).toList());
    }
}
```

---

## Paso 5 — ConvocatoriaDetalleController.java

```java
@RestController
@RequestMapping("/api/convocatorias")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ConvocatoriaDetalleController {

    private final BdnsClientService     bdnsClient;
    private final RecomendacionRepository recomendacionRepo;
    private final ConvocatoriaRepository  convocatoriaRepo;

    @GetMapping("/{idBdns}/detalle")
    public ResponseEntity<ConvocatoriaDetalleDTO> getDetalle(@PathVariable String idBdns) {
        ConvocatoriaDetalleDTO detalle = bdnsClient.obtenerDetalleCompleto(idBdns);

        // Enriquecer con datos IA de Syntia si ya fue analizada
        convocatoriaRepo.findByIdBdns(idBdns).ifPresent(conv -> {
            detalle.setSector(conv.getSector());
            detalle.setInstrumento(conv.getTipo());
            recomendacionRepo.findTopByConvocatoriaOrderByPuntuacionDesc(conv)
                .ifPresent(rec -> {
                    detalle.setPuntuacion(rec.getPuntuacion());
                    detalle.setExplicacion(rec.getExplicacion());
                    detalle.setGuia(rec.getGuia());
                    detalle.setFechaAnalisis(rec.getFechaCreacion());
                });
        });

        return ResponseEntity.ok(detalle);
    }
}
```

---

## Verificacion

```bash
mvn compile && mvn spring-boot:run &
sleep 15

# Obtener token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test123"}' | jq -r '.token')

# Probar los 4 endpoints de catalogos
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/catalogos/regiones | jq 'length'
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/catalogos/finalidades | jq 'length'
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/catalogos/instrumentos | jq 'length'

# Probar detalle (sustituir 609545 por un idBdns real de tu BD)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/convocatorias/609545/detalle | jq '{titulo, tipo, fuente, fechaFinSolicitud}'
```

BACKEND COMPLETO. Pasar a FRONT_P1 cuando los 4 endpoints respondan con datos reales (no arrays vacios).
