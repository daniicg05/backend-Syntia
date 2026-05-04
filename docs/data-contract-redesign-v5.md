# Syntia — Data Contract & Backend Gap Analysis (Redesign v5)

> Generated: 2026-04-13
> Scope: UI/UX redesign with Framer Motion. No new DB tables. Documents existing API
> shapes and identifies missing fields / endpoints the redesign will need.

---

## 1. Domain Entities (JPA — source of truth)

### 1.1 `usuarios` table — `Usuario.java`
| Column | Java type | Notes |
|--------|-----------|-------|
| `id` | `Long` | PK, auto |
| `email` | `String` | unique, not null |
| `password_hash` | `String` | bcrypt, never exposed |
| `rol` | `Rol` enum | `ADMIN` \| `USUARIO` |
| `plan` | `Plan` enum | `GRATUITO` \| `PREMIUM`, default GRATUITO |
| `creado_en` | `LocalDateTime` | set on @PrePersist, not updatable |

### 1.2 `perfiles` table — `Perfil.java`
| Column | Java type | Notes |
|--------|-----------|-------|
| `id` | `Long` | PK |
| `usuario_id` | FK → usuarios | OneToOne |
| `sector` | `String` | not null |
| `ubicacion` | `String` | not null |
| `empresa` | `String` | max 255 |
| `provincia` | `String` | max 100 |
| `telefono` | `String` | max 30 |
| `tipo_entidad` | `String` | nullable |
| `objetivos` | `String` | nullable |
| `necesidades_financiacion` | `String` | nullable |
| `descripcion_libre` | `TEXT` | nullable |

### 1.3 `proyectos` table — `Proyecto.java`
| Column | Java type | Notes |
|--------|-----------|-------|
| `id` | `Long` | PK |
| `usuario_id` | FK → usuarios | ManyToOne LAZY |
| `nombre` | `String` | max 255, not null |
| `sector` | `String` | max 100 |
| `ubicacion` | `String` | max 150 |
| `descripcion` | `TEXT` | nullable |

**MISSING**: No `creado_en` / `fecha_creacion` column exists. The frontend
(`proyectos/page.tsx` line 136) renders `proyecto.fechaCreacion` but the entity
and DTO never populate it. The card silently hides the date field.

### 1.4 `convocatorias` table — `Convocatoria.java`
| Column | Java type | Notes |
|--------|-----------|-------|
| `id` | `Long` | PK |
| `titulo` | `TEXT` | not null |
| `tipo` | `String` | nullable |
| `sector` | `String` | nullable |
| `ubicacion` | `TEXT` | nullable |
| `url_oficial` | `TEXT` | nullable |
| `fuente` | `TEXT` | nullable |
| `id_bdns` | `String` | nullable |
| `numero_convocatoria` | `String` | nullable |
| `fecha_cierre` | `LocalDate` | nullable |
| `organismo` | `TEXT` | nullable |
| `fecha_publicacion` | `LocalDate` | nullable |
| `descripcion` | `TEXT` | nullable |
| `texto_completo` | `TEXT` | nullable |
| `mrr` | `Boolean` | nullable |
| `presupuesto` | `Double` | nullable |
| `abierto` | `Boolean` | nullable |
| `finalidad` | `TEXT` | nullable |
| `fecha_inicio` | `LocalDate` | nullable |

### 1.5 `recomendaciones` table — `Recomendacion.java`
| Column | Java type | Notes |
|--------|-----------|-------|
| `id` | `Long` | PK |
| `proyecto_id` | FK → proyectos | ManyToOne LAZY |
| `convocatoria_id` | FK → convocatorias | ManyToOne LAZY |
| `puntuacion` | `int` | 0–100 |
| `explicacion` | `TEXT` | nullable |
| `guia` | `TEXT` | nullable |
| `guia_enriquecida` | `TEXT` | serialized JSON |
| `usada_ia` | `boolean` | default false |
| `generada_en` | `LocalDateTime` | set on @PrePersist |

### 1.6 `sync_state` table — `SyncState.java`
| Column | Java type | Notes |
|--------|-----------|-------|
| `id` | `Long` | PK |
| `eje` | `String` | unique (e.g. "ESTADO", "ANDALUCIA") |
| `ultima_pagina_ok` | `int` | resumption pointer |
| `total_paginas` | `Integer` | nullable |
| `registros_nuevos` | `int` | |
| `registros_actualizados` | `int` | |
| `estado` | `Estado` enum | PENDIENTE\|EN_PROGRESO\|COMPLETADO\|ERROR |
| `ts_inicio` | `Instant` | |
| `ts_ultima_carga` | `Instant` | |

### 1.7 `sync_log` table — `SyncLog.java`
| Column | Java type | Notes |
|--------|-----------|-------|
| `id` | `Long` | PK |
| `ejecucion_id` | `String` | UUID string |
| `eje` | `String` | |
| `pagina` | `int` | |
| `registros_nuevos` | `int` | |
| `registros_actualizados` | `int` | |
| `errores` | `int` | |
| `ts` | `Instant` | |

### 1.8 `historial_correo` table — `HistorialCorreo.java`
| Column | Java type | Notes |
|--------|-----------|-------|
| `id` | `Long` | PK |
| `usuario_id` | FK → usuarios | ManyToOne LAZY |
| `anterior` | `String` | old email |
| `nuevo` | `String` | new email |
| `fecha` | `LocalDateTime` | set on @PrePersist |
| `actor` | `String` | who made the change |

### 1.9 Enums
- `Rol`: `ADMIN`, `USUARIO`
- `Plan`: `GRATUITO`, `PREMIUM`
- `SyncState.Estado`: `PENDIENTE`, `EN_PROGRESO`, `COMPLETADO`, `ERROR`
- `BdnsImportJobService.EstadoImportacion`: `INACTIVO`, `EN_CURSO`, `COMPLETADO`, `FALLIDO`

---

## 2. Complete API Endpoint Catalogue

### 2.1 Auth — `/api/auth/**`

#### POST `/api/auth/registro`
**Request body** (`RegistroDTO`):
```json
{
  "email": "string",
  "password": "string",
  "confirmarPassword": "string"
}
```
**Response** `201 Created` (`LoginResponseDTO`):
```json
{
  "token": "string (JWT)",
  "email": "string",
  "rol": "USUARIO | ADMIN",
  "expiresIn": 86400000
}
```
**Error** `400`:
```json
{ "error": "Las contraseñas no coinciden" }
```

#### POST `/api/auth/login`
**Request body** (`LoginRequestDTO`):
```json
{
  "email": "string",
  "password": "string"
}
```
**Response** `200 OK` (`LoginResponseDTO`):
```json
{
  "token": "string (JWT)",
  "email": "string",
  "rol": "USUARIO | ADMIN",
  "expiresIn": 86400000
}
```
**Error** `401`:
```json
{ "error": "Credenciales incorrectas" }
```

**JWT Payload** (decoded by frontend):
```json
{
  "sub": "user@email.com",
  "rol": "USUARIO",
  "iat": 1234567890,
  "exp": 1234654290
}
```

---

### 2.2 Perfil — `/api/usuario/perfil/**`
All endpoints require `Authorization: Bearer <token>` (ROLE_USUARIO).

#### GET `/api/usuario/perfil`
**Response** `200 OK` (`PerfilDTO`):
```json
{
  "sector": "string",
  "ubicacion": "string",
  "empresa": "string | null",
  "provincia": "string | null",
  "telefono": "string | null",
  "tipoEntidad": "string | null",
  "objetivos": "string | null",
  "necesidadesFinanciacion": "string | null",
  "descripcionLibre": "string | null"
}
```
**Error** `404` (`ErrorResponse`):
```json
{
  "status": 404,
  "message": "El usuario aún no ha completado su perfil",
  "timestamp": "2026-04-13T12:00:00",
  "path": "/api/usuario/perfil"
}
```

#### PUT `/api/usuario/perfil` (also POST)
**Request body** (`PerfilDTO`): same shape as GET response.
**Response** `200 OK` (`PerfilDTO`): updated perfil.

#### GET `/api/usuario/perfil/estado`
**Response** `200 OK`:
```json
{ "perfilCompleto": true }
```

#### PUT `/api/usuario/perfil/email`
**Request body** (`CambiarEmailDTO`):
```json
{
  "nuevoEmail": "string",
  "passwordActual": "string"
}
```
**Response** `200 OK` (`LoginResponseDTO`): new token with updated email.
**Errors**: `400` (bad password), `409` (email already in use).

#### PUT `/api/usuario/perfil/password`
**Request body** (`CambiarPasswordDTO`):
```json
{
  "passwordActual": "string",
  "nuevaPassword": "string",
  "confirmarPassword": "string"
}
```
**Response** `200 OK` (empty body).
**Error** `400`: validation message.

---

### 2.3 Dashboard — `/api/usuario/dashboard`

#### GET `/api/usuario/dashboard`
Requires ROLE_USUARIO.

**Response** `200 OK` (assembled inline in `AuthController`):
```json
{
  "usuario": {
    "id": 1,
    "email": "user@email.com",
    "rol": "USUARIO",
    "plan": "GRATUITO",
    "creadoEn": "2026-01-01T10:00:00"
  },
  "topRecomendaciones": [
    {
      "proyecto": {
        "id": 1,
        "nombre": "string",
        "sector": "string",
        "ubicacion": "string"
      },
      "recomendaciones": [
        {
          "id": 1,
          "puntuacion": 85,
          "explicacion": "string",
          "guia": "string | null",
          "guiaEnriquecida": "string | null",
          "usadaIa": true,
          "convocatoriaId": 42,
          "titulo": "string",
          "tipo": "string | null",
          "sector": "string | null",
          "ubicacion": "string | null",
          "urlOficial": "string | null",
          "fuente": "string | null",
          "fechaCierre": "2026-06-30",
          "vigente": true
        }
      ]
    }
  ],
  "roadmap": [
    {
      "proyecto": { "id": 1, "nombre": "string", "sector": "string", "ubicacion": "string" },
      "recomendacion": { /* same RecomendacionDTO shape above */ }
    }
  ],
  "totalRecomendaciones": 12
}
```

**MISMATCH — Dashboard response shape vs frontend expectation:**

The frontend (`dashboard/page.tsx`) destructures `data.topRecomendaciones` as
`Record<string, RecomendacionDTO[]>` (keyed by project name string), but the
backend `DashboardService` returns a `List<TopRecomendacionesProyecto>` — an
array of objects with `{ proyecto, recomendaciones }`. The frontend then also
uses `data.usuario.email` implicitly via JWT, not from the response body.
The actual `AuthController` serializes `usuario` as the raw `Usuario` entity
(including internal fields), not a DTO.

**CRITICAL GAP**: The dashboard response shape is inconsistent between what
`DashboardService.obtenerTopRecomendacionesPorProyecto()` returns (a list) and
what the frontend expects (a map keyed by project name). This works at runtime
only if Jackson serializes the record list in a way the frontend can iterate —
the frontend uses `Object.entries(data.topRecomendaciones)`, which would fail
on an array. This is a latent bug.

---

### 2.4 Proyectos — `/api/usuario/proyectos/**`
All require ROLE_USUARIO.

#### GET `/api/usuario/proyectos`
**Response** `200 OK` — array of `ProyectoDTO`:
```json
[
  {
    "id": 1,
    "nombre": "string",
    "sector": "string | null",
    "ubicacion": "string | null",
    "descripcion": "string | null"
  }
]
```

#### GET `/api/usuario/proyectos/{id}`
**Response** `200 OK` — single `ProyectoDTO`.

#### POST `/api/usuario/proyectos`
**Request body** (`ProyectoDTO` — id ignored):
```json
{
  "nombre": "string (required, max 100)",
  "sector": "string | null",
  "ubicacion": "string | null",
  "descripcion": "string | null (max 2000)"
}
```
**Response** `201 Created` — `ProyectoDTO` with assigned `id`.

#### PUT `/api/usuario/proyectos/{id}`
**Request body**: same as POST.
**Response** `200 OK` — updated `ProyectoDTO`.

#### DELETE `/api/usuario/proyectos/{id}`
**Response** `204 No Content`.

---

### 2.5 Recomendaciones — `/api/usuario/proyectos/{proyectoId}/recomendaciones/**`
All require ROLE_USUARIO.

#### GET `/api/usuario/proyectos/{proyectoId}/recomendaciones`
**Response** `200 OK` — array of `RecomendacionDTO`:
```json
[
  {
    "id": 1,
    "puntuacion": 85,
    "explicacion": "string | null",
    "guia": "string | null",
    "guiaEnriquecida": "string | null",
    "usadaIa": false,
    "convocatoriaId": 42,
    "titulo": "string",
    "tipo": "string | null",
    "sector": "string | null",
    "ubicacion": "string | null",
    "urlOficial": "string | null",
    "fuente": "string | null",
    "fechaCierre": "2026-06-30",
    "vigente": true
  }
]
```

#### POST `/api/usuario/proyectos/{proyectoId}/recomendaciones/buscar`
Fase 1: search BDNS without AI. Rate-limited: 30s per project.
**Response** `200 OK`:
```json
{ "candidatas": 12, "mensaje": "12 convocatorias encontradas. Pulsa «Analizar con IA» para puntuar y ordenar." }
```
**Error** `429 Too Many Requests`:
```json
{ "error": "Espera 28 segundos antes de volver a buscar.", "esperarSegundos": 28 }
```

#### GET `/api/usuario/proyectos/{proyectoId}/recomendaciones/stream`
Fase 2: AI analysis via Server-Sent Events. Rate-limited: 60s per project.
**Content-Type**: `text/event-stream`
**SSE event types emitted**:
- `event: estado` — status string
- `event: keywords` — keyword string extracted
- `event: busqueda` — searching status
- `event: progreso` — progress update
- `event: resultado` — new recommendation found (JSON of `RecomendacionDTO`)
- `event: completado` — final summary string
- `event: error` — error message

#### GET `/api/usuario/proyectos/{proyectoId}/recomendaciones/{recId}/guia-enriquecida`
**Response** `200 OK` (`GuiaSubvencionDTO`):
```json
{
  "grant_summary": {
    "title": "string",
    "organism": "string",
    "objective": "string",
    "who_can_apply": "string",
    "deadline": "string",
    "official_link": "string",
    "legal_basis": "string"
  },
  "application_methods": [
    { "method": "string", "description": "string", "official_portal": "string" }
  ],
  "required_documents": ["string"],
  "universal_requirements_lgs_art13": ["string"],
  "workflows": [
    {
      "method": "string",
      "steps": [
        {
          "step": 1,
          "phase": "string",
          "title": "string",
          "description": "string",
          "user_action": "string",
          "portal_action": "string",
          "required_documents": ["string"],
          "official_link": "string",
          "estimated_time_minutes": 15
        }
      ]
    }
  ],
  "visual_guides": [
    {
      "method": "string",
      "steps": [
        {
          "step": 1,
          "phase": "string",
          "title": "string",
          "description": "string",
          "screen_hint": "string",
          "image_prompt": "string",
          "official_link": "string"
        }
      ]
    }
  ],
  "legal_disclaimer": "string"
}
```

#### POST `/api/usuario/proyectos/{proyectoId}/recomendaciones/generar`
Legacy AI generation (not streamed). Returns array of `RecomendacionDTO` or
empty message object.

---

### 2.6 Convocatorias Públicas — `/api/convocatorias/publicas/**`
No authentication required.

#### GET `/api/convocatorias/publicas/buscar`
**Query params**: `q` (string), `sector` (string), `page` (int, default 0), `size` (int, default 20, max 50)
**Response** `200 OK` (`BusquedaPublicaResponse`):
```json
{
  "content": [
    {
      "id": 1,
      "titulo": "string",
      "sector": "string | null",
      "organismo": "string | null",
      "ubicacion": "string | null",
      "fechaCierre": "2026-06-30",
      "fechaPublicacion": "2026-01-15",
      "abierto": true,
      "urlOficial": "https://...",
      "idBdns": "string | null",
      "numeroConvocatoria": "string | null",
      "matchScore": null,
      "matchRazon": null
    }
  ],
  "totalElements": 1234,
  "totalPages": 62,
  "page": 0,
  "size": 20
}
```

#### GET `/api/convocatorias/publicas/destacadas`
**Response** `200 OK` — array of up to 16 `ConvocatoriaPublicaDTO` (no matchScore).

---

### 2.7 Convocatorias Autenticadas — `/api/usuario/convocatorias/**`
Requires ROLE_USUARIO.

#### GET `/api/usuario/convocatorias/recomendadas`
**Query params**: `page` (default 0), `size` (default 16, max 50)
**Response** `200 OK` — array of `ConvocatoriaPublicaDTO` with `matchScore` populated:
```json
[
  {
    "id": 1,
    "titulo": "string",
    "sector": "string | null",
    "organismo": "string | null",
    "ubicacion": "string | null",
    "fechaCierre": "2026-06-30",
    "fechaPublicacion": "2026-01-15",
    "abierto": true,
    "urlOficial": "https://...",
    "idBdns": "string | null",
    "numeroConvocatoria": "string | null",
    "matchScore": 72,
    "matchRazon": "string | null"
  }
]
```

#### GET `/api/usuario/convocatorias/buscar`
Same params as public buscar. Response shape is `BusquedaPublicaResponse` with
`matchScore` and `matchRazon` populated per item.

---

### 2.8 Admin — `/api/admin/**`
All require ROLE_ADMIN.

#### GET `/api/admin/dashboard`
**Response** `200 OK`:
```json
{
  "adminEmail": "admin@email.com",
  "totalUsuarios": 42,
  "totalConvocatorias": 150000,
  "totalProyectos": 87,
  "totalRecomendaciones": 412
}
```

#### GET `/api/admin/usuarios`
**Response** `200 OK`:
```json
{
  "usuarios": [
    {
      "id": 1,
      "email": "string",
      "rol": "USUARIO",
      "plan": "GRATUITO",
      "creadoEn": "2026-01-01T10:00:00"
    }
  ],
  "roles": ["ADMIN", "USUARIO"]
}
```
**NOTE**: Returns raw `Usuario` entities (serialized by Jackson). The `password`
field is named `password_hash` in DB but Jackson serializes the Java field
`password` — this is a security leak risk; it returns the bcrypt hash.

#### GET `/api/admin/usuarios/{id}`
**Response** `200 OK` (`AdminDetalleUsuarioResponseDTO`):
```json
{
  "usuario": {
    "id": 1,
    "email": "string",
    "rol": "USUARIO",
    "creadoEn": "2026-01-01T10:00:00",
    "empresa": "string | null",
    "provincia": "string | null",
    "telefono": "string | null"
  },
  "proyectos": [
    { "id": 1, "nombre": "string", "sector": "string" }
  ],
  "recsPerProyecto": { "1": 5, "3": 2 },
  "emailCambiado": false,
  "historialCorreo": [
    {
      "anterior": "old@email.com",
      "nuevo": "new@email.com",
      "fecha": "2026-03-15T10:00:00",
      "actor": "admin@email.com"
    }
  ]
}
```

#### PUT `/api/admin/usuarios/{id}/rol`
**Request body**: `{ "rol": "ADMIN" }`
**Response** `200 OK`: `{ "message": "Rol actualizado correctamente." }`

#### DELETE `/api/admin/usuarios/{id}`
**Response** `200 OK`: `{ "message": "Usuario eliminado correctamente." }`
**Error** `403`: cannot delete self.

#### GET `/api/admin/convocatorias`
**Query params**: `page` (default 0)
**Response** `200 OK` (raw `Convocatoria` entities, not DTO):
```json
{
  "convocatorias": [/* all Convocatoria fields */],
  "page": 0,
  "size": 50,
  "totalElements": 150000,
  "totalPages": 3000,
  "hasNext": true
}
```

#### POST `/api/admin/convocatorias`
**Request body** (`ConvocatoriaDTO`): all Convocatoria fields except `id`.
**Response** `201 Created`: `{ "message": "Convocatoria creada correctamente." }`

#### GET `/api/admin/convocatorias/{id}`
**Response** `200 OK` (`ConvocatoriaDTO`): all fields.

#### PUT `/api/admin/convocatorias/{id}`
**Request body** (`ConvocatoriaDTO`).
**Response** `200 OK`: success message.

#### DELETE `/api/admin/convocatorias/{id}`
**Response** `200 OK`: success message.

#### POST `/api/admin/convocatorias/importar-bdns`
**Query params**: `pagina` (default 0), `tamano` (default 20)
**Response** `200 OK`: `{ "message": "Se importaron N convocatorias nuevas desde BDNS." }`

---

### 2.9 Admin BDNS ETL — `/api/admin/bdns/**`

#### POST `/api/admin/bdns/importar`
**Query params**: `modo` (`FULL` | `INCREMENTAL`, default `FULL`), `delayMs` (default -1)
**Response** `202 Accepted`:
```json
{ "message": "Importación masiva BDNS iniciada en segundo plano.", "modo": "FULL", "delayMs": "configuración por defecto" }
```
**Error** `409`: already running.

#### DELETE `/api/admin/bdns/importar`
Cancels running import.
**Response** `200 OK`: `{ "mensaje": "Cancelación solicitada" }`

#### GET `/api/admin/bdns/estado`
**Response** `200 OK` (`ImportacionBdnsEstadoDTO`):
```json
{
  "estado": "EN_CURSO | INACTIVO | COMPLETADO | FALLIDO",
  "registrosImportados": 50000,
  "ejeActual": "ANDALUCIA pág. 42/180",
  "iniciadoEn": "2026-04-13T03:00:00",
  "finalizadoEn": null,
  "error": null,
  "modo": "FULL"
}
```

#### GET `/api/admin/bdns/ejes`
**Response** `200 OK` — list of `SyncStateDTO`:
```json
[
  {
    "eje": "ANDALUCIA",
    "estado": "COMPLETADO",
    "ultimaPaginaOk": 180,
    "registrosNuevos": 12000,
    "registrosActualizados": 500,
    "tsInicio": "2026-04-13T03:00:00Z",
    "tsUltimaCarga": "2026-04-13T04:30:00Z"
  }
]
```

#### GET `/api/admin/bdns/historial`
**Response** `200 OK` — list of `ResumenEjecucionDTO`:
```json
[
  {
    "ejecucionId": "uuid-string",
    "tsInicio": "2026-04-13T03:00:00Z",
    "tsFin": "2026-04-13T05:00:00Z",
    "totalRegistrosNuevos": 45000,
    "totalRegistrosActualizados": 2100,
    "totalErrores": 3,
    "ejesProcesados": 23,
    "totalPaginas": 4200
  }
]
```

#### GET `/api/admin/bdns/historial/{ejecucionId}`
**Response** `200 OK` — list of `SyncLog` records (raw entity).

#### GET `/api/admin/bdns/cobertura`
**Response** `200 OK` (`CoberturaDTO`):
```json
{
  "totalConvocatorias": 150000,
  "campos": [
    { "campo": "organismo", "conValor": 148000, "porcentaje": 98.7 },
    { "campo": "fechaPublicacion", "conValor": 140000, "porcentaje": 93.3 },
    { "campo": "descripcion", "conValor": 110000, "porcentaje": 73.3 },
    { "campo": "textoCompleto", "conValor": 80000, "porcentaje": 53.3 },
    { "campo": "sector", "conValor": 50000, "porcentaje": 33.3 },
    { "campo": "fechaCierre", "conValor": 130000, "porcentaje": 86.7 },
    { "campo": "ubicacion", "conValor": 90000, "porcentaje": 60.0 }
  ]
}
```

#### PUT `/api/admin/bdns/sync-state/pagina`
**Query param**: `pagina` (int ≥ 0)
**Response** `200 OK`: `{ "message": "...", "ultimaPaginaOk": N, "siguientePagina": N+1 }`

#### POST `/api/admin/bdns/enriquecer`
Starts enrichment job.
**Response** `202 Accepted`: `{ "message": "...", "nota": "..." }`

#### GET `/api/admin/bdns/enriquecer/estado`
**Response** `200 OK`:
```json
{
  "estado": "EN_CURSO",
  "procesados": 5000,
  "enriquecidos": 4800,
  "errores": 200,
  "total": 150000,
  "iniciadoEn": "2026-04-13T03:00:00",
  "finalizadoEn": null
}
```

#### DELETE `/api/admin/bdns/enriquecer`
Cancel enrichment.

#### GET `/api/admin/bdns/ultima-importacion`
**Response** `200 OK`: `{ "estado": "COMPLETADO", "registrosImportados": 45000, "finalizadoEn": "..." }`

---

## 3. Frontend TypeScript Types (existing)

### `src/lib/auth.ts`
```typescript
interface JwtPayload {
  sub: string;    // user email
  rol: string;    // "USUARIO" | "ADMIN"
  iat: number;
  exp: number;
}
```

### `src/lib/api.ts` — inline types
```typescript
interface ConvocatoriaPublica {
  id: number;
  titulo: string;
  sector?: string;
  organismo?: string;
  ubicacion?: string;
  fechaCierre?: string;       // ISO date string
  fechaPublicacion?: string;  // ISO date string
  abierto?: boolean;
  urlOficial?: string;
  idBdns?: string;
  numeroConvocatoria?: string;
  matchScore?: number;        // 0-100, null for public endpoints
  matchRazon?: string;
}

interface BusquedaPublicaResponse {
  content: ConvocatoriaPublica[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
```

### `src/lib/types/convocatorias.ts`
```typescript
interface Convocatoria {  // used only in admin panel
  id: number;
  titulo: string;
  tipo: string;
  sector: string;
  ubicacion: string;
  fuente: string;
  fechaCierre: string;
}

interface ConvocatoriasPageResponse {
  convocatorias: Convocatoria[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}
```

### `src/app/dashboard/page.tsx` — inline
```typescript
interface RecomendacionDTO {
  id: number;
  puntuacion: number;
  explicacion: string;
  convocatoria: { titulo: string };   // WRONG: backend sends flat fields, not nested
  proyecto: { id: number; nombre: string };  // WRONG: backend sends via topRecomendaciones wrapper
}

interface DashboardData {
  email: string;                                        // NOT in backend response body
  totalRecomendaciones: number;
  topRecomendaciones: Record<string, RecomendacionDTO[]>;  // WRONG shape (see §2.3 gap)
  roadmap: { proyecto: { id: number; nombre: string }; recomendacion: RecomendacionDTO }[];
}
```

### `src/app/proyectos/[id]/recomendaciones/page.tsx` — inline
```typescript
interface Recomendacion {
  id: number;
  puntuacion: number;
  explicacion: string;
  guia?: string;
  convocatoriaId: number;
  titulo: string;
  tipo: string;
  sector: string;
  ubicacion: string;
  urlOficial: string;
  fuente: string;
  vigente: boolean;
  usadaIa: boolean;
}
```

### `src/app/proyectos/page.tsx` — inline
```typescript
interface Proyecto {
  id: number;
  nombre: string;
  descripcion: string;
  sector?: string;
  presupuesto?: number;       // MISSING in backend
  fechaCreacion?: string;     // MISSING in backend
}
```

### `src/app/perfil/page.tsx` — inline
```typescript
interface PerfilData {
  nombre: string;                           // MISSING in backend PerfilDTO
  email: string;                            // NOT in PerfilDTO, from JWT
  empresa?: string;
  provincia?: string;
  telefono?: string;
  rol?: string;                             // NOT in PerfilDTO, from JWT
  notificacionesConvocatorias?: boolean;    // MISSING in backend
  notificacionesRecordatorios?: boolean;    // MISSING in backend
  notificacionesNovedades?: boolean;        // MISSING in backend
}
```

### `src/app/admin/dashboard/page.tsx` — inline
```typescript
interface Stats {
  totalUsuarios: number;
  totalProyectos: number;
  totalRecomendaciones: number;
  totalConvocatorias: number;
}
```

### `src/app/admin/usuarios/page.tsx` — inline
```typescript
interface Usuario {
  id: number;
  email: string;
  rol: string;
  creadoEn: string;
}
```

### `src/app/admin/bdns/page.tsx` — inline
```typescript
interface EstadoJob {
  estado: "INACTIVO" | "EN_CURSO" | "COMPLETADO" | "FALLIDO";
  registrosImportados: number;
  ejeActual: string | null;
  iniciadoEn: string | null;
  finalizadoEn: string | null;
  error: string | null;
  modo: "FULL" | "INCREMENTAL" | null;
}

interface ResumenEjecucionDTO {
  ejecucionId: string;
  tsInicio: string | null;
  tsFin: string | null;
  totalRegistrosNuevos: number;
  totalRegistrosActualizados: number;
  totalErrores: number;
  ejesProcesados: number;
  totalPaginas: number;
}

interface CampoCobertura { campo: string; conValor: number; porcentaje: number; }
interface CoberturaDTO { totalConvocatorias: number; campos: CampoCobertura[]; }
```

---

## 4. Gap Analysis — Missing Fields & Endpoints

### 4.1 Critical Bugs / Data Shape Mismatches

| # | Location | Issue | Severity |
|---|----------|-------|----------|
| B1 | `GET /api/usuario/dashboard` | Backend returns `List<TopRecomendacionesProyecto>` (array with `proyecto` + `recomendaciones` keys), frontend expects `Record<string, RecomendacionDTO[]>` (map keyed by name). The `Object.entries()` call on an array works but returns index strings, not project names. The roadmap item structure `{ proyecto, recomendacion }` (singular) from `DashboardService` does match the frontend expectation. **The `topRecomendaciones` shape is broken.** | Critical |
| B2 | `GET /api/admin/usuarios` | Returns raw `Usuario[]` with the `password` field (bcrypt hash). Jackson will serialize it unless `@JsonIgnore` is on the field. This is a security leak. | Critical |
| B3 | `PerfilDTO` used as GET response | Does not include `email` or `nombre`. Frontend `perfil/page.tsx` tries to merge API response with JWT data, but the `nombre` field is silently empty because the backend has no `nombre` column on `perfiles`. | High |

### 4.2 Missing Fields — Backend Does Not Return

| Frontend expects | Backend entity/DTO | Verdict |
|------------------|--------------------|---------|
| `Proyecto.fechaCreacion` | Not in `Proyecto` entity or `ProyectoDTO` | **MISSING** — add `creadoEn LocalDateTime` to `Proyecto` entity and expose in `ProyectoDTO` |
| `Proyecto.presupuesto` | Not in entity or DTO | **MISSING** — frontend renders it if present, backend never sends it |
| `PerfilData.nombre` | Not in `Perfil` entity or `PerfilDTO` | **MISSING** — no full name field exists; profile only has `empresa`, `telefono`, `provincia` |
| `PerfilData.notificacionesConvocatorias` | Not in backend | Intentionally stored in `localStorage` only — no persistence |
| `PerfilData.notificacionesRecordatorios` | Not in backend | Same — localStorage only |
| `PerfilData.notificacionesNovedades` | Not in backend | Same — localStorage only |
| `Usuario.plan` | Not in `AdminUsuarioDetalleDTO` | `Plan` field exists on entity, not exposed to admin user list. Admin panel cannot see or change user plan. |
| `ConvocatoriaPublica.tipo` | Missing from `ConvocatoriaPublicaDTO` | `ConvocatoriaPublicaDTO` only has 11 fields; `tipo` is on entity but not in the DTO. |
| `RecomendacionDTO.organismo` | Not in DTO | `Convocatoria.organismo` exists but `RecomendacionDTO` does not expose it. The recommendations list cannot show organismo. |
| `RecomendacionDTO.presupuesto` | Not in DTO | `Convocatoria.presupuesto` exists but not surfaced. |
| `RecomendacionDTO.fechaPublicacion` | Not in DTO | Useful for redesign card display. |

### 4.3 Missing Endpoints

| Endpoint | Purpose | Priority |
|----------|---------|---------|
| `GET /api/usuario/perfil/completo` | Return perfil fields merged with user account info (`email`, `rol`, `plan`, `creadoEn`) in one call. Eliminates frontend's need to pull email from JWT separately. | High |
| `GET /api/usuario/stats` | Aggregate user statistics: `totalProyectos`, `totalRecomendaciones`, `totalConvocatoriasVigentes`, `plan`. Needed by redesigned dashboard header/hero stats bar. | High |
| `GET /api/usuario/convocatorias/recomendadas` | Already exists but returns a flat array. The redesign will need pagination metadata (`totalElements`, `totalPages`, `page`). Currently returns `List<>` not a page wrapper. | Medium |
| `GET /api/admin/convocatorias` | Returns raw entities (all fields including internal ones). Should return a DTO with only fields needed by the admin table. Also, filtering by `q`, `sector`, `abierto` would be needed for the redesigned admin convocatorias page. | Medium |
| `GET /api/admin/stats` | Single endpoint returning `totalUsuarios`, `totalConvocatorias`, `totalProyectos`, `totalRecomendaciones`, `totalConvocatoriasAbiertas`, `porcentajeConCobertura`. The redesigned admin dashboard needs richer stats cards. | Medium |
| `PUT /api/usuario/usuario/plan` (admin changes user plan) | `PUT /api/admin/usuarios/{id}/plan` — currently no endpoint to upgrade/downgrade a user's plan. | Low |
| `GET /api/usuario/proyectos/{id}/stats` | Per-project stats: `totalRecomendaciones`, `puntuacionMedia`, `mejorPuntuacion`, `convocatoriasVigentes`. Needed by redesigned project detail header. | Low |

### 4.4 Admin Convocatorias — Missing Filter Parameters
`GET /api/admin/convocatorias` only accepts `page`. The redesigned admin panel
needs:
- `q` — free-text title search
- `sector` — sector filter
- `abierto` — boolean filter
- `organismo` — filter by granting body
- `sort` — field and direction

### 4.5 Public Convocatorias — Missing Filter Parameters
`GET /api/convocatorias/publicas/buscar` and the authenticated counterpart need:
- `abierto=true` filter to show only open grants (high UX value)
- `organismo` filter
- `fechaCierreDesde` / `fechaCierreHasta` date range filters
- `presupuestoMin` / `presupuestoMax` (when budget field has reasonable coverage)

### 4.6 Guías de Subvenciones — No Backend at All
`/app/guias/page.tsx` is 100% hardcoded static data (6 guides). There is no
`/api/guias/**` endpoint. The page shows "Próximamente disponible" on all modals.
For the redesign, no backend change is needed unless the team wants to make
guides dynamic (not in scope).

### 4.7 Security Gap
`GET /api/admin/usuarios` returns raw `Usuario` entity list which includes the
`password` field (bcrypt hash). A `@JsonIgnore` annotation on `Usuario.password`
or a proper `AdminUsuarioListDTO` is needed.

---

## 5. Summary — Recommended Backend Changes for Redesign

### Priority 1 (Blocking — fix before UI build)
1. **Fix `GET /api/usuario/dashboard` response shape**: Change `topRecomendaciones`
   to be a `Map<String, List<RecomendacionDTO>>` (keyed by project name) to match
   frontend expectation, OR update the frontend to consume the current list shape.
   The list shape with `{ proyecto, recomendaciones }` objects is the correct one
   to keep; update the frontend type and destructuring logic.

2. **Add `@JsonIgnore` to `Usuario.password`**: Prevents bcrypt hash from leaking
   via admin API. Apply to `Usuario.java`.

3. **Add `creadoEn` to `Proyecto` entity and `ProyectoDTO`**: Expose creation
   timestamp so the project list card can render it.

4. **Add `tipo` to `ConvocatoriaPublicaDTO`**: Currently missing; used by
   recommendation cards in `recomendaciones/page.tsx`.

### Priority 2 (Important for redesign UX)
5. **Add `organismo`, `presupuesto`, `fechaPublicacion` to `RecomendacionDTO`**:
   These are available on the `Convocatoria` entity; adding them to `RecomendacionDTO`
   enables the redesigned recommendation cards to show richer metadata.

6. **Create `GET /api/usuario/perfil/completo`** (or extend existing GET): Return
   `{ email, rol, plan, creadoEn }` merged with all `PerfilDTO` fields. Removes
   the need for the frontend to patch from JWT.

7. **Add `nombre` field to `Perfil` entity + `PerfilDTO`**: Currently no
   "full name" concept exists at all. Redesigned profile page renders a name field.

8. **Add pagination wrapper to `GET /api/usuario/convocatorias/recomendadas`**:
   Change from `List<>` to a paginated response matching `BusquedaPublicaResponse`
   shape for consistency.

### Priority 3 (Enhancement for redesign)
9. **Add search/filter params to `GET /api/admin/convocatorias`**: `q`, `sector`,
   `abierto`, `organismo`, `sort`.

10. **Add `GET /api/admin/stats`**: Richer stats for admin dashboard redesign,
    including `totalConvocatoriasAbiertas` and breakdown by plan.

11. **Expose `plan` in admin user list** (`AdminUsuarioDetalleDTO`): Include
    `plan: "GRATUITO" | "PREMIUM"` in user list and detail responses.

12. **Add `abierto` filter to public buscar**: `GET /api/convocatorias/publicas/buscar?abierto=true`
    to allow the search page to surface only live grants.

---

## 6. Canonical TypeScript Types for Redesign

These are the corrected / complete types the redesign should define in
`src/lib/types/*.ts`:

```typescript
// src/lib/types/auth.ts
export interface JwtPayload {
  sub: string;   // email
  rol: "USUARIO" | "ADMIN";
  iat: number;
  exp: number;
}

export interface LoginResponse {
  token: string;
  email: string;
  rol: "USUARIO" | "ADMIN";
  expiresIn: number;
}

// src/lib/types/perfil.ts
export interface PerfilDTO {
  sector: string;
  ubicacion: string;
  empresa?: string;
  provincia?: string;
  telefono?: string;
  tipoEntidad?: string;
  objetivos?: string;
  necesidadesFinanciacion?: string;
  descripcionLibre?: string;
  // Proposed additions (Priority 2 backend changes):
  nombre?: string;
}

export interface PerfilCompletoDTO extends PerfilDTO {
  email: string;
  rol: "USUARIO" | "ADMIN";
  plan: "GRATUITO" | "PREMIUM";
  creadoEn: string;
}

// src/lib/types/proyecto.ts
export interface ProyectoDTO {
  id: number;
  nombre: string;
  sector?: string;
  ubicacion?: string;
  descripcion?: string;
  // Proposed addition (Priority 1 backend change):
  creadoEn?: string;
}

// src/lib/types/recomendacion.ts
export interface RecomendacionDTO {
  id: number;
  puntuacion: number;
  explicacion?: string;
  guia?: string;
  guiaEnriquecida?: string;
  usadaIa: boolean;
  vigente: boolean;
  // Convocatoria fields (flat):
  convocatoriaId: number;
  titulo: string;
  tipo?: string;           // currently missing from DTO — Priority 1
  sector?: string;
  ubicacion?: string;
  urlOficial?: string;
  fuente?: string;
  fechaCierre?: string;    // ISO date
  // Proposed additions (Priority 2):
  organismo?: string;
  presupuesto?: number;
  fechaPublicacion?: string;
}

// src/lib/types/convocatoria.ts
export interface ConvocatoriaPublicaDTO {
  id: number;
  titulo: string;
  sector?: string;
  organismo?: string;
  ubicacion?: string;
  fechaCierre?: string;
  fechaPublicacion?: string;
  abierto?: boolean;
  urlOficial?: string;
  idBdns?: string;
  numeroConvocatoria?: string;
  matchScore?: number;
  matchRazon?: string;
  // Currently missing from backend DTO — Priority 1:
  tipo?: string;
}

export interface ConvocatoriasPageResponse<T = ConvocatoriaPublicaDTO> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface ConvocatoriaAdminDTO {
  id: number;
  titulo: string;
  tipo?: string;
  sector?: string;
  ubicacion?: string;
  fuente?: string;
  idBdns?: string;
  numeroConvocatoria?: string;
  fechaCierre?: string;
  organismo?: string;
  fechaPublicacion?: string;
  descripcion?: string;
  presupuesto?: number;
  abierto?: boolean;
  finalidad?: string;
  fechaInicio?: string;
}

// src/lib/types/dashboard.ts
export interface DashboardData {
  usuario: {
    id: number;
    email: string;
    rol: "USUARIO" | "ADMIN";
    plan: "GRATUITO" | "PREMIUM";
    creadoEn: string;
  };
  topRecomendaciones: Array<{
    proyecto: { id: number; nombre: string; sector?: string; ubicacion?: string };
    recomendaciones: RecomendacionDTO[];
  }>;
  roadmap: Array<{
    proyecto: { id: number; nombre: string; sector?: string; ubicacion?: string };
    recomendacion: RecomendacionDTO;
  }>;
  totalRecomendaciones: number;
}

// src/lib/types/admin.ts
export interface AdminDashboardStats {
  adminEmail: string;
  totalUsuarios: number;
  totalConvocatorias: number;
  totalProyectos: number;
  totalRecomendaciones: number;
}

export interface AdminUsuarioListItem {
  id: number;
  email: string;
  rol: "USUARIO" | "ADMIN";
  plan: "GRATUITO" | "PREMIUM";  // not yet exposed — Priority 3
  creadoEn: string;
}

export interface AdminUsuarioDetalle {
  usuario: {
    id: number;
    email: string;
    rol: "USUARIO" | "ADMIN";
    creadoEn: string;
    empresa?: string;
    provincia?: string;
    telefono?: string;
  };
  proyectos: Array<{ id: number; nombre: string; sector: string }>;
  recsPerProyecto: Record<number, number>;
  emailCambiado: boolean;
  historialCorreo: Array<{
    anterior: string;
    nuevo: string;
    fecha: string;
    actor: string;
  }>;
}

// src/lib/types/bdns.ts
export interface ImportacionBdnsEstadoDTO {
  estado: "INACTIVO" | "EN_CURSO" | "COMPLETADO" | "FALLIDO";
  registrosImportados: number;
  ejeActual: string | null;
  iniciadoEn: string | null;
  finalizadoEn: string | null;
  error: string | null;
  modo: "FULL" | "INCREMENTAL" | null;
}

export interface ResumenEjecucionDTO {
  ejecucionId: string;
  tsInicio: string | null;
  tsFin: string | null;
  totalRegistrosNuevos: number;
  totalRegistrosActualizados: number;
  totalErrores: number;
  ejesProcesados: number;
  totalPaginas: number;
}

export interface SyncStateDTO {
  eje: string;
  estado: "PENDIENTE" | "EN_PROGRESO" | "COMPLETADO" | "ERROR";
  ultimaPaginaOk: number;
  registrosNuevos: number;
  registrosActualizados: number;
  tsInicio: string | null;
  tsUltimaCarga: string | null;
}

export interface CoberturaDTO {
  totalConvocatorias: number;
  campos: Array<{ campo: string; conValor: number; porcentaje: number }>;
}

// src/lib/types/guia.ts  (GuiaSubvencionDTO)
export interface GuiaSubvencionDTO {
  grant_summary?: {
    title?: string;
    organism?: string;
    objective?: string;
    who_can_apply?: string;
    deadline?: string;
    official_link?: string;
    legal_basis?: string;
  };
  application_methods?: Array<{
    method?: string;
    description?: string;
    official_portal?: string;
  }>;
  required_documents?: string[];
  universal_requirements_lgs_art13?: string[];
  workflows?: Array<{
    method?: string;
    steps?: Array<{
      step?: number;
      phase?: string;
      title?: string;
      description?: string;
      user_action?: string;
      portal_action?: string;
      required_documents?: string[];
      official_link?: string;
      estimated_time_minutes?: number;
    }>;
  }>;
  visual_guides?: Array<{
    method?: string;
    steps?: Array<{
      step?: number;
      phase?: string;
      title?: string;
      description?: string;
      screen_hint?: string;
      image_prompt?: string;
      official_link?: string;
    }>;
  }>;
  legal_disclaimer?: string;
}
```
