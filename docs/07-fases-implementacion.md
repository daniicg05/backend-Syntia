# Plan de Implementación por Fases: Syntia

> Documento actualizado el **2026-03-12**. Refleja el estado real del código hasta v4.2.0.
> Plan de implementación v4.0.0 completado en `docs/13-plan-fases-v4.md`.
> Landing page documentada en `docs/14-plan-landing-page.md`.
> Repositorio: https://github.com/daniicg05/Syntia.git

---

## Resumen General del Proyecto

Syntia es una plataforma web que permite a usuarios (emprendedores, autónomos, PYMEs) recibir recomendaciones personalizadas sobre subvenciones, ayudas y licitaciones públicas mediante un motor de matching con IA. La arquitectura es **monolítica modular** con Spring Boot, PostgreSQL y seguridad JWT.

**Stack fijo:**
- Backend: Java 17, Spring Boot 3.5.x, Spring Security 6.x
- Seguridad: JWT (jjwt 0.12.6) + BCrypt + CORS
- Persistencia: Spring Data JPA + PostgreSQL 17.2 (`syntia_db`)
- Frontend: Angular + API REST
- **IA:** OpenAI Chat Completions API (`gpt-4.1`) con fallback rule-based automático
- **Streaming:** Server-Sent Events (SSE) con `SseEmitter` + `CompletableFuture` + `TransactionTemplate`
- Puerto: `8080` | BD usuario: `syntia` / pass: `syntia`

---

## Estado Actual (2026-03-10) — v3.0.0

| Componente | Estado |
|------------|--------|
| Infraestructura base (pom.xml, application.properties) | ✅ Completo |
| Seguridad (SecurityConfig, CorsConfig, JWT) | ✅ Completo |
| Modelo de dominio (entidades JPA) | ✅ Completo |
| Repositorios JPA | ✅ Completo |
| Registro de usuario (`/registro`) | ✅ Completo |
| Login / Logout (`/login`, `/logout`) | ✅ Completo |
| Redirección por rol (`/default`) | ✅ Completo |
| Perfil de usuario (crear/editar/ver) | ✅ Completo |
| Gestión de proyectos (CRUD) | ✅ Completo |
| Motor de matching (búsqueda directa BDNS + OpenAI gpt-4.1) | ✅ Completo |
| **SSE Streaming (feedback en tiempo real durante análisis IA)** | ✅ Completo |
| Filtrado convocatorias caducadas (`vigente=true` + filtro en memoria) | ✅ Completo |
| Recomendaciones (generar, ver, filtrar) | ✅ Completo |
| Dashboard usuario (métricas, top recs, roadmap) | ✅ Completo |
| Panel administrativo completo | ✅ Completo |
| API REST con JWT | ✅ Completo |
| Integración OpenAI gpt-4.1 (análisis semántico) | ✅ Completo |
| Integración API BDNS real (búsqueda directa) | ✅ Completo |
| Vista perfil solo lectura (`perfil-ver.html`) | ✅ Completo |
| Componentes de interfaz reutilizables | ✅ Completo |
| Aviso legal público (`/aviso-legal`) | ✅ Completo |
| Optimización N+1 métricas admin | ✅ Completo |
| Filtros recomendaciones delegados a BD | ✅ Completo |
| Persistencia selectiva (solo convocatorias recomendadas ≥ 20pts) | ✅ Completo |
| **Optimización de tokens (max-tokens=500, 15 candidatas máx)** | ✅ Completo |
| **Guía de solicitud con base legal (LGS art. 13, 8 pasos)** | ✅ Completo |
| Datos de prueba (`data-test.sql`) | ❌ Eliminado (se usan datos reales de BDNS) |
| Perfil Spring producción (`application-prod.properties`) | ✅ Completo |

---

## Fase 1 – Autenticación y Perfil de Usuario
> **Estado: ✅ COMPLETADA (v1.0.0)**

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 1.1 | Registro de usuario con validación (email + contraseña) | ✅ |
| 1.2 | Login por formulario con redirección por rol | ✅ |
| 1.3 | Logout con invalidación de sesión | ✅ |
| 1.4 | Formulario de creación/edición de perfil | ✅ |
| 1.5 | Vista de perfil solo lectura (`/usuario/perfil/ver`) | ✅ |

**Componentes:** `PerfilService`, `PerfilController`, `PerfilDTO`, `perfil.html`, `perfil-ver.html`, `perfil.js`

---

## Fase 2 – Gestión de Proyectos
> **Estado: ✅ COMPLETADA (v1.1.0)**

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 2.1–2.5 | CRUD completo de proyectos | ✅ |

**Componentes:** `ProyectoService`, `ProyectoController`, `ProyectoDTO`, vistas lista/formulario/detalle, `proyecto.js`

---

## Fase 3 – Motor de Matching con IA
> **Estado: ✅ COMPLETADA (v1.2.0 + v1.7.0 + v2.0.0 + v2.1.0 + v2.3.0 + v3.0.0)**

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 3.1 | Carga manual de convocatorias (admin) | ✅ |
| 3.2 | Búsqueda directa en API BDNS real (~615.000 convocatorias) | ✅ |
| 3.3 | Filtrado `vigente=true` — solo convocatorias con plazo abierto | ✅ |
| 3.4 | Generación de keywords con OpenAI basadas en perfil + proyecto | ✅ |
| 3.5 | Evaluación semántica de compatibilidad con gpt-4.1 | ✅ |
| 3.6 | Puntuación 0-100 con criterios explícitos por rango | ✅ |
| 3.7 | Explicación en lenguaje natural (punto fuerte + condición a verificar) | ✅ |
| 3.8 | Guía de solicitud de 8 pasos con base legal (LGS art. 13) generada por IA | ✅ |
| 3.9 | Persistencia selectiva: solo convocatorias ≥ 20 puntos se guardan en BD | ✅ |
| 3.10 | Fallback automático a motor rule-based si OpenAI no disponible | ✅ |
| 3.11 | Filtrado recomendaciones por tipo, sector, ubicación (delegado a BD) | ✅ |
| 3.12 | **SSE Streaming: resultados aparecen uno a uno en tiempo real** | ✅ |
| 3.13 | **Ejecución asíncrona: `CompletableFuture` + `TransactionTemplate`** | ✅ |
| 3.14 | **Optimización de tokens: max-tokens=500, 15 candidatas máx** | ✅ |
| 3.15 | **Auditoría de guía vs. procedimiento real (LGS 38/2003, Ley 39/2015)** | ✅ |
| 3.16 | **Stepper visual en modal guía: flowchart horizontal + timeline vertical con iconos** | ✅ |
| 3.17 | **Botón guía funcional en tarjetas SSE streaming con modal dinámico** | ✅ |
| 3.18 | **Prompt optimizado: sin datos vacíos, sin URL, deduplicación sector, limpieza HTML** | ✅ |
| 3.19 | **Pre-filtro geográfico: descarta convocatorias autonómicas incompatibles antes de IA** | ✅ |
| 3.20 | **Keywords mejoradas: incluyen `descripcionLibre` y `perfil.ubicacion` como fallback** | ✅ |
| 3.21 | **Paralelismo BDNS: detalles descargados con CompletableFuture.allOf() (-85% latencia)** | ✅ |
| 3.22 | **Deduplicación por idBdns: cero evaluaciones duplicadas por la misma convocatoria** | ✅ |
| 3.23 | **Informe técnico flujo BDNS: endpoints, parámetros, mapeo perfil→BDNS, 41 llamadas documentadas** | ✅ |

**Flujo del motor (v4.0.0 — BDNS-First):**
```
Perfil + Proyecto
      ↓
BdnsFiltrosBuilder.construir() → FiltrosBdns { descripcion, ccaa }
  (fuentes: SectorNormalizador(sector), UbicacionNormalizador(ubicacion))
  [0 tokens, 0 latencia, sin depender de OpenAI]
      ↓
BdnsClientService.buscarPorFiltros() → paralelo ESTADO(10) + AUTONOMICA(10)
  + fallback progresivo: si < 3 resultados → relajar descripción → relajar territorio
      ↓
Deduplicación doble en memoria:
  1. Por idBdns (más fiable)
  2. Por título (fallback)
  + Descartar caducadas (fechaCierre < hoy)
      ↓
Pre-filtro geográfico (safety net): descarta autonómicas incompatibles
      ↓
Obtención paralela de detalles BDNS (CompletableFuture.allOf, 10 hilos):
  Por cada candidata simultáneamente → /api/convocatorias/{idBdns}
  → O(t) en vez de O(n×t) → -85% latencia esta fase
      ↓
Por cada candidata (secuencial, SSE):
  ├─ Lookup detalle en Map (ya cargado en paralelo, 0 latencia)
  ├─ Prompt optimizado: sin datos vacíos, sin URL, sector deduplicado
  ├─ OpenAI evalúa → puntuación 0-100 + explicación + guía 8 pasos (LGS 38/2003)
  ├─ SSE: evento "progreso" + evento "resultado" (incluye campo guia) si ≥ 20 pts
  └─ Persistencia selectiva en BD (TransactionTemplate)
      ↓
SSE: evento "completado" → recarga de página (2.5s)
```

**Constantes del motor (`MotorMatchingService.java`):**
```java
UMBRAL_RECOMENDACION = 20    // Mínimo para persistir (0-100)
RESULTADOS_POR_KEYWORD = 15  // Resultados BDNS por keyword
MAX_CANDIDATAS_IA = 15       // Máximo de evaluaciones OpenAI
```

**Configuración (`application.properties`):**
```properties
openai.api-key=${OPENAI_API_KEY:}   # vacío = fallback a motor rule-based
openai.model=gpt-4.1
openai.max-tokens=500               # JSON de respuesta 8 pasos ~350-500 tokens
openai.temperature=0.1              # Determinismo alto
```

**Componentes:** `MotorMatchingService`, `OpenAiClient`, `OpenAiMatchingService`, `BdnsClientService`, `RecomendacionService`, `RecomendacionController`, `RecomendacionDTO`, `recomendaciones.html`, `recomendaciones-stream.js`

---

## Fase 4 – Dashboard Interactivo y Roadmap
> **Estado: ✅ COMPLETADA (v1.3.0)**

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 4.1 | Dashboard usuario con métricas y top recomendaciones | ✅ |
| 4.2 | Roadmap estratégico ordenado por fecha de cierre | ✅ |
| 4.3 | Indicadores visuales de puntuación (barra de progreso + badge) | ✅ |
| 4.4 | Aviso legal visible | ✅ |

**Componentes:** `DashboardService`, `dashboard.html`, `dashboard.js`

---

## Fase 5 – Panel Administrativo
> **Estado: ✅ COMPLETADA (v1.4.0)**

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 5.1–5.4 | CRUD usuarios (listar, ver detalle, cambiar rol, eliminar) | ✅ |
| 5.5–5.6 | CRUD convocatorias + importación desde BDNS | ✅ |
| 5.7 | Métricas generales del sistema (sin N+1) | ✅ |

**Componentes:** `AdminController`, vistas admin/dashboard, admin/usuarios/*, admin/convocatorias/*

---

## Fase 6 – API REST y Despliegue
> **Estado: ✅ COMPLETADA (v1.5.0 + v1.8.0)**

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 6.1 | `POST /api/auth/login` → devuelve JWT | ✅ |
| 6.2 | `GET/PUT /api/usuario/perfil` protegido con JWT | ✅ |
| 6.3 | CRUD `/api/usuario/proyectos` protegido con JWT | ✅ |
| 6.4 | `GET/POST /api/usuario/proyectos/{id}/recomendaciones` | ✅ |
| 6.5 | `application-prod.properties` con variables de entorno | ✅ |

**Componentes:** `AuthRestController`, `PerfilRestController`, `ProyectoRestController`, `RecomendacionRestController`, `LoginRequestDTO`, `LoginResponseDTO`

---

## Próximas Mejoras (Backlog)

> **Nota:** La implementación BDNS-First (doc 13) eliminará la generación de keywords con IA,
> por lo que B.1 (caché de keywords) ya no aplicará.

| # | Mejora | Prioridad | Estado |
|---|--------|-----------|--------|
| ~~B.1~~ | ~~Caché de keywords por proyecto (Caffeine, 24h TTL)~~ | ~~Alta~~ | ❌ Obsoleta (BDNS-First elimina keywords) |
| B.2 | Caché de detalles BDNS (Caffeine, 1h TTL) | Alta | Pendiente |
| B.3 | Paralelizar evaluaciones OpenAI con CompletableFuture.supplyAsync() | Alta | Pendiente |
| B.4 | Rate limiting por usuario (cooldown 60s por proyecto) | Alta | Pendiente |
| B.5 | Tests de integración automatizados (JUnit 5 + MockMvc) | Alta | Pendiente |
| B.6 | System prompt compacto (~200 tokens en lugar de ~500) | Media | Pendiente |
| B.7 | Pre-screening con gpt-4.1-mini (barato) antes de gpt-4.1 | Media | Pendiente |
| B.8 | Batch evaluation (3 convocatorias por prompt) | Media | Pendiente |
| B.9 | Alertas automáticas por email cuando aparezcan nuevas convocatorias | Media | Pendiente |
| B.10 | Exportación de recomendaciones en PDF | Media | Pendiente |
| B.11 | Reforzar estrategia CSRF/CORS para clientes web y API según entorno | Baja | Pendiente |
| B.12 | Estimación de probabilidad de éxito según perfil histórico | Baja | Pendiente |
| B.13 | Integración con fuentes europeas (Horizon Europe, FEDER) | Baja | Pendiente |

---

## Fase 7 – Galería Visual Interactiva (v3.5.0)
> **Estado:** ✅ COMPLETADA
> **Plan detallado:** `docs/13-plan-fases-v4.md` — FASE 1

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 7.1 | Mapa PORTALES_GOB unificado (sin duplicación PORTALES_LB) | ✅ |
| 7.2 | Sedes autonómicas y ministeriales (6 CCAA + matcher inteligente) | ✅ |
| 7.3 | Matcher inteligente por subdominio `.gob.es` / `.es` | ✅ |
| 7.4 | user_action de IA priorizado sobre hint hardcodeado | ✅ |
| 7.5 | Mockups realistas con breadcrumbs, labels, campos con nombre real | ✅ |
| 7.6 | Diseño checklist para steps sin URL (docs, preparación) | ✅ |
| 7.7 | Enriquecimiento con datos de visual_guides (screen_hint, image_prompt) | ✅ |

---

## Fase 8 – Pipeline BDNS-First (v4.0.0)
> **Estado:** ✅ COMPLETADA
> **Plan detallado:** `docs/13-plan-fases-v4.md` — FASES 2-3
> **Análisis previo:** `docs/12-refactoring-pipeline-motor-busqueda-bdns-first.md`

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 8.1 | SectorNormalizador.java — mapeo sector → término BDNS (50+ sectores) | ✅ |
| 8.2 | FiltrosBdns record + BdnsFiltrosBuilder (determinístico, sin IA) | ✅ |
| 8.3 | BdnsClientService.buscarPorFiltros() con fallback progresivo en 2 niveles | ✅ |
| 8.4 | MotorMatchingService integrado con BDNS-First (ambos métodos públicos) | ✅ |
| 8.5 | OpenAiMatchingService sin bloque keywords (~90 líneas eliminadas) | ✅ |
| 8.6 | Evento SSE `keywords` → `filtros` en recomendaciones-stream.js | ✅ |

## Fase 9 – Búsqueda rápida sin IA (v4.1.0)
> **Estado:** ✅ COMPLETADA

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 9.1 | BusquedaRapidaService.java — búsqueda BDNS determinística, persiste candidatas con `usadaIa=false` | ✅ |
| 9.2 | RecomendacionRepository: `deleteByProyectoIdAndUsadaIaFalse()`, `findByProyectoId()` | ✅ |
| 9.3 | Endpoint `POST /buscar-candidatas` en RecomendacionController | ✅ |
| 9.4 | Botón `🔎 Buscar convocatorias` en recomendaciones.html | ✅ |
| 9.5 | Diferenciación visual: tarjetas candidatas (borde amarillo) vs IA (borde limpio + puntuación) | ✅ |
| 9.6 | Modal guía condicionado a `th:if="${rec.usadaIa}"` | ✅ |

---

## Fase 10 – Landing Page Pública (v4.2.0)
> **Estado:** ✅ COMPLETADA
> **Plan detallado:** `docs/14-plan-landing-page.md`

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 10.1 | `MainController.java` — mapea `GET /` → `templates/main.html` | ✅ |
| 10.2 | `SecurityConfig.java` — añadida `"/"` a rutas públicas (`permitAll()`) | ✅ |
| 10.3 | `main.html` — landing page con botón "Acceder a Syntia" (→ `/login`) y "Crear cuenta" (→ `/registro`) | ✅ |

**Flujo resultante:**
```
http://localhost:8080/ → main.html → /login → /dashboard
```

---

## Alineación Arquitectónica Vigente (2026-03-13)

> Esta directriz prevalece sobre referencias históricas del documento en caso de conflicto.

- Backend fijo: `Java 17 + Spring Boot + Maven + PostgreSQL + JWT + SSE`.
- Frontend objetivo: `Angular + API REST`.
- Capa de presentación server-side: fuera del alcance objetivo.
- Prioridad de evolución: `controller/api/`, JWT, flujo `BDNS+IA`.
- Restricción de diseño: mantener lógica en servicios; cambiar solo capa de presentación.

## Fase 11 – Migración de Capa de Presentación a Angular (v5.x)
> **Estado:** 🔲 PLANIFICADA

| # | Funcionalidad | Estado |
|---|--------------|--------|
| 11.1 | Inventario de vistas SSR y equivalencia en rutas Angular | 🔲 |
| 11.2 | Migrar autenticación cliente a flujo JWT API-first | 🔲 |
| 11.3 | Consumir SSE desde cliente Angular para análisis BDNS+IA | 🔲 |
| 11.4 | Mantener `controller/api/` como contrato único estable | 🔲 |
| 11.5 | Retirar progresivamente dependencias de presentación legacy | 🔲 |
