# PROMPT BACK — Parte 1: Catálogos BDNS

## INSTRUCCIONES PARA EL AGENTE
Lee este archivo completo ANTES de escribir una sola línea de código.
Respeta el orden de los pasos numerados.
NO modifiques archivos que no estén en "Archivos a MODIFICAR".
NO elimines métodos existentes, solo añade los indicados.
Al finalizar, ejecuta el comando de verificación y reporta el resultado.

## Contexto
- Repo: backend-Syntia
- Rama: feature/filtros-bdns-completos
- Stack: Java 17 · Spring Boot 3.3.x · Maven · PostgreSQL 17.2
- Archivos que YA EXISTEN y NO tocar: BdnsClientService.java, MotorMatchingService.java, RateLimitService.java
- Archivos a CREAR: V5__catalogos_bdns.sql, BdnsRegion.java, BdnsFinalidad.java, BdnsInstrumento.java, BdnsOrgano.java, BdnsRegionesRepository.java, BdnsFinalidadesRepository.java, BdnsInstrumentosRepository.java, BdnsOrganosRepository.java, CatalogosBdnsService.java
- Ejecutar tras implementar: mvn flyway:migrate y verificar 4 tablas en PostgreSQL

---

## Paso 1 — Migración Flyway: V5__catalogos_bdns.sql

```sql
CREATE TABLE bdns_regiones (
    id       INTEGER PRIMARY KEY,
    nombre   VARCHAR(255) NOT NULL,
    nivel    VARCHAR(50),
    activo   BOOLEAN DEFAULT TRUE,
    sync_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE bdns_finalidades (
    id      INTEGER PRIMARY KEY,
    nombre  VARCHAR(500) NOT NULL,
    activo  BOOLEAN DEFAULT TRUE,
    sync_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE bdns_instrumentos (
    id      INTEGER PRIMARY KEY,
    nombre  VARCHAR(255) NOT NULL,
    activo  BOOLEAN DEFAULT TRUE,
    sync_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE bdns_organos (
    id         INTEGER PRIMARY KEY,
    nombre     VARCHAR(500) NOT NULL,
    tipo_admon CHAR(1) NOT NULL,
    activo     BOOLEAN DEFAULT TRUE,
    sync_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_regiones_nombre     ON bdns_regiones     USING gin(to_tsvector('spanish', nombre));
CREATE INDEX idx_finalidades_nombre  ON bdns_finalidades  USING gin(to_tsvector('spanish', nombre));
CREATE INDEX idx_instrumentos_nombre ON bdns_instrumentos USING gin(to_tsvector('spanish', nombre));
CREATE INDEX idx_organos_nombre      ON bdns_organos      USING gin(to_tsvector('spanish', nombre));
```

---

## Paso 2 — Entidades JPA (model/)

BdnsRegion.java:
```java
@Entity @Table(name = "bdns_regiones")
@Data @NoArgsConstructor @AllArgsConstructor
public class BdnsRegion {
    @Id private Integer id;
    private String nombre;
    private String nivel;
    private Boolean activo;
    private LocalDateTime syncAt;
}
```
Replicar mismo patrón para BdnsFinalidad (tabla bdns_finalidades), BdnsInstrumento (tabla bdns_instrumentos).
BdnsOrgano añade campo: private String tipoAdmon; mapeado a columna tipo_admon.

---

## Paso 3 — Repositorios JPA (repository/)

```java
public interface BdnsRegionesRepository extends JpaRepository<BdnsRegion, Integer> {
    List<BdnsRegion> findByNombreContainingIgnoreCase(String nombre);
}

public interface BdnsFinalidadesRepository extends JpaRepository<BdnsFinalidad, Integer> {
    @Query("SELECT f FROM BdnsFinalidad f WHERE LOWER(f.nombre) LIKE LOWER(CONCAT('%',:texto,'%')) ORDER BY f.nombre ASC")
    List<BdnsFinalidad> findBestMatch(@Param("texto") String texto);
}

public interface BdnsInstrumentosRepository extends JpaRepository<BdnsInstrumento, Integer> {
    List<BdnsInstrumento> findByNombreContainingIgnoreCase(String nombre);
}

public interface BdnsOrganosRepository extends JpaRepository<BdnsOrgano, Integer> {
    List<BdnsOrgano> findByTipoAdmon(String tipoAdmon);
    List<BdnsOrgano> findByNombreContainingIgnoreCase(String nombre);
}
```

---

## Paso 4 — CatalogosBdnsService.java (service/)

```java
@Service @Slf4j @RequiredArgsConstructor
public class CatalogosBdnsService {

    private final BdnsClientService bdnsHttpClient;
    private final BdnsRegionesRepository regionesRepo;
    private final BdnsFinalidadesRepository finalidadesRepo;
    private final BdnsInstrumentosRepository instrumentosRepo;
    private final BdnsOrganosRepository organosRepo;

    private static final String BASE = "https://www.infosubvenciones.es/bdnstrans/api";

    @PostConstruct
    @Scheduled(fixedRate = 604800000L)
    public void sincronizarTodos() {
        log.info("[BDNS-CAT] Iniciando sincronizacion de catalogos...");
        sincronizarRegiones();
        sincronizarFinalidades();
        sincronizarInstrumentos();
        sincronizarOrganos();
        log.info("[BDNS-CAT] Sincronizacion completada.");
    }

    private void sincronizarRegiones() {
        try {
            List<Map<String, Object>> data = bdnsHttpClient.getRawList(BASE + "/regiones");
            regionesRepo.deleteAll();
            regionesRepo.saveAll(data.stream().map(m -> {
                BdnsRegion r = new BdnsRegion();
                r.setId(((Number) m.get("id")).intValue());
                r.setNombre((String) m.get("nombre"));
                r.setActivo(true);
                r.setSyncAt(LocalDateTime.now());
                return r;
            }).toList());
            log.info("[BDNS-CAT] Regiones sincronizadas: {}", regionesRepo.count());
        } catch (Exception e) {
            log.warn("[BDNS-CAT] Error sync regiones: {}", e.getMessage());
        }
    }

    private void sincronizarFinalidades() {
        try {
            List<Map<String, Object>> data = bdnsHttpClient.getRawList(BASE + "/finalidades?vpd=GE");
            finalidadesRepo.deleteAll();
            finalidadesRepo.saveAll(data.stream().map(m -> {
                BdnsFinalidad f = new BdnsFinalidad();
                f.setId(((Number) m.get("id")).intValue());
                f.setNombre((String) m.get("nombre"));
                f.setActivo(true);
                f.setSyncAt(LocalDateTime.now());
                return f;
            }).toList());
            log.info("[BDNS-CAT] Finalidades sincronizadas: {}", finalidadesRepo.count());
        } catch (Exception e) {
            log.warn("[BDNS-CAT] Error sync finalidades: {}", e.getMessage());
        }
    }

    private void sincronizarInstrumentos() {
        try {
            List<Map<String, Object>> data = bdnsHttpClient.getRawList(BASE + "/instrumentos");
            instrumentosRepo.deleteAll();
            instrumentosRepo.saveAll(data.stream().map(m -> {
                BdnsInstrumento i = new BdnsInstrumento();
                i.setId(((Number) m.get("id")).intValue());
                i.setNombre((String) m.get("nombre"));
                i.setActivo(true);
                i.setSyncAt(LocalDateTime.now());
                return i;
            }).toList());
            log.info("[BDNS-CAT] Instrumentos sincronizados: {}", instrumentosRepo.count());
        } catch (Exception e) {
            log.warn("[BDNS-CAT] Error sync instrumentos: {}", e.getMessage());
        }
    }

    private void sincronizarOrganos() {
        for (String tipo : List.of("C", "A", "L", "O")) {
            try {
                List<Map<String, Object>> data = bdnsHttpClient.getRawList(
                    BASE + "/organos?vpd=GE&idAdmon=" + tipo);
                organosRepo.saveAll(data.stream().map(m -> {
                    BdnsOrgano o = new BdnsOrgano();
                    o.setId(((Number) m.get("id")).intValue());
                    o.setNombre((String) m.get("nombre"));
                    o.setTipoAdmon(tipo);
                    o.setActivo(true);
                    o.setSyncAt(LocalDateTime.now());
                    return o;
                }).toList());
            } catch (Exception e) {
                log.warn("[BDNS-CAT] Error sync organos tipo={}: {}", tipo, e.getMessage());
            }
        }
    }

    @Cacheable(value = "bdns-catalogos", key = "'region:' + #ubicacion")
    public List<Integer> resolverRegionIds(String ubicacion) {
        if (ubicacion == null || ubicacion.isBlank()) return List.of();
        return regionesRepo.findByNombreContainingIgnoreCase(ubicacion)
            .stream().map(BdnsRegion::getId).toList();
    }

    @Cacheable(value = "bdns-catalogos", key = "'finalidad:' + #sector")
    public Integer resolverFinalidadId(String sector) {
        if (sector == null || sector.isBlank()) return null;
        List<BdnsFinalidad> r = finalidadesRepo.findBestMatch(sector);
        return r.isEmpty() ? null : r.get(0).getId();
    }

    @Cacheable(value = "bdns-catalogos", key = "'instrumento:' + #nombre")
    public List<Integer> resolverInstrumentoIds(String nombre) {
        if (nombre == null || nombre.isBlank()) return List.of();
        return instrumentosRepo.findByNombreContainingIgnoreCase(nombre)
            .stream().map(BdnsInstrumento::getId).toList();
    }

    public List<BdnsRegion>      getAllRegiones()             { return regionesRepo.findAll(); }
    public List<BdnsFinalidad>   getAllFinalidades()          { return finalidadesRepo.findAll(); }
    public List<BdnsInstrumento> getAllInstrumentos()         { return instrumentosRepo.findAll(); }
    public List<BdnsOrgano>      getOrganos(String tipo)     {
        return tipo != null ? organosRepo.findByTipoAdmon(tipo) : organosRepo.findAll();
    }
}
```

Anadir en BdnsClientService.java (NO borrar nada existente):
```java
public List<Map<String, Object>> getRawList(String url) {
    try {
        String json = restClient.get().uri(url).retrieve().body(String.class);
        return new ObjectMapper().readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
        log.warn("[BDNS] getRawList error url={}: {}", url, e.getMessage());
        return List.of();
    }
}
```

Anadir en application.properties:
```
spring.cache.type=caffeine
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=86400s
```

---

## Verificacion

```bash
mvn compile
mvn flyway:migrate
# Buscar en logs: "[BDNS-CAT] Regiones sincronizadas: XX"
# Debe aparecer para las 4 tablas
```

PASA A BACK_P2 solo cuando los 4 mensajes de sincronizacion aparezcan en logs.
