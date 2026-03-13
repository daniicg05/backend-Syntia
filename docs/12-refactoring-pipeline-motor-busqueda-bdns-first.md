# 12 – Refactoring del Pipeline del Motor de Búsqueda: BDNS-First

## Alineación Arquitectónica Vigente (2026-03-13)

> Este documento sigue vigente para el pipeline backend. La evolución actual se interpreta así:
>
> - Backend y pipeline `BDNS-First` se mantienen sin cambios funcionales de negocio.
> - Frontend objetivo: `Angular + API REST`.
> - Referencias históricas de consumo MVC se tratan como legado temporal.
> - Prioridad de entrega: endpoints `controller/api/`, JWT y SSE para el cliente SPA.

**Versión analizada:** v3.0.0 → **Implementado en v4.0.0**  
**Fecha:** 2026-03-11  
**Estado:** ✅ **IMPLEMENTADO** — Ver `docs/13-plan-fases-v4.md` (Fases 2-3, ambas completadas)

> Este documento fue el análisis previo que sirvió de base para el refactoring BDNS-First.
> Los cambios propuestos se implementaron completamente en v4.0.0.
> Para el estado actual del código, consultar `07-fases-implementacion.md` y `05-changelog.md`.

---

## 1. Flujo actual del motor de búsqueda

El motor está orquestado por `MotorMatchingService`. El pipeline completo, en orden de ejecución:

```
Perfil + Proyecto
       │
       ▼
[1] OpenAiMatchingService.generarKeywordsBusqueda()
       │  → llama a OpenAI con KEYWORDS_SYSTEM_PROMPT
       │  → genera 6-8 términos de búsqueda en texto libre
       │  → fallback: generarKeywordsBasicas() si OpenAI falla
       ▼
[2] BdnsClientService.buscarPorTexto(keyword, pagina, tamano)  × N keywords
       │  → GET BDNS con parámetro &descripcion={keyword}&vigente=true
       │  → hasta 15 resultados por keyword → hasta 120 candidatas brutas
       │  → deduplicación por idBdns y por título (en memoria)
       ▼
[3] Pre-filtro geográfico en memoria (MotorMatchingService)
       │  → descarta convocatorias de otras CCAA por comparación de strings
       │  → limita a MAX_CANDIDATAS_IA = 15
       ▼
[4] BdnsClientService.obtenerDetalleTexto(idBdns)  × N candidatas (en paralelo)
       │  → pool de hasta 10 hilos
       │  → enriquece cada candidata con el detalle oficial de la API BDNS
       ▼
[5] OpenAiMatchingService.analizar()  × N candidatas (hasta 15)
       │  → llama a OpenAI con SYSTEM_PROMPT + detalle oficial
       │  → devuelve: puntuación 0-100, explicación, sector inferido, guía 8 pasos
       │  → descarta si puntuación < UMBRAL_RECOMENDACION (20)
       ▼
[6] Persistencia selectiva en BD + emisión de eventos SSE
```

**Llamadas totales a OpenAI por ejecución:** 1 (keywords) + hasta 15 (evaluaciones) = **≤ 16 llamadas**  
**Llamadas totales a BDNS por ejecución:** 6-8 (búsquedas) + hasta 15 (detalles en paralelo) = **≤ 23 peticiones HTTP**  
**Latencia estimada:** 35-45 segundos

---

## 2. Flujo propuesto (BDNS-First)

Se invierte el orden: los filtros estructurados de la API BDNS sustituyen a la generación de keywords por IA. OpenAI solo entra en el pipeline una vez que ya hay una lista acotada y pre-cualificada de candidatas.

```
Perfil + Proyecto
       │
       ▼
[1] Constructor de filtros BDNS (sin IA)
       │  → sector del Proyecto/Perfil → parámetro de búsqueda BDNS
       │  → ubicación → parámetro nivel1/nivel2 de la API BDNS
       │  → tipo de entidad → filtro opcional adicional
       │  → lógica determinista, sin llamada a OpenAI
       ▼
[2] BdnsClientService.buscarPorFiltros(sector, ubicacion, tipo)  × 1-2 llamadas
       │  → GET BDNS con parámetros estructurados en lugar de texto libre
       │  → devuelve 5-20 convocatorias ya pre-cualificadas por la propia BDNS
       ▼
[3] BdnsClientService.obtenerDetalleTexto(idBdns)  × N candidatas (en paralelo)
       │  → igual que en el flujo actual, sin cambios
       ▼
[4] OpenAiMatchingService.analizar()  × N candidatas (solo evaluación)
       │  → igual que en el flujo actual, sin cambios
       │  → descarta si puntuación < UMBRAL_RECOMENDACION (20)
       ▼
[5] Persistencia selectiva en BD + emisión de eventos SSE
```

**Llamadas totales a OpenAI por ejecución:** 0 (keywords eliminadas) + hasta 20 (evaluaciones) = **≤ 20 llamadas**  
**Llamadas totales a BDNS por ejecución:** 1-2 (búsquedas estructuradas) + hasta 20 (detalles en paralelo)  
**Latencia estimada:** 15-25 segundos

---

## 3. Comparación de pipelines

| Dimensión | Flujo actual (IA→BDNS→IA) | Flujo propuesto (BDNS→IA) |
|-----------|--------------------------|--------------------------|
| Llamadas a OpenAI | ≤ 16 (1 keywords + 15 eval.) | ≤ 20 (0 keywords + 20 eval.) |
| Llamadas HTTP a BDNS | ≤ 23 | ≤ 22 |
| Candidatas brutas antes del filtro | Hasta 120 (6-8 kw × 15) | 5-20 (directamente relevantes) |
| Calidad del filtrado inicial | Débil — texto libre, ruido alto | Alta — filtros nativos de la BDNS |
| IA en el camino crítico de búsqueda | **Sí** — falla la búsqueda si cae OpenAI | **No** — IA solo en la evaluación final |
| Latencia total estimada | 35-45 s | 15-25 s |
| Coste de tokens OpenAI | Alto | Medio |
| Fallback sin IA | Parcial (`generarKeywordsBasicas`) | Total (búsqueda siempre determinista) |

---

## 4. Clases y servicios donde se encuentra el flujo actual

### 4.1 `MotorMatchingService.java`
**Ruta:** `src/main/java/com/syntia/mvp/service/MotorMatchingService.java`

Orquestador central del pipeline. Contiene los dos puntos de entrada públicos:

| Método | Descripción |
|--------|-------------|
| `generarRecomendaciones(Proyecto)` | Ejecución síncrona, devuelve `List<Recomendacion>` |
| `generarRecomendacionesStream(Proyecto, SseEmitter)` | Ejecución asíncrona con SSE, emite eventos al navegador en tiempo real |

Métodos privados que implementan el pipeline actual:

| Método | Paso del pipeline |
|--------|------------------|
| `generarKeywords(proyecto, perfil)` | **Paso 1** — delega en `OpenAiMatchingService` |
| `generarKeywordsBasicas(proyecto, perfil)` | **Paso 1 fallback** — keywords sin IA |
| `buscarEnBdns(keywords)` | **Paso 2** — itera keywords, llama a BDNS, deduplica |
| `obtenerDetallesEnParalelo(candidatas)` | **Paso 3** — pool de hilos para detalles BDNS |

### 4.2 `OpenAiMatchingService.java`
**Ruta:** `src/main/java/com/syntia/mvp/service/OpenAiMatchingService.java`

Contiene dos responsabilidades bien diferenciadas:

| Método / constante | Pertenece al flujo | Estado en propuesta |
|--------------------|--------------------|---------------------|
| `KEYWORDS_SYSTEM_PROMPT` | Generación de keywords (paso 1) | **Eliminar** |
| `generarKeywordsBusqueda(proyecto, perfil)` | Generación de keywords (paso 1) | **Eliminar** |
| `construirPromptKeywords(proyecto, perfil)` | Generación de keywords (paso 1) | **Eliminar** |
| `parsearKeywords(respuesta)` | Generación de keywords (paso 1) | **Eliminar** |
| `generarKeywordsBasicas(proyecto, perfil)` | Fallback keywords (paso 1) | **Eliminar** |
| `SYSTEM_PROMPT` | Evaluación de convocatorias (paso 4) | **Mantener intacto** |
| `analizar(proyecto, perfil, convocatoria, detalle)` | Evaluación de convocatorias (paso 4) | **Mantener intacto** |
| `construirPrompt(...)` | Evaluación de convocatorias (paso 4) | **Mantener intacto** |
| `parsearRespuesta(...)` | Evaluación de convocatorias (paso 4) | **Mantener intacto** |

### 4.3 `BdnsClientService.java`
**Ruta:** `src/main/java/com/syntia\mvp/service/BdnsClientService.java`

| Método | Estado en propuesta |
|--------|---------------------|
| `buscarPorTexto(keywords, pagina, tamano)` | **Sustituir** como método principal de búsqueda |
| `obtenerDetalleTexto(idBdns)` | **Mantener intacto** |
| `importar(pagina, tamano)` | **Mantener intacto** (usado por el panel admin) |

### 4.4 `RecomendacionController.java`
**Ruta:** `src/main/java/com/syntia/mvp/controller/RecomendacionController.java`

No contiene lógica de pipeline. Solo invoca `motorMatchingService.generarRecomendaciones()` y `motorMatchingService.generarRecomendacionesStream()`. **El contrato público no cambia** — el controlador no necesita modificarse.

---

## 5. Fragmentos de código afectados

### 5.1 Paso 1 actual — Generación de keywords con IA

**Fichero:** `MotorMatchingService.java`, método `generarKeywords()` (líneas ~210-240)

```java
// ── Llamada a OpenAI para generar keywords ────────────────────────────
private List<String> generarKeywords(Proyecto proyecto, Perfil perfil) {
    try {
        return openAiMatchingService.generarKeywordsBusqueda(proyecto, perfil);
    } catch (Exception e) {
        log.warn("Error generando keywords con OpenAI, usando datos básicos del proyecto: {}", e.getMessage());
        return generarKeywordsBasicas(proyecto, perfil);
    }
}

private List<String> generarKeywordsBasicas(Proyecto proyecto, Perfil perfil) {
    List<String> kw = new ArrayList<>();
    if (proyecto != null) {
        if (proyecto.getSector() != null && !proyecto.getSector().isBlank())
            kw.add(proyecto.getSector());
        if (proyecto.getNombre() != null && !proyecto.getNombre().isBlank())
            kw.add(proyecto.getNombre());
        if (proyecto.getUbicacion() != null && !proyecto.getUbicacion().isBlank())
            kw.add("subvencion " + proyecto.getUbicacion());
    }
    if (perfil != null) {
        if (perfil.getTipoEntidad() != null && !perfil.getTipoEntidad().isBlank())
            kw.add(perfil.getTipoEntidad() + " subvencion");
        if (perfil.getSector() != null && !perfil.getSector().isBlank())
            kw.add("ayuda " + perfil.getSector());
    }
    kw.add("subvencion pyme");
    kw.add("ayuda empresa innovacion");
    return kw;
}
```

**En `OpenAiMatchingService.java`** — prompt y método que orquesta la llamada:

```java
private static final String KEYWORDS_SYSTEM_PROMPT = """
        Eres un experto en subvenciones públicas españolas. \
        A partir del proyecto y perfil de usuario, genera términos de búsqueda \
        para encontrar convocatorias relevantes en la BDNS. \
        Genera entre 6 y 8 búsquedas distintas ... \
        RESPONDE ÚNICAMENTE con este JSON: {"busquedas": ["kw1", "kw2", "kw3"]}
        """;

public List<String> generarKeywordsBusqueda(Proyecto proyecto, Perfil perfil) {
    String userPrompt = construirPromptKeywords(proyecto, perfil);
    String respuesta = openAiClient.chat(KEYWORDS_SYSTEM_PROMPT, userPrompt);
    return parsearKeywords(respuesta);
}
```

### 5.2 Paso 2 actual — Búsqueda en BDNS por texto libre

**Fichero:** `MotorMatchingService.java`, método `buscarEnBdns()` (líneas ~243-280)

```java
private Map<String, ConvocatoriaDTO> buscarEnBdns(List<String> keywords) {
    Map<String, ConvocatoriaDTO> resultado = new LinkedHashMap<>();
    java.util.Set<String> idsBdnsVistos = new java.util.HashSet<>();
    LocalDate hoy = LocalDate.now();
    for (String kw : keywords) {
        try {
            List<ConvocatoriaDTO> encontradas =
                bdnsClientService.buscarPorTexto(kw, 0, RESULTADOS_POR_KEYWORD);
            for (ConvocatoriaDTO dto : encontradas) {
                // Deduplicar por idBdns + por título
                if (dto.getIdBdns() != null && idsBdnsVistos.contains(dto.getIdBdns())) continue;
                if (resultado.containsKey(dto.getTitulo())) continue;
                if (dto.getFechaCierre() != null && dto.getFechaCierre().isBefore(hoy)) continue;
                resultado.put(dto.getTitulo(), dto);
            }
        } catch (Exception e) {
            log.warn("Error consultando BDNS con keyword '{}': {}", kw, e.getMessage());
        }
    }
    return resultado;
}
```

**Fichero:** `BdnsClientService.java`, método `buscarPorTexto()` (líneas ~85-104)

```java
public List<ConvocatoriaDTO> buscarPorTexto(String keywords, int pagina, int tamano) {
    Map<String, Object> respuesta = restClient.get()
            .uri(BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag={pag}&tamPag={tam}" +
                 "&descripcion={desc}&descripcionTipoBusqueda=1&vigente=true",
                 pagina, Math.min(tamano, 50), keywords)
            .retrieve()
            .body(Map.class);
    return mapearRespuesta(respuesta);
}
```

La URL construida con texto libre para una keyword como `"tecnologia pyme"` queda así:

```
GET https://www.infosubvenciones.es/bdnstrans/api/convocatorias/busqueda
    ?vpn=GE&vln=es&numPag=0&tamPag=15
    &descripcion=tecnologia+pyme
    &descripcionTipoBusqueda=1
    &vigente=true
```

### 5.3 Paso 3 actual — Pre-filtro geográfico en memoria

**Fichero:** `MotorMatchingService.java` (fragmento dentro de `generarRecomendaciones()`)

```java
// Pre-filtro geográfico en memoria — comparación de strings por contains()
if (ubicacionUsuario != null && !ubicacionUsuario.isBlank()) {
    final String ubiFinal = ubicacionUsuario.toLowerCase().trim();
    aEvaluar = candidatasUnicas.values().stream()
            .filter(dto -> {
                String ubiConv = dto.getUbicacion();
                if (ubiConv == null || ubiConv.isBlank() || "Nacional".equalsIgnoreCase(ubiConv))
                    return true;
                return ubiConv.toLowerCase().contains(ubiFinal)
                        || ubiFinal.contains(ubiConv.toLowerCase());
            })
            .limit(MAX_CANDIDATAS_IA)
            .toList();
}
```

Este bloque de filtrado en memoria desaparecería en el flujo propuesto: la API BDNS filtra por `nivel1`/`nivel2` directamente en el servidor.

### 5.4 Referencia: la llamada a BDNS con filtros estructurados ya existe en la API

La API BDNS pública acepta parámetros adicionales que actualmente **no se usan**. Se descubren inspeccionando las peticiones de la SPA Angular del portal oficial:

```
GET https://www.infosubvenciones.es/bdnstrans/api/convocatorias/busqueda
    ?vpn=GE&vln=es
    &numPag=0&tamPag=20
    &vigente=true
    &nivel1=AUTONOMICA          ← ámbito: ESTADO | AUTONOMICA | LOCAL
    &nivel2=Comunidad+de+Madrid ← comunidad autónoma o nombre del organismo
    &descripcion=tecnologia     ← sector como texto (opcional, más acotado que sin filtros)
```

---

## 6. Partes del código que se verían afectadas

### 6.1 Cambios en `MotorMatchingService.java`

| Acción | Qué | Por qué |
|--------|-----|---------|
| **Eliminar** | `generarKeywords(proyecto, perfil)` | Reemplazado por constructor de filtros |
| **Eliminar** | `generarKeywordsBasicas(proyecto, perfil)` | Sin keywords, no hay fallback que mantener |
| **Eliminar** | `buscarEnBdns(List<String> keywords)` | Sustituido por `buscarConFiltros()` |
| **Eliminar** | Referencia a `openAiMatchingService` en el constructor | Ya no se necesita en la fase de búsqueda |
| **Añadir** | `construirFiltrosBdns(Proyecto, Perfil)` (privado) | Convierte campos del modelo a parámetros BDNS |
| **Añadir** | llamada a `bdnsClientService.buscarPorFiltros(...)` | Nuevo método en `BdnsClientService` |
| **Mantener** | `obtenerDetallesEnParalelo(candidatas)` | Sin cambios |
| **Modificar** | Bloque de pre-filtro geográfico en `generarRecomendaciones()` | Se elimina o simplifica — BDNS ya filtra por ámbito |
| **Modificar** | Evento SSE `"keywords"` en `generarRecomendacionesStream()` | Reemplazar por evento `"filtros"` o eliminar |

### 6.2 Cambios en `OpenAiMatchingService.java`

| Acción | Qué | Por qué |
|--------|-----|---------|
| **Eliminar** | `KEYWORDS_SYSTEM_PROMPT` | Ya no se genera con IA |
| **Eliminar** | `generarKeywordsBusqueda(proyecto, perfil)` | Punto de entrada del paso eliminado |
| **Eliminar** | `construirPromptKeywords(proyecto, perfil)` | Auxiliar del paso eliminado |
| **Eliminar** | `parsearKeywords(respuesta)` | Auxiliar del paso eliminado |
| **Eliminar** | `generarKeywordsBasicas(proyecto, perfil)` | Fallback del paso eliminado |
| **Mantener** | Todo lo relacionado con `analizar()` | Sin cambios — es el paso que se conserva |

### 6.3 Cambios en `BdnsClientService.java`

| Acción | Qué | Por qué |
|--------|-----|---------|
| **Añadir** | `buscarPorFiltros(String sector, String ubicacion, String nivel1)` | Nuevo método con parámetros estructurados de la API BDNS |
| **Mantener** | `buscarPorTexto(keywords, pagina, tamano)` | Se conserva para el panel admin y como posible fallback |
| **Mantener** | `obtenerDetalleTexto(idBdns)` | Sin cambios |

### 6.4 Cambios menores en el frontend

| Fichero | Acción |
|---------|--------|
| `src/main/resources/static/javascript/recomendaciones-stream.js` | El handler del evento SSE `"keywords"` (que muestra el bloque "Keywords generadas: N") debe eliminarse o sustituirse por un evento `"filtros"` |

---

## 7. Qué cambios conceptuales hay que realizar para invertir el pipeline

### 7.1 Eliminar la dependencia de OpenAI en la fase de búsqueda

El paso 1 actual (`generarKeywordsBusqueda`) es una llamada a OpenAI que convierte datos estructurados del modelo (`Proyecto.sector`, `Perfil.tipoEntidad`, `Proyecto.ubicacion`) en texto libre para usarlo como parámetro de búsqueda en BDNS.

**El problema:** estos campos ya son estructurados. No es necesario convertirlos a texto libre para luego buscar por texto libre; se pueden pasar directamente como filtros a la API de BDNS.

### 7.2 Construir un `BdnsFiltrosBuilder` determinista

En lugar de la llamada a IA, se necesita una función pura que reciba `Proyecto` + `Perfil` y devuelva los parámetros de la URL de BDNS. Ejemplo conceptual:

```
Entrada:
  proyecto.sector    = "Tecnología"
  proyecto.ubicacion = "Madrid"
  perfil.tipoEntidad = "PYME"

Salida (parámetros BDNS):
  descripcion = "tecnologia"       ← normalización del sector
  nivel1      = "AUTONOMICA"       ← derivado de que Madrid es CCAA
  nivel2      = "Comunidad de Madrid"
```

El reto técnico principal de este paso es la **tabla de mapeo** entre los valores de `sector` que usan los usuarios en Syntia (texto libre o de un desplegable) y los términos que la API BDNS entiende como `descripcion`. Sin esta tabla, búsquedas con valores como `"Energías renovables"` o `"Agroalimentario"` pueden devolver cero resultados.

### 7.3 Mantener un fallback de cobertura

Si la búsqueda estructurada devuelve menos de un umbral mínimo (ej.: 3 convocatorias), el sistema debería ampliar automáticamente la búsqueda eliminando algún filtro (primero sector, luego comunidad autónoma) hasta obtener candidatas suficientes para evaluar.

### 7.4 El paso de evaluación con IA no cambia

`OpenAiMatchingService.analizar()`, `construirPrompt()` y `parsearRespuesta()` permanecen exactamente igual. La IA recibe el mismo input (proyecto, perfil, convocatoria, detalle BDNS) y devuelve el mismo output (puntuación, explicación, guía de 8 pasos).

### 7.5 El contrato público del motor no cambia

Los métodos `generarRecomendaciones(Proyecto)` y `generarRecomendacionesStream(Proyecto, SseEmitter)` de `MotorMatchingService` mantienen su firma y su semántica. `RecomendacionController`, `RecomendacionRestController`, la capa de presentación y los repositorios no necesitan ninguna modificación.

---

## Resumen ejecutivo

| Componente | Estado |
|-----------|--------|
| `MotorMatchingService` | **Modificar** — eliminar lógica de keywords, añadir constructor de filtros |
| `OpenAiMatchingService` | **Modificar** — eliminar ~90 líneas del bloque keywords |
| `BdnsClientService` | **Modificar** — añadir método `buscarPorFiltros()` |
| `recomendaciones-stream.js` | **Modificar** — ajuste menor del evento SSE |
| `RecomendacionController` | **Sin cambios** |
| Modelo de datos (entidades JPA) | **Sin cambios** |
| Repositorios | **Sin cambios** |
| Capa de presentación | **Sin cambios** |
| Seguridad / JWT | **Sin cambios** |

**Esfuerzo estimado de implementación:** 12-20 horas (incluyendo investigación de parámetros BDNS y pruebas con datos reales).

**Riesgo técnico principal:** calidad de la tabla de mapeo `sector Syntia → término BDNS`. Si el mapeo es incompleto, el flujo propuesto puede devolver menos candidatas que el flujo actual para sectores con terminología no estándar.
