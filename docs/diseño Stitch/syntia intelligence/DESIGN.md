# Manifiesto de Diseño: La Inteligencia Elevada

Este sistema de diseño ha sido concebido para transformar la complejidad de los datos de licitaciones y subvenciones en una experiencia editorial de alta gama. No se trata simplemente de una herramienta SaaS; es un entorno de toma de decisiones estratégicas que respira autoridad, precisión y vanguardia tecnológica.

## 1. El Norte Creativo: "El Estratega Lúcido"

Nuestra estrella polar es **"El Estratega Lúcido"**. El diseño debe alejarse de la estética genérica de los cuadros de mando industriales para abrazar una narrativa visual que combine la claridad del brutalismo suave con la sofisticación de una publicación financiera premium.

**Principios de Composición:**
- **Asimetría Intencional:** Evita las rejillas monótonas. Utiliza anchos de columna variados para guiar el ojo hacia los puntos de datos clave (insights) por encima de la navegación secundaria.
- **Espacio Negativo Activo:** El "aire" no es desperdicio; es lujo y claridad mental. Las secciones deben respirar con márgenes generosos que separen visualmente los flujos de trabajo críticos.
- **Jerarquía Editorial:** Tratamos la información de las licitaciones como noticias de última hora de alta prioridad, utilizando escalas tipográficas dramáticas.

---

## 2. Paleta Cromática y Profundidad Tonal

La paleta no solo comunica marca, comunica estado y jerarquía. Utilizamos una base de azules profundos y teales tecnológicos para asentar la confianza, con acentos vibrantes para la acción.

### La Regla del "No-Line" (Sin Bordes)
**Queda estrictamente prohibido el uso de bordes sólidos de 1px para seccionar la interfaz.** La delimitación de áreas debe lograrse exclusivamente mediante:
1. **Cambios de tono de fondo:** Un contenedor `surface-container-low` sobre un fondo `surface`.
2. **Transiciones tonales sutiles:** Utilizando la escala de superficies para crear profundidad.

### Jerarquía de Superficies y Anidación
Tratamos la UI como capas de cristal esmerilado o papel de alta calidad:
- **Nivel Base:** `surface` (#f7f9fb) para el fondo general.
- **Nivel de Contenido:** `surface-container-lowest` (#ffffff) para tarjetas que requieren máxima atención.
- **Nivel de Agrupación:** `surface-container` (#eceef0) para secciones que contienen múltiples sub-elementos.

### Texturas de Firma: "Glass & Gradient"
Para elementos flotantes o destacados (como el panel lateral de una licitación seleccionada), aplica **Glassmorphism**:
- Fondo: `surface_variant` con opacidad al 70%.
- Efecto: `backdrop-blur` de 12px a 20px.
- **Gradientes de Alma:** Los CTAs principales deben usar un gradiente lineal sutil desde `primary` (#005a71) hacia `primary_container` (#0e7490) para evitar la planitud y añadir "pulso" digital.

---

## 3. Tipografía: Autoridad en la Lectura

Combinamos **Manrope** para la expresividad y **Inter** para la funcionalidad.

| Rol | Token | Fuente | Tamaño | Peso | Propósito |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Display** | `display-lg` | Manrope | 3.5rem | 700 | Grandes cifras de presupuesto. |
| **Headline** | `headline-md` | Manrope | 1.75rem | 600 | Títulos de licitaciones. |
| **Title** | `title-md` | Inter | 1.125rem | 500 | Encabezados de tarjetas y modales. |
| **Body** | `body-md` | Inter | 0.875rem | 400 | Lectura densa de pliegos técnicos. |
| **Label** | `label-md` | Inter | 0.75rem | 600 | Metadatos y etiquetas de estado. |

---

## 4. Elevación y Capas

La profundidad en este sistema es atmosférica, no estructural.

- **Principio de Apilamiento:** En lugar de sombras, coloca una tarjeta `surface-container-lowest` sobre una sección `surface-container-low`. La diferencia de contraste crea un "salto" visual natural.
- **Sombras Ambientales:** Si un elemento debe flotar (ej. un menú flotante), usa sombras extra-difusas: `box-shadow: 0 10px 40px rgba(25, 28, 30, 0.06)`. El color de la sombra debe ser un tinte de `on-surface`, nunca gris puro.
- **Ghost Border (El último recurso):** Si la accesibilidad requiere un borde, usa `outline-variant` (#bec8cd) con una opacidad del 15%. Nunca debe ser una línea sólida y opaca.

---

## 5. Componentes de Firma

### Botones (Buttons)
- **Primary:** Sin bordes. Gradiente sutil de `primary` a `primary_container`. Radio de `xl` (1.5rem) para un tacto orgánico.
- **Tertiary:** Solo texto en `primary`. En hover, añadir un fondo `primary_fixed` con 30% de opacidad.

### Tarjetas de Licitación (Cards)
- **Prohibición de Divisores:** No uses líneas para separar el título de la descripción. Usa un salto de 1.5rem (escala de espaciado) o un cambio de color de fuente entre `on-surface` y `on-surface-variant`.
- **Bordes:** Siempre `rounded-2xl` (1rem) para transmitir modernidad y suavidad.

### Chips de Estado (Smart Tags)
- **Éxito (Adjudicada):** Fondo `tertiary_container`, texto `on_tertiary_fixed_variant`.
- **Urgencia (Cierre próximo):** Fondo `error_container`, texto `on_error_container`.
- **Forma:** Píldora completa (`rounded-full`).

### Campos de Entrada (Input Fields)
- **Estado Reposo:** Fondo `surface-container-highest`, sin borde visible.
- **Estado Foco:** Un "Ghost Border" sutil de `primary` al 40% y un ligero aumento de la sombra ambiental.

---

## 6. Do's & Don'ts (Lo que se debe y no se debe hacer)

### SÍ (Do)
- **Usa el color para indicar datos, no solo decoración.** El verde `tertiary` solo aparece cuando hay una ganancia o éxito real.
- **Agrupa por proximidad.** Usa el espacio en blanco para conectar visualmente un título con su gráfico correspondiente.
- **Prioriza la legibilidad.** En tablas de datos complejos, usa `body-sm` con un interlineado generoso (leading) para evitar la fatiga visual.

### NO (Don't)
- **No uses sombras pesadas.** Si la sombra se nota a primera vista, es demasiado opaca.
- **No uses iconos multicolores.** Todos los iconos deben ser minimalistas, de trazo fino (out-lined) y usar el color `on-surface-variant` o `primary`.
- **No satures la pantalla de botones.** En una vista de dashboard, solo debe haber un CTA primario claro; el resto deben ser secundarios o terciarios.

---

### Nota del Director
*Recordad: No estamos construyendo una base de datos, estamos diseñando la ventaja competitiva de nuestros usuarios. Cada píxel debe respirar precisión técnica y elegancia ejecutiva.*