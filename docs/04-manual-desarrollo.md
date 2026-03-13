# Manual de Desarrollo: Syntia

## Alineación Arquitectónica Vigente (2026-03-13)

> Esta sección prevalece sobre instrucciones históricas cuando haya conflicto.

### Guía operativa API-first
- Backend base: `Java 17 + Spring Boot + Maven + PostgreSQL + JWT + SSE`.
- Frontend objetivo: `Angular` consumiendo endpoints REST.
- Prioridad de desarrollo en backend: `controller/api/` + seguridad JWT.
- Lógica de negocio: mantener en `service/` (matching, filtros, roadmap, BDNS+IA).
- Capa de presentación objetivo: SPA Angular sin renderizado server-side.

### Implicación para nuevos cambios
- Nuevas funcionalidades deben exponerse primero por API REST.
- La UI Angular debe consumir contratos estables de `controller/api/`.
- Evitar introducir nuevas dependencias o acoplamientos a SSR salvo mantenimiento legado.

## 1. Repositorio y Control de Versiones

- **Repositorio remoto:** https://github.com/daniicg05/Syntia.git
- **Flujo de desarrollo basado en ramas:**
  - `main` → rama estable (producción).
  - `develop` → rama de integración.
  - `feature/*` → funcionalidad específica (ej: `feature/jwt-auth`, `feature/perfil-usuario`).
  - `bugfix/*` → corrección de errores.
- Pull requests con revisión por al menos un miembro del equipo.
- Protección de ramas `main` y `develop` contra push directo.

### Clonar el repositorio

```bash
git clone https://github.com/daniicg05/Syntia.git
cd Syntia
```

## 2. Estándares de Codificación

| Elemento | Convención | Ejemplo |
|----------|------------|---------|
| Clases | PascalCase | `UsuarioService`, `JwtAuthenticationFilter` |
| Métodos / Variables | camelCase | `buscarPorEmail()`, `tokenExpiration` |
| Constantes | MAYÚSCULAS_CON_GUIONES | `JWT_SECRET`, `ROL_ADMIN` |
| Paquetes | minúsculas | `com.syntia.mvp.config` |
| Entidades JPA | Singular, PascalCase | `Usuario`, `Perfil`, `Proyecto` |
| Tablas BD | snake_case, plural | `usuarios`, `perfiles`, `proyectos` |
| Endpoints REST | kebab-case, plural | `/api/usuarios`, `/api/convocatorias` |
| Comentarios | JavaDoc en clases y métodos públicos | `/** Descripción */` |

## 3. Uso de Git
- Commits frecuentes y descriptivos.
- Formato de commit recomendado: `tipo(alcance): descripción breve`
  - Tipos: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
  - Ejemplo: `feat(auth): implementar generación de JWT en login`
- Sincronización regular de ramas para evitar conflictos.
- No subir credenciales ni secrets al repositorio (usar `.gitignore` y variables de entorno).

## 4. Resolución de Conflictos
- Actualizar la rama local antes de fusionar: `git pull origin develop`
- Resolver conflictos localmente y documentar los cambios.
- Ejecutar las pruebas antes de hacer push tras resolver conflictos.

## 5. Configuración del Entorno Local

### 5.1. Prerrequisitos

| Herramienta | Versión mínima |
|-------------|----------------|
| Java JDK | 17+ |
| Maven | 3.8+ (o usar el wrapper `mvnw` incluido) |
| PostgreSQL | 17.2 |
| Git | 2.x |
| IDE recomendado | IntelliJ IDEA / VS Code con extensiones Java |

### 5.2. Configuración de la Base de Datos

```sql
-- Conectar a PostgreSQL como superusuario y ejecutar:
CREATE USER syntia WITH PASSWORD 'syntia';
CREATE DATABASE syntia_db OWNER syntia;
GRANT ALL PRIVILEGES ON DATABASE syntia_db TO syntia;
```

Verificar conexión:
```bash
psql -h localhost -p 5432 -U syntia -d syntia_db
```

### 5.3. Configuración de `application.properties`

El archivo `src/main/resources/application.properties` debe contener:

```properties
spring.application.name=SyntiaMVP
server.port=8080

# PostgreSQL
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

> **Nota:** Si `openai.api-key` está vacío, el motor de matching usa automáticamente el algoritmo rule-based como fallback. La variable se puede definir en un archivo `.env` en la raíz del proyecto (cargado automáticamente por `spring-dotenv`).

### 5.4. Ejecución del Proyecto

```bash
# Con Maven wrapper (recomendado)
./mvnw spring-boot:run

# O con Maven instalado
mvn spring-boot:run
```

La aplicación estará disponible en: `http://localhost:8080`

### 5.5. Dependencias del `pom.xml`

Todas las dependencias necesarias están incluidas. Ver tabla completa en `03-especificaciones-tecnicas.md § 2.1`.

## 6. Estructura de Paquetes del Proyecto

Estado actual implementado (v3.0.0 — fases 1–7 completas + SSE streaming):

```
com.syntia.mvp
├── SyntiaMvpApplication.java
├── config/
│   ├── SecurityConfig.java           ✅ Dual filter chain: JWT (/api/**) + formulario (web)
│   ├── CorsConfig.java               ✅ allowedOriginPatterns para dev; ajustar en prod
│   ├── ConvocatoriaInitializer.java   ✅ Inicializador de datos de convocatorias
│   ├── GlobalExceptionHandler.java   ✅ @RestControllerAdvice para /api/**
│   ├── RestExceptionHandler.java     ✅ Limpiado (sin código muerto)
│   └── WebExceptionHandler.java      ✅ Manejo de errores para vistas MVC
├── security/
│   ├── JwtService.java               ✅ Generación, validación y extracción de claims
│   └── JwtAuthenticationFilter.java  ✅ Filtro OncePerRequestFilter
├── controller/
│   ├── AuthController.java           ✅ Login, registro, dashboard usuario/admin
│   ├── PerfilController.java         ✅ GET/POST /usuario/perfil + vista solo lectura
│   ├── ProyectoController.java       ✅ CRUD /usuario/proyectos
│   ├── RecomendacionController.java  ✅ GET/POST recomendaciones + GET SSE streaming (/generar-stream)
│   ├── AdminController.java          ✅ CRUD /admin/usuarios y /admin/convocatorias
│   └── CustomErrorController.java    ✅ Páginas de error personalizadas
├── controller/api/
│   ├── AuthRestController.java       ✅ POST /api/auth/login → JWT
│   ├── PerfilRestController.java     ✅ GET/PUT /api/usuario/perfil
│   ├── ProyectoRestController.java   ✅ CRUD /api/usuario/proyectos
│   └── RecomendacionRestController.java ✅ GET + POST /generar
├── model/
│   ├── Usuario.java                  ✅ @Entity, Lombok, BCrypt password
│   ├── Rol.java                      ✅ enum: ADMIN, USUARIO
│   ├── Perfil.java                   ✅ @OneToOne con Usuario
│   ├── Proyecto.java                 ✅ @ManyToOne con Usuario
│   ├── Convocatoria.java             ✅ Catálogo global: titulo, tipo, sector, idBdns, numeroConvocatoria, fechaCierre
│   ├── Recomendacion.java            ✅ Proyecto + Convocatoria + puntuacion + guia (TEXT) + usadaIa (boolean)
│   └── ErrorResponse.java            ✅ DTO para errores REST
├── model/dto/
│   ├── RegistroDTO.java              ✅ Registro con confirmación de contraseña
│   ├── PerfilDTO.java                ✅ @NotBlank, @Size
│   ├── ProyectoDTO.java              ✅ @NotBlank, @Size
│   ├── RecomendacionDTO.java         ✅ Aplana relación LAZY para vistas + campo guia
│   ├── ConvocatoriaDTO.java          ✅ @NotBlank, @Size + idBdns, numeroConvocatoria
│   ├── LoginRequestDTO.java          ✅ @Email, @NotBlank
│   └── LoginResponseDTO.java         ✅ token + email + rol + expiresIn
├── repository/
│   ├── UsuarioRepository.java        ✅ findByEmail, existsByEmail
│   ├── PerfilRepository.java         ✅ findByUsuarioId
│   ├── ProyectoRepository.java       ✅ findByUsuarioId + countAll()
│   ├── ConvocatoriaRepository.java   ✅ filtrar() JPQL, findByTituloIgnoreCaseAndFuente, sectores/tipos distintos
│   └── RecomendacionRepository.java  ✅ findByProyectoId, deleteByProyectoId, countByProyectoId, countAll(), filtrar() JPQL
└── service/
    ├── CustomUserDetailsService.java  ✅ Carga por email para Spring Security
    ├── UsuarioService.java            ✅ registrar, buscar, obtenerTodos, eliminar, cambiarRol
    ├── PerfilService.java             ✅ tienePerfil, obtenerPerfil, crear, actualizar, toDTO
    ├── ProyectoService.java           ✅ CRUD + verificarPropiedad + toDTO
    ├── MotorMatchingService.java      ✅ Orquestador + SSE streaming (generarRecomendacionesStream)
    ├── OpenAiClient.java              ✅ Cliente HTTP RestClient para OpenAI Chat Completions API
    ├── OpenAiMatchingService.java     ✅ System prompts, construcción de prompts, parseo JSON
    ├── RecomendacionService.java      ✅ obtenerPorProyecto, contarPorProyecto, filtrar, toDTO
    ├── ConvocatoriaService.java       ✅ CRUD completo + toDTO + importarDesdeBdns()
    ├── BdnsClientService.java         ✅ Cliente API pública BDNS (búsqueda, detalle, SSL permisivo)
    └── DashboardService.java          ✅ topRecomendaciones, roadmap, contarTotal, RoadmapItem record
```

### Recursos estáticos y plantillas

```
src/main/resources/
├── application.properties            ✅ PostgreSQL, JPA, JWT, OpenAI (max-tokens=500)
├── application-prod.properties       ✅ Perfil producción — todas las props por variable de entorno
├── static/
│   ├── bootstrap/                    ✅ Bootstrap 5 CSS
│   ├── bootsprap/                    ✅ Bootstrap 5 JS (nombre con typo, no renombrar)
│   └── javascript/
│       ├── registro.js               ✅ Validación contraseñas + email frontend
│       ├── perfil.js                 ✅ Validaciones formulario perfil
│       ├── proyecto.js               ✅ Validaciones + contador caracteres
│       ├── dashboard.js              ✅ Contador días restantes en roadmap
│       └── recomendaciones-stream.js ✅ Cliente SSE con EventSource para streaming de recomendaciones
└── templates/
    ├── login.html
    ├── registro.html
    ├── aviso-legal.html              ✅ Página pública de aviso legal (ruta GET /aviso-legal)
    ├── error.html
    ├── error/403.html, 404.html, 409.html, 500.html
    ├── fragments/
    │   ├── navbar-usuario.html       ✅ Fragment navbar azul (bg-primary) para vistas usuario
    │   ├── navbar-admin.html         ✅ Fragment navbar oscuro (bg-dark) para vistas admin
    │   └── footer.html               ✅ Fragment pie de página con copyright y enlace aviso legal
    ├── usuario/
    │   ├── dashboard.html            ✅ Métricas, top recomendaciones, roadmap
    │   ├── perfil.html               ✅ Formulario crear/editar perfil
    │   ├── perfil-ver.html           ✅ Vista solo lectura del perfil (ruta GET /usuario/perfil/ver)
    │   └── proyectos/
    │       ├── lista.html
    │       ├── formulario.html       ✅ Crear y editar (vista unificada)
    │       ├── detalle.html
    │       └── recomendaciones.html  ✅ SSE streaming + filtros BD, puntuación, badges IA/reglas, guía modal, aviso legal
    └── admin/
        ├── dashboard.html            ✅ Métricas del sistema (countAll directo, sin N+1)
        ├── usuarios/
        │   ├── lista.html            ✅ Cambio de rol inline + modal eliminar
        │   └── detalle.html          ✅ Datos + proyectos + nº recomendaciones por proyecto
        └── convocatorias/
            ├── lista.html            ✅ Tabla + sección "Importar desde BDNS"
            └── formulario.html       ✅ Crear y editar convocatoria
```

## 7. Referencia de la API REST

Todos los endpoints REST están bajo `/api/**` y usan autenticación JWT.  
Cabecera requerida: `Authorization: Bearer <token>`

### Autenticación

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| `POST` | `/api/auth/login` | ❌ | Credenciales → JWT |

**Body request:** `{ "email": "...", "password": "..." }`  
**Body response:** `{ "token": "...", "email": "...", "rol": "...", "expiresIn": 86400000 }`

### Perfil

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| `GET` | `/api/usuario/perfil` | JWT | Ver perfil del usuario autenticado |
| `PUT` | `/api/usuario/perfil` | JWT | Crear o actualizar perfil |

### Proyectos

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| `GET` | `/api/usuario/proyectos` | JWT | Listar proyectos |
| `GET` | `/api/usuario/proyectos/{id}` | JWT | Ver proyecto por ID |
| `POST` | `/api/usuario/proyectos` | JWT | Crear proyecto |
| `PUT` | `/api/usuario/proyectos/{id}` | JWT | Editar proyecto |
| `DELETE` | `/api/usuario/proyectos/{id}` | JWT | Eliminar proyecto |

### Recomendaciones

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| `GET` | `/api/usuario/proyectos/{id}/recomendaciones` | JWT | Ver recomendaciones del proyecto |
| `POST` | `/api/usuario/proyectos/{id}/recomendaciones/generar` | JWT | Disparar motor de matching (síncrono) |

### Recomendaciones — Streaming SSE (cliente Angular)

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| `GET` | `/usuario/proyectos/{id}/recomendaciones/generar-stream` | Sesión | SSE streaming — feedback en tiempo real |

**Content-Type:** `text/event-stream`  
**Timeout:** 180 segundos (3 minutos)

**Eventos SSE emitidos:**

| Evento | Datos | Descripción |
|--------|-------|-------------|
| `estado` | `"texto de estado"` | Mensajes de progreso (buscando, evaluando...) |
| `keywords` | `{total, keywords[]}` | Keywords generadas por IA |
| `busqueda` | `{candidatas}` | Número de candidatas encontradas en BDNS |
| `progreso` | `{actual, total, titulo}` | Progreso de evaluación IA (barra) |
| `resultado` | `{titulo, puntuacion, explicacion, tipo, sector, ...}` | Recomendación encontrada (tarjeta en tiempo real) |
| `completado` | `{totalRecomendaciones, totalEvaluadas, descartadas, errores}` | Resumen final |
| `error` | `"mensaje de error"` | Si ocurre un error |

### Códigos de respuesta estándar

| Código | Situación |
|--------|-----------|
| `200` | Éxito |
| `201` | Recurso creado |
| `400` | Validación fallida (`MethodArgumentNotValidException`) |
| `401` | Credenciales incorrectas |
| `403` | Sin permisos (`AccessDeniedException`) |
| `404` | Recurso no encontrado (`EntityNotFoundException`) |
| `409` | Conflicto de estado (`IllegalStateException`) |
| `500` | Error interno del servidor |

---

## 8. Despliegue en Producción

### 8.1 Variables de entorno requeridas

Antes de arrancar en producción, configura las siguientes variables de entorno:

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `DB_URL` | URL JDBC de la BD PostgreSQL | `jdbc:postgresql://host:5432/syntia_db` |
| `DB_USER` | Usuario de BD | `syntia` |
| `DB_PASSWORD` | Contraseña de BD | `s3cr3t` |
| `JWT_SECRET` | Secreto para firmar JWT (≥ 32 chars) | `M1S3cr3tK3yP4r4JWT...` |
| `JWT_EXPIRATION` | Expiración del token en ms | `86400000` (24h) |
| `OPENAI_API_KEY` | API key de OpenAI (opcional) | `sk-proj-...` |
| `PORT` | Puerto del servidor | `8080` |
| `SPRING_PROFILES_ACTIVE` | Perfil Spring activo | `prod` |

> **Nota:** Si `OPENAI_API_KEY` está vacía, el motor de matching usa automáticamente el algoritmo rule-based como fallback.

### 8.2 Construir el artefacto JAR

```bash
./mvnw clean package -DskipTests
```

El JAR se genera en `target/syntia-mvp-*.jar`.

### 8.3 Arrancar con perfil de producción

**Opción A — argumento de línea de comandos:**
```bash
java -jar target/syntia-mvp-*.jar --spring.profiles.active=prod
```

**Opción B — variable de entorno (recomendada en Docker/Railway/Render):**
```bash
export SPRING_PROFILES_ACTIVE=prod
java -jar target/syntia-mvp-*.jar
```

### 8.4 Despliegue en Railway / Render

1. Conecta el repositorio GitHub en el panel de Railway o Render.
2. Configura las variables de entorno en **Settings → Environment**.
3. El comando de arranque es automático (`java -jar`). Asegúrate de que el `Procfile` o la configuración de build use:
   ```
   web: java -jar target/syntia-mvp-*.jar --spring.profiles.active=prod
   ```
4. Verifica que la aplicación responde en `GET /login` (no hay `/actuator/health` en el MVP).

### 8.5 Base de datos en producción

- **Schema:** Spring Boot aplica `ddl-auto=validate` en prod, por lo que el schema debe crearse manualmente o con una herramienta de migración (recomendado: Flyway).
- **Inicialización inicial:** La aplicación obtiene convocatorias directamente de la API BDNS en tiempo real. No se requieren datos de prueba precargados.
- **Primer acceso:** Registra un usuario administrador manualmente en la BD o mediante el formulario `/registro` y luego cambia su rol a `ADMIN` via SQL.

### 8.6 HTTPS y proxy inverso (nginx)

Ejemplo mínimo de configuración nginx como proxy inverso con certificado Let's Encrypt:

```nginx
server {
    listen 443 ssl;
    server_name tudominio.com;

    ssl_certificate     /etc/letsencrypt/live/tudominio.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/tudominio.com/privkey.pem;

    location / {
        proxy_pass         http://localhost:8080;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto https;
    }

    # SSE streaming — CRÍTICO para que el análisis IA funcione correctamente
    location /usuario/proyectos/ {
        proxy_pass         http://localhost:8080;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto https;
        proxy_set_header   X-Accel-Buffering no;   # Deshabilitar buffering para SSE
        proxy_buffering    off;                      # Sin buffer de proxy
        proxy_read_timeout 180s;                     # 3 minutos para análisis largo
    }
}
server {
    listen 80;
    server_name tudominio.com;
    return 301 https://$host$request_uri;
}
```

Obtener certificado: `certbot --nginx -d tudominio.com`

### 8.7 Checklist pre-despliegue

- [ ] Variables de entorno configuradas en el servidor/panel
- [ ] BD PostgreSQL creada y accesible desde el servidor
- [ ] Schema de BD inicializado (tablas creadas)
- [ ] `spring.profiles.active=prod` activo
- [ ] `spring.jpa.show-sql=false` (incluido en application-prod.properties)
- [ ] HTTPS activo con certificado válido
- [ ] CORS configurado con el dominio de producción real en `CorsConfig.java`
- [ ] `OPENAI_API_KEY` configurada si se quiere usar IA (si no, fallback rule-based)
- [ ] nginx configurado con `X-Accel-Buffering: no` para rutas SSE
- [ ] `proxy_read_timeout 180s` en nginx para análisis largos
- [ ] Prueba de login con usuario admin tras el despliegue
- [ ] Prueba de análisis IA con un proyecto de ejemplo

