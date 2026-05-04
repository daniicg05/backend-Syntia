# Estudio: Boton "Analizar con IA" - Rediseno completo

## 1. Estado actual y problemas detectados

### Que hace ahora el boton (endpoint `GET /api/usuario/convocatorias/{id}/analisis`)

```
Usuario pulsa "Analizar con IA"
  -> Backend carga Convocatoria de BD local (20 campos basicos)
  -> Si tiene numeroConvocatoria: llama a BDNS API obtenerDetalleTexto()
  -> Llamada 1 OpenAI: analizarConvocatoria() -> explicacion + guia 8 pasos (pipe-separated)
  -> Llamada 2 OpenAI: generarGuiaSinProyecto() -> GuiaSubvencionDTO (galeria visual)
  -> Devuelve { explicacion, guia, guiaCompleta }
```

### Problemas criticos

| Problema | Impacto |
|----------|---------|
| **No usa el perfil del usuario** | `generarGuiaSinProyecto(null, ...)` pasa `perfil=null`. La guia es generica, no personalizada. |
| **No usa proyectos del usuario** | No sabe que proyecto quiere financiar el usuario. No puede evaluar compatibilidad. |
| **No lee los PDFs de la convocatoria** | Los `documentos` del BDNS (bases reguladoras en PDF) contienen el 90% de la informacion real: requisitos exactos, criterios de valoracion, formularios, importes maximos. Actualmente se ignoran por completo. |
| **Texto BDNS truncado** | `detalleTexto` se trunca a 1500 chars (matching) / 3000 chars (guia). Una convocatoria real tiene 5.000-50.000 chars de texto oficial. Se pierde informacion critica. |
| **No usa datos de catalogo indexados** | `tiposBeneficiario`, `finalidades`, `instrumentos`, `actividades`, `reglamentos`, `objetivos`, `sectoresProducto` estan en BD pero no se pasan al prompt. |
| **No usa el detalle live enriquecido** | `ConvocatoriaDetalleDTO` tiene 65+ campos (organo jerarquico, urlBasesReguladoras, sedeElectronica, anuncios, fondos UE, etc.) que no se aprovechan. |
| **Prompt de matching reutilizado para analisis** | `analizarConvocatoria()` usa el SYSTEM_PROMPT de scoring (0-100), que no tiene sentido sin proyecto. El score siempre sera generico. |
| **Dos llamadas AI secuenciales** | Una para `explicacion+guia` y otra para `guiaCompleta`. Podria ser una sola llamada mas potente o paralela. |

---

## 2. Objetivo del nuevo "Analizar con IA"

> **Mision:** Que el usuario reciba TODO lo que necesita saber para decidir si solicitar la subvencion y, si decide hacerlo, una guia paso a paso completa que le lleve desde cero hasta la presentacion exitosa de la solicitud.

### Que debe conseguir el boton:

1. **Leer y comprender la convocatoria completa** (incluyendo PDFs de bases reguladoras)
2. **Personalizar el analisis** al perfil y proyectos del usuario
3. **Resumir lo esencial** de forma clara y accionable
4. **Generar una guia paso a paso** como galeria interactiva
5. **Evaluar compatibilidad real** del usuario con la convocatoria

---

## 3. Datos que debe recopilar el backend ANTES de llamar a la IA

### 3.1 Datos de la convocatoria (ya disponibles)

```
CAPA 1: BD Local (Convocatoria entity)
  - titulo, tipo, sector, ubicacion, organismo
  - descripcion, textoCompleto, finalidad
  - fechaCierre, fechaPublicacion, fechaInicio
  - presupuesto, abierto, mrr
  - urlOficial, idBdns, numeroConvocatoria
  - regionId, provinciaId

CAPA 2: Catalogos indexados (tablas idx_convocatoria_*)
  - tiposBeneficiario: ["Microempresas", "Pequenas empresas", ...]
  - finalidades: ["Innovacion tecnologica", ...]
  - instrumentos: ["Subvencion a fondo perdido", "Prestamo bonificado", ...]
  - actividades: ["Investigacion y desarrollo", ...]
  - reglamentos: ["Reglamento UE 651/2014", ...]
  - objetivos: ["Fomento del empleo", ...]
  - sectoresProducto: ["TIC", "Agroalimentario", ...]
  - organos: ["Ministerio de Industria", ...]
  - regiones: ["Comunidad de Madrid", ...]
  - tipoAdmin: "Estatal" / "Autonomica" / "Local"

CAPA 3: BDNS API Live (obtenerDetalleLive)
  - organo: {nivel1, nivel2, nivel3} (jerarquia completa)
  - tipoConvocatoria: "Concurrencia competitiva" / "Concesion directa"
  - descripcionBasesReguladoras + urlBasesReguladoras
  - fechaInicioSolicitud, fechaFinSolicitud (plazos reales)
  - textInicio, textFin (descripcion de plazos)
  - sePublicaDiarioOficial (boolean)
  - ayudaEstado, urlAyudaEstado
  - sedeElectronica (URL de la sede para tramitar)
  - reglamento, fechaRecepcion
  - anuncios: [texto HTML completo del BOE/boletin]
  - fondos: ["FEDER", "FSE+", ...]
  - documentos: [{id, descripcion, nombreFic, tamanio, fechaPublicacion}]
```

### 3.2 PDFs de la convocatoria (NUEVO - no se usa actualmente)

```
CAPA 4: Documentos PDF (descargar y extraer texto)
  - Bases reguladoras (el documento mas importante)
  - Extracto BOE/boletin oficial
  - Formularios de solicitud
  - Anexos tecnicos
  - Memorias tipo

  Cada PDF tiene: id, descripcion, nombreFic, tamanio
  Se descargan de la API BDNS y se extrae el texto con Apache PDFBox o similar.

  CONTENIDO TIPICO de las bases reguladoras:
  - Objeto y finalidad de la subvencion
  - Beneficiarios y requisitos de elegibilidad (detallados)
  - Cuantia individual maxima y criterios de modulacion
  - Criterios de valoracion con pesos (ej: viabilidad 30%, innovacion 25%, empleo 20%, igualdad 15%, sostenibilidad 10%)
  - Gastos subvencionables y no subvencionables
  - Plazo y forma de presentacion
  - Documentacion a aportar (lista exhaustiva)
  - Organo instructor y resolucion
  - Obligaciones del beneficiario
  - Justificacion: tipo, plazo, documentos
  - Regimen de compatibilidad con otras ayudas
  - Revocacion y reintegro
```

### 3.3 Datos del usuario (ya disponibles pero no usados)

```
PERFIL DEL USUARIO (Perfil entity):
  - nombre, empresa
  - tipoEntidad: "PYME", "Autonomo", "Startup", "Gran empresa", "Asociacion"...
  - sector, ubicacion, provincia
  - objetivos (texto libre)
  - necesidadesFinanciacion (texto libre)
  - descripcionLibre (texto libre)

PROYECTOS DEL USUARIO (Proyecto entities):
  - nombre, sector, ubicacion, descripcion
  (El usuario puede tener varios proyectos; idealmente se pregunta
   para cual proyecto quiere analizar la convocatoria)
```

---

## 4. Arquitectura propuesta del nuevo flujo

### 4.1 Flujo completo

```
PASO 1: Usuario pulsa "Analizar con IA" en pagina de detalle de convocatoria
         (opcionalmente selecciona proyecto, o se usa el mas relevante)
                |
PASO 2: Backend recopila TODOS los datos en paralelo:
         |
         |-- [Thread 1] Cargar Convocatoria + catalogos indexados de BD
         |-- [Thread 2] Fetch BDNS live detail (obtenerDetalleLive)
         |-- [Thread 3] Descargar PDFs de documentos BDNS + extraer texto
         |-- [Thread 4] Cargar Perfil + Proyectos del usuario
         |
PASO 3: Construir contexto unificado (ConvocatoriaContexto)
         Fusionar todas las capas en un unico bloque de texto estructurado
         con prioridad: PDF > anuncio BDNS > detalle live > BD local
                |
PASO 4: Llamada IA con prompt mejorado
         System prompt especializado en analisis completo
         User prompt con TODO el contexto recopilado
         Respuesta: AnalisisCompletoDTO (nuevo DTO enriquecido)
                |
PASO 5: Devolver al frontend como galeria interactiva de slides
```

### 4.2 Nuevo endpoint propuesto

```java
// Reemplaza el actual GET /{id}/analisis
GET /api/usuario/convocatorias/{id}/analisis?proyectoId={optional}

// Parametros:
//   id: ID de la convocatoria (obligatorio)
//   proyectoId: ID del proyecto del usuario (opcional, si no se pasa
//               se usa el mas afin por sector o el primero)

// Respuesta: AnalisisCompletoDTO (ver seccion 5)
```

### 4.3 Servicio de extraccion de PDFs (nuevo)

```java
@Service
public class PdfExtractionService {

    /**
     * Descarga un documento PDF de BDNS y extrae su texto.
     * Usa Apache PDFBox para la extraccion.
     * Cache en memoria con TTL de 1 hora (igual que detalleTexto).
     * Limite: solo procesa PDFs < 10MB.
     * Trunca el texto extraido a MAX_PDF_CHARS (15000) para el prompt.
     */
    public String extraerTexto(Long documentoId, String numeroConvocatoria) { ... }

    /**
     * Descarga y extrae texto de TODOS los documentos de una convocatoria.
     * Prioriza: bases reguladoras > extracto > formularios > otros
     * Retorna texto concatenado con separadores y etiquetas.
     */
    public String extraerTodosLosDocumentos(
        List<DocumentoBdnsDTO> documentos,
        String numeroConvocatoria) { ... }
}
```

---

## 5. Estructura de la respuesta: AnalisisCompletoDTO

### 5.1 Estructura de slides para la galeria

La respuesta se estructura como **slides de una galeria** que el frontend renderiza como un carrusel interactivo. Cada slide es una "tarjeta" con informacion especifica.

```
SLIDE 0: RESUMEN EJECUTIVO
  - Titulo de la convocatoria
  - Organismo convocante (jerarquia completa)
  - Que financia (objetivo en 2-3 frases claras)
  - Presupuesto total y cuantia maxima individual
  - Tipo de ayuda: subvencion, prestamo, mixta
  - Estado: abierta/cerrada + dias restantes
  - Compatibilidad con tu perfil: ALTA / MEDIA / BAJA + explicacion

SLIDE 1: QUIEN PUEDE SOLICITAR (Elegibilidad)
  - Lista de tipos de beneficiario admitidos
  - Requisitos especificos: tamano empresa, antiguedad, sector, ubicacion
  - Requisitos universales LGS art.13
  - EVALUACION PERSONALIZADA: "Tu perfil como [tipoEntidad] en [sector]
    ubicado en [ubicacion] [CUMPLE / NO CUMPLE / VERIFICAR] los requisitos"
  - Que verificar si hay dudas

SLIDE 2: QUE SE PUEDE FINANCIAR (Gastos subvencionables)
  - Lista de gastos cubiertos (personal, equipamiento, servicios, viajes...)
  - Lista de gastos NO cubiertos (IVA recuperable, gastos previos, etc.)
  - Cuantia maxima individual y porcentaje maximo de cofinanciacion
  - PERSONALIZADO: "Para tu proyecto [nombre], los gastos principales
    que podrias incluir son: [lista relevante]"

SLIDE 3: CRITERIOS DE VALORACION (solo si concurrencia competitiva)
  - Tabla con criterio + peso (%) + descripcion
  - Puntuacion minima para aprobar (si existe)
  - CONSEJO: "Con tu perfil, tendrias ventaja en los criterios de
    [X, Y] y deberias reforzar [Z]"

SLIDE 4: DOCUMENTACION NECESARIA
  - Documentos OBLIGATORIOS con descripcion de cada uno:
    * Certificados (AEAT, TGSS, registro mercantil...)
    * Formularios oficiales (donde descargarlos)
    * Memoria tecnica/proyecto (que debe incluir)
    * Presupuesto desglosado (formato requerido)
    * Declaraciones responsables
  - Documentos OPCIONALES que suman puntos
  - LINKS: URL de descarga de cada formulario cuando exista

SLIDE 5: COMO PRESENTAR LA SOLICITUD (Procedimiento)
  - Metodo principal: electronico / presencial
  - Sede electronica especifica (URL real)
  - Sistema de identificacion: certificado digital, Cl@ve, DNIe
  - Pasos de navegacion en la sede (instrucciones clickeables)
  - AutoFirma: si/no y donde descargarlo
  - Registro: SIR, registro electronico del organismo

SLIDE 6: PLAZOS Y CALENDARIO
  - Fecha apertura de solicitudes
  - Fecha limite de presentacion
  - Dias habiles o naturales (y que significa)
  - Plazo de resolucion estimado
  - Fecha maxima de ejecucion del proyecto
  - Plazo de justificacion
  - TIMELINE VISUAL: linea de tiempo con hitos clave

SLIDE 7: DESPUES DE CONSEGUIRLA (Obligaciones)
  - Aceptacion de la subvencion (plazo y forma)
  - Obligaciones durante la ejecucion
  - Comunicacion de incidencias y modificaciones
  - Publicidad y difusion (logo UE si aplica, carteles)
  - Compatibilidad con otras ayudas (minimis, acumulacion)
  - Contabilidad separada

SLIDE 8: JUSTIFICACION DE GASTOS
  - Tipo de cuenta justificativa (simplificada / ordinaria)
  - Plazo para justificar
  - Documentos necesarios: facturas, nominas, TC2, memoria final
  - Indicadores a reportar
  - Errores frecuentes en la justificacion

SLIDE 9: ADVERTENCIAS Y CONSEJOS
  - Errores frecuentes de exclusion
  - Diferencia entre extracto BOE y bases reguladoras
  - Incompatibilidades comunes
  - Consejos practicos de un asesor
  - "Lee SIEMPRE las bases reguladoras completas"
  - Disclaimer legal de Syntia

SLIDE 10: ENLACES Y RECURSOS
  - URL convocatoria oficial BDNS
  - URL bases reguladoras (PDF)
  - URL sede electronica del organismo
  - URL portal BDNS
  - URLs de certificados (AEAT, TGSS, Cl@ve, FNMT)
  - Telefono/email de consultas del organismo (si disponible)
```

### 5.2 DTO Java propuesto

```java
@Getter @Setter @Builder
public class AnalisisCompletoDTO {

    // Metadatos
    private Long convocatoriaId;
    private Long proyectoId;          // null si no se paso proyecto
    private LocalDateTime generadoEn;

    // Compatibilidad personalizada
    private String nivelCompatibilidad; // "ALTA", "MEDIA", "BAJA", "NO_EVALUABLE"
    private int puntuacionEstimada;     // 0-100
    private String explicacionCompatibilidad; // 2-3 frases

    // Slides de la galeria (lista ordenada)
    private List<SlideAnalisis> slides;

    // Recursos y enlaces
    private RecursosDTO recursos;

    // Legal
    private String legalDisclaimer;

    @Getter @Setter @Builder
    public static class SlideAnalisis {
        private int orden;              // 0, 1, 2...
        private String tipo;            // "resumen", "elegibilidad", "gastos", "criterios",
                                        // "documentacion", "procedimiento", "plazos",
                                        // "obligaciones", "justificacion", "advertencias", "recursos"
        private String titulo;          // Titulo del slide
        private String subtitulo;       // Subtitulo contextual
        private String icono;           // Emoji o nombre de icono
        private String fase;            // "antes", "durante", "despues"

        // Contenido principal
        private String textoResumen;    // Parrafo resumen del slide
        private List<ItemAnalisis> items;// Lista de items detallados

        // Personalizacion (solo si hay perfil/proyecto)
        private String consejoPersonalizado; // Consejo basado en el perfil del usuario
        private String alertaPersonalizada;  // Alerta si algo no cuadra
    }

    @Getter @Setter @Builder
    public static class ItemAnalisis {
        private String titulo;
        private String descripcion;
        private String tipo;            // "requisito", "documento", "paso", "criterio",
                                        // "plazo", "obligacion", "consejo", "advertencia"
        private String estado;          // "cumple", "no_cumple", "verificar", null
        private String url;             // URL oficial si aplica
        private Integer peso;           // Peso % (solo para criterios)
        private Integer tiempoEstimadoMinutos; // Tiempo estimado (solo para pasos)
        private List<String> subItems;  // Sub-elementos (ej: documentos dentro de un paso)
    }

    @Getter @Setter @Builder
    public static class RecursosDTO {
        private String urlConvocatoria;
        private String urlBasesReguladoras;
        private String urlSedeElectronica;
        private String urlBdns;
        private List<DocumentoEnlace> documentosOficiales;

        @Getter @Setter @Builder
        public static class DocumentoEnlace {
            private String nombre;
            private String descripcion;
            private String url;
            private Long tamanoBytes;
        }
    }
}
```

---

## 6. Prompt de IA rediseñado

### 6.1 System prompt (nuevo)

El prompt debe ser un experto en subvenciones que analiza con profundidad y personaliza.

```
SYSTEM PROMPT:

Eres un asesor experto en subvenciones publicas espanolas con 20 anos de experiencia.
Tu trabajo es analizar una convocatoria de subvencion y generar un informe completo
y personalizado para que un solicitante pueda decidir si le conviene y, si decide
solicitarla, tenga una guia paso a paso para hacerlo con exito.

FUENTES DE INFORMACION (en orden de prioridad):
1. TEXTO DE BASES REGULADORAS (PDF) - fuente principal, contiene los requisitos reales
2. TEXTO DEL ANUNCIO OFICIAL (BOE/boletin) - complementa las bases
3. DATOS ESTRUCTURADOS BDNS - fechas, organismos, catalogos
4. DATOS LOCALES - informacion basica de la convocatoria

IMPORTANTE:
- Si tienes el texto de las bases reguladoras, USALO como fuente primaria.
  Los requisitos exactos, criterios de valoracion, gastos subvencionables
  y procedimiento estan en las bases, NO en el extracto.
- Si NO tienes bases reguladoras, indica claramente que datos son inferidos
  y cuales son del extracto/anuncio.
- NUNCA inventes requisitos, importes o plazos que no esten en las fuentes.
- Si un dato no esta disponible, di "No especificado en la documentacion
  disponible. Consultar bases reguladoras."
- Las URLs deben ser REALES de portales gubernamentales espanoles.

PERSONALIZACION:
- Si se proporciona perfil del solicitante, evalua compatibilidad real.
- Si se proporciona proyecto, indica que gastos podrian ser subvencionables.
- Usa el tipo de entidad, sector y ubicacion para evaluar elegibilidad.
- Da consejos especificos basados en el perfil ("Como PYME del sector
  tecnologico en Madrid, tendrias ventaja en...").

RESPONDE UNICAMENTE con el JSON del esquema indicado. Sin markdown, sin texto fuera del JSON.

ESQUEMA JSON: { ... } (ver seccion 5.2 adaptada a JSON)
```

### 6.2 User prompt (nuevo - con todo el contexto)

```
User prompt construido por el backend:

=== CONVOCATORIA ===
Titulo: [titulo]
Organismo: [organoNivel1] > [organoNivel2] > [organoNivel3]
Tipo convocatoria: [tipoConvocatoria] (Concurrencia competitiva / Concesion directa)
Ambito geografico: [ubicacion] | Regiones: [lista regiones]
Sector: [sector]
Tipo administracion: [tipoAdmin]
Presupuesto total: [presupuesto] EUR
Abierta: [si/no]
MRR: [si/no]

=== PLAZOS ===
Publicacion: [fechaPublicacion]
Inicio solicitudes: [fechaInicioSolicitud] ([textInicio])
Fin solicitudes: [fechaFinSolicitud] ([textFin])
Cierre: [fechaCierre]

=== CLASIFICACION BDNS ===
Beneficiarios admitidos: [lista tiposBeneficiario]
Finalidades: [lista finalidades]
Instrumentos: [lista instrumentos]
Actividades economicas: [lista actividades]
Reglamentos aplicables: [lista reglamentos]
Objetivos: [lista objetivos]
Sectores producto: [lista sectoresProducto]

=== BASES REGULADORAS ===
Descripcion: [descripcionBasesReguladoras]
URL: [urlBasesReguladoras]

=== SEDE ELECTRONICA ===
URL: [sedeElectronica]
Diario oficial: [sePublicaDiarioOficial]

=== FONDOS ===
[lista fondos: FEDER, FSE+, etc.]

=== AYUDA DE ESTADO ===
[ayudaEstado] | URL: [urlAyudaEstado]

=== TEXTO OFICIAL DE LAS BASES REGULADORAS (PDF) ===
[Texto extraido del PDF de bases reguladoras - hasta 15.000 chars]
(Este es el documento mas importante. Contiene requisitos exactos,
criterios de valoracion, gastos subvencionables, procedimiento completo.)

=== TEXTO DEL ANUNCIO OFICIAL ===
[Texto del anuncio BOE/boletin - hasta 5.000 chars]

=== DESCRIPCION BDNS ===
[textoCompleto o descripcion de la convocatoria]

=== DOCUMENTOS DISPONIBLES ===
[Lista de PDFs disponibles: nombre, descripcion, tamano]

=== PERFIL DEL SOLICITANTE ===
Tipo entidad: [tipoEntidad]
Empresa: [empresa]
Sector: [sector]
Ubicacion: [ubicacion], Provincia: [provincia]
Objetivos: [objetivos]
Necesidades financiacion: [necesidadesFinanciacion]
Descripcion: [descripcionLibre]

=== PROYECTO (si aplica) ===
Nombre: [nombre]
Sector: [sector]
Ubicacion: [ubicacion]
Descripcion: [descripcion]

=== INSTRUCCION ===
Genera el analisis completo de esta convocatoria en formato JSON
siguiendo el esquema del system prompt. Personaliza al perfil del
solicitante si esta disponible. Prioriza la informacion del texto
de bases reguladoras sobre cualquier otra fuente.
```

---

## 7. Mejoras en el frontend (galeria de slides)

### 7.1 Rediseno de la galeria

```
ANTES (GuiaGalleryModal actual):
  - Slide 0: Resumen basico
  - Slides 1-N: Workflow steps (generico)
  - Ultimo: Legal + docs
  - Navegacion: prev/next + dots

DESPUES (AnalisisGallery nuevo):
  - 10 slides tematicos con contenido rico
  - Cada slide tiene seccion personalizada si hay perfil
  - Indicador de progreso con % completado
  - Tabs para saltar entre secciones
  - Badges de estado: "Cumples", "Verificar", "No cumples"
  - Links clicables a portales oficiales
  - Boton "Descargar resumen PDF" al final
  - Modo "timeline" alternativo al carrusel
```

### 7.2 Componentes de UI por slide

```
SlideResumen:
  - Header con titulo + badge estado (Abierta/Cerrada)
  - Metricas: presupuesto, plazo, tipo ayuda (3 cards)
  - Compatibilidad: barra de progreso coloreada + texto
  - CTA: "Ver requisitos" (avanza al slide 1)

SlideElegibilidad:
  - Checklist interactivo con iconos verde/rojo/amarillo
  - Cada requisito: titulo + explicacion + estado personalizado
  - Separador: "Requisitos universales" / "Requisitos especificos"

SlideGastos:
  - Dos columnas: "Subvencionable" vs "No subvencionable"
  - Cuantia maxima destacada
  - Items relevantes del proyecto resaltados

SlideCriterios:
  - Tabla visual con barras de peso (%)
  - Puntuacion minima si existe
  - Consejos personalizados por criterio

SlideDocumentacion:
  - Lista categorizada con iconos por tipo
  - Links de descarga de formularios
  - Estado: "Ya lo tienes" / "Necesitas obtenerlo"

SlideProcedimiento:
  - Stepper vertical con pasos numerados
  - Cada paso: accion + portal + tiempo estimado
  - Links a sedes electronicas reales

SlidePlazos:
  - Timeline horizontal visual
  - Countdown del plazo de presentacion
  - Hitos post-concesion

SlideObligaciones:
  - Lista con iconos de advertencia
  - Separado por fase: ejecucion / justificacion

SlideAdvertencias:
  - Cards de alerta con nivel: info / warning / danger
  - Errores frecuentes destacados

SlideRecursos:
  - Grid de enlaces con iconos
  - Boton de descarga de cada PDF oficial
  - Contacto del organismo
```

---

## 8. Implementacion tecnica - Plan de trabajo

### 8.1 Backend (Spring Boot)

```
PASO 1: PdfExtractionService (NUEVO)
  - Dependencia: Apache PDFBox
  - Descargar PDF desde URL BDNS (construir URL con documentoId)
  - Extraer texto con PDFBox PDFTextStripper
  - Cache en memoria ConcurrentHashMap (TTL 1h)
  - Limite: PDFs < 10MB, texto < 15000 chars
  - Priorizar: bases reguladoras > extracto > formularios

PASO 2: ConvocatoriaContextBuilder (NUEVO)
  - Servicio que recopila y fusiona TODAS las fuentes de datos
  - Ejecuta Thread 1-4 en paralelo con CompletableFuture
  - Devuelve ConvocatoriaContexto con todo el texto estructurado

PASO 3: OpenAiAnalisisService (NUEVO o refactor de OpenAiGuiaService)
  - Nuevo system prompt mas completo
  - User prompt con todo el contexto (ver seccion 6.2)
  - max_tokens: 6000 (respuesta mas rica)
  - Modelo: gpt-4.1
  - Temperature: 0.2
  - Response format: json_object
  - Timeout: 120s (PDFs largos = respuestas grandes)

PASO 4: AnalisisCompletoDTO (NUEVO)
  - Estructura de slides como en seccion 5.2
  - Incluye metadatos, compatibilidad, slides, recursos, legal

PASO 5: Refactor del endpoint analisis
  - GET /api/usuario/convocatorias/{id}/analisis?proyectoId=
  - Carga perfil + proyecto
  - Llama a ConvocatoriaContextBuilder
  - Llama a OpenAiAnalisisService
  - Cache resultado en BD (nueva tabla o campo en convocatoria)
  - Devuelve AnalisisCompletoDTO

PASO 6: Cache inteligente
  - Guardar analisis generado en BD
  - Invalidar si cambia la convocatoria o el perfil del usuario
  - TTL: 7 dias o hasta que cambie algo
```

### 8.2 Frontend (Next.js)

```
PASO 1: AnalisisGallery component (NUEVO)
  - Reemplaza GuiaGalleryModal
  - 10 slides tematicos con contenido rico
  - Navegacion: carrusel + tabs + dots + teclado
  - Responsive: modal en desktop, fullscreen en movil

PASO 2: Slide components (10 componentes)
  - SlideResumen, SlideElegibilidad, SlideGastos, etc.
  - Cada uno con layout y logica propia
  - Badges de estado personalizados

PASO 3: API integration
  - convocatoriasUsuarioApi.analisis(id, proyectoId?)
  - Loading state con skeleton + estimacion de tiempo
  - Error handling con retry

PASO 4: Selector de proyecto (NUEVO)
  - Antes de analizar, mostrar dropdown con proyectos del usuario
  - "Analizar sin proyecto" como opcion
  - Recordar ultima seleccion
```

---

## 9. Consideraciones de coste y rendimiento

### Tokens estimados por analisis

| Componente | Tokens actuales | Tokens propuestos |
|-----------|----------------|-------------------|
| System prompt | ~500 | ~800 |
| User prompt (convocatoria) | 400-1200 | 2000-5000 |
| User prompt (PDF bases) | 0 | 3000-8000 |
| User prompt (perfil+proyecto) | 0-200 | 200-500 |
| **Total input** | **~1700** | **~6000-14000** |
| Respuesta (output) | ~350 + ~4000 | ~5000-6000 |
| **Total por analisis** | **~6000 tokens** | **~12000-20000 tokens** |
| **Coste estimado (gpt-4.1)** | ~$0.02 | ~$0.05-0.08 |

### Optimizaciones propuestas

1. **Una sola llamada AI** en vez de dos (ahorra overhead)
2. **Cache agresiva**: resultado cacheado por (convocatoriaId + perfilHash + proyectoId)
3. **PDF cache**: texto extraido cacheado 24h
4. **Truncado inteligente**: priorizar secciones criticas del PDF (requisitos > procedimiento > justificacion)
5. **Streaming opcional**: para convocatorias con mucho PDF, usar SSE para mostrar slides progresivamente

### Latencia estimada

| Fase | Actual | Propuesto |
|------|--------|-----------|
| Fetch BDNS | 1-3s | 1-3s (paralelo) |
| Download PDF | 0s | 2-5s (paralelo) |
| Extract PDF text | 0s | 1-2s (paralelo) |
| Cargar perfil+proyecto | 0s | <100ms (paralelo) |
| Cargar catalogos | 0s | <100ms (paralelo) |
| OpenAI call 1 | 3-8s | - |
| OpenAI call 2 | 8-15s | - |
| OpenAI call unica | - | 10-20s |
| **Total** | **12-26s** | **13-25s** |

> Nota: el tiempo total es similar o mejor porque eliminamos una llamada AI y paralelizamos la recopilacion de datos.

---

## 10. Resumen de cambios necesarios

### Backend (prioridad alta)

| # | Cambio | Ficheros | Complejidad |
|---|--------|----------|-------------|
| 1 | PdfExtractionService | nuevo servicio | Media |
| 2 | ConvocatoriaContextBuilder | nuevo servicio | Media |
| 3 | AnalisisCompletoDTO + SlideAnalisis | nuevos DTOs | Baja |
| 4 | OpenAiAnalisisService (prompt nuevo) | nuevo servicio | Alta |
| 5 | Refactor endpoint analisis | ConvocatoriaPersonalizadaController | Media |
| 6 | Cache de analisis | nueva tabla o campo | Baja |
| 7 | Dependencia Apache PDFBox | pom.xml | Baja |

### Frontend (prioridad alta)

| # | Cambio | Ficheros | Complejidad |
|---|--------|----------|-------------|
| 1 | AnalisisGallery component | nuevo componente | Alta |
| 2 | 10 Slide components | nuevos componentes | Media |
| 3 | Selector de proyecto | nuevo componente | Baja |
| 4 | API types + calls | api.ts, types | Baja |
| 5 | Pagina detalle: integracion | convocatorias/[id]/page.tsx | Media |

### Dependencias nuevas

```xml
<!-- Backend: pom.xml -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.4</version>
</dependency>
```

---

## 11. Criterios de exito

1. El usuario recibe un analisis que le permite decidir SI/NO solicitar en < 2 minutos de lectura
2. La guia paso a paso es tan detallada que un usuario sin experiencia puede presentar la solicitud
3. Los requisitos extraidos del PDF coinciden al 95%+ con los reales
4. La evaluacion de compatibilidad es correcta en el 90% de los casos
5. El tiempo de respuesta es < 25 segundos
6. El coste por analisis es < $0.10
