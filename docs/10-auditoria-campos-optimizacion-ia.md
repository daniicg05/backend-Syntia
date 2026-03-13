# Auditoría de Campos, Optimización IA y Galería Visual — Syntia

## Alineación Arquitectónica Vigente (2026-03-13)

> Este informe conserva hallazgos técnicos válidos; aplicar estas reglas de arquitectura para evolución actual:
>
> - Backend fijo: `Java 17 + Spring Boot + Maven + PostgreSQL + JWT + SSE`.
> - Frontend objetivo: `Angular + API REST`.
> - Referencias a interfaces históricas deben entenderse como transición hacia SPA.
> - Se prioriza `controller/api/`, JWT y flujo `BDNS+IA`.
> - La lógica funcional auditada se mantiene en servicios.

---

## FASE 1 — AUDITORÍA COMPLETA DE CAMPOS

### 1.1 Inventario de campos: Perfil

| Campo | Frontend (HTML) | DTO (`PerfilDTO`) | Entidad (`Perfil`) | Usado en keywords | Usado en prompt evaluación | Impacto real en matching | Observaciones |
|-------|----------------|-------------------|--------------------|--------------------|---------------------------|--------------------------|---------------|
| `sector` | ✅ `<select>` 12 opciones | ✅ `@NotBlank` | ✅ `@NotBlank` | ✅ `generarKeywordsBasicas()` — `"ayuda " + perfil.getSector()` | ✅ `construirPrompt()` — "Sector de actividad" | 🟢 **ALTO** — alimenta keywords BDNS + evaluación IA | Correcto. Campo obligatorio, bien aprovechado |
| `ubicacion` | ✅ `<input text>` libre | ✅ `@NotBlank` | ✅ `@NotBlank` | ❌ **NO se usa** en `generarKeywordsBusqueda()` | ✅ `construirPrompt()` — "Ubicación" | 🟡 **MEDIO** — solo IA, no filtra BDNS | ⚠️ **GAP:** La ubicación del perfil NO se usa para generar keywords ni filtrar por CCAA en BDNS |
| `tipoEntidad` | ✅ `<select>` 9 opciones | ✅ opcional | ✅ `@Column` | ✅ `generarKeywordsBasicas()` — `"subvención " + tipoEntidad` | ✅ `construirPrompt()` — "Tipo de entidad" | 🟢 **ALTO** — clave para elegibilidad | Correcto. Bien integrado en ambas fases |
| `objetivos` | ✅ `<textarea>` max 500 | ✅ `@Size(500)` | ✅ `@Column` | ✅ `construirPromptKeywords()` — "Objetivos: ..." | ✅ `construirPrompt()` — "Objetivos" | 🟡 **MEDIO** — solo texto para IA | Correcto. Útil para contexto semántico |
| `necesidadesFinanciacion` | ✅ `<textarea>` max 500 | ✅ `@Size(500)` | ✅ `@Column` | ✅ `construirPromptKeywords()` — "Necesidades: ..." | ✅ `construirPrompt()` — "Necesidades de financiación" | 🟡 **MEDIO** — refinamiento semántico | Correcto. Aporta contexto |
| `descripcionLibre` | ✅ `<textarea>` max 2000 | ✅ `@Size(2000)` | ✅ `TEXT` | ❌ **NO se usa** en keywords | ✅ `construirPrompt()` — "Descripción libre" (condicional) | 🟡 **MEDIO** — solo si hay datos | ⚠️ **GAP:** Hasta 2000 chars que podrían aportar keywords valiosas, pero solo se envían al prompt de evaluación |

### 1.2 Inventario de campos: Proyecto

| Campo | Frontend (HTML) | DTO (`ProyectoDTO`) | Entidad (`Proyecto`) | Usado en keywords | Usado en prompt evaluación | Impacto real en matching | Observaciones |
|-------|----------------|---------------------|----------------------|--------------------|---------------------------|--------------------------|---------------|
| `nombre` | ✅ `<input>` max 150 | ✅ `@NotBlank` | ✅ `@NotBlank` | ✅ `construirPromptKeywords()` — "Nombre: ..." | ✅ `construirPrompt()` — "Nombre" | 🟢 **ALTO** — alimenta keywords + evaluación | Correcto |
| `sector` | ✅ `<select>` 12 opciones | ✅ opcional | ✅ `@Column` | ✅ `construirPromptKeywords()` — "Sector: ..." | ✅ `construirPrompt()` — "Sector" | 🟢 **ALTO** — principal driver de keywords | ⚠️ **Posible redundancia** con `Perfil.sector` — si ambos son "Tecnología", se duplica en prompt |
| `ubicacion` | ✅ `<input text>` libre | ✅ opcional | ✅ `@Column` | ✅ `construirPromptKeywords()` — "Ubicación: ..." | ✅ `construirPrompt()` — "Ubicación" | 🟡 **MEDIO** — genera keyword pero NO filtra BDNS | ⚠️ **GAP:** Igual que perfil — la ubicación no filtra vía API BDNS |
| `descripcion` | ✅ `<textarea>` max 2000 | ✅ `@Size(2000)` | ✅ `TEXT` | ✅ `construirPromptKeywords()` — "Descripción: ..." | ✅ `construirPrompt()` — "Descripción" | 🟢 **ALTO** — texto más rico para IA | Correcto |

### 1.3 Hallazgos críticos

#### 🔴 GAP 1: `Perfil.ubicacion` no genera keywords geográficas
- **Situación:** `construirPromptKeywords()` solo usa `proyecto.ubicacion`, nunca `perfil.ubicacion`.
- **Impacto:** Si el usuario tiene perfil con "Andalucía" pero el proyecto no tiene ubicación, se pierden keywords geográficas.
- **Recomendación:** En `construirPromptKeywords()`, añadir `perfil.ubicacion` si `proyecto.ubicacion` está vacío.

#### 🔴 GAP 2: `Perfil.descripcionLibre` no alimenta keywords
- **Situación:** Hasta 2000 chars de texto rico que solo se usan en el prompt de evaluación (por candidata), nunca para generar keywords.
- **Impacto:** El usuario puede escribir "buscamos financiación para digitalización IoT en sector agrario andaluz" y esas keywords no llegan a BDNS.
- **Recomendación:** Incluir `descripcionLibre` (truncada) en `construirPromptKeywords()`.

#### 🟡 GAP 3: Ubicación no filtra en BDNS
- **Situación:** La API BDNS acepta filtros por `nivel1` (ESTADO/AUTONOMICA/LOCAL) y `nivel2` (comunidad autónoma), pero `buscarPorTexto()` no los usa — solo busca por `descripcion`.
- **Impacto:** Se evalúan convocatorias de ámbito geográfico incompatible (ej: convocatoria de Cataluña para un proyecto en Andalucía).
- **Recomendación:** Investigar si `nivel1` y `nivel2` se pueden pasar como parámetros de query a la API BDNS para pre-filtrar.

#### 🟡 GAP 4: Sector duplicado perfil vs. proyecto
- **Situación:** Si `perfil.sector = "Tecnología"` y `proyecto.sector = "Tecnología"`, ambos se envían al prompt de evaluación como campos separados, consumiendo tokens redundantes.
- **Recomendación:** En `construirPrompt()`, si `perfil.sector` == `proyecto.sector`, omitir uno de los dos.

#### 🟢 GAP 5: `Perfil.sector` y `Perfil.ubicacion` son campos no normalizados
- **Situación:** `ubicacion` es `<input text>` libre — el usuario puede escribir "Madrid", "madrid", "Comunidad de Madrid", "CM" o "28001". `sector` usa `<select>` con opciones fijas, lo cual es correcto.
- **Impacto:** Ubicaciones no normalizadas pueden dar keywords pobres.
- **Recomendación:** Considerar un `<select>` con las 17 CCAA + "Nacional" + "Ceuta" + "Melilla" para ubicación.

---

## FASE 2 — ANÁLISIS Y DISEÑO DE FILTRADO ÓPTIMO

### 2.1 Evaluación de estrategias

| Estrategia | Tokens consumidos | Velocidad | Precisión | Escalabilidad | ¿Viable ahora? |
|-----------|-------------------|-----------|-----------|---------------|-----------------|
| **A) Filtrado SQL previo** (por sector, región en BD local) | Mínimo | ⚡ Rápida | 🟡 Media (solo datos locales) | ✅ Alta | ❌ No — las convocatorias no están en BD local, se buscan en BDNS en tiempo real |
| **B) Filtrado híbrido** (parámetros BDNS + evaluación IA) | Bajo | 🟢 Buena | 🟢 Alta | ✅ Alta | ✅ **SÍ — RECOMENDADA** |
| **C) Búsqueda vectorial** (embeddings) | Medio (setup) | 🟡 Media | 🟢 Alta | 🟡 Media | ❌ No — requiere infraestructura de vectores (Pinecone/pgvector) |
| **D) IA sin filtrado** (todas a OpenAI) | 🔴 Máximo | 🔴 Lenta | 🟢 Alta | ❌ Baja | ❌ No — es el problema actual |

### 2.2 Estrategia recomendada: B) Filtrado Híbrido

```
┌────────────────────────────────────────────────────────────────┐
│ FLUJO OPTIMIZADO DE FILTRADO                                    │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. NORMALIZACIÓN (sin IA, sin tokens)                         │
│     └─ sector → normalizar contra lista fija                   │
│     └─ ubicacion → mapear a CCAA/Nacional                      │
│     └─ tipoEntidad → mapear a categorías BDNS                 │
│                                                                 │
│  2. FILTRADO BDNS (API, sin IA)                                │
│     └─ keywords generadas por IA (ya implementado)             │
│     └─ &vigente=true (ya implementado)                         │
│     └─ Deduplicación por título (ya implementado)              │
│     └─ ⚠️ NUEVO: filtro geográfico si BDNS lo soporta        │
│                                                                 │
│  3. PRE-FILTRO LOCAL (sin IA, sin tokens)                      │
│     └─ ⚠️ NUEVO: descartar por ámbito geográfico              │
│        Si proyecto.ubicacion="Andalucía" y convocatoria        │
│        es de nivel2="Cataluña" → descartar sin evaluar con IA  │
│     └─ ⚠️ NUEVO: descartar si título contiene sector          │
│        claramente incompatible (heurística básica)             │
│                                                                 │
│  4. EVALUACIÓN IA (tokens, solo las filtradas)                 │
│     └─ OpenAI gpt-4.1 evalúa solo las candidatas filtradas    │
│     └─ ~15 máximo (ya implementado)                            │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

### 2.3 Campos según fase

| Campo | Fase de uso | Justificación |
|-------|------------|---------------|
| `proyecto.sector` | Keywords (IA) + Pre-filtro | Genera keywords temáticas, descarta incompatibles |
| `proyecto.ubicacion` | Pre-filtro geográfico | Descarta convocatorias de CCAA incompatibles |
| `proyecto.nombre` | Keywords (IA) | Contexto semántico para búsqueda |
| `proyecto.descripcion` | Keywords (IA) + Evaluación IA | Texto rico para semántica |
| `perfil.sector` | Keywords (IA, fallback) | Solo si proyecto.sector está vacío |
| `perfil.ubicacion` | Pre-filtro geográfico (fallback) | Solo si proyecto.ubicacion está vacío |
| `perfil.tipoEntidad` | Keywords (IA) + Evaluación IA | Clave para elegibilidad |
| `perfil.objetivos` | Keywords (IA) | Contexto |
| `perfil.necesidadesFinanciacion` | Keywords (IA) | Contexto |
| `perfil.descripcionLibre` | Keywords (IA, truncada) + Evaluación IA | Texto rico |
| `convocatoria.urlOficial` | **Excluir del prompt** | No aporta valor semántico, gasta tokens |
| Etiquetas repetidas ("No indicado") | **Excluir del prompt** | Gasta tokens sin información |

---

## FASE 3 — ANÁLISIS DE USO DE LA API OPENAI

### 3.1 Diagnóstico de por qué puede tardar

| Factor | Estado actual | Impacto en tiempo | Impacto en tokens |
|--------|--------------|-------------------|-------------------|
| **15 llamadas secuenciales a OpenAI** | Cada evaluación espera respuesta (~2-4s) | 🔴 ~30-60s total | — |
| **System prompt de 1100+ chars** | Se envía 15 veces idéntico | — | 🔴 ~15×300 = ~4500 tokens repetidos |
| **User prompt con datos redundantes** | URL, "No indicado", sector duplicado perfil/proyecto | — | 🟡 ~50-100 tokens desperdiciados ×15 |
| **max_tokens=500** | Permite respuestas largas pero la guía de 8 pasos lo necesita | — | 🟡 Adecuado para 8 pasos |
| **Detalle BDNS truncado a 1500 chars** | Puede incluir HTML/formato no útil | — | 🟡 ~300-400 tokens de ruido |
| **Latencia red: backend→OpenAI→backend** | ~1-2s por llamada | 🔴 ×15 | — |
| **Latencia red: backend→BDNS detalle** | 1 llamada por candidata | 🟡 ~0.5-1s ×15 | — |
| **Sin paralelismo** | Las 15 evaluaciones son secuenciales | 🔴 Cuello de botella principal | — |

### 3.2 Estimación de tokens por análisis

| Componente | Tokens entrada | Tokens salida | ×15 candidatas | Total |
|-----------|---------------|---------------|----------------|-------|
| System prompt (evaluación) | ~300 | — | ×15 = 4500 | 4500 |
| User prompt (convocatoria+perfil+proyecto) | ~500-800 | — | ×15 = 7500-12000 | ~10000 |
| Respuesta JSON (8 pasos) | — | ~350-500 | ×15 = 5250-7500 | ~6000 |
| System prompt (keywords) | ~120 | — | ×1 | 120 |
| User prompt (keywords) | ~100 | — | ×1 | 100 |
| Respuesta keywords | — | ~50 | ×1 | 50 |
| **TOTAL** | | | | **~20.000-21.000** |

**Coste estimado por análisis:** ~$0.04-0.06 with gpt-4.1 (~$2.50/1M input, $10/1M output).

### 3.3 Mejoras concretas recomendadas

#### 3.3.1 Eliminar datos vacíos del prompt (ahorro: ~5-10% tokens)
Actualmente se envían strings como "No indicado", "No indicados", "No indicada" cuando un campo está vacío. Estos tokens no aportan información.

**Cambio:** Solo incluir campos que tienen valor real.

#### 3.3.2 Deduplicar sector perfil/proyecto (ahorro: ~2% tokens)
Si `perfil.sector == proyecto.sector`, no repetirlo.

#### 3.3.3 Excluir URL del prompt (ahorro: ~1-2% tokens)
`convocatoria.urlOficial` no aporta valor semántico y puede tener 80+ chars.

#### 3.3.4 Limpiar detalle BDNS antes de enviar (ahorro: ~5-15% tokens)
El texto de BDNS puede contener HTML, saltos de línea múltiples, espacios repetidos. Limpiarlo antes de truncar.

#### 3.3.5 Paralelizar evaluaciones (ahorro: ~60-70% tiempo)
Ejecutar las 15 evaluaciones con `CompletableFuture.supplyAsync()` en paralelo (lotes de 5).

---

## FASE 4 — ARQUITECTURA OPTIMIZADA PROPUESTA

### 4.1 Flujo optimizado completo

```
┌──────────────────────────────────────────────────────────────────────┐
│ FLUJO OPTIMIZADO v4.0                                                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ENTRADA: Proyecto + Perfil del usuario                             │
│  ────────────────────────────────────────                           │
│                                                                      │
│  1. NORMALIZACIÓN (0 tokens, 0 latencia)                            │
│     ├─ sector → normalizar contra lista fija                        │
│     ├─ ubicacion → mapear a CCAA estándar                           │
│     └─ Fusionar campos perfil+proyecto sin duplicar                 │
│                                                                      │
│  2. GENERACIÓN DE KEYWORDS (1 llamada IA, ~200 tokens)             │
│     ├─ Incluir: nombre, sector, ubicación, descripción, tipoEntidad│
│     ├─ NUEVO: incluir descripcionLibre (truncada a 300 chars)       │
│     └─ Respuesta: 6-8 keywords de 2-4 palabras                     │
│                                                                      │
│  3. BÚSQUEDA BDNS (0 tokens, ~6-8 llamadas API)                    │
│     ├─ buscarPorTexto(keyword, vigente=true) por cada keyword       │
│     ├─ Deduplicación por título                                      │
│     └─ Límite: 15 candidatas únicas                                  │
│                                                                      │
│  4. PRE-FILTRO GEOGRÁFICO (0 tokens, 0 latencia) ← NUEVO           │
│     ├─ Si proyecto.ubicacion != null:                                │
│       ├─ Convocatoria nivel1=AUTONOMICA y nivel2 ≠ ubicación → ❌  │
│       └─ Convocatoria nivel1=ESTADO o nivel2 = ubicación → ✅      │
│     └─ Reduce candidatas de ~15 a ~10-12                            │
│                                                                      │
│  5. OBTENCIÓN DE DETALLE BDNS (0 tokens, 1 llamada por candidata)   │
│     ├─ obtenerDetalleTexto(idBdns)                                   │
│     └─ NUEVO: limpiar HTML, normalizar espacios, truncar a 1200     │
│                                                                      │
│  6. EVALUACIÓN IA (15×~800 tokens entrada + 15×~450 tokens salida)  │
│     ├─ NUEVO: prompt optimizado sin datos vacíos ni URL             │
│     ├─ NUEVO: paralelismo en lotes de 5                              │
│     ├─ Umbral ≥ 20 para persistir                                   │
│     └─ SSE: resultado parcial por cada candidata evaluada           │
│                                                                      │
│  7. PERSISTENCIA SELECTIVA (solo las ≥ umbral)                      │
│     ├─ Convocatoria → BD (si no existe)                              │
│     ├─ Recomendación → BD con guía de 8 pasos                       │
│     └─ NUEVO: hash SHA-256(proyectoId+convocatoriaTitulo)           │
│        como identificador único de solicitud                         │
│                                                                      │
│  SALIDA: SSE streaming + recarga con vista completa                  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.2 Prompt optimizado propuesto

**Principio:** Solo enviar datos que existen y que aportan valor.

```java
// ANTES (actual):
sb.append("Organismo convocante: ").append(Optional.ofNullable(convocatoria.getFuente()).orElse("No especificado")).append("\n");
sb.append("Tipo de convocatoria: ").append(Optional.ofNullable(convocatoria.getTipo()).orElse("No especificado")).append("\n");

// DESPUÉS (optimizado):
appendIfPresent(sb, "Organismo", convocatoria.getFuente());
appendIfPresent(sb, "Tipo", convocatoria.getTipo());

// Método helper:
private void appendIfPresent(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) {
        sb.append(label).append(": ").append(value).append("\n");
    }
}
```

### 4.3 Estrategia de unicidad por solicitud

| Concepto | Implementación |
|----------|---------------|
| **Identificador único** | `SHA-256(proyectoId + "|" + convocatoriaTitulo + "|" + timestamp)` |
| **Trazabilidad** | Campo `generadaEn` en `Recomendacion` (ya existe) |
| **No reutilización** | `deleteByProyectoId()` limpia anteriores antes de regenerar (ya implementado) |
| **Cache segmentada** | No aplica — cada análisis genera datos frescos de BDNS |

---

## FASE 5 — GALERÍA VISUAL DEL FLUJO WEB OFICIAL

> La galería visual del flujo web ya fue implementada en v3.2.0 dentro del modal de guía de solicitud:
> - **Stepper horizontal** (8 nodos con iconos): Portal → Requisitos → Documentación → Sede → Plazos → Régimen → Post-concesión → Advertencias
> - **Timeline vertical** con iconos, colores y texto IA personalizado por convocatoria
> - **Wireframes detallados** en `docs/09-auditoria-guia-subvenciones.md` (8 pantallas)
> 
> Los wireframes y el diagrama de estados completo están documentados en la Fase 5 de `09-auditoria-guia-subvenciones.md`.

### 5.1 Cómo se genera una galería única por solicitud

Cada convocatoria recomendada tiene una guía de 8 pasos generada específicamente para ella por OpenAI. El contenido de cada paso varía según:

1. El **contenido oficial de la convocatoria** (extraído de BDNS vía `obtenerDetalleTexto(idBdns)`).
2. Los **datos del perfil y proyecto** del usuario.
3. Las **bases reguladoras** específicas (si están disponibles en el detalle BDNS).

La galería visual (stepper + timeline) es la misma estructura, pero el **contenido de cada paso es único por convocatoria**.

---

## FASE 6 — ENTREGABLE FINAL

### E1. Nivel de eficiencia actual

| Dimensión | Nivel | Justificación |
|-----------|-------|---------------|
| **Uso de campos** | 🟡 MEDIO | 2 campos no alimentan keywords (perfil.ubicacion, perfil.descripcionLibre). Datos vacíos se envían como "No indicado" consumiendo tokens |
| **Filtrado pre-IA** | 🟡 MEDIO | vigente=true y deduplicación son correctos, pero falta pre-filtro geográfico |
| **Consumo de tokens** | 🟡 MEDIO | ~20.000 tokens/análisis. Reducible a ~15.000-17.000 con optimizaciones |
| **Velocidad** | 🔴 BAJO | 30-60s por análisis (15 evaluaciones secuenciales). Reducible a ~10-15s con paralelismo |
| **Calidad de resultados** | 🟢 ALTO | El system prompt de 8 pasos con LGS es excelente. Detalle BDNS aporta mucho valor |
| **UI/UX de la guía** | 🟢 ALTO | Stepper visual + timeline con iconos es excelente |

### E2. Recomendaciones priorizadas

| # | Mejora | Prioridad | Esfuerzo | Reducción tokens | Reducción tiempo |
|---|--------|-----------|----------|------------------|-----------------|
| 1 | **Optimizar prompt**: eliminar datos vacíos, URL, deduplicar sector | 🔴 Crítica | 30 min | ~10-15% | — |
| 2 | **Incluir `descripcionLibre` en keywords** | 🔴 Crítica | 15 min | — | Mejora precisión |
| 3 | **Incluir `perfil.ubicacion` como fallback en keywords** | 🟡 Alta | 10 min | — | Mejora precisión |
| 4 | **Pre-filtro geográfico** antes de evaluar con IA | 🟡 Alta | 1h | ~20-30% (menos candidatas) | ~20-30% |
| 5 | **Limpiar HTML del detalle BDNS** antes de enviar al prompt | 🟡 Alta | 30 min | ~5-10% | — |
| 6 | **Paralelizar evaluaciones** en lotes de 5 | 🟢 Media | 2-3h | — | ~60-70% |
| 7 | **Cachear detalle BDNS** por idBdns (TTL 24h) | 🟢 Media | 1-2h | — | ~15-20% |

### E3. Impacto estimado tras implementar mejoras 1-5

| Métrica | Antes | Después | Mejora |
|---------|-------|---------|--------|
| Tokens por análisis | ~20.000 | ~14.000-16.000 | ~20-30% |
| Tiempo de análisis | ~30-60s | ~25-45s | ~15-25% |
| Candidatas evaluadas por IA | 15 (fijas) | 10-12 (tras pre-filtro geográfico) | ~20-30% |
| Coste por análisis | ~$0.05 | ~$0.03-0.04 | ~25-35% |

---

*Fin del informe.*
