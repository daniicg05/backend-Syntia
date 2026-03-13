# Documento de Requisitos del Proyecto: Syntia

## 1. Título del Proyecto
Syntia – Plataforma de Recomendación de Subvenciones

## 2. Contexto y Justificación
El acceso a subvenciones, ayudas, licitaciones y sistemas de financiación, tanto nacionales como europeos, es un proceso complejo y fragmentado. Emprendedores, autónomos y pequeñas empresas se enfrentan a información dispersa, requisitos legales variados y dificultades para identificar oportunidades que se ajusten realmente a su perfil y proyecto. La búsqueda tradicional implica recorrer múltiples portales oficiales, interpretar normativas y gestionar formularios, lo que puede resultar lento, confuso y poco eficiente.

Syntia propone una solución innovadora para transformar este proceso. La plataforma permitirá que el usuario describa su situación personal, profesional y los objetivos de su proyecto, mientras un motor de inteligencia artificial analiza esta información y realiza un matching inteligente con las oportunidades disponibles. De esta manera, un flujo complejo y técnico se convierte en un proceso claro, visual e interactivo, brindando recomendaciones priorizadas, explicadas y estructuradas.

El objetivo no es replicar portales oficiales, sino facilitar decisiones estratégicas y mejorar la eficiencia en la identificación de oportunidades aplicables.

## 3. Objeto del Proyecto
El reto consiste en diseñar y desarrollar una plataforma web que permita a los usuarios recibir recomendaciones personalizadas sobre subvenciones, ayudas, licitaciones y sistemas de financiación. El foco inicial estará en oportunidades públicas y programas europeos, aunque la arquitectura deberá permitir la integración futura de nuevas fuentes privadas o especializadas.

Syntia capturará la información del usuario mediante campos estructurados y descripciones en lenguaje natural. Esto permitirá al motor de IA interpretar tanto datos concretos como matices cualitativos del perfil del usuario y de su proyecto. La plataforma generará un listado de oportunidades compatibles, priorizadas según adecuación, relevancia y potencial impacto, mostrando un roadmap interactivo que facilite la planificación de solicitudes.

## 4. Alcance Funcional
El producto mínimo viable incluirá un sistema de registro y autenticación seguro, con roles diferenciados para administradores y usuarios finales. Los usuarios podrán describir su proyecto y situación personal, profesional o empresarial, incluyendo objetivos, sector de actividad y necesidades de financiación o contratación.

La plataforma interpretará esta información para generar recomendaciones inteligentes basadas en datos públicos oficiales. El motor de inteligencia artificial filtrará y priorizará las oportunidades según su compatibilidad con el perfil del usuario. Cada recomendación incluirá un resumen claro de los requisitos y enlaces a las fuentes oficiales para que el usuario pueda verificar la información.

Los resultados se presentarán en un dashboard interactivo que permitirá filtrar por tipo de oportunidad, sector o ubicación, y visualizar un roadmap estratégico con las acciones recomendadas.

Syntia también incluirá un panel administrativo que permitirá supervisar el funcionamiento del sistema, gestionar usuarios y configurar parámetros del motor de IA, así como monitorizar la calidad de las recomendaciones generadas.

## 5. Requisitos Técnicos
La arquitectura de Syntia garantizará escalabilidad, modularidad y seguridad. El frontend ofrecerá una experiencia de usuario profesional, intuitiva y completamente responsive, priorizando claridad visual y facilidad de navegación en todos los dispositivos.

El backend gestionará perfiles, proyectos y resultados del motor de IA, asegurando integridad y confidencialidad de los datos. El motor de inteligencia artificial interpretará la información del usuario, identificará variables clave y realizará el matching con oportunidades públicas y sistemas de financiación, generando explicaciones comprensibles sobre la relevancia de cada recomendación.

Se implementarán mecanismos de seguridad como autenticación robusta, cifrado de datos y control de permisos por rol, así como validaciones de información para prevenir accesos no autorizados.

La plataforma incluirá un aviso legal indicando que las recomendaciones se basan en información pública y que corresponde al usuario verificar los requisitos oficiales antes de presentar solicitudes.

El despliegue contemplará un entorno de desarrollo documentado y un entorno de producción accesible públicamente para demostraciones, con instrucciones claras de instalación y uso.

## 6. Requisitos Funcionales Detallados

### 6.1. Sistema de Registro y Autenticación
- Registro de nuevos usuarios.
- Inicio y cierre de sesión.
- Gestión segura de credenciales.
- Diferenciación de roles: Usuario final / Administrador.

### 6.2. Captura y Gestión del Perfil del Usuario
- Información estructurada: sector, ubicación, tipo de entidad, objetivos, necesidades de financiación.
- Descripción libre en lenguaje natural.
- **Exclusiones:** subida de documentación, integración con certificados digitales, firma electrónica, gestión documental avanzada.

### 6.3. Integración con Fuentes Oficiales (BDNS y programas europeos)
- Recuperación de convocatorias públicas y generación de enlaces directos.
- Almacenamiento mínimo de metadatos y enlaces, sin replicar toda la base de datos.

### 6.4. Motor de Interpretación y Matching (IA)
- Interpretación de información textual y estructurada del perfil.
- Identificación de variables relevantes para el matching.
- Filtrado y priorización de convocatorias compatibles.
- Generación de explicación comprensible de cada recomendación.
- **Exclusiones:** aprendizaje automático avanzado, IA autónoma, modelos estadísticos complejos.

### 6.5. Dashboard Interactivo y Roadmap Estratégico
- Visualización de oportunidades priorizadas.
- Filtrado por tipo de oportunidad, sector o ubicación.
- Roadmap estratégico con acciones recomendadas.
- **Exclusiones:** comparativas avanzadas, análisis predictivos, exportación automática de informes, alertas automáticas.

### 6.6. Panel Administrativo
- Gestión básica de usuarios.
- Supervisión del sistema y parámetros del motor de IA.
- Monitorización de la calidad de las recomendaciones.
- **Exclusiones:** inteligencia empresarial avanzada, análisis estadístico detallado.

## 7. Requisitos No Funcionales
- **Seguridad:** autenticación robusta, control de acceso por roles, cifrado de datos, HTTPS, validación de entradas.
- **Usabilidad:** interfaz intuitiva, profesional y completamente responsive en todos los dispositivos.
- **Escalabilidad:** arquitectura modular que permita integrar nuevas fuentes de datos en el futuro.
- **Legal:** aviso legal sobre el carácter orientativo de las recomendaciones.

## 8. Riesgos y Consideraciones Críticas
- **Técnico:** garantizar que el motor de IA interprete correctamente perfiles diversos y genere recomendaciones precisas y relevantes.
- **Legal:** considerar la responsabilidad sobre la interpretación de datos públicos y la asesoría automatizada.
- **UX:** evitar interfaces saturadas que dificulten la comprensión del roadmap y la priorización de oportunidades.

## 9. Mejoras y Extensiones Futuras (Valor Añadido)
- Incorporación de nuevas fuentes de datos (privadas o especializadas).
- Estimación automática de probabilidad de éxito según perfil.
- Generación de informes descargables.
- Alertas automáticas sobre nuevas oportunidades compatibles.
- Comparativas entre regiones, sectores o tipos de financiación.
- Simuladores estratégicos para planificación de recursos públicos.

## 10. Seguimiento del Proyecto
El desarrollo incluirá reuniones periódicas de seguimiento en las que se revisará:
- Registro de usuarios y correcta captura del perfil.
- Funcionamiento del motor de IA y calidad de los resultados.
- Estabilidad general de la plataforma.
- Coherencia del dashboard y del roadmap interactivo.

## 11. Restricciones
- No se incluye presentación automática de solicitudes ni integración con sedes electrónicas.
- No se consideran sistemas predictivos ni alertas automáticas en el MVP.

## 12. Criterio de Finalización del MVP
- Registro y autenticación funcional con diferenciación de roles.
- Captura de perfil de usuario y proyecto (estructurada y en lenguaje natural).
- Recomendaciones priorizadas disponibles con explicaciones.
- Visualización de roadmap estratégico en dashboard interactivo.
- Panel administrativo operativo.
- Entorno de producción accesible con instrucciones de despliegue.

## 13. Conclusión
Syntia redefine el acceso a oportunidades públicas mediante el uso de inteligencia artificial, datos oficiales y visualización interactiva. La plataforma permite que usuarios con perfiles diversos comprendan, prioricen y planifiquen la gestión de subvenciones, licitaciones y sistemas de financiación de manera clara y eficiente. Más allá de su carácter formativo, Syntia tiene potencial real de evolucionar hacia un producto profesional, escalable y aplicable en el ecosistema emprendedor y empresarial, ofreciendo un soporte tangible y estratégico para la toma de decisiones informadas.

## Alineación Arquitectónica Vigente (2026-03-13)

> Esta sección prevalece sobre referencias históricas del documento cuando exista conflicto.

- **Backend obligatorio:** `Java 17 + Spring Boot + Maven + PostgreSQL + JWT + SSE`.
- **Frontend objetivo:** `Angular` consumiendo `API REST`.
- **Thymeleaf / SSR:** considerado **legado temporal** durante transición, no objetivo de evolución.
- **Prioridad de exposición funcional:** endpoints en `controller/api/`.
- **Seguridad objetivo:** JWT para API, CORS y flujo stateless.
- **Motor funcional clave:** flujo `BDNS + IA` mantenido en servicios.
- **Regla de arquitectura:** la lógica de negocio permanece en `service/`; los cambios se limitan a capa de presentación.
