# 13 – Plan de Implementación por Fases: v4.0.0

**Fecha:** 2026-03-11  
**Versión actual:** v4.0.0  
**Versión objetivo:** v4.0.0  
**Estado:** ✅ COMPLETADO — 4/4 fases implementadas

---

## Diagnóstico Previo

### Problema 1 — Galería visual no funciona correctamente

**Síntomas detectados:**

1. **Mockups no parecen reales**: Las tarjetas muestran un header institucional + 2 campos grises + 1 botón. No simulan la interfaz real de los portales gubernamentales (menú, breadcrumbs, formularios con labels, sidebar).

2. **`portal.hint` hardcodeado ignora la IA**: Cuando se detecta un portal conocido (AEAT, TGSS, etc.), el hint es siempre el mismo texto fijo del mapa `PORTALES`, ignorando el `step.user_action` que la IA generó específicamente para esa convocatoria. Resultado: todos los pasos AEAT muestran "Pulsa Certificados → Estar al corriente" aunque la convocatoria sea de otro tipo.

3. **Portales no reconocidos → mockup vacío**: Solo se reconocen 8 portales centrales. Cualquier convocatoria autonómica o ministerial (la mayoría de convocatorias reales) genera un mockup genérico con 2 items de nav ("Trámite actual", "Ayuda") que se ve como un placeholder.

4. **Steps sin `official_link` → tarjeta rota**: Pasos como "Preparar documentación" o "Firmar con AutoFirma" no siempre tienen URL. La tarjeta queda sin URL en la barra del navegador y sin mockup útil.

5. **Datos de `visual_guides` se desperdician**: La IA genera `visual_guides[].steps[].screen_hint` e `image_prompt` pero el frontend los ignora completamente (usa `workflows[0].steps` como fuente única).

6. **Mapa `PORTALES` duplicado**: Existe como `PORTALES` (galería) y `PORTALES_LB` (lightbox) con los mismos datos pero mantenidos por separado.

### Problema 2 — Pipeline de búsqueda ineficiente

**Según doc 12-refactoring y estado actual del código:**

1. El pipeline actual genera 6-8 keywords con OpenAI → busca cada una en BDNS → trae hasta 120 candidatas brutas → filtra en memoria. Desperdicia HTTP y tokens.

2. `buscarPorTextoFiltrado()` ya existe con `nivel1`/`nivel2` (implementado en v3.4.0) pero aún depende de keywords de texto libre generadas por IA.

3. `UbicacionNormalizador.java` ya existe con mapeo completo CCAA ↔ variantes.

4. La propuesta BDNS-First elimina la dependencia de OpenAI en la fase de búsqueda: los campos estructurados del modelo (`sector`, `ubicacion`, `tipoEntidad`) se mapean directamente a parámetros BDNS.

5. **Beneficio estimado**: -50% latencia, -100% dependencia OpenAI en búsqueda, -35% tokens consumidos.

---

## Fases de Implementación

### FASE 1 — Galería visual: corrección completa (v3.5.0)
> **Prioridad:** CRÍTICA — Bug visible al usuario  
> **Esfuerzo:** 4-6 horas  
> **Dependencias:** Ninguna  
> **Estado:** ✅ COMPLETADA

| Paso | Descripción | Archivos | Estado |
|------|-------------|----------|--------|
| 1.1 | Extraer `PORTALES` a variable compartida `window.PORTALES_GOB`, eliminar duplicado `PORTALES_LB` | `recomendaciones.html` | ✅ |
| 1.2 | Ampliar mapa con sedes autonómicas (Andalucía, Valencia, Cataluña, Galicia, País Vasco, Madrid) y ministerios clave | `recomendaciones.html` | ✅ |
| 1.3 | Añadir matcher inteligente por subdominio: si hostname contiene `sede.` + `.gob.es`/`.es`, generar portal institucional genérico con color y nombre inferidos | `recomendaciones.html` | ✅ |
| 1.4 | **Priorizar `user_action` de la IA sobre hint hardcodeado**: si el step tiene `user_action`, usarlo como hint; sino, fallback al `portal.cta` | `recomendaciones.html` | ✅ |
| 1.5 | Mejorar mockup realista: añadir breadcrumbs, labels de campos reales (`NIF/CIF`, `Tipo de certificado`...), formularios con label-input rows, CTA dinámico | `recomendaciones.html` | ✅ |
| 1.6 | Diseño alternativo tipo checklist para steps sin `official_link`: lista de documentos/acciones con checks, ícono de fase, sin barra de navegador | `recomendaciones.html` | ✅ |
| 1.7 | Enriquecer mockup con datos de `visual_guides` si existen: usar `screen_hint` como URL fallback, `image_prompt` como hint fallback | `recomendaciones.html` | ✅ |
| 1.8 | Compilación BUILD SUCCESS + Tests pasados | — | ✅ |

**Entregable:** Galería con mockups realistas que muestran la interfaz real de cada portal con indicador de dónde hacer clic.

---

### FASE 2 — Pipeline BDNS-First: constructor de filtros (v3.6.0)
> **Prioridad:** ALTA — Reducción de latencia y coste  
> **Esfuerzo:** 6-8 horas  
> **Dependencias:** Ninguna (independiente de Fase 1)  
> **Estado:** ✅ COMPLETADA

| Paso | Descripción | Archivos | Estado |
|------|-------------|----------|--------|
| 2.1 | Crear `SectorNormalizador.java` — clase utilitaria estática con mapeo sector texto libre → términos BDNS. 50+ sectores + fallback. | `service/SectorNormalizador.java` (NUEVO) | ✅ |
| 2.2 | Crear record `FiltrosBdns.java` — inmutable: `(String descripcion, String nivel1, String nivel2)` con métodos `sinDescripcion()` y `sinTerritorio()` para fallback | `model/dto/FiltrosBdns.java` (NUEVO) | ✅ |
| 2.3 | Crear `BdnsFiltrosBuilder.java` — clase utilitaria que recibe `Proyecto` + `Perfil` y devuelve `FiltrosBdns` usando `UbicacionNormalizador` + `SectorNormalizador` | `service/BdnsFiltrosBuilder.java` (NUEVO) | ✅ |
| 2.4 | Añadir `buscarPorFiltros(FiltrosBdns)` en `BdnsClientService` con URL construida con parámetros estructurados y fallback progresivo (si < 3 resultados → ampliar) | `service/BdnsClientService.java` | ✅ |
| 2.5 | Tests unitarios: 17 tests para `SectorNormalizador`, `FiltrosBdns`, `BdnsFiltrosBuilder` | `test/.../BdnsFirstPipelineTest.java` (NUEVO) | ✅ |

**Entregable:** Infraestructura de búsqueda estructurada lista para integrar en el motor.

---

### FASE 3 — Pipeline BDNS-First: integración en el motor (v4.0.0)
> **Prioridad:** ALTA  
> **Esfuerzo:** 8-10 horas  
> **Dependencias:** Fase 2 completada  
> **Estado:** ✅ COMPLETADA

| Paso | Descripción | Archivos | Estado |
|------|-------------|----------|--------|
| 3.1 | Añadir `deduplicarYFiltrarCaducadas()` y `aplicarPreFiltroGeografico()` como helpers compartidos entre ambos métodos públicos | `service/MotorMatchingService.java` | ✅ |
| 3.2 | Modificar `generarRecomendaciones()` — reemplazar `generarKeywords()` + `buscarEnBdns()` por `BdnsFiltrosBuilder.construir()` + `bdnsClientService.buscarPorFiltros()` | `service/MotorMatchingService.java` | ✅ |
| 3.3 | Modificar `generarRecomendacionesStream()` — mismo cambio + reemplazar evento SSE `"keywords"` por evento `"filtros"` con descripcion y ccaa | `service/MotorMatchingService.java` | ✅ |
| 3.4 | Eliminar de `MotorMatchingService`: `generarKeywords()`, `generarKeywordsBasicas()`. Mantener `buscarEnBdns()` como método privado legacy (safety net) | `service/MotorMatchingService.java` | ✅ |
| 3.5 | Eliminar de `OpenAiMatchingService`: `KEYWORDS_SYSTEM_PROMPT`, `generarKeywordsBusqueda()`, `construirPromptKeywords()`, `parsearKeywords()`, `generarKeywordsBasicas()` (~90 líneas). Mantener intacto `analizar()` | `service/OpenAiMatchingService.java` | ✅ |
| 3.6 | Actualizar `recomendaciones-stream.js` — handler de evento `"keywords"` → `"filtros"` | `static/javascript/recomendaciones-stream.js` | ✅ |
| 3.7 | Compilación BUILD SUCCESS + 18 tests passed | — | ✅ |

**Entregable:** Motor BDNS-First funcional, sin dependencia de OpenAI para búsqueda.

---

### FASE 4 — Documentación y limpieza (v4.0.0)
> **Prioridad:** MEDIA  
> **Esfuerzo:** 2-3 horas  
> **Dependencias:** Fases 1-3 completadas  
> **Estado:** ✅ COMPLETADA

| Paso | Descripción | Archivos | Estado |
|------|-------------|----------|--------|
| 4.1 | Actualizar `05-changelog.md` con entradas v3.5.0, v3.6.0 y v4.0.0 | `docs/05-changelog.md` | ✅ |
| 4.2 | Reescribir `07-fases-implementacion.md` — añadir Fase 7 (Galería Visual ✅) y Fase 8 (BDNS-First ✅), actualizar flujo del motor v4.0.0, backlog actualizado | `docs/07-fases-implementacion.md` | ✅ |
| 4.3 | Marcar `12-refactoring-pipeline-motor-busqueda-bdns-first.md` como IMPLEMENTADO | `docs/12-refactoring-...md` | ✅ |
| 4.4 | Verificar `09-auditoria-guia-subvenciones.md` como ARCHIVADO | `docs/09-...md` | ✅ |
| 4.5 | Actualizar `11-flujo-bdns-analisis-tecnico.md` — v4.0.0, nivel1/nivel2 como implementados | `docs/11-...md` | ✅ |
| 4.6 | Backlog en `07-fases-implementacion.md` — B.1 obsoleta, resto actualizado | `docs/07-...md` | ✅ |

---

## Resumen de impacto

| Archivo | Fase | Acción |
|---------|------|--------|
| `recomendaciones.html` | 1 | MODIFICAR — Galería visual completa |
| `SectorNormalizador.java` | 2 | CREAR — Mapeo sector → BDNS |
| `FiltrosBdns.java` | 2 | CREAR — Record inmutable |
| `BdnsFiltrosBuilder.java` | 2 | CREAR — Constructor de filtros |
| `BdnsClientService.java` | 2 | MODIFICAR — Añadir `buscarPorFiltros()` |
| `MotorMatchingService.java` | 3 | MODIFICAR — Integrar BDNS-First, eliminar keywords |
| `OpenAiMatchingService.java` | 3 | MODIFICAR — Eliminar bloque keywords (~90 líneas) |
| `recomendaciones-stream.js` | 3 | MODIFICAR — Evento SSE `keywords` → `filtros` |
| Docs: 05, 07, 09, 11, 12 | 4 | ACTUALIZAR/ARCHIVAR |
| `RecomendacionController.java` | — | SIN CAMBIOS |
| `GuiaSubvencionDTO.java` | — | SIN CAMBIOS |
| `OpenAiGuiaService.java` | — | SIN CAMBIOS |
| Modelo JPA (entidades) | — | SIN CAMBIOS |
| Repositorios | — | SIN CAMBIOS |
| Seguridad / JWT | — | SIN CAMBIOS |

---

## Pipeline resultante (v4.0.0)

```
Perfil + Proyecto
       │
       ▼
[1] BdnsFiltrosBuilder (determinista, sin IA)
       │  → SectorNormalizador: proyecto.sector → término BDNS
       │  → UbicacionNormalizador: proyecto.ubicacion → CCAA oficial
       │  → Devuelve: FiltrosBdns(descripcion, nivel1, nivel2)
       ▼
[2] BdnsClientService.buscarPorFiltros(FiltrosBdns)
       │  → GET BDNS con parámetros estructurados
       │  → Fallback progresivo: si <3 → ampliar (quitar sector, luego CCAA)
       │  → 5-20 convocatorias ya pre-cualificadas
       ▼
[3] Deduplicación + descarte caducadas (igual que v3.4.0)
       ▼
[4] Pre-filtro geográfico en memoria (safety net, ya no filtro principal)
       ▼
[5] obtenerDetallesEnParalelo() — sin cambios (CompletableFuture.allOf)
       ▼
[6] OpenAiMatchingService.analizar() × N candidatas — sin cambios
       │  → gpt-4.1: puntuación 0-100 + explicación + guía 8 pasos
       │  → Descarta si < UMBRAL_RECOMENDACION (20)
       ▼
[7] Persistencia selectiva + SSE
```

**Mejoras cuantitativas vs v3.4.0:**
- Llamadas OpenAI: ≤16 → ≤20 (0 keywords + más candidatas evaluadas)
- Llamadas HTTP BDNS: ≤23 → ≤22
- Candidatas brutas: hasta 120 → 5-20 (directamente relevantes)
- Latencia estimada: 35-45s → 15-25s
- Dependencia OpenAI en búsqueda: SÍ → NO
- Fallback sin IA: Parcial → Total (búsqueda siempre determinista)

---

## Alineación Arquitectónica Vigente (2026-03-13)

> El plan v4.0.0 se mantiene como base técnica. Para roadmap vigente aplicar:

- Backend fijo: `Java 17 + Spring Boot + Maven + PostgreSQL + JWT + SSE`.
- Frontend objetivo: `Angular + API REST`.
- Presentación server-side: fuera del objetivo de evolución.
- Prioridad de evolución: `controller/api/`, seguridad JWT y flujo `BDNS+IA`.
- Regla de transición: no mover lógica de negocio fuera de servicios.

## FASE 5 — Migración de Capa de Presentación a Angular (v5.x)
> **Prioridad:** ALTA
> **Estado:** 🔲 PLANIFICADA

| Paso | Descripción | Estado |
|------|-------------|--------|
| 5.1 | Inventariar vistas legacy y mapearlas a rutas/componentes Angular | 🔲 |
| 5.2 | Consolidar contratos REST en `controller/api/` para todas las pantallas de usuario | 🔲 |
| 5.3 | Consumir SSE en Angular para progreso del análisis BDNS+IA | 🔲 |
| 5.4 | Mantener coexistencia temporal SSR/API hasta completar migración | 🔲 |
| 5.5 | Retirar capa de presentación legacy cuando Angular cubra el flujo funcional | 🔲 |
