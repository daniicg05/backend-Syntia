# Informe Técnico — Flujo BDNS: Análisis End-to-End

## Alineación Arquitectónica Vigente (2026-03-13)

> Regla de lectura para este documento técnico:
>
> - El flujo `BDNS+IA` se mantiene en backend (`service/`) sin mover lógica al frontend.
> - La exposición al cliente se prioriza por `controller/api/` con JWT.
> - El consumo de progreso/resultados en tiempo real debe orientarse a cliente `Angular` vía SSE.
> - Cualquier referencia a vistas server-side se considera legado temporal de transición.

> **Fecha:** 2026-03-11  
> **Versión analizada:** v4.0.0 (BDNS-First)  
> **Actualizado desde:** v3.3.0  
> **Autor:** Arquitecto Senior — Integración BDNS y optimización IA  
> **Fuentes:** Código real de `BdnsClientService.java`, `MotorMatchingService.java`, `OpenAiMatchingService.java`, `SectorNormalizador.java`, `BdnsFiltrosBuilder.java`, `FiltrosBdns.java`. Las inferencias sobre la API BDNS se marcan con ⚠️ *[inferido]*.

> **Nota v4.0.0:** El pipeline cambió fundamentalmente. La búsqueda ya no depende de OpenAI para generar keywords. Los filtros se construyen determinísticamente desde los campos del proyecto y perfil. Ver `docs/13-plan-fases-v4.md` para el plan de implementación.

---

## FASE 1 — FLUJO DE CONSULTA EN BDNS: ANÁLISIS TÉCNICO REAL

### 1.1 La API BDNS que usa Syntia

La integración actual **no usa la web pública de búsqueda** de BDNS (`infosubvenciones.es`), sino la **API REST interna de la SPA Angular** del portal, descubierta por ingeniería inversa del frontend oficial.

**Endpoint de búsqueda confirmado:**
```
GET https://www.infosubvenciones.es/bdnstrans/api/convocatorias/busqueda
```

**Endpoint de detalle confirmado:**
```
GET https://www.infosubvenciones.es/bdnstrans/api/convocatorias/{id}
```

> ⚠️ Estos endpoints **no son API pública documentada**. Son los mismos que usa el frontend Angular del portal oficial. No existe SLA, rate limit documentado, ni contrato de estabilidad. Pueden cambiar sin aviso.

### 1.2 Parámetros de la API de búsqueda (confirmados por el código)

| Parámetro | Valor usado | Descripción | ¿Obligatorio? |
|-----------|------------|-------------|---------------|
| `vpn` | `GE` | Versión/entorno de la API | ✅ Sí |
| `vln` | `es` | Idioma de respuesta | ✅ Sí |
| `numPag` | `0` (siempre) | Número de página (0-indexed) | ✅ Sí |
| `tamPag` | `15` | Registros por página (máx. 50) | ✅ Sí |
| `descripcion` | keyword de búsqueda | Texto a buscar en el título/descripción | ❌ Opcional |
| `descripcionTipoBusqueda` | `1` | Tipo: `1`=contiene todas las palabras | ❌ Opcional |
| `vigente` | `true` | Solo convocatorias con plazo abierto | ❌ Opcional |

**Parámetros adicionales que acepta la API — estado de implementación:**

| Parámetro | Uso | Estado v4.0.0 |
|-----------|-----|---------------|
| `nivel1` | Filtrar por ámbito: `ESTADO`, `AUTONOMICA`, `LOCAL` | ✅ Implementado en `buscarPorFiltros()` y `buscarPorTextoFiltrado()` |
| `nivel2` | Filtrar por CCAA específica (ej: "Comunidad Valenciana") | ✅ Implementado via `UbicacionNormalizador` |
| `nivel3` | Filtrar por organismo concreto | ❌ No implementado |
| `fechaDesde` | Fecha de publicación mínima | ❌ No implementado |
| `fechaHasta` | Fecha de publicación máxima | ❌ No implementado |
| `importeDesde` | Importe mínimo de la dotación | ❌ No implementado |
| `importeHasta` | Importe máximo de la dotación | ❌ No implementado |
| `tipoConvocatoria` | Tipo: subvención, beca, licitación... | No implementado aún |
| `sortField` | Campo de ordenación | No implementado aún |
| `sortDir` | Dirección: `asc`, `desc` | No implementado aún |

> ⚠️ *[inferido]* Los nombres de parámetros de filtro adicionales se infieren de la SPA Angular del portal y de la estructura del JSON de respuesta. Necesitan validación con pruebas reales contra la API.

### 1.3 Estructura de respuesta de búsqueda (confirmada por mapeo en código)

```json
{
  "content": [
    {
      "id": "123456",
      "descripcion": "Título de la convocatoria",
      "descripcionLeng": "Título alternativo (multilingüe)",
      "numeroConvocatoria": "12345",
      "nivel1": "AUTONOMICA",
      "nivel2": "Comunidad de Madrid",
      "nivel3": "Consejería de Economía",
      "fechaRecepcion": "2025-11-15",
      "fechaFinSolicitud": "2026-03-31",
      "fechaCierre": null
    }
  ],
  "totalElements": 615234,
  "totalPages": 41016,
  "size": 15,
  "number": 0
}
```

**Campos que Syntia extrae y cómo:**

| Campo BDNS | Campo DTO | Transformación en código |
|------------|-----------|--------------------------|
| `id` | `idBdns` | Directo |
| `descripcion` | `titulo` | Directo (fallback a `descripcionLeng`) |
| `numeroConvocatoria` | `numeroConvocatoria` | Directo → genera URL oficial |
| `nivel1` | `tipo` | `ESTADO`→"Estatal", `AUTONOMICA`→"Autonómica", `LOCAL`→"Local" |
| `nivel1` + `nivel2` | `ubicacion` | `ESTADO`→"Nacional", otros→`nivel2` |
| `nivel2`/`nivel3` | `fuente` | `"BDNS – " + nivel3 (o nivel2)` |
| `fechaFinSolicitud` / `fechaCierre` | `fechaCierre` | Primer campo no null, formato `yyyy-MM-dd` |
| — | `sector` | **null siempre** — BDNS no devuelve sector en búsqueda |
| — | `urlOficial` | `https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/{numeroConvocatoria}` |

### 1.4 Estructura de respuesta de detalle (campos extraídos)

```
GET /api/convocatorias/{idBdns}
```

Campos que intenta extraer `obtenerDetalleTexto()`:

| Campo intentado | Alias alternativos | Valor típico |
|----------------|-------------------|--------------|
| `objeto` | `descripcionObjeto`, `finalidad` | Texto del objeto de la convocatoria |
| `beneficiarios` | `tiposBeneficiarios` | Lista de tipos de beneficiarios |
| `requisitos` | `condicionesAcceso`, `requisitosParticipacion` | Condiciones de acceso |
| `dotacion` | `presupuestoTotal`, `importeTotal` | Importe total disponible |
| `basesReguladoras` | `normativa` | Referencia normativa |
| `plazoSolicitudes` | `plazoPresentacion` | Plazo de presentación |
| `procedimiento` | `formaPresentacion` | Cómo presentar la solicitud |
| `documentacion` | `documentosRequeridos` | Documentos requeridos |

> ⚠️ *[inferido]* Los nombres exactos de los campos del detalle son aproximaciones basadas en convenciones de APIs administrativas españolas. La API real puede devolver nombres diferentes. El código tiene fallbacks múltiples por esto.

### 1.5 Flujo de consulta completo (confirmado por código)

```
Usuario hace clic en "🤖 Analizar con IA"
        │
        ▼
POST /usuario/proyectos/{id}/recomendaciones/generar-stream
        │
        ▼
MotorMatchingService.generarRecomendacionesStream()
        │
        ├─ 1. DELETE recomendaciones anteriores del proyecto
        │
        ├─ 2. PerfilService.obtenerPerfil(usuarioId) → Perfil
        │
        ├─ 3. OpenAI: KEYWORDS_SYSTEM_PROMPT + datos proyecto+perfil
        │      └─ Respuesta: {"busquedas": ["kw1","kw2",...,"kw8"]}
        │      SSE → evento "keywords"
        │
        ├─ 4. Por cada keyword (6-8 keywords):
        │      └─ GET /api/convocatorias/busqueda?descripcion={kw}&vigente=true&tamPag=15
        │         └─ Mapear content[] → List<ConvocatoriaDTO>
        │         └─ Deduplicar por título → Map<titulo, DTO>
        │         └─ Descartar si fechaCierre < hoy
        │      SSE → evento "busqueda"
        │
        ├─ 5. Pre-filtro geográfico:
        │      └─ Si ubicación usuario existe:
        │         └─ Descartar convocatorias autonómicas de CCAA diferente
        │      Limitar a MAX_CANDIDATAS_IA (15)
        │
        ├─ 6. Por cada candidata (hasta 15):
        │      SSE → evento "progreso"
        │      ├─ GET /api/convocatorias/{idBdns} → detalleTexto
        │      ├─ OpenAI: SYSTEM_PROMPT + userPrompt (conv + proyecto + perfil)
        │      │   └─ Respuesta: {"puntuacion": N, "explicacion": "...", "sector": "...", "guia": "..."}
        │      ├─ Si puntuacion >= 20:
        │      │   ├─ persistirConvocatoria(dto) → BD
        │      │   ├─ save(Recomendacion) → BD
        │      │   └─ SSE → evento "resultado"
        │      └─ Si puntuacion < 20: descartar
        │
        └─ 7. SSE → evento "completado"
               └─ Frontend: setTimeout(2500ms) → window.location.reload()
```

### 1.6 Comportamiento ante casos especiales

| Caso | Comportamiento actual |
|------|----------------------|
| **BDNS no disponible** | `BdnsException` capturada por keyword → log.warn, sigue con otras keywords |
| **OpenAI no disponible** | `OpenAiUnavailableException` → fallosOpenAi++, sigue con otras candidatas |
| **0 candidatas BDNS** | SSE "estado" de aviso + "completado" con totales a 0 |
| **Todas las evaluaciones IA fallan** | Si `fallosOpenAi == aEvaluar.size()`, lanza excepción con mensaje claro |
| **SSL del gobierno inválido** | `SSLContext` permisivo ignora validación (riesgo de seguridad conocido) |
| **Título duplicado** | Deduplicado por `Map<titulo, DTO>` — solo se evalúa una vez |
| **Convocatoria caducada** | Descartada si `fechaCierre < LocalDate.now()` |
| **Sin fecha de cierre** | Se evalúa igual (tratada como "abierta sin plazo definido") |

---

## FASE 2 — ANÁLISIS DE VISUALIZACIÓN DE RESULTADOS

### 2.1 Modelo estructural: pantalla de resultados

```
┌──────────────────────────────────────────────────────────────────┐
│ RECOMENDACIONES IA — Proyecto: {nombre}                [🤖 Analizar] │
├──────────────────────────────────────────────────────────────────┤
│ [Panel SSE — oculto por defecto]                                 │
│  🔄 Analizando... ████████░░░░░░ 60%                            │
│  Evaluando 9/15: "Convocatoria X..."    Encontradas: 3          │
├──────────────────────────────────────────────────────────────────┤
│ [Tarjetas SSE en tiempo real — div#resultadosStream]             │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ Título convocatoria        [🤖 IA] [Estatal] [Tecnología]  │  │
│  │ "Explicación IA en 2 frases..."                     85/100 │  │
│  │ Fecha límite: 31/03/2026                       ████████░░  │  │
│  │ [📋 Ver guía de solicitud] [Ver convocatoria oficial ↗]   │  │
│  └────────────────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────────────┤
│ [Filtros: Tipo | Sector | Ubicación] [Filtrar] [Limpiar]         │
├──────────────────────────────────────────────────────────────────┤
│ X recomendaciones encontradas.                                    │
│  [Tarjetas estáticas de cliente web — recarga tras SSE]          │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 Campos visibles en tarjeta de resultado

| Campo | Origen | Visible | Componente visual |
|-------|--------|---------|------------------|
| Título | `Convocatoria.titulo` | ✅ | `<h5>` |
| Tipo | `Convocatoria.tipo` | ✅ | badge azul |
| Sector | `Convocatoria.sector` (inferido IA) | ✅ | badge gris |
| Ubicación | `Convocatoria.ubicacion` | ✅ | badge cyan |
| Fuente | `Convocatoria.fuente` | ✅ | badge blanco |
| Badge IA/Reglas | `Recomendacion.usadaIa` | ✅ | badge verde/gris |
| Explicación | `Recomendacion.explicacion` | ✅ | texto gris |
| Fecha límite | `Convocatoria.fechaCierre` | ✅ | rojo si <30 días |
| Puntuación | `Recomendacion.puntuacion` | ✅ | número grande + barra |
| Guía | `Recomendacion.guia` | 📋 modal | stepper + timeline |
| URL oficial | `Convocatoria.urlOficial` | ↗ enlace | botón outline |

### 2.3 Modelo estructural: modal de guía de solicitud (v3.2.0)

```
┌─────────────────────────────────────────────────────────────────┐
│ 📋 Guía de solicitud — Flujo completo              [✕ Cerrar]   │
├─────────────────────────────────────────────────────────────────┤
│ Convocatoria: {titulo}  [Tipo badge] [Sector badge]             │
├─────────────────────────────────────────────────────────────────┤
│ 📍 Flujo del proceso de solicitud                               │
│  🏛️──●──📄──●──📎──○──💻──○──📅──○──⚖️──○──✅──○──⚠️        │
│ Portal  Req.   Doc.   Sede   Plazos  Régimen Post   Advert.     │
├─────────────────────────────────────────────────────────────────┤
│ Timeline vertical:                                               │
│  📄 PASO 1 — REQUISITOS LEGALES                                 │
│  │  Texto IA específico para esta convocatoria...              │
│  📎 PASO 2 — DOCUMENTACIÓN OBLIGATORIA                          │
│  │  Texto IA específico...                                      │
│  💻 PASO 3 — ACCESO Y PRESENTACIÓN                              │
│  │  Texto IA específico...                                      │
│  [Pasos 4-8 con animación fadeInPaso escalonada]                │
├─────────────────────────────────────────────────────────────────┤
│ ⚠️ Guía orientativa (LGS 38/2003)                              │
│ [Ir a convocatoria oficial ↗]          [Cerrar]                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.4 Diagrama de navegación entre pantallas

```
Dashboard
    │
    ▼
Mis Proyectos (/usuario/proyectos)
    │
    ├──[Ver proyecto]──▶ Detalle proyecto (/usuario/proyectos/{id})
    │                         │
    │                         └──[Recomendaciones]──▶ Recomendaciones (/usuario/proyectos/{id}/recomendaciones)
    │                                                       │
    │                         [Analizar con IA] ────────────┤
    │                                                       │
    │                                                       ├──[Ver guía]──▶ Modal guía (stepper)
    │                                                       │
    │                                                       └──[Ver oficial]──▶ infosubvenciones.es/{numConv}
    │
    └──[Nuevo proyecto]──▶ Formulario nuevo proyecto
```

### 2.5 Estados posibles de la vista

| Estado | Condición | UI mostrada |
|--------|-----------|-------------|
| **Sin análisis** | `totalSinFiltro == 0` | Tarjeta vacía con mensaje + botón Analizar |
| **Analizando** | SSE activo | Panel progreso + tarjetas apareciendo |
| **Con resultados** | `totalSinFiltro > 0` | Filtros + tarjetas + paginación pendiente |
| **Filtrado sin coincidencias** | Filtros activos, `recomendaciones.isEmpty()` | Alert warning |
| **Error OpenAI** | Todas las evaluaciones fallaron | Aviso en panel SSE |
| **BDNS no disponible** | 0 candidatas | Mensaje "No se encontraron convocatorias" |

---

## FASE 3 — MAPEO PERFIL → CRITERIOS BDNS

### 3.1 Tabla de mapeo completa

| Campo perfil/proyecto | Campo BDNS equivalente | Transformación actual | Transformación óptima | Obligatorio | Impacto |
|----------------------|----------------------|----------------------|----------------------|-------------|---------|
| `proyecto.sector` | `descripcion` (keyword) | "subvención {sector}" en keyword | Mapear a taxonomía BDNS | ✅ Crítico | 🔴 ALTO |
| `proyecto.ubicacion` | `nivel1`/`nivel2` | Pre-filtro post-búsqueda | Parámetro directo en URL BDNS | ❌ No | 🟡 MEDIO |
| `proyecto.nombre` | `descripcion` (keyword) | Nombre completo en keyword | Extraer palabras clave del nombre | ❌ No | 🟡 MEDIO |
| `proyecto.descripcion` | `descripcion` (keyword vía IA) | IA extrae keywords del texto | ✅ Correcto | ❌ No | 🟢 ALTO |
| `perfil.sector` | `descripcion` (keyword fallback) | Igual que proyecto.sector | Igual | ✅ Crítico | 🔴 ALTO |
| `perfil.ubicacion` | `nivel1`/`nivel2` | Pre-filtro fallback | Parámetro URL si proyecto vacío | ❌ No | 🟡 MEDIO |
| `perfil.tipoEntidad` | `descripcion` (keyword) | "{tipo} subvención" | Mapear a categorías BDNS | ✅ Crítico | 🔴 ALTO |
| `perfil.objetivos` | `descripcion` (keyword vía IA) | IA extrae keywords | ✅ Correcto | ❌ No | 🟡 MEDIO |
| `perfil.necesidadesFinanciacion` | `descripcion` (keyword vía IA) | IA extrae keywords | ✅ Correcto | ❌ No | 🟡 MEDIO |
| `perfil.descripcionLibre` | `descripcion` (keyword vía IA) | IA extrae keywords (v3.3.0) | ✅ Correcto | ❌ No | 🟡 MEDIO |
| — | `vigente=true` | Siempre aplicado | ✅ Correcto | ✅ Crítico | 🔴 ALTO |
| — | `nivel1` directo | **No implementado** | Añadir según ubicación | ❌ No | 🟡 MEDIO |
| — | `importeDesde` / `importeHasta` | **No implementado** | Si perfil tiene rango importe | ❌ No | 🟢 BAJO |

### 3.2 Campos críticos (sin ellos el resultado falla gravemente)

1. **`proyecto.sector` o `perfil.sector`** — Es la base para generar keywords temáticas. Sin sector, las búsquedas son genéricas y producen miles de resultados irrelevantes.
2. **`vigente=true`** — Sin este filtro, la BDNS devuelve convocatorias cerradas de los últimos años.
3. **`proyecto.descripcion`** — El texto más rico para que la IA genere keywords precisas.

### 3.3 Campos que no aportan valor directamente a BDNS

- `perfil.necesidadesFinanciacion`: Solo útil para el refinamiento IA, no para keywords BDNS.
- `proyecto.nombre`: Solo útil si es muy descriptivo. Un nombre corto aporta poco.

### 3.4 Campos que deberían normalizarse

| Campo | Problema actual | Solución recomendada |
|-------|----------------|---------------------|
| `ubicacion` | Texto libre: "madrid", "Madrid", "Comunidad de Madrid", "CM" | `<select>` con 17 CCAA + "Nacional" |
| `sector` (perfil y proyecto) | `<select>` con 12 opciones fijas | ✅ Correcto, pero expandir a taxonomía BDNS |
| `tipoEntidad` | "PYME", "Startup", "Autónomo" — no coinciden con categorías BDNS | Mapear: PYME→"pequeña empresa", Startup→"empresa innovadora" |

---

## FASE 4 — FLUJO END-TO-END COMPLETO

### 4.1 Diagrama secuencial

```
┌──────┐  ┌────────────┐  ┌──────────────────┐  ┌──────────┐  ┌─────────┐
│Usuario│  │ Controller  │  │MotorMatchingService│  │OpenAI API│  │BDNS API │
└──┬───┘  └─────┬──────┘  └────────┬─────────┘  └────┬─────┘  └────┬────┘
   │             │                  │                  │              │
   │ Clic Analizar│                  │                  │              │
   │─────────────▶                  │                  │              │
   │             │ generarRecomendaciones               │              │
   │             │─────────────────▶│                  │              │
   │             │                  │ generarKeywords  │              │
   │             │                  │─────────────────▶│              │
   │             │ SSE: "Analizando"│◀─ keywords JSON  │              │
   │◀────────────│─────────────────-│                  │              │
   │             │                  │ buscarPorTexto() │              │
   │             │                  │─────────────────────────────────▶
   │             │                  │◀─ content[] JSON │              │
   │             │ SSE: "busqueda"  │                  │              │
   │◀────────────│──────────────────│                  │              │
   │             │                  │─ Pre-filtro geo  │              │
   │             │                  │                  │              │
   │             │                  │ Por cada candidata (≤15):       │
   │             │                  │ obtenerDetalle() │              │
   │             │                  │─────────────────────────────────▶
   │             │                  │◀─ detalle JSON   │              │
   │             │ SSE: "progreso"  │                  │              │
   │◀────────────│──────────────────│                  │              │
   │             │                  │ analizar()       │              │
   │             │                  │─────────────────▶│              │
   │             │                  │◀─ {puntuacion,   │              │
   │             │                  │    explicacion,  │              │
   │             │                  │    guia}         │              │
   │             │                  │                  │              │
   │             │                  │─ Si punt >= 20:  │              │
   │             │                  │   persistir BD   │              │
   │             │ SSE: "resultado" │                  │              │
   │◀────────────│──────────────────│                  │              │
   │             │                  │                  │              │
   │             │ SSE: "completado"│                  │              │
   │◀────────────│──────────────────│                  │              │
   │ reload (2.5s)│                 │                  │              │
   │─────────────▶                  │                  │              │
```

### 4.2 Llamadas HTTP reales por análisis

| # | Tipo | Destino | Descripción | Latencia estimada |
|---|------|---------|-------------|-------------------|
| 1 | POST | OpenAI API | Generación de 6-8 keywords | 1-3s |
| 2-9 | GET | BDNS `/busqueda` | 1 llamada por keyword (6-8 keywords) | 0.5-1s c/u → 3-8s |
| 10-24 | GET | BDNS `/convocatorias/{id}` | 1 detalle por candidata (hasta 15) | 0.5-1s c/u → 7-15s |
| 25-39 | POST | OpenAI API | 1 evaluación por candidata (hasta 15) | 2-4s c/u → 30-60s |
| **Total** | | | **27-41 llamadas HTTP** | **40-86 segundos** |

> La latencia dominante son las **15 evaluaciones OpenAI secuenciales** (30-60s de los ~40-86s totales).

### 4.3 Puntos críticos de fallo

| Punto de fallo | Probabilidad | Impacto | Mitigación actual | Mitigación recomendada |
|----------------|-------------|---------|-------------------|----------------------|
| **BDNS API no disponible** | Media (API no oficial) | Alto | try/catch por keyword | Cache de resultados 24h |
| **OpenAI rate limit** | Baja-Media | Alto | `OpenAiUnavailableException` | Retry con backoff |
| **SSL del gobierno** | Baja | Alto | SSLContext permisivo | Importar certificado real |
| **Timeout OpenAI (>30s)** | Media | Medio | ReadTimeout=30s | Reducir con paralelismo |
| **Cambio en API BDNS** | Media | Alto | Ninguna | Tests de integración + alertas |
| **0 candidatas BDNS** | Media | Bajo | Mensaje al usuario | Fallback con keywords básicas |

---

## FASE 5 — OPORTUNIDADES DE MEJORA

### 5.1 Cuellos de botella identificados

| Cuello de botella | Causa raíz | Coste actual | Mejora propuesta | Reducción estimada |
|------------------|-----------|--------------|-----------------|-------------------|
| **15 evaluaciones IA secuenciales** | Bucle `for` sin paralelismo | 30-60s | `CompletableFuture` en lotes de 5 | 60-70% tiempo |
| **15 llamadas BDNS detalle secuenciales** | Bucle `for` sin paralelismo | 7-15s | Paralelo con `ExecutorService` | 70-80% tiempo |
| **SSL permisivo por petición** | `SSLContext` nuevo innecesario | — | `SSLContext` singleton | Mínima |
| **Deduplicación solo por título** | `Map<titulo, DTO>` | Duplicados por idBdns | Deduplicar por `idBdns` primero | Mejora precisión |
| **Sin paginación en resultados** | No implementada | UX limitada | Paginación 10/página | UX |

### 5.2 Arquitectura optimizada propuesta

```
┌──────────────────────────────────────────────────────────────────────┐
│ ARQUITECTURA OPTIMIZADA v4.0                                         │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│ FASE A: NORMALIZACIÓN (0 latencia)                                  │
│  ubicacion → mapear a CCAA estándar (SELECT con 19 opciones)        │
│  sector → mantener SELECT actual (12 opciones)                       │
│  tipoEntidad → mapear a categorías BDNS                             │
│                                                                      │
│ FASE B: KEYWORDS (1 llamada IA, ~2s)                                │
│  OpenAI → 6-8 keywords optimizadas para BDNS                        │
│  Cache: 24h por hash(proyecto+perfil) → 0s si hit                   │
│                                                                      │
│ FASE C: BÚSQUEDA BDNS PARALELA (6-8 llamadas, ~1-2s paralelo)       │
│  CompletableFuture.allOf() para todas las keywords simultáneas       │
│  Parámetros adicionales: nivel1 si ubicacion="Cataluña"             │
│  Cache: 4h por keyword + vigente                                     │
│                                                                      │
│ FASE D: PRE-FILTROS (0 latencia)                                    │
│  ✅ Geográfico (ya implementado v3.3.0)                             │
│  ⚠️ Por idBdns: deduplicar antes del título                         │
│  ⚠️ Temporal: descartar publicadas hace >18 meses si siguen abiertas│
│                                                                      │
│ FASE E: DETALLE BDNS PARALELO (hasta 15 llamadas, ~1-2s paralelo)   │
│  CompletableFuture.allOf() para todos los detalles simultáneos       │
│  Cache: 24h por idBdns                                              │
│                                                                      │
│ FASE F: EVALUACIÓN IA EN LOTES (15 evaluaciones en lotes de 5)      │
│  Lote 1 (candidatas 1-5): paralelo → ~3-5s                         │
│  Lote 2 (candidatas 6-10): paralelo → ~3-5s                        │
│  Lote 3 (candidatas 11-15): paralelo → ~3-5s                       │
│  Total estimado: ~10-15s (vs. 30-60s actual)                        │
│                                                                      │
│ LATENCIA TOTAL ESTIMADA: ~15-20s (vs. 40-86s actual)               │
│ REDUCCIÓN: ~60-75%                                                   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.3 Estrategia de cache

| Dato a cachear | TTL | Clave de cache | Implementación |
|----------------|-----|----------------|----------------|
| Keywords por proyecto+perfil | 24h | `SHA256(proyectoId + perfilHash)` | Spring Cache + ConcurrentHashMap |
| Resultados BDNS por keyword | 4h | `"bdns:" + keyword + ":vigente"` | Spring Cache |
| Detalle BDNS por idBdns | 24h | `"bdns:detalle:" + idBdns` | Spring Cache |
| Recomendaciones por proyecto | Sin TTL (invalidar al regenerar) | Persistidas en BD | Ya implementado |

### 5.4 Estrategia de reducción de latencia

**Quick wins (< 1 día de implementación):**

1. **Paralelizar detalle BDNS** — Las 15 llamadas a `/api/convocatorias/{id}` son independientes. Usar `CompletableFuture.allOf()` reduce de ~10-15s a ~1-2s.

2. **Deduplicar por `idBdns`** antes que por título — Más fiable, evita evaluar la misma convocatoria dos veces si aparece con títulos ligeramente diferentes en dos keywords.

3. **Normalizar `ubicacion` a `<select>`** — Elimina el pre-filtro geográfico heurístico (substring matching) y permite usar `nivel1`/`nivel2` directamente en BDNS.

**Mejoras de impacto medio (1-3 días):**

4. **Paralelizar evaluaciones IA en lotes de 5** — Reduce evaluación de ~30-60s a ~10-15s.

5. **Cache de keywords** — Si el usuario regenera recomendaciones sin cambiar perfil/proyecto, reutiliza las mismas keywords sin llamar a OpenAI.

6. **Cache de detalles BDNS** — Los detalles de convocatoria no cambian frecuentemente. Cache 24h reduce llamadas repetidas.

---

## ENTREGABLE — RIESGOS TÉCNICOS

| Riesgo | Probabilidad | Impacto | Plan de mitigación |
|--------|-------------|---------|-------------------|
| **API BDNS cambia sin aviso** | Alta (API no oficial) | Crítico | Tests de integración diarios, alertas de respuesta, fallback a búsqueda por título |
| **OpenAI aumenta latencia** | Media | Alto | Timeout configurable, paralelismo, reducción de prompts |
| **Rate limit BDNS** | Media | Medio | Rate limiter local, cache agresiva |
| **SSL del gobierno revocado** | Baja | Alto | Importar certificado FNMT/AEAT en JVM truststore |
| **Cambio en estructura JSON BDNS** | Media | Alto | Tests de deserialización, logging detallado del JSON crudo |
| **Deuda técnica: SSLContext permisivo** | — | Alto | Importar cert real en keystore del servidor |

---

## ENTREGABLE — RECOMENDACIONES PRIORIZADAS

| # | Mejora | Prioridad | Esfuerzo | Impacto |
|---|--------|-----------|----------|---------|
| 1 | Paralelizar llamadas detalle BDNS (`CompletableFuture.allOf`) | 🔴 Crítica | 2h | -70% latencia fase detalle |
| 2 | Paralelizar evaluaciones IA en lotes de 5 | 🔴 Crítica | 4h | -60% latencia fase IA |
| 3 | Deduplicar por `idBdns` antes de por título | 🟡 Alta | 30min | Precisión +10% |
| 4 | Normalizar `ubicacion` a `<select>` con 19 CCAA | 🟡 Alta | 2h | Pre-filtro exacto |
| 5 | Cache de keywords por proyecto+perfil | 🟡 Alta | 2h | -100% coste keywords en regeneración |
| 6 | Cache de detalles BDNS por idBdns (TTL 24h) | 🟡 Alta | 2h | -70% llamadas BDNS repetidas |
| 7 | Tests de integración automáticos contra API BDNS | 🟡 Alta | 1 día | Detectar cambios de API |
| 8 | Importar certificado SSL BDNS real en JVM | 🟢 Media | 1h | Eliminar riesgo seguridad |
| 9 | Paginación en vista recomendaciones | 🟢 Media | 3h | UX mejora |
| 10 | Parámetros `nivel1`/`nivel2` en búsqueda BDNS | 🟢 Baja | 1h | Pre-filtro a nivel API |

