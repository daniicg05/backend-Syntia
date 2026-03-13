# Informe de Arquitectura, Optimización IA y Streaming — Syntia

## Alineación Arquitectónica Vigente (2026-03-13)

> Esta directriz prevalece sobre cualquier referencia histórica en conflicto dentro del informe.

- Backend objetivo mantenido: `Java 17 + Spring Boot + Maven + PostgreSQL + JWT + SSE`.
- Frontend objetivo: `Angular + API REST`.
- Toda mención a flujos web históricos debe interpretarse dentro del proceso de migración a SPA.
- Prioridad de evolución: contratos en `controller/api/`, seguridad JWT, streaming SSE y pipeline `BDNS+IA`.
- La lógica de negocio continúa en servicios; la migración se limita a capa de presentación.

---

## FASE 1 — AUDITORÍA DE DOCUMENTACIÓN

### 1.1 Qué está correcto

| Documento | Estado |
|-----------|--------|
| `01-requisitos.md` | ✅ Completo, bien estructurado. Refleja fielmente el alcance del MVP. |
| `02-plan-proyecto.md` | ✅ Cronograma y recursos coherentes. Stack tecnológico correcto. |
| `03-especificaciones-tecnicas.md` | ✅ Estructura de paquetes, seguridad, JWT — todo bien documentado. |
| `04-manual-desarrollo.md` | ✅ Instrucciones claras de setup, despliegue y API REST. |
| `05-changelog.md` | ✅ Registro detallado de cada versión con archivos modificados. |
| `06-diagramas.md` | ✅ ER, clases, casos de uso, secuencia — todos coherentes con el código. |
| `07-fases-implementacion.md` | ✅ Estado actualizado a v2.3.0. Flujo del motor documentado. |

### 1.2 Qué está desactualizado

| Problema | Documento | Detalle |
|----------|-----------|---------|
| **`spring-dotenv` no documentado** | `03-especificaciones-tecnicas.md` §2.1 | El `pom.xml` incluye `me.paulschwarz:spring-dotenv:4.0.0` pero no aparece en la tabla de dependencias Maven. |
| **`openai.max-tokens` inconsistente** | `05-changelog.md` vs `application.properties` | El changelog v2.3.0 dice "reducido de 400 a 150", pero `application.properties` actual tiene `openai.max-tokens=800`. No coincide. |
| **Modelo OpenAI en prod** | `application-prod.properties` | Usa `openai.model=${OPENAI_MODEL:gpt-4.1}` como fallback, alineado con desarrollo. |
| **Constantes del motor** | `07-fases-implementacion.md` | Documenta `MAX_CANDIDATAS_IA=20`, pero el código real usa `MAX_CANDIDATAS_IA=30`, `RESULTADOS_POR_KEYWORD=25`, `UMBRAL_RECOMENDACION=10`. |
| **`ConvocatoriaInitializer.java`** | `03-especificaciones-tecnicas.md` §6 | No aparece en la estructura de paquetes documentada, pero existe en `config/`. |
| **`data-test.sql` eliminado** | `07-fases-implementacion.md` | Se marca como "eliminado", pero `04-manual-desarrollo.md` §8.5 aún referencia credenciales del script. |
| **Diagrama de clases** | `06-diagramas.md` §2 | Falta `OpenAiClient`, `OpenAiMatchingService`, `BdnsClientService`, `DashboardService` en el diagrama. |
| **Diagrama de secuencia de matching** | `06-diagramas.md` §4 | No refleja el flujo real: falta paso de generación de keywords con OpenAI, búsqueda directa en BDNS, detalle enriquecido, y evaluación individual. |
| **CSRF** | `03-especificaciones-tecnicas.md` §5.5 | Dice "CSRF habilitado para formularios web", pero `SecurityConfig.java` tiene `csrf.disable()` en AMBAS cadenas. |
| **Campo `guia`** | `06-diagramas.md` §1 (ER) | La entidad `Recomendacion` no incluye el campo `guia` (TEXT) ni `usadaIa` (boolean). |

### 1.3 Qué falta

| Ausencia | Impacto |
|----------|---------|
| **Documentación de la integración OpenAI** | No hay un documento dedicado que explique: system prompts usados, flujo de tokens, costes estimados, decisión de modelo gpt-4.1. |
| **Documentación de la API BDNS** | `BdnsClientService` tiene lógica compleja (SSL permisivo, endpoints, mapeo). No está documentado en ningún doc. |
| **Diagramas de flujo IA** | Falta un diagrama que muestre: keywords → BDNS → dedup → detalle → OpenAI scoring → persistencia selectiva. |
| **Estimación de costes** | No hay análisis de coste por consulta (tokens de entrada/salida × precio gpt-4.1). |
| **Tests** | `07-fases-implementacion.md` menciona "Tests de integración (Alta prioridad)" en backlog pero no hay tests implementados ni documentados. |
| **Rate limiting** | No hay documentación sobre protección contra abuso de la API de OpenAI ni de BDNS. |
| **Estrategia de caché** | No se documenta ninguna estrategia de caché para respuestas de BDNS o embeddings. |

### 1.4 Recomendaciones de mejora documental

1. ~~Crear `docs/08-integracion-openai.md` con system prompts, flujo completo, costes y decisiones.~~ → ✅ Creado como este informe (`08-informe-arquitectura-ia-streaming.md`).
2. ~~Actualizar `03-especificaciones-tecnicas.md` con `spring-dotenv` y constantes reales del motor.~~ → ✅ Aplicado en v3.0.0.
3. ~~Corregir `05-changelog.md` para reflejar `max-tokens=350` real.~~ → ✅ Aplicado en v3.0.0.
4. ~~Actualizar `06-diagramas.md` con todas las clases de servicio y el flujo real de matching.~~ → ✅ Aplicado en v3.0.0.
5. ~~Documentar la decisión arquitectónica de CSRF deshabilitado.~~ → ✅ Aplicado en `03-especificaciones-tecnicas.md` §5.5.

---

## FASE 2 — ANÁLISIS GLOBAL DEL PROYECTO

### 2.1 Arquitectura general

```
┌─────────────────────────────────────────────────────────────┐
│                        NAVEGADOR                             │
│  Angular (SPA) + Bootstrap 5 + JS/TypeScript                │
└──────────────┬──────────────────────────────────┬───────────┘
               │ Form POST (síncrono)             │ API REST + JWT
               ▼                                  ▼
┌──────────────────────────┐    ┌─────────────────────────────┐
│  RecomendacionController │    │ RecomendacionRestController  │
│  (MVC — sesión)          │    │ (REST — JWT stateless)      │
└──────────┬───────────────┘    └──────────┬──────────────────┘
           │                               │
           ▼                               ▼
┌──────────────────────────────────────────────────────────────┐
│                   MotorMatchingService                        │
│  ┌─────────────────────┐   ┌──────────────────────────────┐ │
│  │ OpenAiMatchingService│   │     BdnsClientService        │ │
│  │ (system prompt,      │   │ (API REST → infosubvenciones)│ │
│  │  parseo JSON)        │   │                              │ │
│  └──────────┬───────────┘   └──────────────┬───────────────┘ │
│             │                              │                  │
│             ▼                              ▼                  │
│  ┌──────────────────┐          ┌──────────────────────────┐  │
│  │   OpenAiClient   │          │ API pública BDNS         │  │
│  │ (RestClient HTTP) │          │ (615.000+ convocatorias) │  │
│  └──────────────────┘          └──────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────┐
│          PostgreSQL 17.2 (syntia_db)                         │
│  convocatorias | recomendaciones | proyectos | perfiles      │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 Separación de capas

**Evaluación:** BUENA. El proyecto sigue correctamente el patrón Controller → Service → Repository con DTOs para la capa de presentación. Puntos positivos:
- DTOs para evitar `LazyInitializationException`.
- `open-in-view=false` (correcto).
- Filtrado delegado a BD en lugar de en memoria.

**Problemas menores:**
- `MotorMatchingService` tiene demasiadas responsabilidades (orquestación + persistencia + búsqueda + dedup). Debería separarse en un `BdnsSearchService` y un `MatchingOrchestrator`.
- `OpenAiClient` y `OpenAiMatchingService` podrían unificarse bajo una interfaz para facilitar testing.

### 2.3 Flujo de datos actual del matching

```
Usuario pulsa "Analizar con IA"
    │
    ▼ POST síncrono (bloquea hilo Tomcat 30-60s)
    │
    ├─1. DELETE recomendaciones anteriores
    ├─2. OpenAI genera 6-8 keywords (~2-3s)
    ├─3. Por cada keyword: HTTP GET a BDNS (~0.5-1s × 6-8 = 3-8s)
    ├─4. Deduplicación → top 30 candidatas
    ├─5. Por cada candidata:
    │   ├─ HTTP GET detalle BDNS (~0.5s)
    │   └─ HTTP POST OpenAI evaluación (~1-3s)
    │   Total por candidata: ~1.5-3.5s × 30 = 45-105s ← ¡CUELLO DE BOTELLA!
    ├─6. Persistir recomendaciones
    └─7. Redirect + recarga completa de página
```

### 2.4 Identificación de cuellos de botella

#### 🔴 CRÍTICO: Bloqueo del hilo principal

**Problema raíz:** `RecomendacionController.generarRecomendaciones()` es un `POST` síncrono que ejecuta TODA la pipeline en el hilo HTTP de Tomcat:
- 1 llamada OpenAI para keywords
- 6-8 llamadas HTTP a BDNS
- Hasta 30 llamadas HTTP a BDNS para detalle
- Hasta 30 llamadas HTTP a OpenAI para evaluación

**Tiempo total estimado:** 50-120 segundos en un solo request HTTP.

**Consecuencias:**
1. El usuario ve una página en blanco/cargando sin feedback.
2. Tomcat tiene pool por defecto de 200 hilos — con 10 usuarios concurrentes, ya se bloquean 10 hilos durante >1 minuto.
3. Timeout del navegador (típicamente 60-90s) puede cortar la petición.
4. Riesgo de timeout en proxies inversos (nginx default: 60s).

#### 🔴 CRÍTICO: Llamadas secuenciales a OpenAI

El bucle en `MotorMatchingService.generarRecomendaciones()` evalúa **secuencialmente** cada una de las 30 candidatas:
```java
for (ConvocatoriaDTO dto : aEvaluar) {
    // Llamada HTTP síncrona a BDNS detalle
    detalleTexto = bdnsClientService.obtenerDetalleTexto(dto.getIdBdns());
    // Llamada HTTP síncrona a OpenAI
    resultado = openAiMatchingService.analizar(proyecto, perfil, temporal, detalleTexto);
}
```
Esto multiplica la latencia × 30. **Paralelizar estas llamadas podría reducir de 90s a ~15-20s.**

#### 🟡 IMPORTANTE: Consumo excesivo de tokens

| Concepto | Valor actual | Impacto |
|----------|--------------|---------|
| System prompt de evaluación | ~500 tokens | Se envía 30 veces = 15.000 tokens solo de system prompt |
| User prompt con detalle BDNS | ~800-1500 tokens | 30 × 1200 = 36.000 tokens de entrada |
| `max_tokens` respuesta | 800 | 30 × 800 = 24.000 tokens máx de salida |
| Llamada keywords | ~200 tokens in + 100 out | 300 tokens |
| **Total por análisis** | **~75.000 tokens** | ~$0.30-0.60 USD con gpt-4.1 |

#### 🟡 IMPORTANTE: No hay caché

- Las keywords generadas para un proyecto no se cachean — si el usuario analiza dos veces, genera keywords nuevas.
- Las convocatorias de BDNS no se cachean en memoria — si dos proyectos del mismo sector buscan, hacen llamadas duplicadas.
- Los detalles de convocatoria BDNS no se cachean.

#### 🟡 IMPORTANTE: No hay rate limiting

- Un usuario puede pulsar "Analizar con IA" repetidamente, generando costes de OpenAI sin límite.
- No hay throttle por usuario ni global.

### 2.5 Por qué "piensa demasiado"

**Resumen ejecutivo de las causas:**

1. **30 llamadas secuenciales a OpenAI** con timeout de 30s cada una → peor caso 15 minutos.
2. **30 llamadas secuenciales a BDNS** para detalle enriquecido → 15-30s adicionales.
3. **`max_tokens=800`** para respuestas que en realidad son JSON corto (~150-300 tokens). La API tarda más si le permites generar más tokens.
4. **POST síncrono** sin ningún feedback al usuario — el navegador muestra su propia rueda de carga.
5. **Redirect tras POST** — toda la página se recarga, perdiendo contexto visual.
6. **System prompt extenso** — 500+ tokens, pero necesario para calidad. El problema es que se envía 30 veces.

---

## FASE 3 — MEJORA DE UI CON AJAX / STREAMING

### 3.1 Evaluación de tecnologías

| Tecnología | Pros | Contras | ¿Para Syntia? |
|-----------|------|---------|---------------|
| **AJAX clásico (XMLHttpRequest)** | Simple, amplio soporte | API anticuada, no soporta streaming nativo | ❌ No |
| **Fetch API** | Moderna, promise-based, soporta streams | Necesita ReadableStream para streaming | ⚠️ Solo para requests simples |
| **Server-Sent Events (SSE)** | Streaming nativo unidireccional, reconexión automática, ligero | Solo texto, solo server→client | ✅ **RECOMENDADO** |
| **WebSockets** | Bidireccional, baja latencia | Complejo de implementar y mantener, overhead para unidireccional | ❌ Sobredimensionado |

### 3.2 Justificación: SSE (Server-Sent Events)

SSE es la elección ideal para Syntia porque:

1. **El flujo es unidireccional** — el servidor envía actualizaciones progresivas al cliente.
2. **Spring Boot lo soporta nativamente** con `SseEmitter` — sin dependencias extra.
3. **Reconexión automática** — si la conexión se pierde, el navegador reconecta.
4. **Compatible con la arquitectura actual** — no requiere WebSocket server ni cambios en infraestructura.
5. **Cliente Angular + SSE** — SSE se consume con `EventSource` nativo del navegador, sin librerías.
6. **Permite enviar eventos tipados** — "buscando", "evaluando", "resultado", "completado".
7. **Funciona detrás de nginx** — solo necesita `X-Accel-Buffering: no`.

### 3.3 Diseño técnico completo

#### 3.3.1 Nuevo endpoint SSE en el controlador

```java
// RecomendacionController.java — NUEVO ENDPOINT SSE

@GetMapping("/generar-stream")
public SseEmitter generarStream(@PathVariable Long proyectoId,
                                 Authentication authentication) {
    
    // Timeout extendido: 3 minutos (el análisis puede tardar)
    SseEmitter emitter = new SseEmitter(180_000L);
    
    Usuario usuario = resolverUsuario(authentication);
    Proyecto proyecto = proyectoService.obtenerPorId(proyectoId, usuario.getId());
    
    // Ejecutar el análisis en un hilo separado para no bloquear Tomcat
    CompletableFuture.runAsync(() -> {
        try {
            motorMatchingService.generarRecomendacionesStream(proyecto, emitter);
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });
    
    // Handlers de limpieza
    emitter.onTimeout(emitter::complete);
    emitter.onError(e -> emitter.complete());
    
    return emitter;
}
```

#### 3.3.2 Nuevo método streaming en MotorMatchingService

```java
// MotorMatchingService.java — NUEVO MÉTODO CON SSE

public void generarRecomendacionesStream(Proyecto proyecto, SseEmitter emitter) {
    try {
        // 1. Limpiar anteriores
        recomendacionRepository.deleteByProyectoId(proyecto.getId());
        enviarEvento(emitter, "estado", "Limpiando recomendaciones anteriores...");

        // 2. Generar keywords
        enviarEvento(emitter, "estado", "🔍 Analizando tu proyecto con IA...");
        Perfil perfil = perfilService.obtenerPerfil(proyecto.getUsuario().getId()).orElse(null);
        List<String> keywords = generarKeywords(proyecto, perfil);
        enviarEvento(emitter, "keywords", 
            Map.of("total", keywords.size(), "keywords", keywords));

        // 3. Buscar en BDNS
        enviarEvento(emitter, "estado", "🌐 Buscando convocatorias en BDNS...");
        Map<String, ConvocatoriaDTO> candidatas = buscarEnBdns(keywords);
        enviarEvento(emitter, "busqueda", 
            Map.of("candidatas", candidatas.size()));

        if (candidatas.isEmpty()) {
            enviarEvento(emitter, "estado", "⚠️ No se encontraron convocatorias.");
            return;
        }

        // 4. Evaluar con IA
        List<ConvocatoriaDTO> aEvaluar = candidatas.values().stream()
                .limit(MAX_CANDIDATAS_IA).toList();
        enviarEvento(emitter, "estado", 
            "🤖 Evaluando " + aEvaluar.size() + " convocatorias con IA...");

        int procesadas = 0;
        List<Recomendacion> recomendaciones = new ArrayList<>();
        
        for (ConvocatoriaDTO dto : aEvaluar) {
            procesadas++;
            enviarEvento(emitter, "progreso", Map.of(
                "actual", procesadas,
                "total", aEvaluar.size(),
                "titulo", dto.getTitulo()
            ));

            try {
                String detalleTexto = obtenerDetalle(dto);
                Convocatoria temporal = dtoAEntidad(dto);
                OpenAiMatchingService.ResultadoIA resultado =
                    openAiMatchingService.analizar(proyecto, perfil, temporal, detalleTexto);

                if (resultado.puntuacion() >= UMBRAL_RECOMENDACION) {
                    Convocatoria persistida = persistirConvocatoria(dto);
                    Recomendacion rec = /* ... builder ... */;
                    recomendaciones.add(recomendacionRepository.save(rec));
                    
                    // ¡Enviar resultado parcial inmediatamente!
                    enviarEvento(emitter, "resultado", Map.of(
                        "titulo", dto.getTitulo(),
                        "puntuacion", resultado.puntuacion(),
                        "explicacion", resultado.explicacion(),
                        "tipo", dto.getTipo(),
                        "sector", dto.getSector(),
                        "urlOficial", dto.getUrlOficial(),
                        "totalEncontradas", recomendaciones.size()
                    ));
                }
            } catch (Exception e) {
                log.warn("Error evaluando: {}", e.getMessage());
            }
        }

        // 5. Resumen final
        enviarEvento(emitter, "completado", Map.of(
            "totalRecomendaciones", recomendaciones.size(),
            "totalEvaluadas", aEvaluar.size()
        ));

    } catch (Exception e) {
        enviarEvento(emitter, "error", e.getMessage());
    }
}

private void enviarEvento(SseEmitter emitter, String tipo, Object datos) {
    try {
        emitter.send(SseEmitter.event()
            .name(tipo)
            .data(datos));
    } catch (Exception e) {
        log.debug("Error enviando SSE: {}", e.getMessage());
    }
}
```

#### 3.3.3 Cambios en SecurityConfig

Añadir la ruta SSE como permitida para usuarios autenticados:

```java
// No requiere cambios específicos — la ruta ya está bajo /usuario/**
// que requiere ROLE_USUARIO. Solo asegurar que no haya timeout en la sesión.
```

Para nginx en producción:
```nginx
location /usuario/proyectos/ {
    proxy_pass http://localhost:8080;
    proxy_set_header X-Accel-Buffering no;  # ← CRÍTICO para SSE
    proxy_buffering off;
    proxy_read_timeout 180s;
}
```

#### 3.3.4 Cambios en cliente web (`recomendaciones.html`)

Reemplazar el `<form>` POST síncrono por un botón que dispare SSE:

```html
<!-- ANTES: -->
<form th:action="@{...}" method="post">
    <button type="submit">🤖 Analizar con IA</button>
</form>

<!-- DESPUÉS: -->
<button type="button" id="btnAnalizar" class="btn btn-primary" onclick="iniciarAnalisis()">
    🤖 Analizar con IA
</button>

<!-- Panel de progreso (oculto por defecto) -->
<div id="panelProgreso" class="card shadow-sm mb-4 border-0" style="display:none;">
    <div class="card-body">
        <div id="estadoTexto" class="fw-semibold mb-2">Iniciando análisis...</div>
        <div class="progress mb-2" style="height: 8px;">
            <div id="barraProgreso" class="progress-bar progress-bar-striped progress-bar-animated"
                 style="width: 0%"></div>
        </div>
        <div id="progresoDetalle" class="small text-muted"></div>
    </div>
</div>

<!-- Contenedor para resultados que llegan en streaming -->
<div id="resultadosStream" class="row g-3"></div>
```

#### 3.3.5 JavaScript para consumir SSE

```javascript
function iniciarAnalisis() {
    const proyectoId = /*[[${proyecto.id}]]*/ 0;
    const btn = document.getElementById('btnAnalizar');
    const panel = document.getElementById('panelProgreso');
    const estado = document.getElementById('estadoTexto');
    const barra = document.getElementById('barraProgreso');
    const detalle = document.getElementById('progresoDetalle');
    const resultados = document.getElementById('resultadosStream');
    
    // UI: deshabilitar botón, mostrar panel
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Analizando...';
    panel.style.display = 'block';
    resultados.innerHTML = '';
    
    // Abrir conexión SSE
    const source = new EventSource(
        `/usuario/proyectos/${proyectoId}/recomendaciones/generar-stream`
    );
    
    // ── Evento: estado (mensajes de texto) ──
    source.addEventListener('estado', function(e) {
        estado.textContent = e.data;
    });
    
    // ── Evento: progreso (barra) ──
    source.addEventListener('progreso', function(e) {
        const data = JSON.parse(e.data);
        const pct = Math.round((data.actual / data.total) * 100);
        barra.style.width = pct + '%';
        barra.textContent = pct + '%';
        detalle.textContent = `Evaluando ${data.actual}/${data.total}: ${data.titulo.substring(0, 60)}...`;
    });
    
    // ── Evento: resultado (nueva recomendación encontrada) ──
    source.addEventListener('resultado', function(e) {
        const rec = JSON.parse(e.data);
        const card = crearTarjetaRecomendacion(rec);
        resultados.insertAdjacentHTML('beforeend', card);
        // Efecto visual de aparición
        const lastCard = resultados.lastElementChild;
        lastCard.style.opacity = '0';
        requestAnimationFrame(() => {
            lastCard.style.transition = 'opacity 0.5s ease-in';
            lastCard.style.opacity = '1';
        });
    });
    
    // ── Evento: completado ──
    source.addEventListener('completado', function(e) {
        const data = JSON.parse(e.data);
        source.close();
        btn.disabled = false;
        btn.innerHTML = '🤖 Analizar con IA';
        barra.style.width = '100%';
        barra.classList.remove('progress-bar-animated');
        barra.classList.add('bg-success');
        estado.textContent = `✅ Análisis completado: ${data.totalRecomendaciones} recomendaciones encontradas de ${data.totalEvaluadas} evaluadas.`;
        
        // Recargar la página después de 2s para mostrar la vista completa con filtros
        setTimeout(() => window.location.reload(), 2000);
    });
    
    // ── Evento: error ──
    source.addEventListener('error', function(e) {
        const data = JSON.parse(e.data);
        source.close();
        btn.disabled = false;
        btn.innerHTML = '🤖 Analizar con IA';
        panel.innerHTML = `<div class="alert alert-danger">${data}</div>`;
    });
    
    // Error de conexión
    source.onerror = function() {
        source.close();
        btn.disabled = false;
        btn.innerHTML = '🤖 Analizar con IA';
        estado.textContent = '⚠️ Error de conexión. Inténtalo de nuevo.';
        barra.classList.add('bg-danger');
    };
}

function crearTarjetaRecomendacion(rec) {
    const claseP = rec.puntuacion >= 70 ? 'puntuacion-alta' : 
                   (rec.puntuacion >= 40 ? 'puntuacion-media' : 'puntuacion-baja');
    const claseBarra = rec.puntuacion >= 70 ? 'bg-success' : 
                       (rec.puntuacion >= 40 ? 'bg-warning' : 'bg-secondary');
    return `
    <div class="col-12">
        <div class="card shadow-sm border-0 border-start border-3 border-success">
            <div class="card-body d-flex justify-content-between align-items-start">
                <div class="flex-grow-1">
                    <h5 class="card-title mb-1">${rec.titulo.substring(0, 100)}</h5>
                    <div class="mb-2">
                        ${rec.tipo ? `<span class="badge bg-primary me-1">${rec.tipo}</span>` : ''}
                        ${rec.sector ? `<span class="badge bg-secondary me-1">${rec.sector}</span>` : ''}
                        <span class="badge bg-success ms-1">🤖 IA</span>
                    </div>
                    <p class="text-muted small mb-0">${rec.explicacion}</p>
                </div>
                <div class="text-center ms-3" style="min-width:80px;">
                    <div class="display-6 fw-bold ${claseP}">${rec.puntuacion}</div>
                    <div class="small text-muted">/ 100</div>
                    <div class="progress mt-1" style="height:6px;">
                        <div class="progress-bar ${claseBarra}" style="width:${rec.puntuacion}%"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>`;
}
```

### 3.4 Diagrama de secuencia del flujo SSE

```
Usuario          Navegador               Spring Boot          OpenAI       BDNS
  │                  │                       │                   │          │
  │─ Click "IA" ────►│                       │                   │          │
  │                  │── GET /generar-stream ►│                   │          │
  │                  │◄── SSE: estado ───────│                   │          │
  │                  │   "Analizando..."     │── keywords ──────►│          │
  │                  │◄── SSE: keywords ─────│◄── ["kw1"...] ───│          │
  │                  │◄── SSE: estado ───────│                   │          │
  │                  │   "Buscando BDNS..."  │── GET búsqueda ──►──────────►│
  │                  │◄── SSE: busqueda ─────│◄── 30 results ───◄──────────│
  │                  │◄── SSE: estado ───────│                   │          │
  │                  │   "Evaluando 30..."   │                   │          │
  │                  │◄── SSE: progreso 1/30 │── analizar(1) ───►│          │
  │  ┌───────────┐   │◄── SSE: resultado ───│◄── {punt:85} ────│          │
  │  │ Tarjeta 1 │   │                       │                   │          │
  │  └───────────┘   │◄── SSE: progreso 2/30 │── analizar(2) ───►│          │
  │                  │◄── SSE: progreso 3/30 │── analizar(3) ───►│          │
  │  ┌───────────┐   │◄── SSE: resultado ───│◄── {punt:72} ────│          │
  │  │ Tarjeta 2 │   │                       │                   │          │
  │  └───────────┘   │   ...                 │   ...             │          │
  │                  │◄── SSE: completado ───│                   │          │
  │  ✅ Terminado    │                       │                   │          │
```

---

## FASE 4 — OPTIMIZACIÓN DE TOKENS

### 4.1 Análisis del consumo actual

#### System prompt de evaluación (enviado 30× por análisis)

```
Tokens: ~500 tokens × 30 llamadas = 15.000 tokens/análisis
```

El prompt actual es exhaustivo (PARTE 1 + PARTE 2 con guía de 5 pasos). Esto produce respuestas de alta calidad, pero es excesivamente largo para 30 repeticiones.

#### User prompt (variable por convocatoria)

```
Tokens: ~400-1200 tokens × 30 = 12.000-36.000 tokens/análisis
```

El `MAX_PROMPT_CHARS=4000` es demasiado generoso. El detalle BDNS se trunca a 1500 chars, lo cual es correcto, pero el prompt completo (convocatoria + proyecto + perfil + instrucción) puede alcanzar 3000+ chars.

#### Respuesta (`max_tokens=800`)

```
Tokens máx: 800 × 30 = 24.000 tokens/análisis
```

El JSON real de respuesta (`{puntuacion, explicacion, sector, guia}`) ocupa ~200-400 tokens. Reservar 800 desperdicia budget y ralentiza la generación.

#### Llamada de keywords

```
~300 tokens (insignificante)
```

#### **Total estimado por análisis: 50.000-75.000 tokens**
#### **Coste estimado con gpt-4.1: $0.30-0.75 USD por análisis**

### 4.2 Estrategia de reducción de tokens

#### Estrategia 1: Reducir `max_tokens` a 350

El JSON de respuesta con guía de 5 pasos ocupa ~200-350 tokens. Pasar de 800 a 350 ahorra ~13.500 tokens por análisis y acelera la generación (la API devuelve la respuesta antes).

```properties
openai.max-tokens=350
```

#### Estrategia 2: System prompt compacto (sin repetir estructura)

Versión optimizada que ahorra ~200 tokens por llamada (6.000 tokens/análisis):

```
Eres Syntia, motor de subvenciones español.
Evalúa compatibilidad proyecto-convocatoria.
Input: perfil usuario, proyecto, convocatoria BDNS (con contenido oficial si disponible).
Output JSON: {"puntuacion":N,"explicacion":"2 frases","sector":"PALABRA","guia":"P1:...|P2:...|P3:...|P4:...|P5:..."}
Puntuación: 90-100=cumple requisitos, 70-89=alta compat, 50-69=media, 30-49=baja, 0-29=incompatible.
Explicación: frase1=punto fuerte, frase2=requisito a verificar.
Guía: P1=elegibilidad, P2=documentación, P3=dónde presentar, P4=plazos, P5=consejo clave.
Usa contenido oficial si existe. Si no, infiere del título.
```

Esto pasa de ~500 tokens a ~200 tokens = **ahorro de 9.000 tokens por análisis**.

#### Estrategia 3: Reducir candidatas evaluadas (`MAX_CANDIDATAS_IA`)

De 30 a 15. La ley de rendimientos decrecientes aplica: las primeras 15 candidatas de BDNS ya son las más relevantes (BDNS ordena por relevancia).

```java
private static final int MAX_CANDIDATAS_IA = 15;
```

**Impacto:** Reduce llamadas a OpenAI a la mitad = **ahorro del 50% en tokens y tiempo**.

#### Estrategia 4: Batch evaluation (evaluar varias convocatorias en una sola llamada)

En lugar de 1 convocatoria por llamada, agrupar 3-5 convocatorias en un solo prompt:

```
Evalúa las siguientes 5 convocatorias para este proyecto:
[conv1], [conv2], [conv3], [conv4], [conv5]

Output: {"resultados":[{puntuacion,explicacion,sector,guia}, ...]}
```

**Ventajas:**
- El system prompt se envía 3-5× menos.
- El contexto del proyecto/perfil se envía 3-5× menos.
- **Ahorro estimado: 60-70% en tokens de entrada.**

**Desventajas:**
- Respuestas más largas (necesita `max_tokens` más alto por batch).
- Si una convocatoria falla el parseo, se pierden todas las del batch.
- No compatible con SSE progresivo (resultados llegan en lotes, no uno a uno).

**Recomendación:** Usar batch de 3 convocatorias como compromiso.

#### Estrategia 5: Caché de keywords

Cachear las keywords generadas por proyecto durante 24h:

```java
@Cacheable(value = "keywords", key = "#proyecto.id", 
           condition = "#proyecto.descripcion != null")
public List<String> generarKeywords(Proyecto proyecto, Perfil perfil) { ... }
```

**Requiere:** Añadir `spring-boot-starter-cache` + `@EnableCaching`.

#### Estrategia 6: Pre-filtrado antes de OpenAI

Antes de enviar a OpenAI, aplicar filtros rápidos en memoria:
- Descartar convocatorias cuyo título contenga palabras del sector incorrecto.
- Descartar convocatorias de ámbito local si el proyecto es nacional.

Esto puede reducir las candidatas de 30 a 10-15 sin coste de tokens.

#### Estrategia 7: Usar `gpt-4.1-mini` para pre-screening

Flujo en dos fases:
1. **gpt-4.1-mini** (barato, rápido) pre-evalúa las 30 candidatas: solo `{puntuacion}` sin explicación ni guía. Coste: ~$0.02.
2. **gpt-4.1** (calidad) genera explicación + guía solo para las top 10 que pasaron el pre-screening.

**Ahorro estimado: 70-80% en coste de tokens de gpt-4.1.**

### 4.3 Resumen de optimización de tokens

| Estrategia | Ahorro tokens | Ahorro tiempo | Complejidad |
|-----------|--------------|---------------|-------------|
| Reducir `max_tokens` 800→350 | ~13.500 | ~15% | ⭐ Trivial |
| System prompt compacto | ~9.000 | ~5% | ⭐ Trivial |
| `MAX_CANDIDATAS_IA` 30→15 | ~50% | ~50% | ⭐ Trivial |
| Batch evaluation (3/prompt) | ~60-70% entrada | ~60% | ⭐⭐ Media |
| Caché de keywords | ~300 | Significativo | ⭐⭐ Media |
| Pre-filtrado en memoria | ~30-50% | ~30-50% | ⭐⭐ Media |
| gpt-4.1-mini pre-screening | ~70-80% coste | ~40% | ⭐⭐⭐ Alta |

### 4.4 Configuración recomendada inmediata

```properties
# application.properties — OPTIMIZADO
openai.max-tokens=350
openai.temperature=0.1
openai.model=gpt-4.1
```

```java
// MotorMatchingService.java — OPTIMIZADO
private static final int MAX_CANDIDATAS_IA = 15;
private static final int RESULTADOS_POR_KEYWORD = 15;
private static final int UMBRAL_RECOMENDACION = 20;
```

---

## FASE 5 — PROPUESTA FINAL

### 5.1 Resumen ejecutivo

Syntia v2.3.0 tenía una arquitectura sólida y bien documentada, pero el motor de matching con IA sufría de un **problema de diseño fundamental**: ejecutaba 30+ llamadas HTTP secuenciales a OpenAI en un request HTTP síncrono, bloqueando el hilo de Tomcat y dejando al usuario sin feedback durante 1-2 minutos.

**En v3.0.0 se han aplicado tres cambios coordinados:**
1. ✅ **SSE para feedback progresivo** — resultados uno a uno con `SseEmitter`.
2. ✅ **Async para no bloquear Tomcat** — `CompletableFuture.runAsync()` + `TransactionTemplate`.
3. ✅ **Optimización de tokens** — `MAX_CANDIDATAS_IA=15`, `max_tokens=350`, `RESULTADOS_POR_KEYWORD=15`.

### 5.2 Lista priorizada de mejoras

| # | Mejora | Prioridad | Impacto | Esfuerzo | Estado |
|---|--------|-----------|---------|----------|--------|
| 1 | Reducir `MAX_CANDIDATAS_IA` de 30 a 15 | 🔴 Crítica | Alto: -50% tiempo y tokens | 5 min | ✅ v3.0.0 |
| 2 | Reducir `max_tokens` de 800 a 350 | 🔴 Crítica | Medio: -15% tiempo | 5 min | ✅ v3.0.0 |
| 3 | Reducir `RESULTADOS_POR_KEYWORD` de 25 a 15 | 🔴 Crítica | Alto: menos candidatas | 5 min | ✅ v3.0.0 |
| 4 | Implementar SSE endpoint | 🔴 Crítica | UX: feedback progresivo | 2-3h | ✅ v3.0.0 |
| 5 | Implementar JS de consumo SSE | 🔴 Crítica | UX: resultados uno a uno | 2-3h | ✅ v3.0.0 |
| 6 | Ejecutar matching en hilo async | 🔴 Crítica | Libera hilos Tomcat | 1h | ✅ v3.0.0 |
| 7 | System prompt compacto | 🟡 Alta | -9.000 tokens | 30 min | ⏳ Pendiente |
| 8 | Caché de keywords por proyecto (24h) | 🟡 Alta | Evita llamada OpenAI repetida | 1h | ⏳ Pendiente |
| 9 | Rate limiting por usuario | 🟡 Alta | Protección contra abuso | 1-2h | ⏳ Pendiente |
| 10 | Paralelizar llamadas OpenAI (CompletableFuture) | 🟡 Alta | -60% tiempo | 2h | ⏳ Pendiente |
| 11 | Caché de detalles BDNS (1h TTL) | 🟢 Media | Reduce llamadas BDNS | 1h | ⏳ Pendiente |
| 12 | Batch evaluation (3 conv/prompt) | 🟢 Media | -60% tokens entrada | 3h | ⏳ Pendiente |
| 13 | Pre-screening con gpt-4.1-mini | 🟢 Media | -70% coste gpt-4.1 | 3h | ⏳ Pendiente |
| 14 | Actualizar documentación (diagramas, constantes) | 🟢 Media | Mantenibilidad | 2h | ✅ v3.0.0 |
| 15 | Tests de integración (MockMvc + @WebMvcTest) | 🟢 Media | Calidad | 4-6h | ⏳ Pendiente |
| 16 | Reforzar estrategia CSRF/CORS para clientes web y API | 🔵 Baja | Seguridad | 30 min | ⏳ Pendiente |

### 5.3 Roadmap técnico en fases

#### Sprint 1 (1-2 días): Quick wins de rendimiento ✅ COMPLETADO (v3.0.0)
- [x] Cambiar `MAX_CANDIDATAS_IA=15`, `RESULTADOS_POR_KEYWORD=15`
- [x] Cambiar `openai.max-tokens=350`
- [ ] Compactar system prompt
- [ ] Rate limiting básico (cooldown de 60s por proyecto)

**Impacto esperado:** Tiempo de análisis de 90-120s → 30-45s. Tokens de 75.000 → 25.000.

#### Sprint 2 (3-5 días): SSE + Async ✅ COMPLETADO (v3.0.0)
- [x] Crear endpoint `GET /generar-stream` con `SseEmitter`
- [x] Refactorizar `MotorMatchingService` para emitir eventos
- [x] Implementar JS con `EventSource` en recomendaciones.html
- [x] UI: panel de progreso, barra, estados, tarjetas animadas
- [x] Deshabilitar botón durante análisis
- [x] Mantener endpoint POST síncrono como fallback para la API REST

**Impacto esperado:** UX de "no responde" → feedback en tiempo real.

#### Sprint 3 (3-5 días): Optimización avanzada ⏳ PENDIENTE
- [ ] `@EnableCaching` + caché de keywords (Caffeine, 24h TTL)
- [ ] Caché de detalles BDNS (Caffeine, 1h TTL)
- [ ] Paralelizar evaluaciones OpenAI con `CompletableFuture.supplyAsync()`
- [ ] Pre-filtrado semántico en memoria antes de OpenAI

**Impacto esperado:** Tiempo de análisis de 30-45s → 10-15s.

#### Sprint 4 (2-3 días): Calidad y documentación — PARCIALMENTE COMPLETADO
- [x] Actualizar `06-diagramas.md` con flujo SSE
- [x] Actualizar `03-especificaciones-tecnicas.md` con constantes reales
- [ ] Tests de integración: `@WebMvcTest` para controladores + `@SpringBootTest` para motor
- [ ] Reforzar estrategia CSRF/CORS para clientes web y API

### 5.4 Riesgos técnicos

| Riesgo | Probabilidad | Impacto | Mitigación | Estado |
|--------|-------------|---------|------------|--------|
| SSE cortado por proxy/CDN | Media | Alto | Header `X-Accel-Buffering: no`; fallback a POST síncrono | ✅ Documentado en nginx config |
| OpenAI rate limiting (RPM) | Media | Alto | `MAX_CANDIDATAS_IA=15`; batch evaluation; retry con backoff | ✅ Reducido a 15 |
| BDNS no disponible | Alta (servidor gubernamental) | Alto | Ya existe handling; añadir caché de respuestas | ⚠️ Handling existe, caché pendiente |
| Timeout de SseEmitter (3min) | Baja | Medio | Reducir candidatas; paralelizar; timeout configurable | ✅ 180s configurado |
| Costes OpenAI en producción | Media | Alto | Pre-screening con mini; rate limit por usuario; monitoring | ⚠️ Pendiente |
| `CompletableFuture` + `@Transactional` | Alta | Alto | Gestión manual de transacciones en hilo async | ✅ TransactionTemplate |
| Concurrencia: dos análisis del mismo proyecto | Media | Medio | Lock por proyectoId o flag "análisis en curso" | ⚠️ Pendiente |
| Navegadores sin soporte SSE (IE) | Muy baja | Bajo | IE no soportado; polyfill si necesario | ✅ noscript fallback |

### 5.5 Recomendación arquitectónica final

**Arquitectura recomendada para el motor de matching:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    RecomendacionController                       │
│         GET /generar-stream → SseEmitter                        │
└──────────┬──────────────────────────────────────────────────────┘
           │ CompletableFuture.runAsync()
           ▼
┌─────────────────────────────────────────────────────────────────┐
│              MotorMatchingService (Orchestrator)                 │
│                                                                 │
│  1. Keywords      ──► @Cacheable(24h) → OpenAI                 │
│  2. BDNS Search   ──► @Cacheable(1h)  → BdnsClientService      │
│  3. Pre-filter    ──► En memoria (título, sector, ubicación)    │
│  4. Evaluate      ──► CompletableFuture.supplyAsync() × 15     │
│                       └► OpenAI gpt-4.1 (max_tokens=350)       │
│  5. Persist       ──► Solo ≥ umbral → BD                       │
│  6. SSE Events    ──► emitter.send() por cada resultado        │
└─────────────────────────────────────────────────────────────────┘
```

**Principios:**
1. **Nunca bloquear el hilo HTTP de Tomcat** — siempre async.
2. **Feedback continuo al usuario** — SSE con eventos tipados.
3. **Tokens mínimos necesarios** — prompt compacto, max_tokens ajustado, candidatas limitadas.
4. **Caché agresiva** — keywords, detalles BDNS, respuestas BDNS.
5. **Degradación elegante** — si OpenAI falla, motor rule-based; si SSE falla, POST síncrono.
6. **Observabilidad** — log de tokens consumidos, tiempo por fase, tasa de éxito.

---

## APÉNDICE A — Implementación inmediata ~~(copiar y aplicar)~~ ✅ YA APLICADO EN v3.0.0

### A.1 Cambios en `application.properties` ✅ Aplicado

```properties
# OPTIMIZADO — reducir tokens y tiempo
openai.max-tokens=350
openai.temperature=0.1
```

### A.2 Cambios en `MotorMatchingService.java` ✅ Aplicado

```java
private static final int UMBRAL_RECOMENDACION = 20;
private static final int RESULTADOS_POR_KEYWORD = 15;
private static final int MAX_CANDIDATAS_IA = 15;
```

### A.3 SSE Streaming ✅ Implementado en v3.0.0

- `MotorMatchingService.generarRecomendacionesStream()` con `TransactionTemplate` para gestión transaccional en hilo async.
- `RecomendacionController.generarStream()` con `SseEmitter` y `CompletableFuture.runAsync()`.
- `recomendaciones-stream.js` como cliente SSE con `EventSource`.
- `recomendaciones.html` actualizado con panel de progreso y contenedor de resultados en streaming.

### A.4 Dependencia Maven para caché (Sprint 3 — pendiente)

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

(Spring Boot BOM la gestiona automáticamente, no necesita versión.)

### A.5 Dependencia Maven para async (ya incluida)

`spring-boot-starter-web` ya incluye `@Async` y `CompletableFuture`. Solo necesita `@EnableAsync` en la clase de configuración o en `SyntiaMvpApplication`.

---

*Fin del informe.*
