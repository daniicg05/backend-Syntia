# Especificaciones Técnicas del Proyecto: Syntia

## Stack actual (2026-03-27)

| Capa | Tecnología | Estado |
|------|------------|--------|
| Backend | Java 17 + Spring Boot 3.3.x + Maven | ✅ Producción |
| Seguridad | Spring Security 6.x + JWT (jjwt 0.12.6) | ✅ Producción |
| Persistencia | Spring Data JPA + PostgreSQL 17 | ✅ Producción |
| Frontend | **Next.js 15 + React 19 + TypeScript** | ✅ Producción |
| Streaming | SSE (`SseEmitter`) consumido via `fetch` + `ReadableStream` | ✅ Producción |
| IA | OpenAI gpt-4.1 (matching + guías) | ✅ Producción |
| Fuente datos | API BDNS pública (~615.000 convocatorias) | ✅ Producción |

> **Nota:** El frontend está implementado en **Next.js** (no Angular). Todas las referencias a Angular en este documento son históricas.

## 1. Arquitectura

- Modelo cliente-servidor con arquitectura monolítica modular (Spring Boot).
- **Backend:** Spring Boot 3.5.x, gestión de usuarios, integración con BDNS, motor de matching con IA (OpenAI gpt-4.1).
- **Frontend:** Next.js 15 + React 19 + TypeScript (App Router) + Tailwind CSS. Consume la API REST y SSE directamente con `fetch`.
- **API REST:** Endpoints REST protegidos con JWT para integraciones futuras y consumo desde JavaScript.
- **Streaming:** Server-Sent Events (SSE) con `SseEmitter` para feedback en tiempo real durante el análisis con IA. Ejecución asíncrona con `CompletableFuture` + `TransactionTemplate`.
- **Motor IA:** OpenAI Chat Completions API (gpt-4.1) para generación de keywords de búsqueda y evaluación semántica de convocatorias. Fallback automático a motor rule-based si la API no está disponible.
- **Fuente de datos:** API pública BDNS (Base de Datos Nacional de Subvenciones, ~615.000 convocatorias) con búsqueda directa por keywords.
- **Base de datos:** PostgreSQL 17.2, almacenamiento de usuarios, perfiles, recomendaciones y metadatos de convocatorias.
- **Seguridad:** JWT como mecanismo de autenticación/autorización para endpoints REST/API. CSRF deshabilitado en la cadena stateless.

## 2. Tecnologías Seleccionadas

| Capa | Tecnología | Notas |
|------|------------|-------|
| Lenguaje | Java 17 | LTS, definido en `pom.xml` |
| Framework | Spring Boot 3.5.x | Parent POM |
| Seguridad | Spring Security 6.x + JWT (jjwt 0.12.x) | Autenticación stateless para API |
| Persistencia | Spring Data JPA + Hibernate | ORM sobre PostgreSQL |
| Frontend | **Next.js 15 + React 19 + TypeScript** | App Router, Tailwind CSS, cliente SSE via `fetch` |
| Base de datos | PostgreSQL 17.2 | Puerto `5432`, BD: `syntia_db` |
| Validación | Spring Boot Starter Validation (Bean Validation) | `@Valid`, `@NotBlank`, etc. |
| Utilidades | Lombok | Reducción de boilerplate |
| Dev Tools | Spring Boot DevTools | Hot-reload en desarrollo |
| Infraestructura | Servidor en la nube, comunicación HTTPS | Tomcat embebido, puerto `8080` |

### 2.1. Dependencias Maven Requeridas

| Dependencia | groupId | artifactId | Estado |
|-------------|---------|------------|--------|
| Spring Web | `org.springframework.boot` | `spring-boot-starter-web` | ✅ Incluida |
| Spring Security | `org.springframework.boot` | `spring-boot-starter-security` | ✅ Incluida |
| Spring Data JPA | `org.springframework.boot` | `spring-boot-starter-data-jpa` | ✅ Incluida |
| PostgreSQL Driver | `org.postgresql` | `postgresql` | ✅ Incluida |
| Lombok | `org.projectlombok` | `lombok` | ✅ Incluida |
| DevTools | `org.springframework.boot` | `spring-boot-devtools` | ✅ Incluida |
| Bean Validation | `org.springframework.boot` | `spring-boot-starter-validation` | ✅ Incluida |
| JWT API | `io.jsonwebtoken` | `jjwt-api` (0.12.6) | ✅ Incluida |
| JWT Impl | `io.jsonwebtoken` | `jjwt-impl` (0.12.6) | ✅ Incluida |
| JWT Jackson | `io.jsonwebtoken` | `jjwt-jackson` (0.12.6) | ✅ Incluida |
| Spring Dotenv | `me.paulschwarz` | `spring-dotenv` (4.0.0) | ✅ Incluida — carga automática de `.env` como variables de entorno |

## 3. Estándares
- **Seguridad:** autenticación JWT para API REST, cifrado de datos con BCrypt, HTTPS, política CORS.
- **Desarrollo:** convenciones de Java, validación de entradas con Bean Validation, control de acceso por roles (`ADMIN`, `USUARIO`).
- **Interfaz:** diseño intuitivo, profesional y responsivo con Bootstrap 5.

## 4. Interfaz de Usuario
- Registro, autenticación y captura de perfil.
- Dashboard interactivo y roadmap estratégico.
- Panel administrativo para supervisión.
- Interfaz cliente implementada como SPA Angular con consumo exclusivo de API REST.

## 5. Seguridad

### 5.1. Roles del Sistema

| Rol | Acceso | Descripción |
|-----|--------|-------------|
| `ADMIN` | `/admin/**` | Gestión de usuarios, supervisión del sistema, configuración del motor de IA |
| `USUARIO` | `/usuario/**` | Captura de perfil, creación de proyectos, visualización de recomendaciones y roadmap |

> **Nota:** El código debe usar `ROLE_ADMIN` y `ROLE_USUARIO` internamente (convención de Spring Security con prefijo `ROLE_`). No se usa `CLIENTE` ni `USER`.

### 5.2. Autenticación JWT (API REST)

El flujo JWT se utiliza para proteger los endpoints REST de la API:

1. **Login:** El usuario envía `POST /api/auth/login` con credenciales (`email`, `password`).
2. **Generación del token:** El backend valida las credenciales, y si son correctas, genera un JWT firmado con HMAC-SHA256 que contiene:
   - `sub` (subject): email del usuario.
   - `rol`: rol del usuario (`ADMIN` o `USUARIO`).
   - `iat` (issued at): timestamp de emisión.
   - `exp` (expiration): timestamp de expiración (configurable, por defecto 24h).
3. **Uso del token:** El cliente envía el token en cada petición REST en la cabecera `Authorization: Bearer <token>`.
4. **Validación:** Un filtro `JwtAuthenticationFilter` intercepta cada petición, extrae el token, lo valida (firma + expiración) y establece el contexto de seguridad.
5. **Configuración:** El secret y la expiración se definen en `application.properties`.

**Clases involucradas:**
- `JwtService`: generación, validación y extracción de claims del token.
- `JwtAuthenticationFilter`: filtro de Spring Security que intercepta y valida los tokens.

### 5.3. Flujo de cliente API (JWT)

Para el cliente Angular:
- Login contra endpoint REST de autenticación.
- Almacenamiento y envío del token JWT en cada petición API.
- Renovación/expiración de sesión controlada por el ciclo de vida del token.

### 5.4. Configuración CORS

La política CORS permite el acceso cross-origin para la API REST:

```
Orígenes permitidos: http://localhost:8080 (desarrollo), dominio de producción
Métodos permitidos: GET, POST, PUT, DELETE, OPTIONS
Headers permitidos: Authorization, Content-Type
Credenciales: true (permite envío de cookies/tokens)
```

> **⚠️ Importante:** Cuando `allowCredentials = true`, NO se puede usar `"*"` como origen. Se deben especificar los orígenes concretos o usar `addAllowedOriginPattern("*")` (solo para desarrollo).

### 5.5. Cifrado y Protección
- Contraseñas cifradas con **BCrypt** (`BCryptPasswordEncoder`).
- Comunicación segura mediante **HTTPS** en producción.
- CSRF deshabilitado en la cadena API stateless (`csrf.disable()` en `SecurityConfig.java`).
- Prevención de vulnerabilidades comunes: sanitización/escape de datos en cliente y API, inyección SQL evitada con JPA parametrizado.

## 6. Estructura de Paquetes

> **Actualizado a 2026-03-10 (v3.0.0)** — Refleja el estado real del código implementado.

```
com.syntia.mvp
├── SyntiaMvpApplication.java          # Clase principal
├── config/
│   ├── SecurityConfig.java            # Dual filter chain: JWT (/api/**) + formulario (web)
│   ├── CorsConfig.java                # Configuración CORS (allowedOriginPatterns)
│   ├── ConvocatoriaInitializer.java   # Inicializador de datos de convocatorias
│   ├── GlobalExceptionHandler.java    # @RestControllerAdvice para /api/**
│   ├── RestExceptionHandler.java      # Manejo de excepciones REST
│   └── WebExceptionHandler.java       # Manejo de excepciones para rutas web de compatibilidad
├── security/
│   ├── JwtService.java                # Generación, validación y extracción de claims JWT
│   └── JwtAuthenticationFilter.java   # Filtro OncePerRequestFilter para JWT
├── controller/
│   ├── AuthController.java            # Login, registro, dashboard, redirección por rol
│   ├── PerfilController.java          # GET/POST /usuario/perfil + vista solo lectura
│   ├── ProyectoController.java        # CRUD /usuario/proyectos
│   ├── RecomendacionController.java   # GET/POST recomendaciones + GET SSE streaming
│   ├── AdminController.java           # CRUD /admin/usuarios y /admin/convocatorias
│   └── CustomErrorController.java     # Páginas de error personalizadas (403, 404, 409, 500)
├── controller/api/
│   ├── AuthRestController.java        # POST /api/auth/login → JWT
│   ├── PerfilRestController.java      # GET/PUT /api/usuario/perfil
│   ├── ProyectoRestController.java    # CRUD /api/usuario/proyectos
│   └── RecomendacionRestController.java # GET + POST /generar
├── model/
│   ├── Usuario.java                   # @Entity: email, passwordHash, rol, creadoEn
│   ├── Rol.java                       # enum: ADMIN, USUARIO
│   ├── Perfil.java                    # @Entity: @OneToOne con Usuario
│   ├── Proyecto.java                  # @Entity: @ManyToOne con Usuario
│   ├── Convocatoria.java              # @Entity: titulo, tipo, sector, idBdns, numeroConvocatoria, fechaCierre
│   ├── Recomendacion.java             # @Entity: puntuacion, explicacion, guia (TEXT), usadaIa (boolean)
│   └── ErrorResponse.java            # DTO para respuestas de error REST
├── model/dto/
│   ├── RegistroDTO.java               # Registro con confirmación de contraseña
│   ├── PerfilDTO.java                 # @NotBlank, @Size
│   ├── ProyectoDTO.java              # @NotBlank, @Size
│   ├── RecomendacionDTO.java         # Desnormaliza convocatoria para la vista
│   ├── ConvocatoriaDTO.java          # @NotBlank, @Size + idBdns, numeroConvocatoria
│   ├── LoginRequestDTO.java          # @Email, @NotBlank
│   └── LoginResponseDTO.java         # token + email + rol + expiresIn
├── repository/
│   ├── UsuarioRepository.java         # findByEmail, existsByEmail
│   ├── PerfilRepository.java          # findByUsuarioId
│   ├── ProyectoRepository.java        # findByUsuarioId, countAll()
│   ├── ConvocatoriaRepository.java    # filtrar() JPQL, findByTituloIgnoreCaseAndFuente, sectores/tipos distintos
│   └── RecomendacionRepository.java   # findByProyectoId, deleteByProyectoId, countAll(), filtrar() JPQL
└── service/
    ├── CustomUserDetailsService.java  # Carga por email para Spring Security
    ├── UsuarioService.java            # registrar, buscar, obtenerTodos, eliminar, cambiarRol
    ├── PerfilService.java             # tienePerfil, obtenerPerfil, crear, actualizar, toDTO
    ├── ProyectoService.java           # CRUD + verificarPropiedad + toDTO
    ├── MotorMatchingService.java      # Orquestador: keywords → BDNS → OpenAI → persistencia + SSE streaming
    ├── OpenAiClient.java              # Cliente HTTP RestClient para OpenAI Chat Completions API
    ├── OpenAiMatchingService.java     # System prompts, construcción de prompts, parseo JSON
    ├── RecomendacionService.java      # obtenerPorProyecto, contarPorProyecto, filtrar, toDTO
    ├── ConvocatoriaService.java       # CRUD completo + toDTO + importarDesdeBdns()
    ├── BdnsClientService.java         # Cliente API pública BDNS (búsqueda, detalle, SSL permisivo)
    └── DashboardService.java          # topRecomendaciones, roadmap, contarTotal, RoadmapItem record
```

## 7. Configuración de `application.properties`

> **Actualizado a v3.0.0** — Refleja los valores reales del archivo actual.

```properties
spring.application.name=SyntiaMVP

# Servidor
server.port=8080

# Base de datos PostgreSQL
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/syntia_db}
spring.datasource.username=${DB_USER:syntia}
spring.datasource.password=${DB_PASSWORD:syntia}
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.format_sql=true

# JWT
jwt.secret=${JWT_SECRET:S3cr3tK3yP4r4Synti4Mv9Pl4t4f0rm4D3R3c0m3nd4c10n3sJWT2026}
jwt.expiration=${JWT_EXPIRATION:86400000}

# Logging
logging.level.org.springframework.security=DEBUG
logging.level.com.syntia.mvp=DEBUG

# OpenAI - Motor de matching
openai.api-key=${OPENAI_API_KEY:}
openai.model=gpt-4.1
openai.max-tokens=500
openai.temperature=0.1
```

> **Nota:** Si `openai.api-key` está vacío, el motor de matching usa automáticamente el algoritmo rule-based como fallback.

## 8. Base de Datos

```sql
-- Crear usuario y base de datos
CREATE USER syntia WITH PASSWORD 'syntia';
CREATE DATABASE syntia_db OWNER syntia;
GRANT ALL PRIVILEGES ON DATABASE syntia_db TO syntia;
```
