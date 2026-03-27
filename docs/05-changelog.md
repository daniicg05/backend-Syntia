# Registro de Cambios (Changelog): Syntia

Formato de cada entrada:
- **Fecha**
- **Versión** (MAJOR.MINOR.PATCH)
- **Cambios realizados**
- **Autor**

---

## [4.5.0] – 2026-03-27

### Rate Limiting + Caché BDNS (Fase 13)

#### Nuevos archivos creados
- `RateLimitService.java` — Rate limiting stateful en memoria (`ConcurrentHashMap<String, Instant>`). Cooldowns: 30s para búsqueda BDNS, 60s para análisis IA. Claves: `usuarioId:proyectoId`. Métodos: `puedeBuscar`, `registrarBusqueda`, `segundosRestantesBusqueda`, `puedeAnalizar`, `registrarAnalisis`, `segundosRestantesAnalisis`.

#### Archivos modificados
- `RecomendacionController.java` — Inyectado `RateLimitService`. Endpoint `POST /buscar`: verifica cooldown 30s, devuelve HTTP 429 con `esperarSegundos` si bloqueado. Endpoint `GET /stream`: verifica cooldown 60s, emite evento SSE `error` con segundos restantes si bloqueado.
- `BdnsClientService.java` — Caché en memoria para `obtenerDetalleTexto()`: `ConcurrentHashMap<String, CachedDetalle>` donde `CachedDetalle` es un record con `texto` e `Instant savedAt`. TTL: 1 hora. Si el resultado está en caché y no ha caducado, se devuelve directamente sin llamada HTTP.

#### Impacto
- **Coste OpenAI:** protegido contra análisis repetidos dentro del cooldown de 60s
- **Carga API BDNS:** detalles de convocatorias cacheados 1h reducen llamadas HTTP repetidas hasta 0
- **Abuso de búsqueda:** cooldown 30s por usuario+proyecto

**Autor(es):** Equipo técnico

---

## [4.4.0] – 2026-03-23

### Estabilización: Next.js + Flujo dos pasos + Correcciones críticas (Fase 12)

#### Archivos modificados (backend)
- `ProyectoService.java` — `eliminar()` ahora llama `recomendacionRepository.deleteByProyectoId(id)` antes de `proyectoRepository.delete(proyecto)`. Corrige error 500 por violación FK `recomendaciones.proyecto_id NOT NULL` al borrar un proyecto con recomendaciones asociadas.
- `RecomendacionController.java` — Añadido endpoint `POST /buscar` (Fase 1: búsqueda BDNS sin IA). Modificado `GET /stream` (Fase 2: solo análisis IA de candidatas existentes, ya no invoca `BusquedaRapidaService`). Añadidas dependencias: `BusquedaRapidaService`, `RateLimitService`.

#### Archivos modificados (frontend Next.js)
- `src/middleware.ts` (movido desde raíz) — El middleware de Next.js debe estar en `src/middleware.ts` cuando se usa directorio `src/`. Corrige acceso libre a `/dashboard` sin autenticación. Añadido parámetro `?redirect=<ruta>` a la URL de redirección al login.
- `src/app/login/page.tsx` — Tras login exitoso, redirige a `searchParams.get("redirect")` o `/dashboard`. Enlace a registro preserva parámetro `redirect`.
- `src/app/registro/page.tsx` — Tras registro exitoso, redirige a `searchParams.get("redirect")` o `/dashboard`. Enlace a login preserva parámetro `redirect`.
- `src/lib/api.ts` — Añadido `recomendacionesApi.buscar(proyectoId)`: `POST /usuario/proyectos/{id}/recomendaciones/buscar`.
- `src/app/proyectos/[id]/recomendaciones/page.tsx` — Reescrito completo. Flujo dos pasos: sección "Convocatorias encontradas" (candidatas `usadaIa=false`) + sección "Recomendaciones IA" (analizadas `usadaIa=true`, puntuación ≥ 20). Dos botones: "Buscar convocatorias" + "Analizar con IA".

#### Bugs corregidos
- **DELETE proyecto 500:** FK constraint violado al borrar proyectos con recomendaciones → resuelto eliminando recs primero
- **Dashboard sin auth:** middleware.ts en raíz ignorado por Next.js con `src/` → movido a `src/middleware.ts`
- **Registro sin JWT:** endpoint de registro ya devuelve `LoginResponseDTO` con token

**Autor(es):** Equipo técnico

---

## [4.3.0] – 2026-03-13

### Migración Frontend a Next.js 15 (Fase 11)

#### Cambios arquitectónicos
- Frontend migrado de Angular/SSR a **Next.js 15 + React 19 + TypeScript** (App Router).
- Todas las rutas implementadas como componentes React con App Router.
- Autenticación: JWT almacenado en cookie `syntia_token`, gestionado por middleware.
- SSE: consumido via `fetch` + `ReadableStream` desde componentes cliente Next.js.
- Proxy Next.js: rewrite `/api/*` → `http://localhost:8080/api/*` en `next.config.ts`.

**Autor(es):** Equipo técnico

---

## [4.3.0-legacy] – 2026-03-13

### Alineación documental API-first (Angular + REST)

#### Cambios de documentación
- Se actualiza la documentación para fijar el objetivo arquitectónico: **Backend Java 17 + Spring Boot + Maven + PostgreSQL + JWT + SSE**.
- Se establece **Angular + API REST** como frontend objetivo.
- Se reclasifica cualquier referencia a vistas server-side (`Thymeleaf`) como **legado temporal**.
- Se prioriza `controller/api/`, seguridad JWT y pipeline `BDNS+IA`.
- Se refuerza que la lógica de negocio se mantiene en servicios y la migración afecta solo a presentación.

**Autor(es):** Equipo técnico

---

## [4.2.0] – 2026-03-12

### Landing Page Pública

#### Nuevos archivos
- `MainController.java` (`com.syntia.mvp.controller`) — Mapea `GET /` a `templates/main.html`. Controller de presentación pura sin dependencias de servicios.
- `templates/main.html` — Landing page pública con botón "Acceder a Syntia" (→ `/login`), enlace "Crear cuenta gratuita" (→ `/registro`) y listado de 4 características de la plataforma. Diseño Bootstrap 5 con fondo degradado azul.

#### Archivos modificados
- `SecurityConfig.java` — Añadida `"/"` al bloque `requestMatchers(...).permitAll()` en `webSecurityFilterChain`. Sin cambios en cadena JWT ni en `defaultSuccessUrl`.

#### Flujo resultante
```
http://localhost:8080/ → main.html → /login → /dashboard
```

#### Entorno verificado
- **Compilación:** `mvn compile` → BUILD SUCCESS
- **Tests:** 18 passed, 0 failures, 0 errors

**Autor(es):** Diego

---

## [4.1.0] – 2026-03-11

### Búsqueda rápida: convocatorias sin IA

#### Nuevo servicio
- `BusquedaRapidaService.java` (`com.syntia.mvp.service`) — Busca convocatorias en BDNS usando solo sector + ubicación del proyecto/perfil, sin consumir tokens de OpenAI. Persiste candidatas como recomendaciones con `puntuacion=0` y `usadaIa=false`. Respeta deduplicación, filtro geográfico y control de caducadas. No borra las recomendaciones evaluadas por IA (solo limpia las candidatas sin evaluar anteriores).

#### Nuevos métodos en repositorio
- `RecomendacionRepository.deleteByProyectoIdAndUsadaIaFalse()` — Elimina solo candidatas no evaluadas por IA
- `RecomendacionRepository.findByProyectoId()` — Lista todas las recomendaciones con JOIN FETCH

#### Nuevo endpoint
- `POST /usuario/proyectos/{id}/recomendaciones/buscar-candidatas` — Ejecuta búsqueda rápida, redirige con mensaje de resultado

#### Cambios en vista (`recomendaciones.html`)
- **Nuevo botón:** `🔎 Buscar convocatorias` — búsqueda instantánea sin IA (~2-4s)
- **Diferenciación visual de tarjetas:**
  - Candidatas BDNS (sin evaluar): borde izquierdo amarillo, icono 🔎, badge "Candidata BDNS", sin puntuación, sin botones de guía, texto invitando a "Analizar con IA"
  - Recomendaciones IA (evaluadas): diseño original con puntuación, barras de progreso, botones de guía y galería visual
- **Modal de guía:** solo se renderiza para recomendaciones evaluadas por IA (`th:if="${rec.usadaIa}"`)
- **Estado vacío actualizado:** explica ambos botones al usuario

#### Flujo de usuario
```
1. Usuario guarda proyecto (sector + ubicación)
2. Click "🔎 Buscar convocatorias" → 2-4s → ve candidatas BDNS (borde amarillo, sin puntuar)
3. Click "🤖 Analizar con IA" → 20-35s → candidatas se convierten en recomendaciones con puntuación + guía
```

#### Entorno verificado
- **Compilación:** `mvn clean test` → BUILD SUCCESS
- **Tests:** 18 passed, 0 failures

**Autor(es):** Diego

---

## [4.0.0] – 2026-03-11

### BDNS-First: Motor sin dependencia de OpenAI para búsqueda (FASE 3)

> **BREAKING CHANGE:** El motor ya no usa OpenAI para generar keywords de búsqueda.
> Los filtros se construyen determinísticamente desde los campos del proyecto y perfil.
> OpenAI solo se usa para la evaluación semántica de cada candidata (scoring + guía).

#### Archivos modificados
- `MotorMatchingService.java` — **Refactoring completo del flujo de búsqueda:**
  - `generarRecomendaciones()`: reemplaza `generarKeywords()` + `buscarEnBdns()` por `BdnsFiltrosBuilder.construir()` + `bdnsClientService.buscarPorFiltros()`
  - `generarRecomendacionesStream()`: mismo cambio + evento SSE `"filtros"` en vez de `"keywords"`
  - Nuevos helpers extraídos: `deduplicarYFiltrarCaducadas()`, `aplicarPreFiltroGeografico()` (compartidos entre ambos métodos públicos)
  - Eliminados: `generarKeywords()`, `generarKeywordsBasicas()` (sustituidos por BdnsFiltrosBuilder)
  - Mantenido: `buscarEnBdns()` como método legacy privado (safety net)
  - JavaDoc actualizado para pipeline v4.0.0

- `OpenAiMatchingService.java` — **~90 líneas eliminadas:**
  - Eliminado: `KEYWORDS_SYSTEM_PROMPT` (text block de 12 líneas)
  - Eliminado: `generarKeywordsBusqueda()`, `construirPromptKeywords()`, `parsearKeywords()`, `generarKeywordsBasicas()` (4 métodos, ~75 líneas)
  - Eliminados imports: `ArrayList`, `List`, `Optional`
  - Mantenido intacto: `analizar()`, `SYSTEM_PROMPT`, `construirPrompt()`, `parsearRespuesta()`, `ResultadoIA`

- `recomendaciones-stream.js` — Handler SSE `'keywords'` → `'filtros'`: muestra "Búsqueda: {descripcion} · Ámbito: {ccaa}" en vez de la lista de keywords

#### Pipeline resultante (v4.0.0)
```
Proyecto + Perfil
      ↓
BdnsFiltrosBuilder → FiltrosBdns { descripcion, nivel2(ccaa) }  [0 tokens, 0 latencia]
      ↓
BdnsClientService.buscarPorFiltros() → paralelo ESTADO+AUTONOMICA + fallback progresivo
      ↓
Deduplicación idBdns + título + descarte caducadas
      ↓
Pre-filtro geográfico (safety net en memoria)
      ↓
Detalles BDNS en paralelo (CompletableFuture.allOf, 10 hilos)
      ↓
OpenAI evalúa cada candidata → puntuación + explicación + guía
      ↓
Persistencia selectiva (≥ 20 puntos)
```

#### Entorno de compilación verificado
- **Compilación Maven:** `BUILD SUCCESS`, exit code `0`
- **Tests:** 18 passed, 0 failures, 0 errors

**Autor(es):** Diego

---

## [3.6.0] – 2026-03-11

### Pipeline BDNS-First: Infraestructura de Filtros Estructurados (FASE 2)

#### Nuevos archivos creados
- `SectorNormalizador.java` (`com.syntia.mvp.service`) — Clase utilitaria estática con mapeo de 50+ sectores de texto libre a términos de búsqueda BDNS optimizados. Cubre: tecnología, digitalización, energía, agroalimentario, industria, comercio, cultura, salud, social, I+D+i, emprendimiento, internacionalización, empleo. Fallback inteligente: sectores no reconocidos se usan como texto libre prefijado con "subvencion".
- `FiltrosBdns.java` (`com.syntia.mvp.model.dto`) — Record inmutable Java 17 con 3 campos: `descripcion`, `nivel1`, `nivel2`. Incluye métodos de fallback progresivo: `sinDescripcion()` (relaja texto, mantiene territorio) y `sinTerritorio()` (relaja territorio, mantiene texto). Helper `tieneAlgunFiltro()`.
- `BdnsFiltrosBuilder.java` (`com.syntia.mvp.service`) — Clase utilitaria que construye `FiltrosBdns` a partir de `Proyecto` + `Perfil`. Prioridad: sector proyecto → sector perfil → nombre proyecto → fallback genérico. Ubicación: proyecto → perfil → null. Usa `SectorNormalizador` + `UbicacionNormalizador`.
- `BdnsFirstPipelineTest.java` (`com.syntia.mvp.service`) — 17 tests unitarios: 7 para `SectorNormalizador` (conocido, variante, desconocido, null, blank, reconocimiento, principales), 4 para `FiltrosBdns` (campos, vacío, sinDescripcion, sinTerritorio), 6 para `BdnsFiltrosBuilder` (completo, fallback ubicación, fallback sector, fallback nombre, nacional, perfil null).

#### Archivos modificados
- `BdnsClientService.java` — Nuevo método `buscarPorFiltros(FiltrosBdns)` con:
  - Si hay CCAA: doble búsqueda paralela ESTADO (tamPag=10) + AUTONOMICA (tamPag=10) con `CompletableFuture.allOf()`
  - Si no hay CCAA: búsqueda simple con descripción (tamPag=20)
  - Fallback progresivo: si < 3 resultados → relajar descripción → si sigue < 3 → relajar territorio
  - Deduplicación por `idBdns` en todas las combinaciones
  - Métodos auxiliares: `ejecutarBusquedaFiltrada()`, `combinarYDeduplicar()`, `deduplicarPorIdBdns()`

#### Entorno de compilación verificado
- **Compilación Maven:** `BUILD SUCCESS`, exit code `0`
- **Tests:** 18 passed (17 nuevos + 1 existente), 0 failures, 0 errors

**Autor(es):** Diego

---

## [3.5.0] – 2026-03-11

### Galería Visual Interactiva — Mockups Realistas de Portales Gubernamentales

#### Archivos modificados
- `recomendaciones.html` — **Reescritura completa de la galería visual (FASE 1 del plan v4.0.0):**
  1. **`window.PORTALES_GOB`**: mapa global unificado (eliminado duplicado `PORTALES_LB`). Contiene 15 portales: AEAT, TGSS, Cl@ve, FNMT, AutoFirma, REG, BDNS, PAG + 6 sedes autonómicas (Andalucía, Valencia, Cataluña, Galicia, País Vasco, Madrid).
  2. **`detectarPortalGob(url)`**: función global de detección con matcher inteligente — cualquier hostname `.gob.es`/`sede.*.es` no mapeado genera un portal institucional genérico con nombre inferido del dominio.
  3. **Mockups realistas**: cada tarjeta simula la interfaz real del portal con header institucional (colores oficiales), barra de navegación con items reales (item activo detectado por `user_action`), breadcrumb, campos de formulario con labels reales (`NIF/CIF`, `Tipo de certificado`...), y botón CTA con indicador pulsante 👆.
  4. **Prioridad `user_action` de IA**: el hint del mockup y del lightbox usa `step.user_action` de la IA (dinámico por convocatoria), con fallback a `portal.cta` (estático).
  5. **Diseño checklist para pasos sin URL**: pasos de preparación/documentación muestran lista de documentos necesarios con checks ✓ en vez de mockup de portal.
  6. **Enriquecimiento con `visual_guides`**: si la IA genera `screen_hint` o `image_prompt`, se usan como URL fallback y hint fallback respectivamente.
  7. **Lightbox ampliado**: usa `window.PORTALES_GOB` compartido (cero duplicación), mockup grande con breadcrumb, campos con labels, y `user_action` como hint dinámico.

- `OpenAiGuiaService.java` — `PROMPT_VERSION` incrementado a 4 para invalidar guías cacheadas. Prompt mejorado (FASE 3): `user_action` ahora requiere instrucciones precisas de navegación ("Pulsa X → Selecciona Y"); `official_link` marcado como OBLIGATORIO en todos los pasos.

#### Nuevos archivos creados
- `docs/13-plan-fases-v4.md` — Plan de implementación v4.0.0 con 4 fases: galería visual (completada), BDNS-First infraestructura, integración en motor, documentación.

#### Documentación actualizada
- `docs/07-fases-implementacion.md` — Añadidas Fase 7 (Galería Visual, ✅) y Fase 8 (BDNS-First, 🔲). Backlog actualizado: B.1 marcada como obsoleta.
- `docs/09-auditoria-guia-subvenciones.md` — Marcado como ARCHIVADO (hallazgos resueltos en v3.4.0+).
- `docs/12-refactoring-pipeline-motor-busqueda-bdns-first.md` — Estado actualizado: "Implementación planificada en 13-plan-fases-v4.md".

#### Entorno de compilación verificado
- **Compilación Maven:** `BUILD SUCCESS`, exit code `0`
- **Tests:** 1 passed, 0 failures, 0 errors

**Autor(es):** Diego

---

## [3.4.0] – 2026-03-10

### Informe Técnico BDNS + Paralelismo de Detalles + Deduplicación por idBdns

#### Nuevos archivos creados
- `docs/11-flujo-bdns-analisis-tecnico.md` — Informe técnico de 5 fases: ingeniería inversa del flujo BDNS real (endpoints, parámetros, estructura JSON), modelo de visualización de resultados, mapeo perfil→BDNS, diagrama end-to-end (27-41 llamadas HTTP por análisis), y propuesta de arquitectura optimizada con reducción de latencia estimada del 60-75%.

#### Archivos modificados
- `MotorMatchingService.java` — **3 mejoras de rendimiento críticas:**
  1. `obtenerDetallesEnParalelo()`: nuevo método que descarga los detalles BDNS de todas las candidatas en paralelo usando `CompletableFuture.allOf()` + `Executors.newCachedThreadPool()`. Reduce la fase de detalles de O(n×t) a O(t) — estimado de ~10-15s a ~1-2s.
  2. `buscarEnBdns()`: doble deduplicación — primero por `idBdns` (más fiable), luego por título como capa adicional. Elimina el problema de evaluar la misma convocatoria dos veces con títulos ligeramente distintos.
  3. Ambos métodos (`generarRecomendaciones()` y `generarRecomendacionesStream()`) usan `detallesPorId` precargado en paralelo, eliminando llamadas síncronas a BDNS dentro del bucle de evaluación IA.

#### Ajustes de calidad de código (v3.4.0 final)
- `OpenAiMatchingService.java` — `SYSTEM_PROMPT` y `KEYWORDS_SYSTEM_PROMPT` convertidos de concatenación de strings a **text blocks** de Java 17 (más legibles, sin warnings). JavaDoc duplicado (dangling) eliminado. `limpiarTexto()` refactorizado: parámetro `maxChars` reemplazado por constante `MAX_DETALLE_CHARS = 1500`. `limpiarTexto(String, int)` → `limpiarTexto(String)` — cero warnings en el IDE.
- `MotorMatchingService.java` — `@SuppressWarnings("resource")` sobre el `ExecutorService` para documentar explícitamente que Java 17 no soporta `try-with-resources` en `ExecutorService` (añadido en Java 19). El `finally { executor.shutdown() }` garantiza la liberación del pool.

#### Entorno de compilación verificado
- **JDK usado:** `jbr-17.0.14` (JetBrains Runtime, ubicado en `C:\Users\danie\.jdks\jbr-17.0.14`)
- **Compilación Maven:** `BUILD SUCCESS`, exit code `0`, `0` errores de compilación
- **Warnings pendientes:** solo 1 warning de IntelliJ sobre `ExecutorService` (falso positivo del analizador, no afecta a compilación ni ejecución)
- **Duplicados evaluados por IA:** de posibles duplicados a **cero** (doble deduplicación)
- **Latencia total del análisis:** de ~40-86s a ~25-50s (**-35-45%** con este cambio solo)
- **Siguiente step para reducir más:** paralelizar las evaluaciones IA (Mejora #2 del informe)

**Autor(es):** Daniel (BDNS analysis + paralelismo)

---

## [3.3.0] – 2026-03-10

### Auditoría de Campos + Optimización de Tokens + Pre-filtro Geográfico

#### Nuevos archivos creados
- `docs/10-auditoria-campos-optimizacion-ia.md` — Informe de 6 fases: auditoría completa de campos perfil/proyecto, análisis de filtrado, evaluación de API OpenAI, arquitectura optimizada, galería visual del flujo y recomendaciones priorizadas.

#### Archivos modificados
- `OpenAiMatchingService.java` — `construirPrompt()` reescrito: elimina datos vacíos ("No indicado"), excluye URL del prompt, deduplica sector perfil/proyecto, limpia HTML del detalle BDNS con `limpiarTexto()`. `construirPromptKeywords()` ahora incluye `perfil.ubicacion` como fallback y `perfil.descripcionLibre` (truncada a 300 chars). Nuevos métodos helper: `appendIfPresent()`, `limpiarTexto()`.
- `MotorMatchingService.java` — Ambos métodos (`generarRecomendaciones()` y `generarRecomendacionesStream()`) ahora aplican pre-filtro geográfico: las convocatorias autonómicas de una CCAA incompatible con la ubicación del usuario se descartan antes de evaluar con IA, ahorrando tokens y tiempo.

#### Impacto
- **Tokens reducidos:** ~15-25% menos por análisis (eliminación datos vacíos + deduplicación + limpieza HTML)
- **Precisión keywords:** mejorada con `descripcionLibre` y `perfil.ubicacion` como fuentes adicionales
- **Candidatas evaluadas:** ~20-30% menos gracias al pre-filtro geográfico (convocatorias autonómicas incompatibles se descartan)
- **Tiempo estimado:** ~15-25% menos (menos candidatas × menos tokens)

**Autor(es):** Daniel (Auditoría campos + Optimización IA)

---

## [3.2.0] – 2026-03-10

### Stepper Visual en Guía de Solicitud + Botón Guía en SSE Streaming

#### Cambios en frontend
Se rediseña completamente el modal "Ver guía de solicitud" con un componente visual de flujo (stepper horizontal + timeline vertical con iconos). Las tarjetas SSE streaming ahora incluyen botón funcional de guía.

#### Archivos modificados
- `recomendaciones.html` — Modal rediseñado a `modal-xl` con dos componentes visuales: (1) Stepper horizontal tipo flowchart con 8 nodos conectados (🏛️ Portal → 📄 Requisitos → 📎 Documentación → 💻 Sede → 📅 Plazos → ⚖️ Régimen → ✅ Post-concesión → ⚠️ Advertencias), con estados `completed`/`active`/pendiente y conectores coloreados. (2) Timeline vertical con iconos emoji, etiquetas semánticas por paso, colores diferenciados por fase y animación de aparición escalonada. Nuevo CSS completo: `.stepper-flow`, `.stepper-step`, `.stepper-icon`, `.stepper-connector`, `.guia-timeline`, `.guia-paso`, `.guia-paso-icon`, `.guia-paso-content` con 8 esquemas de color por paso. Función `abrirGuiaStream(rec)` que genera modal dinámico idéntico para tarjetas SSE. Aviso legal actualizado con referencia a LGS 38/2003.
- `recomendaciones-stream.js` — `crearTarjetaRecomendacion()` ahora incluye botón `📋 Ver guía de solicitud` que llama a `abrirGuiaStream(rec)`. Los datos de la recomendación (incluyendo `guia`) se almacenan en variable global temporal para acceso desde el onclick. Botones de acción reorganizados con `d-flex flex-wrap gap-2`.
- `MotorMatchingService.java` — Evento SSE `resultado` ahora incluye campo `guia` con el texto de la guía de 8 pasos, permitiendo que las tarjetas streaming muestren la guía completa sin recargar. Se usa `LinkedHashMap` para preservar orden de campos en el JSON.

#### Impacto visual
- **Modal:** de texto plano con `border-left` azul a stepper flowchart + timeline con 8 colores e iconos
- **SSE streaming:** tarjetas ahora tienen botón de guía funcional desde el primer momento
- **Responsive:** stepper horizontal con scroll en móvil, modal `modal-xl` con scrollable
- **Animaciones:** pasos aparecen con `fadeInPaso` escalonado (50ms entre pasos)

**Autor(es):** Daniel (Stepper visual + SSE guía)

---

## [3.1.0] – 2026-03-10

### Auditoría de Guía de Subvenciones + Mejora del System Prompt

#### Nuevos archivos creados
- `docs/09-auditoria-guia-subvenciones.md` — Informe completo de 6 fases: auditoría de `/docs` vs. proceso real de solicitud de subvenciones, investigación de la Ley 38/2003 (LGS) y Ley 39/2015 (LPACAP), esquema visual del flujo con 19 pasos y 8 pantallas, modelado REST con 8 endpoints, wireframes estructurales, tabla comparativa (cobertura actual: 16% del flujo real) y propuesta de rediseño de la guía en 3 capas.

#### Archivos modificados
- `OpenAiMatchingService.java` — System prompt (`SYSTEM_PROMPT`) reescrito completamente. La guía de solicitud pasa de 5 a 8 pasos cubriendo el ciclo completo: (1) requisitos legales universales (LGS art. 13: AEAT, TGSS, declaración responsable) + específicos, (2) documentación obligatoria detallada, (3) sede electrónica y medios de identificación, (4) plazos y calendario, (5) régimen de concesión y criterios de valoración, (6) obligaciones post-concesión, (7) justificación de gastos, (8) advertencias críticas (diferencia extracto/bases reguladoras, errores frecuentes). Se añade JavaDoc con referencia normativa.
- `application.properties` — `openai.max-tokens` incrementado de 350 a 500 para acomodar la respuesta JSON de 8 pasos.

#### Impacto
- **Cobertura de la guía:** de 5 pasos genéricos a 8 pasos con base legal (LGS 38/2003, Ley 39/2015)
- **Requisitos universales:** ahora siempre presentes en PASO 1 (AEAT, TGSS, art. 13)
- **Ciclo completo:** la guía cubre desde elegibilidad hasta justificación y advertencias
- **Tokens:** ~30% más por respuesta (~350→~500 tokens), compensado por mayor calidad

**Autor(es):** Daniel (Auditoría guía subvenciones)

---

## [3.0.0] – 2026-03-10

### SSE Streaming + Optimización de Tokens + Informe Arquitectónico

#### Cambios arquitectónicos
Se implementa Server-Sent Events (SSE) para mostrar resultados de IA progresivamente en tiempo real. El análisis se ejecuta en un hilo separado (CompletableFuture) para no bloquear Tomcat. Se reduce el consumo de tokens y el tiempo de respuesta con constantes optimizadas.

#### Nuevos archivos creados
- `static/javascript/recomendaciones-stream.js` — Cliente SSE con EventSource: consume eventos progresivos (estado, keywords, búsqueda, progreso, resultado, completado, error). Renderiza tarjetas de recomendación en tiempo real con animación de aparición.
- `docs/08-informe-arquitectura-ia-streaming.md` — Informe completo de 5 fases: auditoría documental, análisis global, diseño SSE, optimización de tokens y propuesta final con roadmap.

#### Archivos modificados
- `MotorMatchingService.java` — Nuevo método `generarRecomendacionesStream(Proyecto, SseEmitter)` que emite eventos SSE durante el análisis. Usa `TransactionTemplate` para gestión transaccional programática en hilo async. Constantes optimizadas: `UMBRAL_RECOMENDACION=20` (antes 10), `RESULTADOS_POR_KEYWORD=15` (antes 25), `MAX_CANDIDATAS_IA=15` (antes 30). Inyectado `ObjectMapper` para serialización JSON de eventos SSE.
- `RecomendacionController.java` — Nuevo endpoint `GET /generar-stream` con `SseEmitter` (produces=text/event-stream, timeout=180s). El análisis se ejecuta en `CompletableFuture.runAsync()` para liberar el hilo Tomcat. Endpoint POST síncrono mantenido como fallback.
- `recomendaciones.html` — Botón "Analizar con IA" ahora lanza SSE en lugar de POST síncrono. Panel de progreso con spinner, barra animada, detalle de evaluación y contador de resultados encontrados. Contenedor `#resultadosStream` para tarjetas en tiempo real. `<noscript>` con formulario POST como fallback. Referencia a `recomendaciones-stream.js`.
- `application.properties` — `openai.max-tokens` reducido de 800 a 350 (respuesta JSON real ~200-350 tokens).

#### Impacto de rendimiento
- **Tokens por análisis:** ~75.000 → ~25.000 (~67% reducción)
- **Tiempo de análisis:** ~90-120s → ~30-45s (~60% reducción)
- **UX:** De "pantalla blanca sin feedback" a resultados apareciendo uno a uno en tiempo real

**Autor(es):** Daniel (Arquitectura SSE + Optimización IA)

---

## [2.3.0] – 2026-03-09

### Mejora de Prompts IA + Optimizaciones de Velocidad

#### Archivos modificados
- `OpenAiMatchingService.java` — `SYSTEM_PROMPT` reescrito con criterios de puntuación explícitos por rango (90-100 / 70-89 / 50-69 / 30-49 / 0-29) y estructura de explicación en 2 partes (punto fuerte + condición a verificar). `KEYWORDS_SYSTEM_PROMPT` mejorado: ahora permite tildes en español (mejor cobertura BDNS), genera 4-6 búsquedas (antes 3-6). Parseo migrado de extracción manual de strings a Jackson `ObjectMapper` — más robusto ante JSON con caracteres especiales. Errata "espangla" corregida.
- `OpenAiClient.java` — añadido `response_format: {type: json_object}` (OpenAI devuelve JSON puro, sin preámbulos). Timeout 10s conexión / 30s lectura mediante `SimpleClientHttpRequestFactory`. Truncado de `userPrompt` a 1200 caracteres para reducir tokens de entrada. Import `JsonProperty` eliminado.
- `application.properties` — `openai.max-tokens` reducido de 400 a 150 (la respuesta JSON corta no necesita más). `openai.temperature` reducido de 0.3 a 0.1 (respuestas más deterministas y rápidas).

**Autor(es):** Daniel

---

## [2.2.0] – 2026-03-09

### Alineación contador recomendaciones (frontend)

#### Archivos modificados
- `RecomendacionController.java` — el mensaje flash de éxito ahora usa `contarPorProyecto()` (fuente de BD) en lugar de `generadas.size()` (fuente del motor), garantizando que el número del mensaje coincide exactamente con el de la vista.
- `templates/usuario/proyectos/recomendaciones.html` — cuando no hay filtros activos se muestra `"N recomendaciones encontradas"` en lugar del confuso `"Mostrando N de N"`. Con filtros activos sigue mostrando `"Mostrando X de Y"`.

**Autor(es):** Daniel

---

## [2.1.0] – 2026-03-09

### Filtrado de convocatorias caducadas

#### Archivos modificados
- `BdnsClientService.java` — añadido parámetro `&vigente=true` a la URL de búsqueda (`buscarPorTexto`) para que la API BDNS devuelva solo convocatorias con plazo abierto. Corregido el mapeo de fechas: antes se usaba `fechaRecepcion` (fecha de registro en BDNS, siempre pasada) como `fechaCierre`; ahora se buscan los campos reales `fechaFinSolicitud`, `fechaCierre` o `plazoSolicitudes`, dejando `null` si no están disponibles. Añadido log `DEBUG` por convocatoria mapeada.
- `MotorMatchingService.java` — filtro de seguridad en memoria en `buscarEnBdns()`: cualquier candidata con `fechaCierre` anterior a hoy se descarta aunque pase el filtro `vigente=true` de BDNS.

**Autor(es):** Daniel

---

## [2.0.0] – 2026-03-09

### Refactor completo del Motor de Matching

#### Cambios arquitectónicos
El motor se rediseñó completamente para eliminar la acumulación masiva de convocatorias en BD y los inserts infinitos que bloqueaban la interfaz.

**Flujo anterior (problemático):**
1. OpenAI genera keywords → `buscarEImportarDesdeBdns()` importa a BD → `findAll()` devuelve TODAS → evaluación de 150+ convocatorias con OpenAI → 150+ inserts

**Flujo nuevo (correcto):**
1. OpenAI genera keywords → búsqueda directa en API BDNS → deduplicación en memoria → evaluación con IA de top 20 → persistencia selectiva solo de las recomendadas (≥ 40 puntos)

#### Archivos modificados
- `MotorMatchingService.java` — reescrito completamente. Eliminada dependencia de `ConvocatoriaService`. Inyectado `BdnsClientService` directamente. Constantes: `UMBRAL_RECOMENDACION=40`, `RESULTADOS_POR_KEYWORD=20`, `MAX_CANDIDATAS_IA=20`. Nuevo método `buscarEnBdns()` con deduplicación por título. Nuevo método `persistirConvocatoria()`: solo guarda en BD las convocatorias que superan el umbral, usando `findByTituloIgnoreCaseAndFuente()` para evitar duplicados.
- `ConvocatoriaService.java` — eliminado `buscarEImportarDesdeBdns()` (ya no se usa). Eliminado import `ArrayList`.
- `ConvocatoriaRepository.java` — añadido `findByTituloIgnoreCaseAndFuente()` que devuelve `Optional<Convocatoria>`. Añadido import `java.util.Optional`. Añadido `buscarPorTitulos()` con `@Query`.

#### Bugs corregidos
- **Insert infinitos:** el motor ya no itera sobre todas las convocatorias de BD (podían ser 150+).
- **`findAll()` en motor de matching:** eliminado completamente, reemplazado por búsqueda directa en API BDNS.
- **Botón "Analizar con IA" bloqueado:** resuelto al limitar a MAX_CANDIDATAS_IA=20 las llamadas a OpenAI.

**Autor(es):** Daniel

---

## [1.9.0] – 2026-03-09

### Cambio de modelo OpenAI a gpt-4.1

- `application.properties` — `openai.model` cambiado de `gpt-4o-mini` a `gpt-4.1`.

**Autor(es):** Daniel

---

## [1.8.0] – 2026-03-06

### Fase 7: Deuda Técnica, Calidad y Producción

Resolución completa de los 8 faltantes detectados en la auditoría (v1.6.0).

#### Nuevos archivos creados
- `application-prod.properties` — perfil Spring para producción con todas las propiedades sensibles via variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `OPENAI_API_KEY`, `PORT`).
- `templates/usuario/perfil-ver.html` — vista de solo lectura del perfil con DL semántico, badges y botón editar.
- `templates/fragments/navbar-usuario.html` — fragment navbar azul reutilizable para vistas de usuario.
- `templates/fragments/navbar-admin.html` — fragment navbar oscuro reutilizable para vistas de admin.
- `templates/fragments/footer.html` — fragment pie de página con copyright dinámico y enlace aviso legal.
- `templates/aviso-legal.html` — página pública de aviso legal (ruta `GET /aviso-legal`, sin autenticación).
- `service/BdnsClientService.java` — cliente REST para la API pública de BDNS con SSL permisivo para certificados gubernamentales.

#### Archivos modificados

**Backend:**
- `PerfilController` — añadida ruta `GET /usuario/perfil/ver` con redirect automático si no hay perfil.
- `AuthController` — añadida ruta `GET /aviso-legal` pública.
- `AdminController` — métricas del dashboard con `countAll()` directo (sin N+1); `detalleUsuario()` incluye `recsPerProyecto` (`Map<Long,Long>`).
- `ConvocatoriaService` — inyectado `BdnsClientService`; nuevo método `importarDesdeBdns(pagina, tamano)` con detección de duplicados.
- `AdminController` — nuevo endpoint `POST /admin/convocatorias/importar-bdns`.
- `RecomendacionService` — nuevos métodos `filtrar()`, `obtenerTiposDistintos()`, `obtenerSectoresDistintos()`.
- `RecomendacionController` — filtros delegados completamente a BD (eliminado filtrado en memoria).
- `SecurityConfig` — añadida `/aviso-legal` a rutas públicas.

**Repositorios:**
- `ProyectoRepository` — añadido `countAll()` con `@Query`.
- `RecomendacionRepository` — añadidos `countAll()`, `filtrar()` JPQL, `findTiposDistintosByProyectoId()`, `findSectoresDistintosByProyectoId()`.
- `ConvocatoriaRepository` — añadido `existsByTituloAndFuente()` para detección de duplicados BDNS.

**Autor(es):** Daniel (Fase 7 — Deuda técnica y producción)

---

## [1.7.0] – 2026-03-06

### Motor de Matching con OpenAI (Fase 3+)

#### Nuevos archivos creados
- `service/OpenAiClient.java` — cliente HTTP ligero para OpenAI Chat Completions API usando `RestClient` de Spring 6.
- `service/OpenAiMatchingService.java` — construcción del prompt con contexto completo (proyecto + perfil + convocatoria), llamada a OpenAI, parseo de respuesta JSON `{puntuacion, explicacion}`.

#### Archivos modificados
- `MotorMatchingService` — estrategia híbrida: OpenAI como motor primario, fallback automático al motor rule-based si la API falla o no está configurada.
- `Recomendacion` — campo `usadaIa` (boolean) para registrar en BD si fue generada por OpenAI o por reglas.
- `templates/usuario/proyectos/recomendaciones.html` — badge **🤖 Analizado por IA** vs **⚙️ Motor de reglas**.
- `application.properties` — añadidas propiedades `openai.api-key`, `openai.model`, `openai.max-tokens`, `openai.temperature`.

**Autor(es):** Daniel (Integración OpenAI)

---

## [1.6.0] – 2026-03-05

### Fase 6: API REST + JWT + Despliegue

#### Nuevos componentes
- `controller/api/AuthRestController.java` — `POST /api/auth/login` → devuelve JWT.
- `controller/api/PerfilRestController.java` — `GET/PUT /api/usuario/perfil`.
- `controller/api/ProyectoRestController.java` — CRUD `/api/usuario/proyectos`.
- `controller/api/RecomendacionRestController.java` — recomendaciones + generar.
- `model/dto/LoginRequestDTO.java`, `LoginResponseDTO.java`.
- `config/GlobalExceptionHandler.java` — añadidos `AccessDeniedException`, `MethodArgumentNotValidException`.

**Autor(es):** Daniel (Backend/API REST)

---

## [1.4.0] – 2026-03-05

### Fase 5: Panel Administrativo

#### Nuevos componentes
- `controller/AdminController.java` — CRUD usuarios + convocatorias + métricas.
- `service/ConvocatoriaService.java`, `model/dto/ConvocatoriaDTO.java`.
- `templates/admin/dashboard.html`, `usuarios/lista.html`, `usuarios/detalle.html`.
- `templates/admin/convocatorias/lista.html`, `formulario.html`.

**Autor(es):** Daniel

---

## [1.3.0] – 2026-03-05

### Fase 4: Dashboard Interactivo y Roadmap Estratégico

#### Nuevos componentes
- `service/DashboardService.java` — `obtenerTopRecomendacionesPorProyecto`, `obtenerRoadmap`, record `RoadmapItem`.
- `templates/usuario/dashboard.html` — métricas, top recomendaciones, roadmap, aviso legal.
- `static/javascript/dashboard.js` — contador de días restantes.

**Autor(es):** Daniel

---

## [1.2.0] – 2026-03-05

### Fase 3: Convocatorias, Motor de Matching y Recomendaciones

**Autor(es):** Daniel

---

## [1.1.0] – 2026-03-05

### Fase 2: Gestión de Proyectos (CRUD)

**Autor(es):** Daniel

---

## [1.0.0] – 2026-03-05

### Fase 1: Perfil de Usuario

**Autor(es):** Daniel

---

## [0.2.0] – 2026-03-05

### Auditoría Técnica y Actualización de Documentación (Pre-implementación)

Revisión exhaustiva del código base antes de iniciar la implementación. Corrección de imports, roles, dependencias Maven, configuración vacía y modelos de dominio ausentes.

**Autor(es):** Daniel
