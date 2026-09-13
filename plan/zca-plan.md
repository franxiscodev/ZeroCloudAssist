# Plan de Trabajo por Etapas: Aplicación Asistente Técnico Offline

Este plan establece una estrategia de desarrollo incremental. La **Fase 1 (MVP)** entrega un producto funcional para pruebas inmediatas y demostraciones en vivo. Las fases posteriores agregan valor operativo e integración industrial.

---

## Fase 1: MVP Funcional Núcleo (Prueba de Concepto y Demo en Vivo)
**Objetivo:** Disponer de una app plenamente operativa en el iPhone 17 Pro que responda dudas sobre el manual del ABB ACS355 en modo avión.

* **Etapa 1.1: Preprocesamiento de Datos (Ordenador)**
  * Creación de script en Python (`pdfplumber` + `sentence-transformers`).
  * Troceado (chunking) del manual PDF del ABB ACS355 (440 páginas) en bloques de 300 palabras con solapamiento.
  * Generación de embeddings con el modelo ligero `bge-small-en-v1.5`.
  * Exportación de la base de datos vectorial unificada en formato `manual_acs355.sqlite` (~8 MB).

* **Etapa 1.2: Configuración del Motor LLM Local (iOS)**
  * Integración de `llama.cpp` en Xcode usando aceleración Metal.
  * Empaquetado del modelo `Qwen2.5-3B-Instruct-Q4_K_M.gguf` (~2.0 GB) en el Bundle de la app.
  * Ajuste de la directiva de la app (`increased-memory-limit`).

* **Etapa 1.3: Interfaz de Usuario y RAG Básico (SwiftUI)**
  * Pantalla de selección de equipo (inicialmente ABB ACS355).
  * Interfaz de chat fluido con soporte de streaming de texto.
  * Inyección automática del contexto RAG relevante a partir de las consultas en español.

---

## Fase 2: Robustez Industrial, Seguridad y Citas (Versión 2.0)
**Objetivo:** Dar máxima credibilidad al contenido y proteger al operario en planta.

* **Etapa 2.1: Búsqueda Híbrida (FTS5 + Vectores)**
  * Implementación de búsqueda por palabra exacta en SQLite (para localizar alfanuméricos como `F0002`, `2203`) combinada con similitud vectorial semántica.

* **Etapa 2.2: Visor PDF con Cita Directa**
  * Integración del framework nativo `PDFKit`.
  * Generación de enlaces al final de la respuesta (*"[Fuente: Pág. 142]"*). Al tocar el enlace, la app abre el PDF original directamente en la página de referencia.

* **Etapa 2.3: Módulo de Prevención y Seguridad (Safety Gatekeeper)**
  * Implementación de checklists mandatorios interactivos de EPIs y corte de energía (LOTO) previa a la visualización de pasos de desarmado o pruebas con tensión.

* **Etapa 2.4: Arquitectura Multimanual**
  * Habilitación del selector dinámico para alternar entre diferentes manuales (ej. Soldadora Fronius, Inversor Huawei, Variador ABB) guardados en el almacenamiento local.

---

## Fase 3: Ergonomía en Campo y Automatización (Versión 3.0)
**Objetivo:** Reducir la fricción física del operario e integrar la app con la gestión de la empresa.

* **Etapa 3.1: Reconocimiento Óptico de Caracteres (OCR Nativo)**
  * Integración de `Vision.framework` de iOS.
  * Permitir al técnico tomar una foto a la pantalla del variador o a la placa de características para extraer códigos de error o datos nominales automáticamente.

* **Etapa 3.2: Generación de Bitácoras de Trabajo (Informes)**
  * Función de cierre de sesión donde la IA resume la falla diagnosticada, pruebas realizadas y repuestos sugeridos.
  * Exportación del informe en PDF local y JSON estructurado listo para sincronizar con el ERP/CRM cuando el móvil recupere red.

* **Etapa 3.3: Entrada por Voz Offline (Hands-Free)**
  * Integración de reconocimiento de voz nativo en iOS para permitir dictado en ambientes de taller sin necesidad de usar el teclado.