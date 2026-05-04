# Syntia — Arquitectura Completa del Rediseño UI/UX v5

> **Fecha:** 2026-04-13
> **Stack:** Next.js 16.2 + React 19 + TypeScript + Tailwind v4 + Framer Motion v11 + Spring Boot REST + JWT
> **Alcance:** Todas las páginas existentes rediseñadas. Sin nuevas páginas. Auth intacto.

---

## Índice

1. [Sistema de Diseño (Design System)](#1-sistema-de-diseño)
2. [Arquitectura Framer Motion](#2-arquitectura-framer-motion)
3. [Arquitectura de Componentes Frontend](#3-arquitectura-de-componentes-frontend)
4. [Arquitectura Backend — Plan por Fases](#4-arquitectura-backend)
5. [Preocupaciones Transversales](#5-preocupaciones-transversales)

---

## 1. Sistema de Diseño

### 1.1 Color — Extracción y Decisión de Paleta

**Logo:** El logo actual es un PNG en `/public/images/syntia-grants-logo.png`. La búsqueda de un SVG del logo en `/src` no encontró ningún SVG de marca. Los CSS variables en `globals.css` establecen `--primary: #16a34a` (verde Tailwind 600), lo que indica que el color primario histórico del logo es **verde esmeralda**.

**Stitch Design:** El manifiesto de diseño (`docs/diseño Stitch/syntia intelligence/DESIGN.md`) y los HTML de prototipos del dashboard/listado establecen de forma consistente un sistema Material Design 3 basado en **azul petróleo profundo**:

| Token Stitch | Hex | Uso |
|---|---|---|
| `primary` | `#005a71` | CTAs, enlaces, énfasis |
| `primary-container` | `#0e7490` | Hover de botones primarios, gradientes |
| `surface-container-lowest` | `#ffffff` | Cards de máxima atención |
| `surface-container-low` | `#f2f4f6` | Contenido sobre fondo |
| `surface-container` | `#eceef0` | Agrupaciones |
| `background` / `surface` | `#f7f9fb` | Fondo general |
| `on-surface` | `#191c1e` | Texto principal |
| `on-surface-variant` | `#3f484c` | Texto secundario |
| `tertiary` | `#005f40` | Estados de éxito/ganancia |
| `tertiary-container` | `#007a53` | Fondo chips de éxito |
| `error` | `#ba1a1a` | Errores, urgencia |
| `error-container` | `#ffdad6` | Fondo chips de error |
| `outline-variant` | `#bec8cd` | Borders sutiles (uso limitado) |

**Decisión de paleta:** El rediseño adopta la paleta de Stitch (azul petróleo) como sistema de color definitivo, reemplazando completamente el verde actual. El verde queda reservado exclusivamente para el token `tertiary` (estados de éxito real).

### 1.2 Paleta Completa — Tokens HSL

La paleta se expresa en HSL para facilitar la manipulación por opacidad en Tailwind v4:

```
PRIMARY HUE:     193° (azul petróleo)
TERTIARY HUE:    153° (verde esmeralda, solo éxito)
ERROR HUE:       353° (rojo)
NEUTRAL BASE:    200° (slate frío)
```

### 1.3 Dónde está el verde actual y cómo eliminarlo

**Archivos a modificar para eliminar el verde:**

| Archivo | Líneas con verde | Reemplazo |
|---|---|---|
| `src/app/globals.css` | L5-8: `--primary: #16a34a`, `--primary-hover: #15803d`, `--primary-light: #dcfce7`, `--primary-muted: #86efac` | Cambiar a paleta azul petróleo |
| `src/app/globals.css` | L23-24: `--accent-green: #16a34a`, `--accent-emerald: #059669` | Eliminar o renombrar a `--accent-tertiary` |
| `src/app/globals.css` | L60-63: modo oscuro `--primary: #22c55e`, etc. | Cambiar a azul petróleo dark |
| `src/app/globals.css` | L78-79: `--accent-green: #22c55e`, `--accent-emerald: #10b981` | Renombrar |
| `src/app/layout.tsx` | L31: `themeColor: "#f5f5f0"` | Cambiar a `"#f7f9fb"` |
| `src/components/ui/Card.tsx` | Usa `bg-primary-light` (verde) como icono bg | Cambiar a `bg-primary/10` |
| `src/app/dashboard/page.tsx` | L91: `color="green"` en StatCard | Cambiar a `color="primary"` |
| `src/app/proyectos/page.tsx` | L111: `bg-primary-light text-primary` (todavía verde) | Se resuelve al cambiar el token |
| `src/app/admin/dashboard/page.tsx` | L31: `bg: "bg-primary-light"` | Resuelto por token |
| `src/app/proyectos/[id]/recomendaciones/page.tsx` | L220: `text-green-400` en consola SSE | Cambiar a `text-primary` |

**Búsqueda de clases verdes hardcodeadas:**

```bash
# Ejecutar para encontrar cualquier verde restante:
grep -rn "text-green\|bg-green\|border-green\|text-emerald\|bg-emerald" src/
grep -rn "#16a34a\|#22c55e\|#059669\|#10b981" src/
```

Después de cambiar los CSS tokens en `globals.css`, la mayoría desaparecerá porque usan `var(--primary)`. Las únicas hardcodeadas actualmente son `text-green-400` (L220 recomendaciones) y las clases de colores en StatCard.

### 1.4 Archivo `src/app/globals.css` — Reemplazo Completo de Tokens

```css
:root {
  /* ── Brand: Azul Petróleo ───────────────────────────────────── */
  --primary:            #005a71;   /* primary Stitch */
  --primary-hover:      #0e7490;   /* primary-container Stitch */
  --primary-light:      #b9eaff;   /* primary-fixed Stitch */
  --primary-muted:      #81d1f0;   /* primary-fixed-dim Stitch */
  --primary-container:  #0e7490;

  /* ── Superficies (Sistema de 5 niveles sin bordes) ─────────── */
  --background:               #f7f9fb;   /* surface base */
  --surface:                  #f7f9fb;
  --surface-lowest:           #ffffff;   /* cards de máxima atención */
  --surface-low:              #f2f4f6;
  --surface-container:        #eceef0;
  --surface-high:             #e6e8ea;
  --surface-highest:          #e0e3e5;
  --surface-muted:            #f2f4f6;   /* alias para compatibilidad */

  /* ── Texto ─────────────────────────────────────────────────── */
  --foreground:         #191c1e;   /* on-surface */
  --foreground-muted:   #3f484c;   /* on-surface-variant */
  --foreground-subtle:  #6f787d;   /* outline */

  /* ── Bordes (usar con moderación — regla "No-Line") ────────── */
  --border:             #bec8cd;   /* outline-variant */
  --border-subtle:      rgba(190, 200, 205, 0.15);  /* ghost border */

  /* ── Acciones y estados ─────────────────────────────────────── */
  --destructive:        #ba1a1a;   /* error */
  --destructive-light:  #ffdad6;   /* error-container */
  --success:            #005f40;   /* tertiary */
  --success-light:      #007a53;   /* tertiary-container */
  --success-text:       #a3ffd0;   /* on-tertiary-container */
  --warning:            #e6621c;
  --warning-light:      #ffdccc;

  /* ── Radio ──────────────────────────────────────────────────── */
  --radius-sm:   0.5rem;    /* 8px — inputs, badges */
  --radius-md:   0.75rem;   /* 12px — botones */
  --radius-lg:   1rem;      /* 16px — cards */
  --radius-xl:   1.5rem;    /* 24px — modales, CTAs pill */
  --radius-full: 9999px;    /* chips, avatares */
  --radius:      1rem;      /* alias default */

  /* ── Sombras ambientales ────────────────────────────────────── */
  --shadow-sm:  0 2px 8px rgba(25, 28, 30, 0.04);
  --shadow-md:  0 10px 40px rgba(25, 28, 30, 0.06);
  --shadow-lg:  0 20px 60px rgba(25, 28, 30, 0.08);
  --shadow-xl:  0 30px 80px rgba(25, 28, 30, 0.10);

  /* ── Tipografía ─────────────────────────────────────────────── */
  --font-headline: 'Manrope', system-ui, sans-serif;
  --font-body:     'Inter', system-ui, sans-serif;

  /* Escala tipográfica */
  --text-xs:    0.75rem;
  --text-sm:    0.875rem;
  --text-base:  1rem;
  --text-lg:    1.125rem;
  --text-xl:    1.25rem;
  --text-2xl:   1.5rem;
  --text-3xl:   1.875rem;
  --text-4xl:   2.25rem;
  --text-5xl:   3rem;
  --text-display: 3.5rem;

  /* Line heights */
  --leading-tight:   1.25;
  --leading-snug:    1.375;
  --leading-normal:  1.5;
  --leading-relaxed: 1.625;

  /* Letter spacing */
  --tracking-tight:  -0.025em;
  --tracking-normal: 0;
  --tracking-wide:   0.025em;
  --tracking-wider:  0.05em;
  --tracking-widest: 0.1em;

  /* Espaciado */
  --space-1:  0.25rem;
  --space-2:  0.5rem;
  --space-3:  0.75rem;
  --space-4:  1rem;
  --space-5:  1.25rem;
  --space-6:  1.5rem;
  --space-8:  2rem;
  --space-10: 2.5rem;
  --space-12: 3rem;
  --space-16: 4rem;
}

/* ── Modo oscuro ─────────────────────────────────────────────── */
.dark {
  --primary:            #81d1f0;
  --primary-hover:      #b9eaff;
  --primary-light:      #002033;
  --primary-muted:      #00374d;
  --primary-container:  #004a60;

  --background:         #0f1214;
  --surface:            #191c1e;
  --surface-lowest:     #1e2224;
  --surface-low:        #222629;
  --surface-container:  #272b2e;
  --surface-high:       #2d3133;
  --surface-highest:    #323638;
  --surface-muted:      #222629;

  --foreground:         #e2e8ea;
  --foreground-muted:   #9faeb4;
  --foreground-subtle:  #6f8188;

  --border:             #3a4448;
  --border-subtle:      rgba(58, 68, 72, 0.3);

  --destructive:        #ffb4ab;
  --destructive-light:  #93000a;
  --success:            #4edea3;
  --success-light:      #003824;
  --success-text:       #002113;
  --warning:            #ffb77c;
  --warning-light:      #4a1e00;
}

/* ── Tailwind v4 @theme inline ───────────────────────────────── */
@theme inline {
  --color-primary:           var(--primary);
  --color-primary-hover:     var(--primary-hover);
  --color-primary-light:     var(--primary-light);
  --color-primary-muted:     var(--primary-muted);
  --color-primary-container: var(--primary-container);
  --color-background:        var(--background);
  --color-surface:           var(--surface);
  --color-surface-lowest:    var(--surface-lowest);
  --color-surface-low:       var(--surface-low);
  --color-surface-container: var(--surface-container);
  --color-surface-high:      var(--surface-high);
  --color-surface-highest:   var(--surface-highest);
  --color-surface-muted:     var(--surface-muted);
  --color-foreground:        var(--foreground);
  --color-foreground-muted:  var(--foreground-muted);
  --color-foreground-subtle: var(--foreground-subtle);
  --color-border:            var(--border);
  --color-border-subtle:     var(--border-subtle);
  --color-destructive:       var(--destructive);
  --color-destructive-light: var(--destructive-light);
  --color-success:           var(--success);
  --color-success-light:     var(--success-light);
  --color-success-text:      var(--success-text);
  --font-sans:     var(--font-body);
  --font-headline: var(--font-headline);
  --radius:        var(--radius);
}
```

### 1.5 Archivo `src/styles/tokens.ts`

```typescript
// src/styles/tokens.ts
// Tokens de diseño Syntia — fuente de verdad para animaciones y lógica JS

export const colors = {
  primary:          'hsl(193 100% 22%)',   // #005a71
  primaryHover:     'hsl(193 82% 30%)',    // #0e7490
  primaryLight:     'hsl(199 100% 87%)',   // #b9eaff
  primaryMuted:     'hsl(199 82% 72%)',    // #81d1f0
  surface:          'hsl(210 25% 97%)',    // #f7f9fb
  surfaceLowest:    'hsl(0 0% 100%)',      // #ffffff
  surfaceLow:       'hsl(210 15% 96%)',    // #f2f4f6
  surfaceContainer: 'hsl(210 12% 93%)',   // #eceef0
  onSurface:        'hsl(210 10% 11%)',    // #191c1e
  onSurfaceVariant: 'hsl(200 10% 27%)',   // #3f484c
  outline:          'hsl(200 8% 45%)',     // #6f787d
  outlineVariant:   'hsl(198 15% 75%)',   // #bec8cd
  error:            'hsl(353 84% 40%)',    // #ba1a1a
  errorContainer:   'hsl(5 100% 92%)',     // #ffdad6
  tertiary:         'hsl(153 100% 19%)',   // #005f40
  tertiaryContainer:'hsl(153 100% 24%)',  // #007a53
} as const;

export const shadows = {
  sm:  '0 2px 8px rgba(25, 28, 30, 0.04)',
  md:  '0 10px 40px rgba(25, 28, 30, 0.06)',
  lg:  '0 20px 60px rgba(25, 28, 30, 0.08)',
  xl:  '0 30px 80px rgba(25, 28, 30, 0.10)',
  // Sombra glass: para elementos flotantes/glassmorphism
  glass: '0 8px 32px rgba(0, 90, 113, 0.12), inset 0 1px 0 rgba(255,255,255,0.5)',
} as const;

export const radii = {
  sm:   '0.5rem',
  md:   '0.75rem',
  lg:   '1rem',
  xl:   '1.5rem',
  full: '9999px',
} as const;

export const typography = {
  fontHeadline: "'Manrope', system-ui, sans-serif",
  fontBody:     "'Inter', system-ui, sans-serif",
  sizes: {
    display: '3.5rem',
    '4xl':   '2.25rem',
    '3xl':   '1.875rem',
    '2xl':   '1.5rem',
    xl:      '1.25rem',
    lg:      '1.125rem',
    base:    '1rem',
    sm:      '0.875rem',
    xs:      '0.75rem',
  },
  weights: {
    regular:   400,
    medium:    500,
    semibold:  600,
    bold:      700,
    extrabold: 800,
  },
} as const;

export const spacing = {
  1: '0.25rem',  2: '0.5rem',  3: '0.75rem',  4: '1rem',
  5: '1.25rem',  6: '1.5rem',  8: '2rem',     10: '2.5rem',
  12: '3rem',    16: '4rem',   20: '5rem',    24: '6rem',
} as const;

// Duraciones de animación
export const durations = {
  instant:  0,
  fast:     150,
  base:     250,
  moderate: 400,
  slow:     600,
  crawl:    1000,
} as const;

// Easings
export const easings = {
  standard:     [0.2, 0.0, 0.0, 1.0],
  decelerate:   [0.0, 0.0, 0.2, 1.0],
  accelerate:   [0.4, 0.0, 1.0, 1.0],
  spring:       [0.22, 1, 0.36, 1],
  bouncy:       [0.34, 1.56, 0.64, 1],
} as const;
```

### 1.6 Tipografía — Cambio de Fraunces a Manrope

El layout raíz usa actualmente `Inter` + `Fraunces`. El sistema de diseño Stitch requiere `Inter` + `Manrope`.

**Cambio en `src/app/layout.tsx`:**

```typescript
// Eliminar:
import { Inter, Fraunces } from "next/font/google";
const fraunces = Fraunces({ ... });

// Agregar:
import { Inter, Manrope } from "next/font/google";
const manrope = Manrope({
  subsets: ["latin"],
  variable: "--font-headline",
  display: "swap",
  weight: ["400", "600", "700", "800"],
});

// En html className:
className={`${inter.variable} ${manrope.variable} h-full antialiased`}
```

### 1.7 Verificaciones WCAG AA

| Combinación | Ratio mínimo | Estado |
|---|---|---|
| `#005a71` sobre `#ffffff` | 4.5:1 (texto normal) | Calcular: ~7.2:1 ✓ |
| `#191c1e` sobre `#f7f9fb` | 4.5:1 | ~18:1 ✓ |
| `#ffffff` sobre `#005a71` | 4.5:1 | ~7.2:1 ✓ |
| `#3f484c` sobre `#f2f4f6` | 4.5:1 | ~6.1:1 ✓ |
| `#005f40` (tertiary) sobre `#eceef0` | 4.5:1 | Verificar con herramienta |
| `#ba1a1a` sobre `#ffdad6` | 4.5:1 | ~4.6:1 (borderline — verificar) |

**Herramienta:** https://webaim.org/resources/contrastchecker/

---

## 2. Arquitectura Framer Motion

### 2.1 Principios de Animación

El sistema Stitch establece "brutalismo suave con sofisticación editorial". Las animaciones deben:
- Ser **funcionales**, no decorativas — guían la atención, confirman acciones
- Respetar **`prefers-reduced-motion`** en todo momento
- Usar **duración corta**: 200–400ms para microinteracciones, hasta 600ms para transiciones de página
- Usar **spring physics** para interacciones táctiles (botones, cards hover)

### 2.2 Archivo `src/lib/motion.ts` — Completo

```typescript
// src/lib/motion.ts
import type { Variants, Transition } from "framer-motion";

// ── Transiciones base ──────────────────────────────────────────────────────

export const spring: Transition = {
  type: "spring",
  stiffness: 400,
  damping: 30,
};

export const springBouncy: Transition = {
  type: "spring",
  stiffness: 500,
  damping: 25,
};

export const ease: Transition = {
  duration: 0.25,
  ease: [0.2, 0.0, 0.0, 1.0], // Material standard
};

export const easeDecelerate: Transition = {
  duration: 0.35,
  ease: [0.0, 0.0, 0.2, 1.0],
};

export const easeAccelerate: Transition = {
  duration: 0.2,
  ease: [0.4, 0.0, 1.0, 1.0],
};

// ── Variantes de entrada ───────────────────────────────────────────────────

/** Fade simple — para modales, tooltips, overlays */
export const fadeIn: Variants = {
  hidden:  { opacity: 0 },
  visible: { opacity: 1, transition: ease },
  exit:    { opacity: 0, transition: easeAccelerate },
};

/** Slide hacia arriba — para listas de items, cards, contenido principal */
export const slideUp: Variants = {
  hidden:  { opacity: 0, y: 16 },
  visible: { opacity: 1, y: 0, transition: easeDecelerate },
  exit:    { opacity: 0, y: -8, transition: easeAccelerate },
};

/** Slide desde el lateral — para sidebars, paneles deslizantes */
export const slideIn: Variants = {
  hidden:  { opacity: 0, x: -20 },
  visible: { opacity: 1, x: 0, transition: easeDecelerate },
  exit:    { opacity: 0, x: -20, transition: easeAccelerate },
};

/** Slide desde la derecha — para drawers y paneles de detalle */
export const slideInRight: Variants = {
  hidden:  { opacity: 0, x: 24 },
  visible: { opacity: 1, x: 0, transition: easeDecelerate },
  exit:    { opacity: 0, x: 24, transition: easeAccelerate },
};

/** Escala con spring — para elementos que "aparecen" (chips, badges, modales) */
export const springScale: Variants = {
  hidden:  { opacity: 0, scale: 0.92 },
  visible: { opacity: 1, scale: 1, transition: springBouncy },
  exit:    { opacity: 0, scale: 0.96, transition: ease },
};

// ── Variantes de stagger (para listas) ────────────────────────────────────

/** Contenedor de lista escalonada */
export const staggerChildren: Variants = {
  hidden:  { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.06,
      delayChildren: 0.05,
    },
  },
};

/** Ítem de lista escalonada — usar junto con staggerChildren */
export const staggerItem: Variants = {
  hidden:  { opacity: 0, y: 12 },
  visible: { opacity: 1, y: 0, transition: easeDecelerate },
};

// ── Transición de página ───────────────────────────────────────────────────

/** Variante para <AnimatePresence> en el layout raíz */
export const pageTransition: Variants = {
  hidden:  { opacity: 0, x: 12 },
  visible: {
    opacity: 1,
    x: 0,
    transition: {
      duration: 0.38,
      ease: [0.22, 1, 0.36, 1],
    },
  },
  exit:    {
    opacity: 0,
    x: -8,
    transition: {
      duration: 0.2,
      ease: [0.4, 0.0, 1.0, 1.0],
    },
  },
};

// ── Microinteracciones de componentes ─────────────────────────────────────

/** Hover de card — elevación sutil sin sombra pesada */
export const cardHover = {
  rest:  { y: 0, boxShadow: "0 2px 8px rgba(25, 28, 30, 0.04)" },
  hover: {
    y: -2,
    boxShadow: "0 10px 40px rgba(25, 28, 30, 0.08)",
    transition: spring,
  },
};

/** Tap de botón — feedback táctil */
export const buttonTap = {
  tap: { scale: 0.97, transition: { duration: 0.1 } },
};

/** Hover de botón primario — escala leve */
export const buttonHover = {
  rest:  { scale: 1 },
  hover: { scale: 1.02, transition: spring },
  tap:   { scale: 0.97, transition: { duration: 0.1 } },
};

/** Focus de input — glow sutil */
export const inputFocus: Variants = {
  rest:  { boxShadow: "0 0 0 0px rgba(0, 90, 113, 0)" },
  focus: {
    boxShadow: "0 0 0 3px rgba(0, 90, 113, 0.15)",
    transition: ease,
  },
};

/** Shimmer de skeleton — reemplaza CSS keyframes */
export const skeletonShimmer: Variants = {
  initial: { backgroundPosition: "-200% 0" },
  animate: {
    backgroundPosition: "200% 0",
    transition: {
      duration: 1.5,
      ease: "linear",
      repeat: Infinity,
    },
  },
};

// ── Glassmorphism panel ────────────────────────────────────────────────────

/** Panel lateral seleccionado (detalle de oportunidad) */
export const glassPanelSlide: Variants = {
  hidden:  { opacity: 0, x: 40, backdropFilter: "blur(0px)" },
  visible: {
    opacity: 1,
    x: 0,
    backdropFilter: "blur(16px)",
    transition: {
      duration: 0.45,
      ease: [0.0, 0.0, 0.2, 1.0],
    },
  },
  exit: {
    opacity: 0,
    x: 40,
    transition: {
      duration: 0.25,
      ease: [0.4, 0.0, 1.0, 1.0],
    },
  },
};

// ── SSE / Streaming log ───────────────────────────────────────────────────

/** Cada línea del log de streaming IA */
export const streamLogItem: Variants = {
  hidden:  { opacity: 0, x: -8 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.2, ease: "easeOut" } },
};

// ── Hook helper: reduced motion ───────────────────────────────────────────
// Uso: const variants = useMotionVariants(slideUp);
// Si useReducedMotion() === true, devuelve variantes con duration: 0
export function reduceMotionVariants(variants: Variants): Variants {
  const reduced: Variants = {};
  for (const key in variants) {
    const v = variants[key];
    if (typeof v === "object" && v !== null && "transition" in v) {
      reduced[key] = { ...v, transition: { duration: 0 } };
    } else {
      reduced[key] = v;
    }
  }
  return reduced;
}
```

### 2.3 Hook `useMotionVariants`

```typescript
// src/hooks/useMotionVariants.ts
"use client";
import { useReducedMotion } from "framer-motion";
import type { Variants } from "framer-motion";
import { reduceMotionVariants } from "@/lib/motion";

/**
 * Devuelve las variantes originales o variantes reducidas según
 * prefers-reduced-motion del sistema operativo del usuario.
 */
export function useMotionVariants(variants: Variants): Variants {
  const shouldReduce = useReducedMotion();
  return shouldReduce ? reduceMotionVariants(variants) : variants;
}
```

### 2.4 Estrategia de Transición de Página

**Problema actual:** `src/components/PageTransition.tsx` ya existe con `AnimatePresence`. El componente actual usa fade puro (opacity 0→1) en 420ms, que es correcto pero mejorable.

**Estrategia propuesta:**

El `<AnimatePresence>` debe vivir en `src/app/template.tsx` (que ya existe), no en el layout. En Next.js App Router, `template.tsx` se re-monta en cada navegación, lo que permite que `AnimatePresence` detecte el cambio de clave.

```typescript
// src/app/template.tsx — REEMPLAZAR COMPLETAMENTE
"use client";
import { motion, AnimatePresence, useReducedMotion } from "framer-motion";
import { usePathname } from "next/navigation";
import { pageTransition } from "@/lib/motion";

export default function Template({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const shouldReduce = useReducedMotion();

  const variants = shouldReduce
    ? { hidden: { opacity: 0 }, visible: { opacity: 1 }, exit: { opacity: 0 } }
    : pageTransition;

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={pathname}
        variants={variants}
        initial="hidden"
        animate="visible"
        exit="exit"
      >
        {children}
      </motion.div>
    </AnimatePresence>
  );
}
```

**Eliminar** `src/components/PageTransition.tsx` (ya no necesario — `template.tsx` lo reemplaza).

### 2.5 Patrones de Animación por Tipo de Componente

| Componente | Variante | Trigger | Notas |
|---|---|---|---|
| Lista de cards | `staggerChildren` + `staggerItem` | mount | Máximo 8 items animados; el resto aparece directo |
| Card individual | `cardHover` | hover/focus | `whileHover` en `motion.div` |
| Botón primario | `buttonHover` + `buttonTap` | hover + tap | `whileHover` + `whileTap` |
| Botón secundario | solo `buttonTap` | tap | Sin hover scale |
| Modal | `springScale` | AnimatePresence | Backdrop: `fadeIn` |
| Sidebar/panel | `slideIn` | mount condicional | AnimatePresence wrapper |
| Skeleton | `skeletonShimmer` | siempre | Reemplaza CSS keyframes |
| Toast | `slideInRight` + `fadeIn` | AnimatePresence | En ToastProvider |
| Log SSE | `streamLogItem` en stagger | append | Cada nueva línea entra desde izquierda |
| Badge/chip | `springScale` | mount | Duración muy corta |
| Input focus | `inputFocus` | focus/blur | `animate` condicional |
| Número KPI | `spring` counter | mount | Usar `useMotionValue` + `useTransform` |

---

## 3. Arquitectura de Componentes Frontend

### 3.1 Estructura de Archivos — Vista General

```
src/
├── app/
│   ├── layout.tsx              ← Root: providers, fonts, metadata
│   ├── template.tsx            ← AnimatePresence + pageTransition
│   ├── globals.css             ← Tokens CSS (paleta azul petróleo)
│   ├── page.tsx                ← Redirect → /home
│   ├── home/
│   │   └── page.tsx            ← Landing page (fuera de scope de auth)
│   ├── dashboard/
│   │   ├── layout.tsx          ← Contenedor max-w-7xl
│   │   └── page.tsx            ← Dashboard usuario rediseñado
│   ├── proyectos/
│   │   ├── layout.tsx
│   │   ├── page.tsx            ← Lista de proyectos
│   │   ├── nuevo/page.tsx
│   │   └── [id]/
│   │       ├── editar/page.tsx
│   │       └── recomendaciones/page.tsx
│   ├── perfil/
│   │   ├── layout.tsx
│   │   └── page.tsx
│   ├── admin/
│   │   ├── layout.tsx          ← AdminNavbar + contenedor
│   │   ├── dashboard/page.tsx
│   │   ├── usuarios/
│   │   │   ├── page.tsx
│   │   │   └── [id]/page.tsx
│   │   ├── convocatorias/
│   │   │   ├── page.tsx
│   │   │   ├── nueva/page.tsx
│   │   │   └── [id]/editar/page.tsx
│   │   └── bdns/page.tsx
│   ├── buscar/page.tsx
│   └── guias/page.tsx
├── components/
│   ├── Navbar.tsx              ← Barra superior usuario (rediseñar)
│   ├── AdminNavbar.tsx         ← Barra superior admin (rediseñar)
│   ├── GlobalUserNavbar.tsx    ← Wrapper de exclusión de rutas
│   ├── GlobalFooter.tsx        ← Footer con exclusiones
│   ├── ThemeProvider.tsx       ← Dark mode context
│   ├── PageTransition.tsx      ← ELIMINAR (reemplazado por template.tsx)
│   ├── ModalAccesoRequerido.tsx
│   └── ui/
│       ├── Alert.tsx
│       ├── Badge.tsx           ← Añadir variantes de estado
│       ├── Button.tsx          ← Añadir motion.button
│       ├── Card.tsx            ← Añadir variantes hover/glass
│       ├── Input.tsx           ← Añadir focus animation
│       ├── Modal.tsx           ← CREAR (extraído de perfil/page.tsx)
│       ├── Skeleton.tsx        ← Mejorar con Framer Motion shimmer
│       └── Toast.tsx
├── hooks/
│   └── useMotionVariants.ts    ← CREAR
├── lib/
│   ├── api.ts
│   ├── auth.ts
│   ├── motion.ts               ← CREAR (ver §2.2)
│   └── types/
│       ├── auth.ts             ← CREAR
│       ├── convocatoria.ts     ← CREAR (unifica ConvocatoriaPublicaDTO)
│       ├── dashboard.ts        ← CREAR
│       ├── perfil.ts           ← CREAR
│       ├── proyecto.ts         ← CREAR
│       ├── recomendacion.ts    ← CREAR
│       ├── admin.ts            ← CREAR
│       ├── bdns.ts             ← CREAR
│       ├── guia.ts             ← CREAR
│       └── convocatorias.ts    ← Mantener por compatibilidad admin
└── styles/
    └── tokens.ts               ← CREAR (ver §1.5)
```

### 3.2 `src/app/layout.tsx` — Root Layout Rediseñado

**Cambios:**
- Sustituir `Fraunces` por `Manrope`
- Agregar variable `--font-headline` para Manrope
- `themeColor` de `#f5f5f0` → `#f7f9fb`

```typescript
import type { Metadata, Viewport } from "next";
import { Inter, Manrope } from "next/font/google";
import { ThemeProvider } from "@/components/ThemeProvider";
import { GlobalUserNavbar } from "@/components/GlobalUserNavbar";
import { ToastProvider } from "@/components/ui/Toast";
import { GlobalFooter } from "@/components/GlobalFooter";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-sans",
  display: "swap",
});

const manrope = Manrope({
  subsets: ["latin"],
  variable: "--font-headline",
  display: "swap",
  weight: ["400", "600", "700", "800"],
});

export const metadata: Metadata = {
  title: "Syntia — Encuentra subvenciones para tu proyecto",
  description: "Syntia analiza tu proyecto con IA y encuentra las subvenciones más compatibles.",
  keywords: ["subvenciones", "BDNS", "inteligencia artificial", "financiación pública", "España"],
};

export const viewport: Viewport = {
  themeColor: "#f7f9fb",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es" className={`${inter.variable} ${manrope.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col bg-background text-foreground font-sans"
            suppressHydrationWarning>
        <ThemeProvider>
          <ToastProvider>
            <GlobalUserNavbar />
            <main className="flex-1 pb-8">{children}</main>
            <GlobalFooter />
          </ToastProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
```

### 3.3 Navbar Rediseñada

**Principios del rediseño según Stitch:**
- Sidebar fijo `w-64` en desktop con fondo `bg-[#0c2a35]` (cyan-950 oscuro)
- TopBar translúcida con `backdrop-blur-xl bg-surface-low/80`
- No usar `border-b` sólido — usar sombra ambiente

**Jerarquía de componentes Navbar:**

```
GlobalUserNavbar (exclusión de rutas)
└── Navbar
    ├── SidebarDesktop (hidden lg:flex, fixed left-0)
    │   ├── Logo (text-white font-headline text-2xl)
    │   ├── NavItems (stagger animation en mount)
    │   │   └── NavItem (slideIn variant, active con border-r-4 border-primary-light)
    │   ├── PlanBadge ("GRATUITO" / "PREMIUM")
    │   └── UserMini (avatar + email truncado)
    └── TopBar (fixed top-0, backdrop-blur-xl)
        ├── MobileMenuButton
        ├── SearchBar (oculto en mobile)
        ├── ThemeToggle
        ├── NotificationBell (futuro)
        └── UserDropdown
            ├── UserAvatar
            └── DropdownMenu (springScale animation)
                ├── Link → /perfil
                └── Button → logout
```

**Animaciones en Navbar:**
```typescript
// Sidebar items en mount: stagger
// El item activo: bordure derecho animado con layout animation
// UserDropdown: springScale variants en AnimatePresence
// Mobile drawer: slideIn desde izquierda
```

### 3.4 `dashboard/page.tsx` — Dashboard Rediseñado

**Jerarquía:**
```
DashboardPage
├── DashboardHeader
│   ├── Greeting (fade + slideUp con delay 0)
│   │   ├── h1 font-headline text-4xl "Bienvenido, {nombre}"
│   │   └── p text-foreground-muted
│   └── CTA "Nuevo Proyecto" (Button primary)
├── KPIBentoGrid (3 columnas, stagger animation)
│   ├── KPICard: "Recomendaciones" (value counter animation)
│   ├── KPICard: "Proyectos activos"
│   └── KPICard: "Oportunidades roadmap"
├── MainGrid (lg:grid-cols-12)
│   ├── RecomendacionesRecientes (lg:col-span-8)
│   │   ├── SectionHeader
│   │   └── ProyectoGroup[] (stagger por proyecto)
│   │       └── RecCard[] (slideUp)
│   └── RoadmapSidebar (lg:col-span-4)
│       ├── RoadmapCard[] (stagger)
│       └── ConsejoBanner (glassmorphism sutil)
└── SkeletonDashboard (mientras loading)
```

**Fix crítico B1 — DashboardData shape:**
El backend devuelve `topRecomendaciones` como `Array<{ proyecto, recomendaciones }>`.
El componente actual usa `Record<string, RecomendacionDTO[]>` y llama `Object.entries()`.
**Solución:** Actualizar el tipo en `dashboard/page.tsx` para consumir el array directamente:

```typescript
// ANTES (incorrecto):
topRecomendaciones: Record<string, RecomendacionDTO[]>;
// código: Object.entries(data.topRecomendaciones).map(([nombre, recs]) => ...)

// DESPUÉS (correcto — alineado con backend):
topRecomendaciones: Array<{
  proyecto: { id: number; nombre: string; sector?: string };
  recomendaciones: RecomendacionDTO[];
}>;
// código: data.topRecomendaciones.map(({ proyecto, recomendaciones }) => ...)
```

### 3.5 `proyectos/page.tsx` — Lista de Proyectos

**Jerarquía:**
```
ProyectosPage
├── PageHeader
│   ├── Title + Subtitle (slideUp)
│   └── Button "Nuevo Proyecto"
├── SearchBar (filtro local, InputField animado)
├── ProyectosGrid (stagger animation)
│   └── ProyectoCard[] (cardHover, motion.div)
│       ├── IconAvatar (primary/10 bg)
│       ├── ProyectoNombre
│       ├── ProyectoDescripcion (line-clamp-3)
│       ├── Metadata (sector, ubicación, fecha)
│       └── CardFooter
│           ├── EditLink
│           └── ArrowLink "Ver subvenciones"
└── EmptyState (slideUp, ilustración)
```

**Nuevo campo `creadoEn`:** Una vez aplicado el fix de backend (Priority 1, ítem 3), el campo `fechaCreacion` que ya renderiza el frontend (línea 135-139 de proyectos/page.tsx) funcionará sin cambios en el template.

### 3.6 `proyectos/[id]/recomendaciones/page.tsx`

**Jerarquía:**
```
RecomendacionesPage
├── Breadcrumb (← Proyectos / Nombre proyecto)
├── ActionPanel (Card glassmorphism)
│   ├── StepDescription "Buscar BDNS → Analizar IA"
│   ├── BuscarButton + AnalizarButton
│   └── StreamLog (AnimatePresence, stagger items SSE)
│       └── StreamLogLine[] (streamLogItem variant)
├── FiltroInput (InputField animado)
├── CandidatasSection (stagger)
│   └── CandidataCard[] (compact, no score)
└── RecomendacionesIASection (stagger)
    └── RecomendacionCard[] (full, con ScoreBadge)
        └── GuiaExpandida (AnimatePresence height animation)
```

**SSE Log rediseño:** Cambiar `bg-gray-900 text-green-400` (código hardcoded que conserva el verde) por:
```typescript
// Eliminar green hardcoded:
// className="... text-green-400 ..."
// Reemplazar por:
className="... bg-surface-highest text-foreground-muted font-mono ..."
// El símbolo ✓ puede ser text-success (tertiary token)
```

### 3.7 `perfil/page.tsx` — Perfil de Usuario

**Jerarquía:**
```
PerfilPage
├── ProfileHeader
│   ├── AvatarCircle (gradiente primary → primary-container, iniciales)
│   ├── UserName (de perfil.nombre — nuevo campo)
│   ├── UserEmail
│   └── PlanBadge
├── TabBar (Información | Cuenta | Notificaciones) — nuevo
│   └── Tab[] con indicador animado (layoutId)
├── Tab: InformacionPersonal (Section animada)
│   └── Grid 2col de Fields
├── Tab: Cuenta (Section animada)
│   ├── CambiarEmailRow
│   ├── CambiarPasswordRow
│   └── PeligroZone (cerrar sesión)
├── Tab: Notificaciones (Section animada)
│   └── ToggleRows[]
├── ModalCambiarEmail (AnimatePresence springScale)
└── ModalCambiarPassword (AnimatePresence springScale)
```

**Mejora UX:** Extraer los modales inline a `src/components/ui/Modal.tsx` reutilizable:

```typescript
// src/components/ui/Modal.tsx
export function Modal({ isOpen, onClose, title, children }) {
  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          variants={fadeIn}
          initial="hidden"
          animate="visible"
          exit="exit"
        >
          {/* Backdrop */}
          <motion.div
            className="absolute inset-0 bg-black/40 backdrop-blur-sm"
            onClick={onClose}
          />
          {/* Panel */}
          <motion.div
            className="relative bg-surface-lowest rounded-2xl shadow-xl w-full max-w-sm p-6"
            variants={springScale}
            onClick={(e) => e.stopPropagation()}
          >
            {/* ... */}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
```

### 3.8 `admin/dashboard/page.tsx`

**Jerarquía:**
```
AdminDashboardPage
├── PageTitle "Panel de Administración"
├── KPIGrid (4 columnas, stagger)
│   ├── KPICard: Usuarios (Users icon)
│   ├── KPICard: Convocatorias (FileText icon)
│   ├── KPICard: Proyectos (FolderOpen icon)
│   └── KPICard: Recomendaciones (Star icon)
└── QuickActions (grid 2 columnas)
    ├── Link → /admin/usuarios
    ├── Link → /admin/convocatorias
    ├── Link → /admin/bdns
    └── Link → /admin/convocatorias/nueva
```

### 3.9 `admin/usuarios/page.tsx`

**Jerarquía:**
```
AdminUsuariosPage
├── PageHeader
├── SearchInput (filtro local)
├── UsuariosTable (motion.table con stagger rows)
│   ├── TableHeader
│   └── TableRow[] (AnimatePresence para eliminación)
│       ├── EmailCell
│       ├── RolBadge
│       ├── PlanBadge (GRATUITO/PREMIUM)
│       ├── FechaCell
│       └── ActionsCell (link detalle, cambiar rol)
└── EmptyState
```

### 3.10 `admin/bdns/page.tsx`

**Jerarquía:**
```
AdminBdnsPage
├── EstadoJobCard (glassmorphism, pulso animado si EN_CURSO)
│   ├── EstadoBadge (animated indicator)
│   ├── ProgressBar (motion.div width animation)
│   ├── EjeActual
│   └── ActionButtons (Iniciar/Cancelar)
├── EjesGrid (tabla de 23 ejes, stagger)
│   └── EjeRow[] (estado badge por eje)
├── HistorialSection
│   └── EjecucionRow[] (stagger, expandible)
└── CoberturaSection
    └── CampoBar[] (progress bars animadas)
```

### 3.11 Componentes UI Compartidos — Cambios

**`Card.tsx` — Rediseño completo:**
```typescript
// Principio: sin bordes sólidos, profundidad por tono de superficie
// surface-lowest sobre surface-low = contraste natural

interface CardProps {
  children: ReactNode;
  className?: string;
  variant?: "default" | "glass" | "elevated";
  hover?: boolean;
  padding?: "sm" | "md" | "lg" | "none";
  as?: "div" | "article" | "section";
}

// variant="default": bg-surface-lowest, shadow-sm (no border)
// variant="glass": bg-surface-variant/70 backdrop-blur-xl (glassmorphism)
// variant="elevated": bg-surface-lowest, shadow-md
// hover=true: motion.div con cardHover variants
```

**`Button.tsx` — Con Framer Motion:**
```typescript
// Envolver en motion.button con buttonHover + buttonTap
// Gradiente sutil en variant="primary": bg-gradient-to-r from-primary to-primary-container
// Radio: rounded-xl (default) o rounded-full para pill CTAs
```

**`Badge.tsx` — Nuevas variantes de estado:**
```typescript
interface BadgeProps {
  variant?: "default" | "success" | "error" | "warning" | "plan" | "score";
  // success → bg-success-light text-success-text (NO verde genérico)
  // error → bg-destructive-light text-destructive
  // plan → "PREMIUM" en bg-primary/10 text-primary
}
```

**`Skeleton.tsx` — Con Framer Motion shimmer:**
```typescript
// Reemplazar CSS animation por motion.div con skeletonShimmer variants
// Respetar useReducedMotion() (si true → solo animate-pulse CSS)
```

**`Input.tsx` — Con focus animation:**
```typescript
// motion.div wrapper con inputFocus variants
// Estado focus: ghost border 3px rgba(0,90,113,0.15)
// Sin border en reposo: bg-surface-highest, outline: none
```

---

## 4. Arquitectura Backend

### 4.1 Phase 1 — Blocking (implementar antes de construir UI)

#### Fix B1: Dashboard — Alinear tipo frontend con forma del backend

**Archivo a cambiar:** `src/app/dashboard/page.tsx` (frontend)

No se cambia el backend. Se corrige el tipo TypeScript en el frontend para consumir la forma correcta que ya devuelve el backend (`Array<{ proyecto, recomendaciones }>`).

```typescript
// src/app/dashboard/page.tsx — cambios de tipo:

// ELIMINAR:
interface DashboardData {
  topRecomendaciones: Record<string, RecomendacionDTO[]>;  // WRONG
  roadmap: { proyecto: { id: number; nombre: string }; recomendacion: RecomendacionDTO }[];
}

// REEMPLAZAR con tipo correcto alineado al backend:
interface TopRecomendacionesEntry {
  proyecto: { id: number; nombre: string; sector?: string; ubicacion?: string };
  recomendaciones: RecomendacionDTO[];
}

interface DashboardData {
  usuario?: { id: number; email: string; rol: string; plan: string; creadoEn: string };
  topRecomendaciones: TopRecomendacionesEntry[];
  roadmap: Array<{
    proyecto: { id: number; nombre: string };
    recomendacion: RecomendacionDTO;
  }>;
  totalRecomendaciones: number;
}

// En el render, cambiar Object.entries() por .map():
data.topRecomendaciones.map(({ proyecto, recomendaciones }) => (
  <div key={proyecto.id}>
    <p className="text-xs font-semibold ...">{proyecto.nombre}</p>
    {recomendaciones.map((rec) => <RecCard key={rec.id} rec={rec} />)}
  </div>
))
```

#### Fix B2: `@JsonIgnore` en `Usuario.password`

**Archivo:** `src/main/java/com/syntia/ai/model/Usuario.java`

```java
// Añadir import al inicio del archivo:
import com.fasterxml.jackson.annotation.JsonIgnore;

// En el campo password (línea 63):
@NotBlank(message = "La contraseña es obligatoria")
@Column(name = "password_hash", nullable = false)
@JsonIgnore                    // ← AÑADIR ESTA LÍNEA
private String password;
```

**Por qué:** El endpoint `GET /api/admin/usuarios` devuelve entidades `Usuario` serializadas directamente por Jackson. Sin `@JsonIgnore`, el hash bcrypt se expone en la respuesta. Es una fuga de seguridad crítica.

#### Fix B3: `creadoEn` en `Proyecto` + `ProyectoDTO`

**Archivo 1:** `src/main/java/com/syntia/ai/model/Proyecto.java`

```java
// Añadir imports:
import java.time.LocalDateTime;
import jakarta.persistence.PrePersist;

// Añadir campo después de `descripcion`:
@Column(name = "creado_en", nullable = false, updatable = false)
private LocalDateTime creadoEn;

@PrePersist
protected void onCreate() {
    this.creadoEn = LocalDateTime.now();
}
```

**Archivo 2:** `src/main/java/com/syntia/ai/model/dto/ProyectoDTO.java`

```java
// Añadir import:
import java.time.LocalDateTime;

// Añadir campo en la clase ProyectoDTO:
private LocalDateTime creadoEn;
```

**Archivo 3:** Buscar el servicio que convierte `Proyecto` → `ProyectoDTO` (probablemente `ProyectoService.java`) y añadir el mapeo:

```java
dto.setCreadoEn(proyecto.getCreadoEn());
```

**Nota sobre migración:** Agregar `creadoEn` a una tabla existente con `nullable = false` fallará si hay filas existentes. Opciones:
- Opción A: `nullable = false` con `columnDefinition = "TIMESTAMP DEFAULT NOW()"` — Hibernate DDL auto-update rellena las filas existentes
- Opción B: Hacer `nullable = true` temporalmente, llenar valores, luego añadir constraint
- **Recomendado:** `@Column(name = "creado_en", updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")`

#### Fix B4: `tipo` en `ConvocatoriaPublicaDTO`

**Verificación:** Revisando el archivo `ConvocatoriaPublicaDTO.java`, el campo `tipo` **ya NO está** en el DTO (tiene 12 campos, sin `tipo`). El frontend de recomendaciones sí usa `rec.tipo`.

**Archivo:** `src/main/java/com/syntia/ai/model/dto/ConvocatoriaPublicaDTO.java`

```java
// Añadir campo después de numeroConvocatoria:
private String tipo;
```

**Y el mapper que construye el DTO** debe incluir `tipo`:
```java
dto.setTipo(convocatoria.getTipo());
```

Buscar `ConvocatoriaPublicaController.java` o el service que construye `ConvocatoriaPublicaDTO` para aplicar el mapeo.

---

### 4.2 Phase 2 — Important (implementar en paralelo al UI build)

#### P2-1: Añadir `organismo`, `presupuesto`, `fechaPublicacion` a `RecomendacionDTO`

**Archivo:** `src/main/java/com/syntia/ai/model/dto/RecomendacionDTO.java`

```java
// Añadir imports:
import java.time.LocalDate;

// Añadir campos en la clase (los campos ya existen en la entidad Convocatoria):
private String organismo;
private Double presupuesto;
private LocalDate fechaPublicacion;
```

**Y en el mapper** (buscar en `RecomendacionService.java` o `RecomendacionController.java`):
```java
dto.setOrganismo(rec.getConvocatoria().getOrganismo());
dto.setPresupuesto(rec.getConvocatoria().getPresupuesto());
dto.setFechaPublicacion(rec.getConvocatoria().getFechaPublicacion());
```

#### P2-2: Endpoint `GET /api/usuario/perfil/completo`

**Archivo:** `src/main/java/com/syntia/ai/controller/api/PerfilController.java`

```java
// Nuevo DTO inline o en archivo separado PerfilCompletoDTO.java:
// { sector, ubicacion, empresa, provincia, telefono, tipoEntidad, objetivos,
//   necesidadesFinanciacion, descripcionLibre, nombre,
//   email, rol, plan, creadoEn }

@GetMapping("/completo")
public ResponseEntity<?> obtenerPerfilCompleto(Authentication authentication) {
    Usuario usuario = resolverUsuario(authentication);
    // Merge PerfilDTO fields + usuario fields
    var perfilOpt = perfilService.obtenerPerfil(usuario.getId());

    Map<String, Object> resultado = new LinkedHashMap<>();
    resultado.put("email", usuario.getEmail());
    resultado.put("rol", usuario.getRol().name());
    resultado.put("plan", usuario.getPlan().name());
    resultado.put("creadoEn", usuario.getCreadoEn());

    perfilOpt.ifPresent(perfil -> {
        resultado.put("nombre", perfil.getNombre());
        resultado.put("sector", perfil.getSector());
        resultado.put("ubicacion", perfil.getUbicacion());
        resultado.put("empresa", perfil.getEmpresa());
        resultado.put("provincia", perfil.getProvincia());
        resultado.put("telefono", perfil.getTelefono());
        resultado.put("tipoEntidad", perfil.getTipoEntidad());
        resultado.put("objetivos", perfil.getObjetivos());
        resultado.put("necesidadesFinanciacion", perfil.getNecesidadesFinanciacion());
        resultado.put("descripcionLibre", perfil.getDescripcionLibre());
    });

    return ResponseEntity.ok(resultado);
}
```

**En el frontend**, actualizar `perfilApi`:
```typescript
completo: () => api.get<PerfilCompletoDTO>("/usuario/perfil/completo"),
```

#### P2-3: Añadir `nombre` a `Perfil` + `PerfilDTO`

**Archivo 1:** `src/main/java/com/syntia/ai/model/Perfil.java`

```java
// Añadir campo:
@Column(length = 100)
private String nombre;
```

**Archivo 2:** `src/main/java/com/syntia/ai/model/dto/PerfilDTO.java`

```java
@Size(max = 100)
private String nombre;
```

**Nota migración:** `nombre` debe ser nullable (existirán perfiles sin él). `@Column(length = 100)` sin `nullable = false`.

#### P2-4: Paginar `GET /api/usuario/convocatorias/recomendadas`

**Archivo:** Buscar en `src/main/java/com/syntia/ai/controller/api/ConvocatoriaPublicaController.java` o en un `UsuarioConvocatoriasController` el endpoint `/api/usuario/convocatorias/recomendadas`.

**Cambio:** Devolver `Page<ConvocatoriaPublicaDTO>` serializado como la misma estructura que `BusquedaPublicaResponse`:

```java
// De:
return ResponseEntity.ok(List<ConvocatoriaPublicaDTO>);

// A (usando Spring Data Pageable):
@GetMapping("/recomendadas")
public ResponseEntity<Map<String, Object>> getRecomendadas(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "16") int size,
    Authentication auth) {

    // ... lógica existente
    Page<ConvocatoriaPublicaDTO> pageResult = ...; // convertir a Page

    Map<String, Object> response = Map.of(
        "content", pageResult.getContent(),
        "totalElements", pageResult.getTotalElements(),
        "totalPages", pageResult.getTotalPages(),
        "page", pageResult.getNumber(),
        "size", pageResult.getSize()
    );
    return ResponseEntity.ok(response);
}
```

---

### 4.3 Phase 3 — Enhancement

#### P3-1: Filtros en `GET /api/admin/convocatorias`

Añadir params `q`, `sector`, `abierto` al controller. Requiere implementar búsqueda con `Specification` JPA o `@Query` JPQL en `ConvocatoriaRepository`.

#### P3-2: `GET /api/admin/stats` enriquecido

```java
// Nuevo endpoint en AdminController:
@GetMapping("/stats")
public ResponseEntity<?> getStats() {
    return ResponseEntity.ok(Map.of(
        "totalUsuarios", usuarioService.count(),
        "totalConvocatorias", convocatoriaService.count(),
        "totalConvocatoriasAbiertas", convocatoriaService.countAbiertas(),
        "totalProyectos", proyectoService.count(),
        "totalRecomendaciones", recomendacionService.count(),
        "usuariosPremium", usuarioService.countByPlan(Plan.PREMIUM)
    ));
}
```

#### P3-3: Exponer `plan` en lista de admin usuarios

En el servicio/DTO que construye la lista de usuarios admin, añadir `plan: usuario.getPlan().name()`.

#### P3-4: Filtro `abierto=true` en búsqueda pública

En `GET /api/convocatorias/publicas/buscar`, añadir param `abierto` (Boolean) al JPQL existente.

---

## 5. Preocupaciones Transversales

### 5.1 Manejo de Errores — Flujo Completo

**Contrato de error backend** (ya existe `ErrorResponse`):
```json
{
  "status": 404,
  "message": "El usuario aún no ha completado su perfil",
  "timestamp": "2026-04-13T12:00:00",
  "path": "/api/usuario/perfil"
}
```

**Interceptor frontend** (`src/lib/api.ts`):
- `401` → limpia cookie + redirect `/login` (ya implementado)
- `403` → mostrar toast "No tienes permisos"
- `404` → mostrar estado vacío en componente (no redirigir)
- `429` → mostrar toast con `esperarSegundos` countdown
- `5xx` → mostrar toast "Error del servidor" + log

**Patrón en componentes:**

```typescript
// Patrón estándar para páginas:
const [state, setState] = useState<{
  data: T | null;
  loading: boolean;
  error: string | null;
}>({ data: null, loading: true, error: null });

// Render:
if (state.loading) return <SkeletonPage />;
if (state.error) return <ErrorState message={state.error} retry={fetchData} />;
if (!state.data) return <EmptyState />;
return <Content data={state.data} />;
```

**`ErrorState` component:**
```typescript
// src/components/ui/ErrorState.tsx — CREAR
function ErrorState({ message, retry }: { message: string; retry?: () => void }) {
  return (
    <motion.div variants={slideUp} initial="hidden" animate="visible"
      className="text-center py-16 space-y-4">
      <AlertCircle className="w-12 h-12 text-destructive mx-auto" />
      <p className="text-foreground-muted">{message}</p>
      {retry && <Button variant="secondary" onClick={retry}>Reintentar</Button>}
    </motion.div>
  );
}
```

### 5.2 Loading States — Skeletons

**Skeleton con Framer Motion (reemplaza CSS animate-pulse):**

```typescript
// src/components/ui/Skeleton.tsx — REEMPLAZAR

import { motion, useReducedMotion } from "framer-motion";

export function Skeleton({ className = "" }: { className?: string }) {
  const shouldReduce = useReducedMotion();

  if (shouldReduce) {
    return (
      <div className={`bg-surface-container rounded-lg ${className}`} aria-hidden="true" />
    );
  }

  return (
    <motion.div
      className={`rounded-lg ${className}`}
      style={{
        background: "linear-gradient(90deg, var(--surface-container) 25%, var(--surface-high) 50%, var(--surface-container) 75%)",
        backgroundSize: "200% 100%",
      }}
      animate={{ backgroundPosition: ["200% 0", "-200% 0"] }}
      transition={{ duration: 1.5, ease: "linear", repeat: Infinity }}
      aria-hidden="true"
    />
  );
}

// Skeletons específicos por página:
export function SkeletonCard() { /* ... */ }
export function SkeletonKPIGrid() { /* ... */ }
export function SkeletonTable({ rows = 5 }: { rows?: number }) { /* ... */ }
export function SkeletonRecomendacionCard() { /* ... */ }
```

### 5.3 Accesibilidad WCAG AA

**Gestión del foco:**
- En modales: `focus-trap` al abrir, restaurar foco al cerrar. Usar `useEffect` con `ref.current.focus()`.
- En drawers/sidebars: misma lógica.
- En toasts: `role="alert"` ya implementado. Verificar `aria-live="polite"`.

**ARIA labels:**
```typescript
// Botón de eliminar (proyectos):
<button aria-label={`Eliminar proyecto ${proyecto.nombre}`}>

// Score badge:
<span aria-label={`Puntuación de compatibilidad: ${score} sobre 100`}>

// Skeleton loading:
<div aria-hidden="true" aria-label="Cargando...">

// Nav items activos:
<a aria-current="page">  // cuando isActive

// Toggle switch en perfil:
<input type="checkbox" aria-label="Activar notificaciones de convocatorias" role="switch">
```

**Navegación por teclado:**
- Todos los elementos interactivos accesibles con `Tab`
- Modales cierran con `Escape`
- Dropdowns con arrow keys (si se implementa menu complejo, usar Radix UI o similar)
- Focus visible obligatorio: mantener `:focus-visible` con outline en `--primary`

**Verificaciones de contraste (§1.7)** a ejecutar en staging antes de release.

### 5.4 Principio "No-Line" — Implementación Práctica

El manifiesto Stitch prohíbe `border: 1px solid` para separar secciones. En el código actual existen muchos `border border-border`. La transición se hace por partes:

**Regla de aplicación:**
1. Cards → cambiar `border border-border` por `shadow-sm` (sombra ambiente)
2. Separadores dentro de cards → cambiar `border-t border-border` por `my-4` (espacio)
3. Inputs → sin border en reposo, solo foco ring
4. Tabla admin → usar filas `bg-surface-lowest` alternas con `bg-surface-low` en lugar de `divide-y`

**Excepción legítima (ghost border):**
```typescript
// Para accesibilidad en contenedores que necesitan delimitación visual:
className="outline outline-1 outline-border/15"
// Solo cuando el cambio de tono de superficie no es suficiente
```

### 5.5 Animaciones y `prefers-reduced-motion`

Todos los componentes con Framer Motion deben:

1. Importar `useReducedMotion` de framer-motion
2. O usar el hook `useMotionVariants` de `@/hooks/useMotionVariants`
3. Framer Motion respeta automáticamente `prefers-reduced-motion` si se usan `variants` + `motion.div` (no desactiva por defecto, por eso necesitamos el hook `reduceMotionVariants`)

**Regla en CSS:**
```css
@media (prefers-reduced-motion: reduce) {
  /* Mantener solo el ya existente en globals.css */
  .route-fade-transition {
    animation-duration: 150ms;
    animation-timing-function: linear;
  }
  /* Para skeleton CSS (fallback sin JS): */
  .skeleton-shimmer {
    animation: none;
    background: var(--surface-container);
  }
}
```

### 5.6 Arquitectura de Tipos TypeScript

Centralizar todos los tipos en `src/lib/types/` (ver §3.1 estructura). Los tipos inline en cada `page.tsx` se mantienen como retrocompatibilidad, pero nuevos componentes importarán desde `@/lib/types/*`.

**Migración progresiva:**
1. Crear archivos en `src/lib/types/` con los tipos del §6 del data-contract
2. Cada página al redesignarse importa desde tipos centralizados
3. No romper páginas existentes hasta que se redesignen

### 5.7 Resumen de Ficheros a Crear / Modificar

| Acción | Archivo | Descripción |
|---|---|---|
| CREAR | `src/lib/motion.ts` | Todas las variantes Framer Motion |
| CREAR | `src/hooks/useMotionVariants.ts` | Hook reduced-motion |
| CREAR | `src/styles/tokens.ts` | Tokens JS |
| CREAR | `src/components/ui/Modal.tsx` | Modal reutilizable |
| CREAR | `src/components/ui/ErrorState.tsx` | Estado de error |
| CREAR | `src/lib/types/auth.ts` | Tipos de autenticación |
| CREAR | `src/lib/types/dashboard.ts` | Tipos dashboard |
| CREAR | `src/lib/types/proyecto.ts` | ProyectoDTO correcto |
| CREAR | `src/lib/types/recomendacion.ts` | RecomendacionDTO con nuevos campos |
| CREAR | `src/lib/types/convocatoria.ts` | ConvocatoriaPublicaDTO + tipo |
| CREAR | `src/lib/types/perfil.ts` | PerfilDTO + PerfilCompletoDTO |
| CREAR | `src/lib/types/admin.ts` | Admin types |
| CREAR | `src/lib/types/bdns.ts` | BDNS ETL types |
| CREAR | `src/lib/types/guia.ts` | GuiaSubvencionDTO |
| MODIFICAR | `src/app/globals.css` | Paleta azul petróleo completa |
| MODIFICAR | `src/app/layout.tsx` | Manrope, themeColor |
| MODIFICAR | `src/app/template.tsx` | AnimatePresence pageTransition |
| MODIFICAR | `src/app/dashboard/page.tsx` | Fix tipo topRecomendaciones |
| MODIFICAR | `src/app/proyectos/[id]/recomendaciones/page.tsx` | Eliminar text-green-400 |
| MODIFICAR | `src/components/ui/Button.tsx` | motion.button |
| MODIFICAR | `src/components/ui/Card.tsx` | Sin borders, con shadow |
| MODIFICAR | `src/components/ui/Skeleton.tsx` | Framer Motion shimmer |
| MODIFICAR | `src/components/Navbar.tsx` | Sidebar + TopBar rediseñados |
| ELIMINAR | `src/components/PageTransition.tsx` | Reemplazado por template.tsx |
| **BACKEND** | `Usuario.java` L63 | `@JsonIgnore` en password |
| **BACKEND** | `Proyecto.java` | Añadir campo `creadoEn` + `@PrePersist` |
| **BACKEND** | `ProyectoDTO.java` | Añadir campo `creadoEn` |
| **BACKEND** | `ConvocatoriaPublicaDTO.java` | Añadir campo `tipo` |
| **BACKEND** | `Perfil.java` | Añadir campo `nombre` |
| **BACKEND** | `PerfilDTO.java` | Añadir campo `nombre` |
| **BACKEND** | `PerfilController.java` | Añadir `GET /completo` |
| **BACKEND** | `RecomendacionDTO.java` | Añadir `organismo`, `presupuesto`, `fechaPublicacion` |

---

## Apéndice A — Checklist de Implementación por Fase

### Fase 0: Preparación (1-2 días)
- [ ] Crear `src/lib/motion.ts` completo
- [ ] Crear `src/hooks/useMotionVariants.ts`
- [ ] Crear `src/styles/tokens.ts`
- [ ] Aplicar nueva paleta en `globals.css`
- [ ] Cambiar Fraunces → Manrope en `layout.tsx`
- [ ] Actualizar `template.tsx` con AnimatePresence
- [ ] Crear archivos `src/lib/types/*`

### Fase 1: Backend Blocking (1-2 días, paralelo a Fase 0)
- [ ] `@JsonIgnore` en `Usuario.password`
- [ ] `creadoEn` en `Proyecto` + `ProyectoDTO` + mapper
- [ ] `tipo` en `ConvocatoriaPublicaDTO` + mapper
- [ ] Fix tipo `topRecomendaciones` en `dashboard/page.tsx`

### Fase 2: Componentes Base (2-3 días)
- [ ] Rediseñar `Card.tsx` (sin border, shadow, glass variant)
- [ ] Rediseñar `Button.tsx` (motion, gradiente)
- [ ] Rediseñar `Skeleton.tsx` (Framer Motion shimmer)
- [ ] Rediseñar `Input.tsx` (focus animation)
- [ ] Crear `Modal.tsx` reutilizable
- [ ] Crear `ErrorState.tsx`
- [ ] Rediseñar `Navbar.tsx` (sidebar + topbar)

### Fase 3: Páginas de Usuario (3-5 días)
- [ ] `dashboard/page.tsx` — bento grid + stagger + counters
- [ ] `proyectos/page.tsx` — grid animado
- [ ] `proyectos/[id]/recomendaciones/page.tsx` — SSE log animado, eliminar verde
- [ ] `perfil/page.tsx` — tabs + modales extraídos

### Fase 4: Backend Phase 2 + Páginas Admin (2-3 días)
- [ ] `nombre` en Perfil + PerfilDTO
- [ ] Endpoint `GET /api/usuario/perfil/completo`
- [ ] `organismo`, `presupuesto`, `fechaPublicacion` en RecomendacionDTO
- [ ] `admin/dashboard/page.tsx`
- [ ] `admin/usuarios/page.tsx`
- [ ] `admin/bdns/page.tsx`

### Fase 5: Polish + QA (1-2 días)
- [ ] Verificar contraste WCAG AA en todas las combinaciones
- [ ] Test `prefers-reduced-motion`
- [ ] Auditoría de ARIA labels
- [ ] Búsqueda de verde residual: `grep -rn "text-green\|bg-green\|#16a34a\|#22c55e" src/`
- [ ] Test en mobile (Navbar mobile drawer)
- [ ] Performance: verificar `will-change` solo donde necesario

---

## Apéndice B — Notas sobre Glassmorphism

El DESIGN.md especifica glassmorphism para "elementos flotantes o destacados":
```css
/* Implementación para el panel de detalle de oportunidad */
background: rgba(224, 227, 229, 0.7);  /* surface-variant al 70% */
backdrop-filter: blur(16px);
-webkit-backdrop-filter: blur(16px);
box-shadow: 0 8px 32px rgba(0, 90, 113, 0.12), inset 0 1px 0 rgba(255,255,255,0.5);
```

En Tailwind v4 con CSS variables:
```typescript
className="bg-surface-container/70 backdrop-blur-xl"
// más: shadow-[0_8px_32px_rgba(0,90,113,0.12)] inset-ring inset-ring-white/50
```

El glassmorphism se usa **solo** en:
1. Panel lateral de detalle de recomendación seleccionada
2. ConsejoBanner del dashboard
3. ActionPanel de búsqueda BDNS (sutil)

No usar en cards regulares de lista.
