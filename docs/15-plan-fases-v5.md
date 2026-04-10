# 15 – Plan de Fases v5: Estado actual y roadmap

**Fecha:** 2026-04-10
**Versión actual:** v4.6.0
**Repositorio backend:** https://github.com/daniicg05/backend-Syntia
**Repositorio frontend:** https://github.com/daniicg05/front-Syntia

---

## Stack actual

| Capa | Tecnología |
|------|------------|
| Backend | Java 17 · Spring Boot 3.3.x · Maven |
| Seguridad | Spring Security 6.x · JWT (jjwt 0.12.6) · BCrypt |
| Persistencia | Spring Data JPA · PostgreSQL 17 |
| Frontend | **Next.js 16.2.0 · React 19.2.4 · TypeScript · Tailwind CSS 4** |
| Animaciones | Framer Motion 11 |
| Formularios | React Hook Form 7 + Zod 4 |
| Streaming | SSE (`SseEmitter`) + `fetch` + `ReadableStream` |
| IA | OpenAI gpt-4.1 (matching + guías enriquecidas) |
| Fuente datos | API pública BDNS (~615.000 convocatorias) + BD local (plan GRATUITO) |

---

## Estado global (2026-04-10)

| Componente | Estado |
|------------|--------|
| Autenticación JWT (registro, login, roles) | ✅ |
| Perfil de usuario (CRUD + cambio email + cambio contraseña) | ✅ |
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
| Sistema de planes (GRATUITO → BD local / PREMIUM → API live) | ✅ |
| ETL BDNS masivo (FULL + INCREMENTAL, 23 ejes territoriales) | ✅ |
| Retry + circuit breaker en cliente BDNS (spring-retry) | ✅ |
| Persistencia estado ETL (SyncState + SyncLog por ejecución) | ✅ |
| Panel admin ETL (ejes, historial, cobertura de datos) | ✅ |
| Validación datos BDNS (ConvocatoriaValidador, métricas cobertura) | ✅ |
| Scheduler automático BDNS (1 ene + 1 jul a las 3 AM) | ✅ |
| Cambio de email con verificación de contraseña (devuelve nuevo JWT) | ✅ |
| Vista detalle usuario en admin (correo, teléfono, historial email) | ✅ |
| Transiciones de página animadas (Framer Motion) | ✅ |
| Dark mode completo en frontend | ✅ |
| Sección estática /guias (guías informativas sobre subvenciones) | ✅ |

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
| 14 | **ETL BDNS masivo + plan GRATUITO/PREMIUM + mejoras admin y frontend** | v4.6.0 |

---

## Fase 14 – ETL BDNS + Plan + Mejoras (v4.6.0) — COMPLETADA
> **Estado:** ✅ Completada (2026-04-10)

| # | Tarea | Detalle |
|---|-------|---------|
| 14.1 | Sistema de planes | `Plan.java` enum GRATUITO/PREMIUM; GRATUITO usa `ConvocatoriaBdLocalService` (BD local); PREMIUM usa API live BDNS |
| 14.2 | ETL BDNS masivo | `BdnsImportJobService` + `BdnsImportExecutor` (async) + `BdnsImportEstrategiaService` (23 ejes) |
| 14.3 | Importación incremental | Ejes COMPLETADO → omitidos; ejes ERROR → reanudan desde última página exitosa |
| 14.4 | Retry + circuit breaker | `spring-retry` + `@Retryable` con backoff exponencial (1.5s → 3s → 6s), no reintenta en 4xx |
| 14.5 | Persistencia estado ETL | `SyncState` + `SyncLog` por ejecucionId UUID; panel admin en tiempo real |
| 14.6 | Validación datos BDNS | `ConvocatoriaValidador` (título blank/500chars); `ResultadoPersistencia` (nuevas/duplicadas/rechazadas) |
| 14.7 | Cobertura de datos | 7 métricas campo (organismo, fechaPublicacion, descripcion, textoCompleto, sector, fechaCierre, ubicacion) |
| 14.8 | Scheduler automático | `BdnsScheduler` cada 1 ene + 1 jul a las 3 AM; omite si job en curso |
| 14.9 | Cambio de email | `PUT /api/usuario/perfil/email` con verificación contraseña; devuelve nuevo JWT; `HistorialCorreo` en BD |
| 14.10 | Vista detalle usuario admin | PR #101: modal con email, teléfono y historial de cambios de correo |
| 14.11 | Mejoras frontend UX | Transiciones Framer Motion, dark mode completo, correcciones perfil, sección /guias estática |

---

## Fase 15 – Optimización del motor IA (v4.7.0)
> **Prioridad:** Alta — reducir coste y latencia de OpenAI
> **Estado:** 🔲 Pendiente

| # | Tarea | Detalle |
|---|-------|---------|
| 15.1 | System prompt compacto | Reducir de ~500 a ~200 tokens (B.6) |
| 15.2 | Pre-screening con gpt-4.1-mini | Descartar candidatas baratas antes de gpt-4.1 (B.7) |
| 15.3 | Batch evaluation | 2-3 convocatorias por llamada OpenAI (B.8) |
| 15.4 | Paralelizar evaluaciones IA | `CompletableFuture.supplyAsync()` para scoring en paralelo (B.3) |

**Impacto estimado:** -50% coste OpenAI, -40% latencia análisis

---

## Fase 16 – Calidad y seguridad (v4.8.0)
> **Prioridad:** Alta
> **Estado:** 🔲 Pendiente

| # | Tarea | Detalle |
|---|-------|---------|
| 16.1 | Tests de integración backend | JUnit 5 + MockMvc, H2 en memoria (B.5) |
| 16.2 | CORS hardening | Revisar origins permitidos en producción (B.11) |
| 16.3 | Validación input frontend | Sanitizar sector/ubicación antes de enviar al backend |
| 16.4 | Error handling frontend | Mostrar mensajes de error de API (429, 403, 500) al usuario |

---

## Fase 17 – Nuevas funcionalidades (v5.0.0)
> **Prioridad:** Media
> **Estado:** 🔲 Planificada

| # | Tarea | Detalle |
|---|-------|---------|
| 17.1 | Alertas por email | Notificar nuevas convocatorias compatibles (B.9) |
| 17.2 | Exportación PDF | Informe de recomendaciones descargable (B.10) |
| 17.3 | Estimación éxito | Scoring de probabilidad según perfil histórico (B.12) |
| 17.4 | Fuentes europeas | Integración Horizon Europe / FEDER (B.13) |
| 17.5 | Filtros avanzados UI | Filtrar recomendaciones por puntuación mínima, vigencia, tipo |
| 17.6 | Gestión plan desde UI | Pantalla para upgrade GRATUITO → PREMIUM |

---

## Backlog técnico actualizado

| ID | Mejora | Prioridad | Estado |
|----|--------|-----------|--------|
| B.2 | ~~Caché detalles BDNS (TTL 1h)~~ | Alta | ✅ v4.5.0 |
| B.3 | Paralelizar evaluaciones OpenAI | Alta | 🔲 Fase 15 |
| B.4 | ~~Rate limiting por usuario/proyecto~~ | Alta | ✅ v4.5.0 |
| B.5 | Tests de integración (JUnit 5 + MockMvc) | Alta | 🔲 Fase 16 |
| B.6 | System prompt compacto (~200 tokens) | Media | 🔲 Fase 15 |
| B.7 | Pre-screening con gpt-4.1-mini | Media | 🔲 Fase 15 |
| B.8 | Batch evaluation (3 conv/prompt) | Media | 🔲 Fase 15 |
| B.9 | Alertas por email (nuevas convocatorias) | Media | 🔲 Fase 17 |
| B.10 | Exportación PDF recomendaciones | Media | 🔲 Fase 17 |
| B.11 | CORS/CSRF hardening producción | Baja | 🔲 Fase 16 |
| B.12 | Estimación probabilidad éxito | Baja | 🔲 Fase 17 |
| B.13 | Fuentes europeas (Horizon, FEDER) | Baja | 🔲 Fase 17 |
| B.14 | ~~ETL BDNS masivo (FULL + INCREMENTAL)~~ | Alta | ✅ v4.6.0 |
| B.15 | ~~Sistema planes GRATUITO/PREMIUM~~ | Alta | ✅ v4.6.0 |
| B.16 | ~~Cambio email con verificación~~ | Media | ✅ v4.6.0 |

---

## Flujo completo actual (v4.6.0)

### Flujo de recomendaciones (usuario)

```
Usuario (Next.js) → POST /buscar (rate: 30s)
  → BusquedaRapidaService
    → Comprueba plan del usuario:
        GRATUITO → ConvocatoriaBdLocalService.buscarEnBdLocal(proyecto, perfil)
        PREMIUM  → BdnsFiltrosBuilder → BdnsClientService.buscarPorFiltros() [API live BDNS]
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

### Flujo ETL BDNS masivo (admin)

```
Admin → POST /api/admin/bdns/importar?modo=FULL|INCREMENTAL
  → BdnsImportJobService.iniciarImportacion()
    → BdnsImportExecutor.ejecutar() [@Async, hilo independiente]
      → Por cada eje (ESTADO + AUTONOMICA×19CCAA + LOCAL + OTROS):
          SyncState: PENDIENTE → EN_PROGRESO
          BdnsClientService.importarPorEje() [@Retryable backoff exponencial]
            → Paginación hasta fin + delay configurable (300ms)
            → ConvocatoriaValidador.validar() → rechaza blank/>500chars
            → ConvocatoriaService.persistirNuevas() → deduplica idBdns
            → SyncLog por página (ejecucionId, eje, pagina, registrosNuevos)
          SyncState: COMPLETADO (o ERROR con ultimaPaginaOk guardada)
      INCREMENTAL: eje COMPLETADO → omitir; eje ERROR → reanudar desde ultimaPaginaOk+1
  ← SSE/polling: Admin panel actualiza progress cada 5s
  ← Panel muestra: ejes, historial, cobertura de 7 campos
```

---

## Endpoints API actuales (v4.6.0)

### Auth
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/auth/registro` | Registrar usuario, devuelve JWT |
| POST | `/api/auth/login` | Login, devuelve JWT + email + rol + expiresIn |

### Usuario
| Método | Ruta | Descripción | Rate limit |
|--------|------|-------------|------------|
| GET | `/api/usuario/dashboard` | Métricas, top recs, roadmap | — |
| GET | `/api/usuario/perfil` | Obtener perfil | — |
| PUT | `/api/usuario/perfil` | Actualizar perfil | — |
| PUT | `/api/usuario/perfil/email` | Cambiar email (requiere password; devuelve nuevo JWT) | — |
| PUT | `/api/usuario/perfil/password` | Cambiar contraseña (requiere password actual) | — |
| GET | `/api/usuario/proyectos` | Listar proyectos | — |
| POST | `/api/usuario/proyectos` | Crear proyecto | — |
| GET | `/api/usuario/proyectos/{id}` | Obtener proyecto | — |
| PUT | `/api/usuario/proyectos/{id}` | Actualizar proyecto | — |
| DELETE | `/api/usuario/proyectos/{id}` | Eliminar proyecto (+ recs) | — |
| GET | `/api/usuario/proyectos/{id}/recomendaciones` | Listar recs (candidatas + IA) | — |
| POST | `/api/usuario/proyectos/{id}/recomendaciones/buscar` | **Paso 1**: buscar en BDNS/BD local | 30s |
| GET | `/api/usuario/proyectos/{id}/recomendaciones/stream` | **Paso 2**: analizar con IA (SSE) | 60s |
| GET | `/api/usuario/proyectos/{id}/recomendaciones/{recId}/guia-enriquecida` | Guía IA completa (caché BD) | — |

### Admin
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/admin/dashboard` | Estadísticas globales |
| GET | `/api/admin/usuarios` | Listar usuarios |
| GET | `/api/admin/usuarios/{id}` | Detalle usuario (+ proyectos + historial correo) |
| PUT | `/api/admin/usuarios/{id}/rol` | Cambiar rol de usuario |
| DELETE | `/api/admin/usuarios/{id}` | Eliminar usuario |
| GET | `/api/admin/convocatorias` | Listar convocatorias |
| POST | `/api/admin/convocatorias` | Crear convocatoria |
| PUT | `/api/admin/convocatorias/{id}` | Editar convocatoria |
| DELETE | `/api/admin/convocatorias/{id}` | Eliminar convocatoria |
| POST | `/api/admin/convocatorias/importar-bdns` | Importar convocatoria desde BDNS (unitaria) |
| POST | `/api/admin/bdns/importar?modo=FULL\|INCREMENTAL` | Lanzar job ETL masivo BDNS |
| DELETE | `/api/admin/bdns/importar` | Cancelar job ETL en curso |
| GET | `/api/admin/bdns/estado` | Estado en tiempo real del job (polling) |
| GET | `/api/admin/bdns/ultima-importacion` | Resumen última importación |
| GET | `/api/admin/bdns/ejes` | Estado de los 23 ejes territoriales |
| GET | `/api/admin/bdns/historial` | Historial de ejecuciones (agrupado por ejecucionId) |
| GET | `/api/admin/bdns/historial/{ejecucionId}` | Detalle de una ejecución |
| GET | `/api/admin/bdns/cobertura` | Cobertura de datos por campo (7 campos) |
