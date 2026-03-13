# Plan de Proyecto: Syntia

## 1. Título del Proyecto
Syntia – Plataforma de Recomendación de Subvenciones

## 2. Objetivo del Proyecto
Planificar y organizar el desarrollo del MVP de Syntia asegurando una ejecución eficiente y cumplimiento de plazos y recursos definidos.

## 3. Cronograma del Proyecto

### Fase 1 – Desarrollo (2 meses)
- Diseño de arquitectura del sistema y base de datos.
- Desarrollo del backend: gestión de usuarios, integración con BDNS, motor de priorización.
- Desarrollo del frontend: captura de perfil, dashboard y panel administrativo.

### Fase 2 – Pruebas (1 mes)
- Pruebas de funcionalidad y flujo de usuario.
- Ajustes de interfaz y corrección de errores.

### Fase 3 – Despliegue (1 mes)
- Configuración de entorno de pruebas y producción.
- Lanzamiento del MVP.
- Recolección de feedback inicial de usuarios.

## 4. Hitos del Proyecto

| Hito | Fecha |
|------|-------|
| Diseño de arquitectura y prototipo funcional del backend y frontend | Semana 4 |
| Desarrollo completo del MVP | Semana 8 |
| Pruebas y correcciones finales | Semana 12 |
| Lanzamiento del MVP y recolección de feedback | Semana 16 |

## 5. Recursos del Proyecto

### 5.1. Humanos
- **Desarrollador Backend:** lógica de negocio, motor de priorización e integración BDNS.
- **Desarrollador Frontend:** interfaz de usuario, dashboard y panel administrativo.
- **Coordinador de Proyecto:** gestión de cronograma, recursos y comunicación.

### 5.2. Tecnológicos

| Categoría | Tecnología |
|-----------|------------|
| Lenguaje | Java 17 (LTS) |
| Framework principal | Spring Boot 3.5.x |
| Seguridad | Spring Security 6.x + JWT (jjwt 0.12.x) |
| Persistencia | Spring Data JPA + Hibernate |
| Frontend objetivo | Angular (SPA) + TypeScript + consumo API REST |
| Frontend legado temporal | Thymeleaf + Bootstrap 5 + JavaScript vanilla |
| Motor IA | OpenAI Chat Completions API (gpt-4.1) + fallback rule-based |
| Streaming | Server-Sent Events (SSE) con SseEmitter + CompletableFuture |
| Fuente de datos | API pública BDNS (Base de Datos Nacional de Subvenciones) |
| Base de datos | PostgreSQL 17.2 |
| Validación | Spring Boot Starter Validation (Bean Validation) |
| Utilidades | Lombok |
| Control de versiones | Git (repositorio: https://github.com/daniicg05/Syntia.git) |
| Infraestructura | Servidor en la nube para despliegue (Tomcat embebido, puerto 8080) |

### 5.3. Económicos
- Presupuesto estimado para servidores, licencias y recursos iniciales.

## 6. Roles y Responsabilidades

| Rol | Responsabilidades |
|-----|-------------------|
| Backend | Gestión de datos y motor de priorización. |
| Frontend | Diseño e interacción con usuarios. |
| Coordinador | Supervisión de progreso y planificación de tareas. |

## 7. Indicadores de Éxito
- MVP funcional dentro del plazo.
- Usuarios registrados y primeros feedback positivos.

## 8. Suposiciones y Dependencias
- Acceso estable a internet por parte de los usuarios.
- Disponibilidad de entorno de pruebas y servicios en la nube.
- PostgreSQL 17.2 disponible en puerto `5432` con BD `syntia_db` (usuario: `syntia`, contraseña: `syntia`).
- Repositorio Git centralizado en: https://github.com/daniicg05/Syntia.git
- Java 17+ instalado en el entorno de desarrollo.

## Alineación Arquitectónica Vigente (2026-03-13)

> Esta sección prevalece sobre referencias históricas del documento cuando exista conflicto.

- Backend fijo: `Java 17 + Spring Boot + Maven + PostgreSQL + JWT + SSE`.
- Frontend objetivo: `Angular + API REST`.
- `Thymeleaf` se mantiene solo como legado temporal de transición.
- Prioridad técnica: `controller/api/`, seguridad JWT y pipeline `BDNS+IA`.
- La lógica de negocio se conserva en servicios; la migración afecta a presentación.
