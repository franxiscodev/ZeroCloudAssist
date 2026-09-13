# Especificaciones del Proyecto y Entorno Hardware: Samsung Galaxy A53 5G

## 1. Motivos del Cambio de Dispositivo (iPhone 17 Pro ➔ Samsung Galaxy A53 5G)

1. **Desarrollo Directo desde Windows:** Elimina la necesidad estricta de contar con un ordenador Mac o ejecutar complejas máquinas virtuales para compilar y probar la aplicación.
2. **Cero Costes Iniciales ($0):** Permite omitir la suscripción de $99/año a la Apple Developer Account necesaria para instalar aplicaciones locales en un iPhone sin caducidad de 7 días.
3. **Despliegue Rápido por USB:** Permite conectar el dispositivo vía cable USB a la PC con Windows y compilar/instalar en segundos mediante **Android Studio / ADB**.
4. **Reutilización de Hardware:** Aprovecha un terminal disponible en desuso para crear el prototipo funcional (MVP).
5. **Validación Comercial de Alto Impacto:** Demuestra ante clientes potenciales que la solución de IA offline no requiere teléfonos de gama alta premium de $1,200+, sino que funciona fluidamente en dispositivos de gama media estándar de planta o taller.

---

## 2. Características del Samsung Galaxy A53 5G

| Componente | Especificación Técnica | Impacto en el Proyecto |
| :--- | :--- | :--- |
| **SoC / Procesador** | Samsung Exynos 1280 (5 nm, 8 núcleos: 2x2.4 GHz Cortex-A78 + 6x2.0 GHz Cortex-A55) | Soporta instrucciones ARM64 / NEON para ejecuciones optimizadas con `llama.cpp`. |
| **Memoria RAM** | 6 GB u 8 GB LPDDR4X | Permite alojar el LLM local (800 MB - 1.5 GB) y el motor RAG en memoria sin saturar Android. |
| **GPU** | Mali-G68 | Aceleración de tareas de renderizado y cómputo gráfico. |
| **Almacenamiento** | 128 GB / 256 GB (Expandible por microSD) | Espacio de sobra para almacenar múltiples manuales en SQLite y modelos GGUF. |
| **Sistema Operativo** | Android 12/13/14 (64-bit) | Compatible con la pila moderna de Flutter, Android NDK y SQLite C-Extensions. |

---

## 3. Lenguajes y Entorno de Desarrollo

* **Entorno del Sistema:** Windows 10/11.
* **IDE de Desarrollo:** **Android Studio** o **Visual Studio Code**.
* **Lenguajes Principales:**
  * **Flutter (Dart):** Recomendado para construir la interfaz conversacional rápidamente desde Windows.
  * **Kotlin (Jetpack Compose):** Alternativa nativa para Android si se prefiere no usar Flutter.
  * **C / C++:** Para el motor subyacente `llama.cpp` mediante el **Android NDK**.
  * **Python 3.11+:** Para ejecutar scripts de preprocesamiento de PDFs en la PC antes de compilarlos en la app.

---

## 4. Modelos de IA Seleccionados (100 % Gratuitos y Offline)

### Modelos de Lenguaje (LLM)
* **Opción Principal (Recomendada):** `Qwen 2.5 1.5B Instruct` (Formato GGUF, cuantización `Q4_K_M`).
  * *Peso en RAM:* ~1.1 GB.
  * *Rendimiento:* 12 - 18 tokens/segundo en Exynos 1280. Excelente comprensión técnica y respuesta en español.
* **Opción Ultraligera (Máxima Velocidad):** `Llama 3.2 1B Instruct` (Formato GGUF, `Q4_K_M`).
  * *Peso en RAM:* ~800 MB.
  * *Rendimiento:* > 20 tokens/segundo. Ideal si se prioriza inmediatez de respuesta.
* **Opción Avanzada (Solo si el A53 es la versión de 8 GB RAM):** `Qwen 2.5 3B Instruct` (`Q4_K_M`).
  * *Peso en RAM:* ~2.0 GB.
  * *Rendimiento:* 8 - 12 tokens/segundo. Mayor razonamiento sintáctico.

### Modelo de Embeddings
* **`bge-small-en-v1.5`** o **`multilingual-e5-small`**
  * *Tamaño:* ~30 MB (Open Source).
  * *Función:* Generación de vectores de 384 dimensiones para la búsqueda semántica en el manual.

---

## 5. Base de Datos Local (DB)

* **Motor:** **SQLite 3** con la extensión **`sqlite-vec`**.
* **Búsqueda Híbrida:**
  * **`FTS5` (Full-Text Search):** Búsqueda por coincidencia exacta para códigos alfanuméricos (`F0009`, `F0002`).
  * **`sqlite-vec`:** Búsqueda vectorial semántica para consultas coloquiales (*"el motor sobrecalienta"*).
* **Estrategia de Carga:** El archivo `.sqlite` (conteniendo las 440 páginas preprocesadas del variador ABB ACS355) se genera en la PC en Windows y se empaqueta directamente en los `assets` de la app Android (~8 MB).

---

## 6. Recomendaciones de Desarrollo y Buenas Prácticas

1. **Gestión de Memoria en Android:** Configurar la liberación explicita de punteros C++ en `llama.cpp` cuando el usuario minimice la aplicación para evitar que el *Low Memory Killer* de Android cierre la app.
2. **Optimización de Token Length:** Limitar la generación máxima a **150 - 250 tokens por respuesta** para favorecer explicaciones cortas por pasos y reducir el consumo de batería.
3. **Formateo de Citas:** Incluir al final de cada respuesta del LLM la referencia exacta (*Ejemplo: "[Manual ABB ACS355 - Pág. 142]"*).
4. **Verificación en Modo Avión:** Realizar todas las pruebas activando el Modo Avión en el Samsung A53 5G para garantizar la independencia total de red antes de cualquier demostración.