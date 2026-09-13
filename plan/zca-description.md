# Idea del Proyecto: Copiloto Técnico Industrial Offline

## 1. Visión General
**Copiloto Técnico Offline** es una aplicación móvil nativa para iOS (optimizada para dispositivos Apple Silicon como el iPhone 17 Pro) diseñada para guiar a técnicos de campo en el diagnóstico, reparación y mantenimiento de equipos industriales complejos en zonas sin cobertura de red.

La solución combina un motor de Lenguaje de Última Generación (LLM) ejecutado 100% en el chip del dispositivo con una base de datos vectorial local (RAG). Esto permite a los operarios consultar manuales técnicos de cientos de páginas mediante conversación natural en español, incluso si la documentación original del fabricante está en inglés.

---

## 2. Propuesta de Valor

* **Operatividad 100% Offline (Modo Avión):** Cero dependencia de servidores en la nube, conectividad celular o redes Wi-Fi.
* **Soberanía de Datos y Gobernanza:** Cumplimiento absoluto con normativas de secreto industrial y seguridad; la información de la planta jamás abandona el dispositivo.
* **RAG Multilingüe Inteligente:** Búsqueda e interpretación semántica de manuales en inglés con respuestas redactadas en español técnico claro y accesible.
* **Cero Costes Recurrentes por Token:** Al no consumir APIs externas, el coste operativo por consulta es nulo tras el despliegue.
* **Respuesta Ultra Rápida:** Latencia inferior a 1 segundo para el inicio de generación de respuestas (TTFT), gracias a la aceleración por hardware con la GPU y Neural Engine.

---

## 3. Equipo Piloto y Escalabilidad
* **Equipo Base del MVP:** Variador de Frecuencia **ABB ACS355** (Manual de 440 páginas). Un equipo omnipresente en automatización industrial, control de bombas, ventilación y maquinaria pesada.
* **Arquitectura Extensible:** Diseñada para alojar una biblioteca de cientos de manuales (motores, fuentes de soldadura, sistemas CNC, inversores solares) dentro de la misma app sin degradation de rendimiento.

---

## 4. Ejemplos de Uso

### Ejemplo 1: Diagnóstico de Falla con Manual en Inglés
* **Contexto:** El técnico llega a una estación de bombeo en una ruta alejada sin señal móvil. El variador de frecuencia tiene la alarma roja encendida.
* **Interacción:**
  * **Técnico:** *"Hola, estoy frente al variador ABB ACS355. En la pantalla me aparece el error F0009 y se siente mucho calor en el gabinete."*
  * **Copiloto IA:** *"El código **F0009 (DRIVE OVERTEMP)** indica que la temperatura del disipador del variador ha superado el límite de seguridad (88 °C).*
    
    *Pasos recomendados según la Sección 6 del manual:*
    1. *Desconecta la alimentación y verifica que la rejilla de ventilación superior e inferior no esté obstruida.*
    2. *Revisa si el ventilador interno de refrigeración (**Cooling Fan**) gira libremente o si presenta acumulación de polvo/grasa.*
    3. *Verifica que la temperatura ambiente de la caseta no supere los 40 °C.*
    
    *¿El ventilador hace algún ruido anómalo antes de detenerse?"*

### Ejemplo 2: Protocolo de Seguridad Mandatorio (Safety Gatekeeper)
* **Contexto:** El técnico necesita revisar las conexiones del bus de corriente continua.
* **Interacción:**
  * **Técnico:** *"¿Cómo compruebo la tensión en el bus CC?"*
  * **Copiloto IA:** *"⚠️ **ALERTA DE SEGURIDAD ELÉCTRICA:** Antes de proceder con la medición en los terminales UDC+ / UDC-:*
    1. *Corta la corriente del disyuntor principal.*
    2. *Espera al menos 5 minutos para que los condensadores se descarguen completamente.*
    3. *Equípate con guantes aislantes y multímetro en rango 1000V DC.*
    
    *Por favor, confirma que has completado el corte de energía para mostrarte el diagrama de bornes."*