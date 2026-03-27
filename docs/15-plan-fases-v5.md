# 15 – Plan de Fases v5: Estado actual y roadmap

**Fecha:** 2026-03-27
**Versión actual:** v4.5.0
**Repositorio backend:** https://github.com/daniicg05/backend-Syntia
**Repositorio frontend:** https://github.com/daniicg05/front-Syntia

---

## Stack actual

| Capa | Tecnología |
|------|------------|
| Backend | Java 17 · Spring Boot 3.3.x · Maven |
| Seguridad | Spring Security 6.x · JWT (jjwt 0.12.6) · BCrypt |
| Persistencia | Spring Data JPA · PostgreSQL 17 |
| Frontend | **Next.js 15 · React 19 · TypeScript · Tailwind CSS** |
| Streaming | SSE (`SseEmitter`) + `fetch` + `ReadableStream` |
| IA | OpenAI gpt-4.1 (matching + guías enriquecidas) |
| Fuente datos | API pública BDNS (~615.000 convocatorias) |

---

## Estado global (2026-03-27)

| # | Componente | Estado |
|---|------------|--------|
| Autenticación JWT (registro, login, roles) | ✅ |
| Perfil de usuario (CRUD) | ✅ |
| Proyectos (CRUD + validación de propiedad) | ✅ |
| Motor BDNS-First (búsqueda determinista sin IA) | ✅ |
| Motor IA (SSE streaming, scoring, guía 8 pasos) | ✅ |
| Guía enriquecida (GuiaSubvencionDTO + caché BD) | ✅ |
| Dashboard usuario (métricas, top recs, roadmap) | ✅ |
| Panel admin (usuarios, convocatorias, BDNS import) | ✅ |
| Frontend Next.js (middleware auth, todas las páginas) | ✅ |
| Flujo dos pasos: /buscar (BDNS) + /stream (IA) | ✅ |
| Rate limiting (30s buscar / 60s analizar) | ✅ |
| Caché detalles BDNS (1h TTL en memoria) | ✅ |

---

## Fases completadas (resumen)

| Fase | Descripción | Versión |
|------|-------------|---------|
| 1–6 | Base: auth, perfil, proyectos, motor IA, dashboard, admin, API REST | v1.0–v1.8 |
| 7 | Galería visual interactiva (mockups portales gubernamentales) | v3.5.0 |
| 8 | Pipeline BDNS-First (búsqueda sin IA, filtros deterministas) | v4.0.0 |
| 9 | BusquedaRapidaService (candidatas, usadaIa=false) | v4.1.0 |
| 10 | Landing page pública (SSR, luego migrada a Next.js) | v4.2.0 |
| 11 | **Migración frontend a Next.js** (auth middleware, SSE, todas las rutas) | v4.3.x |
| 12 | Estabilización: DELETE FK, CORS proxy, flujo 2 pasos, errores UI | v4.4.x |
| 13 | Rate limiting + caché BDNS detalles | v4.5.0 |

---

## Fase 14 – Optimización del motor IA (v4.6.0)
> **Prioridad:** Alta — reducir coste y latencia de OpenAI
> **Estado:** 🔲 Pendiente

| # | Tarea | Detalle |
|---|-------|---------|
| 14.1 | System prompt compacto | Reducir de ~500 a ~200 tokens (B.6) |
| 14.2 | Pre-screening con gpt-4.1-mini | Descartar candidatas baratas antes de gpt-4.1 (B.7) |
| 14.3 | Batch evaluation | 2-3 convocatorias por llamada OpenAI (B.8) |
| 14.4 | Paralelizar evaluaciones IA | `CompletableFuture.supplyAsync()` para scoring en paralelo (B.3) |

**Impacto estimado:** -50% coste OpenAI, -40% latencia análisis

---

## Fase 15 – Calidad y seguridad (v4.7.0)
> **Prioridad:** Alta
> **Estado:** 🔲 Pendiente

| # | Tarea | Detalle |
|---|-------|---------|
| 15.1 | Tests de integración backend | JUnit 5 + MockMvc, H2 en memoria (B.5) |
| 15.2 | CORS hardening | Revisar origins permitidos en producción (B.11) |
| 15.3 | Validación input frontend | Sanitizar sector/ubicación antes de enviar al backend |
| 15.4 | Error handling frontend | Mostrar mensajes de error de API (429, 403, 500) al usuario |

---

## Fase 16 – Nuevas funcionalidades (v5.0.0)
> **Prioridad:** Media
> **Estado:** 🔲 Planificada

| # | Tarea | Detalle |
|---|-------|---------|
| 16.1 | Alertas por email | Notificar nuevas convocatorias compatibles (B.9) |
| 16.2 | Exportación PDF | Informe de recomendaciones descargable (B.10) |
| 16.3 | Estimación éxito | Scoring de probabilidad según perfil histórico (B.12) |
| 16.4 | Fuentes europeas | Integración Horizon Europe / FEDER (B.13) |
| 16.5 | Filtros avanzados UI | Filtrar recomendaciones por puntuación mínima, vigencia, tipo |

---

## Backlog técnico actualizado

| ID | Mejora | Prioridad | Estado |
|----|--------|-----------|--------|
| B.2 | ~~Caché detalles BDNS (TTL 1h)~~ | Alta | ✅ Implementado v4.5.0 |
| B.3 | Paralelizar evaluaciones OpenAI | Alta | 🔲 Fase 14 |
| B.4 | ~~Rate limiting por usuario/proyecto~~ | Alta | ✅ Implementado v4.5.0 |
| B.5 | Tests de integración (JUnit 5 + MockMvc) | Alta | 🔲 Fase 15 |
| B.6 | System prompt compacto (~200 tokens) | Media | 🔲 Fase 14 |
| B.7 | Pre-screening con gpt-4.1-mini | Media | 🔲 Fase 14 |
| B.8 | Batch evaluation (3 conv/prompt) | Media | 🔲 Fase 14 |
| B.9 | Alertas por email (nuevas convocatorias) | Media | 🔲 Fase 16 |
| B.10 | Exportación PDF recomendaciones | Media | 🔲 Fase 16 |
| B.11 | CORS/CSRF hardening producción | Baja | 🔲 Fase 15 |
| B.12 | Estimación probabilidad éxito | Baja | 🔲 Fase 16 |
| B.13 | Fuentes europeas (Horizon, FEDER) | Baja | 🔲 Fase 16 |

---

## Flujo completo actual (v4.5.0)

```
Usuario (Next.js) → POST /buscar (rate: 30s)
  → BusquedaRapidaService
    → BdnsFiltrosBuilder(proyecto, perfil) → FiltrosBdns
    → BdnsClientService.buscarPorFiltros() [paralelo ESTADO+AUTONOMICA]
    → Deduplicar + filtro geográfico + limitar 150
    → Persistir como Recomendacion(usadaIa=false, puntuacion=0)
  ← {candidatas: N, mensaje: "..."}
  ← Frontend muestra sección "Convocatorias encontradas"

Usuario → GET /stream (rate: 60s)
  → MotorMatchingService.generarRecomendacionesStream()
    → findByProyectoIdAndUsadaIaFalse() → candidatas
    → obtenerDetallesEnParalelo() [10 hilos, caché 1h TTL]
    → Por cada candidata:
        OpenAiMatchingService.analizar() → {puntuacion, explicacion, guia}
        SSE evento "progreso"
        Si puntuacion >= 20: persistir, SSE evento "resultado"
    → SSE evento "completado"
  ← Frontend muestra sección "Recomendaciones IA"

Usuario → GET /{recId}/guia-enriquecida
  → Caché BD (guiaEnriquecida JSON)
  → Si no: BdnsClientService.obtenerDetalleTexto() [caché 1h]
  → OpenAiGuiaService.generarGuia() → GuiaSubvencionDTO
  ← JSON completo con 7 secciones (summary, methods, docs, reqs, workflows, guides, disclaimer)
```

---

## Endpoints API actuales

| Método | Ruta | Descripción | Rate limit |
|--------|------|-------------|------------|
| POST | `/api/auth/registro` | Registrar usuario, devuelve JWT | — |
| POST | `/api/auth/login` | Login, devuelve JWT | — |
| GET | `/api/usuario/perfil` | Obtener perfil | — |
| PUT | `/api/usuario/perfil` | Actualizar perfil | — |
| GET | `/api/usuario/proyectos` | Listar proyectos del usuario | — |
| POST | `/api/usuario/proyectos` | Crear proyecto | — |
| GET | `/api/usuario/proyectos/{id}` | Obtener proyecto | — |
| PUT | `/api/usuario/proyectos/{id}` | Actualizar proyecto | — |
| DELETE | `/api/usuario/proyectos/{id}` | Eliminar proyecto (+ recs) | — |
| GET | `/api/usuario/proyectos/{id}/recomendaciones` | Listar recs (candidatas + IA) | — |
| POST | `/api/usuario/proyectos/{id}/recomendaciones/buscar` | **Fase 1**: buscar en BDNS | 30s |
| GET | `/api/usuario/proyectos/{id}/recomendaciones/stream` | **Fase 2**: analizar con IA (SSE) | 60s |
| GET | `/api/usuario/proyectos/{id}/recomendaciones/{recId}/guia-enriquecida` | Guía IA completa | — |
| GET | `/api/usuario/dashboard` | Métricas, top recs, roadmap | — |
| GET/PUT/DELETE/POST | `/api/admin/**` | Panel administración | ADMIN |
