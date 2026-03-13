# Diagramas Syntia – Versión revisada y completa (2026-03-10)

## Alineación Arquitectónica Vigente (2026-03-13)

> Interpretar los diagramas con estas reglas de prioridad:
>
> - Frontend objetivo: `Angular` consumiendo `API REST`.
> - Arquitectura de presentación: SPA cliente sin motor de plantillas server-side.
> - Contrato principal del sistema: endpoints en `controller/api/` con JWT.
> - Pipeline `BDNS+IA` y lógica de negocio permanecen en servicios.
> - SSE (`SseEmitter`) se mantiene para feedback en tiempo real al cliente SPA.

---

## 1. Modelo Entidad-Relación (ER)

```mermaid
erDiagram
    USUARIO {
        int id PK
        string email UK
        string password_hash
        enum rol "ADMIN | USUARIO"
        datetime creado_en
    }

    PERFIL {
        int id PK
        int usuario_id FK
        string sector
        string ubicacion
        string tipo_entidad
        string objetivos
        string necesidades_financiacion
        text descripcion_libre
    }

    PROYECTO {
        int id PK
        int usuario_id FK
        string nombre
        string sector
        string ubicacion
        text descripcion
    }

    CONVOCATORIA {
        int id PK
        string titulo
        string tipo
        string sector
        string ubicacion
        string url_oficial
        string fuente
        string id_bdns
        string numero_convocatoria
        date fecha_cierre
    }

    RECOMENDACION {
        int id PK
        int proyecto_id FK
        int convocatoria_id FK
        int puntuacion
        text explicacion
        text guia
        boolean usada_ia
        datetime generada_en
    }

    USUARIO ||--|| PERFIL : "tiene"
    USUARIO ||--o{ PROYECTO : "describe"
    PROYECTO ||--o{ RECOMENDACION : "genera"
    CONVOCATORIA ||--o{ RECOMENDACION : "aparece en"
```

---

## 2. Diagrama de Clases UML

> **Actualizado a 2026-03-10 (v3.0.0)** — Refleja el estado real de la implementación incluyendo SSE streaming, OpenAI y BDNS.

```mermaid
classDiagram
    class Rol {
        <<enumeration>>
        ADMIN
        USUARIO
    }

    class Usuario {
        +Long id
        +String email
        +String passwordHash
        +Rol rol
        +LocalDateTime creadoEn
    }

    class Perfil {
        +Long id
        +String sector
        +String ubicacion
        +String tipoEntidad
        +String objetivos
        +String necesidadesFinanciacion
        +String descripcionLibre
    }

    class Proyecto {
        +Long id
        +String nombre
        +String sector
        +String ubicacion
        +String descripcion
    }

    class Convocatoria {
        +Long id
        +String titulo
        +String tipo
        +String sector
        +String ubicacion
        +String urlOficial
        +String fuente
        +String idBdns
        +String numeroConvocatoria
        +LocalDate fechaCierre
    }

    class Recomendacion {
        +Long id
        +int puntuacion
        +String explicacion
        +String guia
        +boolean usadaIa
        +LocalDateTime generadaEn
    }

    class JwtService {
        -String secretKey
        -long expiration
        +generarToken(email, rol) String
        +validarToken(token, username) boolean
        +extraerUsername(token) String
        +extraerRol(token) String
    }

    class JwtAuthenticationFilter {
        -JwtService jwtService
        +doFilterInternal(request, response, chain) void
    }

    class CustomUserDetailsService {
        +loadUserByUsername(username) UserDetails
    }

    class UsuarioService {
        +registrar(email, password, rol) Usuario
        +buscarPorEmail(email) Optional~Usuario~
        +buscarPorId(id) Optional~Usuario~
        +obtenerTodos() List~Usuario~
        +eliminar(id) void
        +cambiarRol(id, nuevoRol) Usuario
    }

    class PerfilService {
        +tienePerfil(usuarioId) boolean
        +obtenerPerfil(usuarioId) Optional~Perfil~
        +crearPerfil(usuario, dto) Perfil
        +actualizarPerfil(usuarioId, dto) Perfil
        +toDTO(perfil) PerfilDTO
    }

    class ProyectoService {
        +obtenerProyectos(usuarioId) List~Proyecto~
        +obtenerPorId(id, usuarioId) Proyecto
        +crear(usuario, dto) Proyecto
        +actualizar(id, usuarioId, dto) Proyecto
        +eliminar(id, usuarioId) void
        +toDTO(proyecto) ProyectoDTO
    }

    class MotorMatchingService {
        -int UMBRAL_RECOMENDACION = 20
        -int RESULTADOS_POR_KEYWORD = 15
        -int MAX_CANDIDATAS_IA = 15
        -TransactionTemplate transactionTemplate
        +generarRecomendaciones(proyecto) List~Recomendacion~
        +generarRecomendacionesStream(proyecto, emitter) void
    }

    class OpenAiClient {
        -String apiKey
        -String model
        -int maxTokens
        -double temperature
        +chat(systemPrompt, userPrompt) String
    }

    class OpenAiMatchingService {
        +analizar(proyecto, perfil, convocatoria, detalle) ResultadoIA
        +generarKeywordsBusqueda(proyecto, perfil) List~String~
    }

    class BdnsClientService {
        +importar(pagina, tamano) List~ConvocatoriaDTO~
        +buscarPorTexto(keywords, pagina, tamano) List~ConvocatoriaDTO~
        +obtenerDetalleTexto(idBdns) String
    }

    class RecomendacionService {
        +obtenerPorProyecto(proyectoId) List~RecomendacionDTO~
        +contarPorProyecto(proyectoId) long
        +filtrar(proyectoId, tipo, sector, ubicacion) List~RecomendacionDTO~
        +obtenerTiposDistintos(proyectoId) List~String~
        +obtenerSectoresDistintos(proyectoId) List~String~
    }

    class ConvocatoriaService {
        +obtenerTodas() List~Convocatoria~
        +obtenerPorId(id) Convocatoria
        +crear(dto) Convocatoria
        +actualizar(id, dto) Convocatoria
        +eliminar(id) void
        +toDTO(convocatoria) ConvocatoriaDTO
        +importarDesdeBdns(pagina, tamano) int
    }

    class DashboardService {
        +obtenerTopRecomendacionesPorProyecto(usuarioId, topN) Map
        +obtenerRoadmap(usuarioId) List~RoadmapItem~
        +contarTotalRecomendaciones(usuarioId) long
    }

    Usuario --> Rol : tiene
    Usuario "1" --> "1" Perfil : tiene
    Usuario "1" --> "0..*" Proyecto : crea
    Proyecto "1" --> "0..*" Recomendacion : genera
    Recomendacion "0..*" --> "1" Convocatoria : referencia
    JwtAuthenticationFilter --> JwtService : usa
    CustomUserDetailsService --> UsuarioService : delega
    UsuarioService --> Usuario : gestiona
    PerfilService --> Perfil : gestiona
    ProyectoService --> Proyecto : gestiona
    MotorMatchingService --> OpenAiMatchingService : genera keywords y evalúa
    MotorMatchingService --> BdnsClientService : busca convocatorias
    MotorMatchingService --> Recomendacion : crea y persiste
    OpenAiMatchingService --> OpenAiClient : envía prompts
    RecomendacionService --> Recomendacion : lee y filtra
    ConvocatoriaService --> Convocatoria : gestiona
    DashboardService --> ProyectoService : usa
    DashboardService --> RecomendacionService : usa
```

---

## 3. Diagrama de Casos de Uso UML

```mermaid
flowchart TD
    UA([Usuario Final])
    AD([Administrador])

    subgraph Syntia
        UC1[Registrarse]
        UC2[Iniciar sesión]
        UC3[Cerrar sesión]
        UC4[Completar perfil]
        UC5[Crear proyecto]
        UC6[Ver recomendaciones]
        UC7[Ver roadmap estratégico]
        UC8[Filtrar oportunidades]
        UC9[Gestionar usuarios]
        UC10[Supervisar sistema]
        UC11[Configurar motor de IA]
    end

    UA --> UC1
    UA --> UC2
    UA --> UC3
    UA --> UC4
    UA --> UC5
    UA --> UC6
    UA --> UC7
    UA --> UC8
    AD --> UC2
    AD --> UC3
    AD --> UC9
    AD --> UC10
    AD --> UC11
```

---

## 4. Diagrama de Secuencia UML – Flujo de Recomendación con SSE Streaming

> **Actualizado a v3.0.0** — Refleja el flujo real con SSE, OpenAI y BDNS.

```mermaid
sequenceDiagram
    actor Usuario
    participant Navegador as Navegador (EventSource)
    participant Controller as RecomendacionController
    participant Motor as MotorMatchingService
    participant OpenAI as OpenAI API (gpt-4.1)
    participant BDNS as API BDNS
    participant BD as PostgreSQL

    Usuario->>Navegador: Click "Analizar con IA"
    Navegador->>Controller: GET /generar-stream (SSE)
    Controller->>Controller: Crea SseEmitter (timeout 180s)
    Controller->>Controller: CompletableFuture.runAsync()
    Controller-->>Navegador: SseEmitter (conexión SSE abierta)

    Note over Motor, BD: Ejecución en hilo separado (no bloquea Tomcat)

    Motor->>BD: DELETE recomendaciones anteriores
    Motor-->>Navegador: SSE estado: "Limpiando..."

    Motor->>OpenAI: Generar 6-8 keywords de búsqueda
    OpenAI-->>Motor: ["kw1", "kw2", ...]
    Motor-->>Navegador: SSE keywords: {total, keywords[]}

    loop Por cada keyword
        Motor->>BDNS: GET búsqueda (?vigente=true)
        BDNS-->>Motor: Convocatorias candidatas
    end
    Motor->>Motor: Deduplicación por título → top 15
    Motor-->>Navegador: SSE busqueda: {candidatas: 15}

    loop Por cada candidata (1..15)
        Motor-->>Navegador: SSE progreso: {actual, total, titulo}
        Motor->>BDNS: GET detalle convocatoria
        BDNS-->>Motor: Texto enriquecido (objeto, requisitos, beneficiarios)
        Motor->>OpenAI: Evaluar compatibilidad (puntuacion + explicacion + guia)
        OpenAI-->>Motor: {puntuacion: 85, explicacion: "...", guia: "..."}

        alt puntuacion ≥ 20
            Motor->>BD: Persistir convocatoria + recomendación
            Motor-->>Navegador: SSE resultado: {titulo, puntuacion, explicacion, ...}
            Note over Navegador: Tarjeta aparece en tiempo real
        end
    end

    Motor-->>Navegador: SSE completado: {totalRecomendaciones, totalEvaluadas}
    Note over Navegador: Barra verde 100%, recarga en 2.5s
```

---

## 5. Diagrama de Secuencia UML – Flujo de Autenticación JWT (API REST)

```mermaid
sequenceDiagram
    actor Cliente
    participant API as API REST
    participant AuthController
    participant UsuarioService
    participant JwtService
    participant BD as Base de Datos

    Note over Cliente, BD: 1. Login y obtención del token

    Cliente->>API: POST /api/auth/login {email, password}
    API->>AuthController: login(credentials)
    AuthController->>UsuarioService: autenticar(email, password)
    UsuarioService->>BD: findByEmail(email)
    BD-->>UsuarioService: Usuario
    UsuarioService->>UsuarioService: Verifica BCrypt(password, hash)
    UsuarioService-->>AuthController: Usuario autenticado
    AuthController->>JwtService: generarToken(usuario)
    JwtService->>JwtService: Firma HMAC-SHA256 (sub=email, rol, exp)
    JwtService-->>AuthController: JWT token
    AuthController-->>API: 200 OK {token, rol}
    API-->>Cliente: JWT token

    Note over Cliente, BD: 2. Petición autenticada con token

    Cliente->>API: GET /api/recomendaciones (Authorization: Bearer token)
    API->>JwtService: validarToken(token)
    JwtService->>JwtService: Verifica firma y expiración
    JwtService-->>API: Token válido (email, rol)
    API->>AuthController: SecurityContext establecido
    AuthController->>BD: Consulta datos
    BD-->>AuthController: Resultados
    AuthController-->>API: 200 OK {datos}
    API-->>Cliente: Respuesta JSON
```

---

## 6. Diagrama de Secuencia UML – Flujo de Login API JWT

```mermaid
sequenceDiagram
    actor Usuario
    participant Navegador
    participant SecurityFilter as Spring Security Filter
    participant AuthController
    participant UserDetailsService
    participant BD as Base de Datos

    Usuario->>Navegador: Accede a /login
    Navegador->>AuthController: GET /login
    AuthController-->>Navegador: login.html (formulario)
    Usuario->>Navegador: Introduce email y contraseña
    Navegador->>SecurityFilter: POST /login {email, password}
    SecurityFilter->>UserDetailsService: loadUserByUsername(email)
    UserDetailsService->>BD: findByEmail(email)
    BD-->>UserDetailsService: Usuario
    UserDetailsService-->>SecurityFilter: UserDetails
    SecurityFilter->>SecurityFilter: Verifica BCrypt(password, hash)
    SecurityFilter->>SecurityFilter: Crea sesión HTTP + SecurityContext

    alt Rol = ADMIN
        SecurityFilter-->>Navegador: Redirect /admin/dashboard
    else Rol = USUARIO
        SecurityFilter-->>Navegador: Redirect /usuario/dashboard
    end

    Navegador-->>Usuario: Dashboard según rol
```
