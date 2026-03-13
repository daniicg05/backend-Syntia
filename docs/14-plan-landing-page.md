# 14 – Plan de Implementación: Landing Page Pública (v4.2.0)

## Alineación Arquitectónica Vigente (2026-03-13)

> Este plan queda clasificado como mejora de presentación **legado temporal**.

- La landing SSR actual se mantiene por compatibilidad durante transición.
- El objetivo de producto es `Angular + API REST`.
- La evolución futura de landing/autenticación visual debe migrar a frontend Angular.
- Backend, seguridad JWT y flujo `BDNS+IA` no cambian por este plan de UI.

---

## Contexto

Hasta la versión v4.1.0 el entry point de la aplicación era directamente `/login`.
El usuario que accedía a `http://localhost:8080/` era redirigido al formulario de login
sin ninguna presentación previa de la plataforma.

Esta fase introduce una **landing page pública** (`/`) que actúa como pantalla de bienvenida
antes del login, manteniendo el flujo de autenticación existente sin modificarlo.

---

## Diagnóstico

| Problema | Descripción |
|----------|-------------|
| Sin presentación | El usuario accede directamente al login sin conocer qué es Syntia |
| Entry point abrupto | No hay contexto de producto antes de pedir credenciales |
| Ruta `/` sin mapear | `GET /` no tenía controller asignado — Spring Security redirigía a `/login` |

---

## Solución implementada

Insertar una capa visual pública por delante del flujo existente:

```
Antes:  http://localhost:8080/  →  /login  →  /dashboard
Después: http://localhost:8080/  →  /  (landing)  →  /login  →  /dashboard
```

El flujo de autenticación existente **no se modificó**.

---

## Fase de Implementación

### FASE ÚNICA — Landing Page Pública (v4.2.0)
> **Prioridad:** MEDIA — Mejora de experiencia de usuario  
> **Esfuerzo:** 1-2 horas  
> **Dependencias:** Ninguna  
> **Estado:** ✅ COMPLETADA

| Paso | Descripción | Archivo | Estado |
|------|-------------|---------|--------|
| 1 | Modificar `home()` en `AuthController.java` — devuelve `"main"` en vez de `"redirect:/login"` | `controller/AuthController.java` | ✅ |
| 2 | Añadir `"/"` a rutas públicas en `SecurityConfig.java` | `config/SecurityConfig.java` | ✅ |
| 3 | Crear `main.html` — landing page con botón "Acceder a Syntia" → `/login` y enlace "Crear cuenta" → `/registro` | `templates/main.html` (NUEVO) | ✅ |
| 4 | Cambiar `logoutSuccessUrl` de `"/login?logout=true"` a `"/"` — el botón "Salir" redirige a la landing page | `config/SecurityConfig.java` | ✅ |
| 5 | Compilación BUILD SUCCESS | — | ✅ |

---

## Archivos modificados

### `AuthController.java` (MODIFICADO)
- Ruta: `com/syntia/mvp/controller/AuthController.java`
- Cambio: método `home()` mapeado a `GET /` — devuelve `"main"` en vez de `"redirect:/login"`
- Nota: `AuthController` ya tenía `GET /` mapeado; no se creó un controller nuevo para evitar conflicto de mappings

### `SecurityConfig.java` (MODIFICADO)
- Cambio 1: añadida `"/"` al bloque `requestMatchers(...).permitAll()`
- Cambio 2: `logoutSuccessUrl` de `"/login?logout=true"` a `"/"` — el botón "Salir" redirige a la landing page
- Línea afectada: cadena de filtros web (`webSecurityFilterChain`)
- Sin cambios en la cadena JWT ni en `defaultSuccessUrl`

### `main.html` (NUEVO)
- Ruta: `src/main/resources/templates/main.html`
- Contenido:
  - Nombre y descripción de Syntia
  - Lista de 4 características principales
  - Botón principal **"Acceder a Syntia"** → `/login`
  - Enlace secundario **"Crear cuenta gratuita"** → `/registro`
  - Enlace al aviso legal → `/aviso-legal`
- Diseño: Bootstrap 5, fondo degradado azul, tarjeta central blanca

---

## Flujo resultante

```
http://localhost:8080/
        │
        ▼
AuthController.home()
        │
        ▼
templates/main.html  ← pública, sin autenticación
        │
        ├── Click "Acceder a Syntia"
        │         │
        │         ▼
        │      /login  ← entry point anterior (sin cambios)
        │         │
        │    Credenciales OK
        │         │
        │         ▼
        │      /default → /usuario/dashboard  o  /admin/dashboard
        │                         │
        │                    Click "Salir"
        │                         │
        │                         ▼
        │                    POST /logout (Spring Security invalida sesión)
        │                         │
        │                         ▼
        │                    logoutSuccessUrl → "/"
        │                         │
        │                         ▼
        │                    main.html  ← vuelve a la landing page
        │
        └── Click "Crear cuenta gratuita"
                  │
                  ▼
               /registro
```

---

## Impacto en la seguridad

| Aspecto | Estado |
|---------|--------|
| Rutas protegidas (`/usuario/**`, `/admin/**`) | Sin cambios — siguen requiriendo autenticación |
| Cadena JWT (`/api/**`) | Sin cambios |
| `defaultSuccessUrl` tras login | Sin cambios — sigue siendo `/default` |
| Nueva ruta `/` | Pública (`permitAll()`) — no expone datos sensibles |

---

## Resumen de impacto

| Archivo | Acción |
|---------|--------|
| `controller/AuthController.java` | MODIFICAR — `home()` devuelve `"main"` en vez de `"redirect:/login"` |
| `config/SecurityConfig.java` | MODIFICAR — añadir `"/"` a `permitAll()` |
| `templates/main.html` | CREAR |

**Total:** 1 archivo nuevo, 2 líneas modificadas en controllers/config.

---

## Verificación

- **Compilación:** `mvn compile` → BUILD SUCCESS
- **Tests:** 18 passed, 0 failures, 0 errors
- **Ruta `/`:** accesible sin autenticación
- **Ruta `/login`:** sigue funcionando exactamente igual
- **Ruta `/usuario/**`:** sigue requiriendo autenticación

