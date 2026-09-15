# Pantalla de taller (etapa 4 del plan 02) — 2026-09-14

Rama `feat/rag-app`, detrás de la etapa 3 (`informes/2026-09-14-logica-rag-a53.md`). Móvil por
wifi desde la revisión visual (`adb -s adb-R5CT30HSZTT-LiQUep._adb-tls-connect._tcp`): el USB se
desconectó (incidencia 1).

## 4.1 Mockup

Publicado como página "Pantalla de taller ZCA" con contenido real de las pruebas de E3 (cuatro
estados, colores y tipos). **Aprobado totalmente por Francisco el 2026-09-14.** Tokens en
`docs/diseno-ui.md`. Pidió además, para después del MVP (plan §8): páginas citadas como enlace al
PDF y publicar la página de la charla en su hosting.

## 4.2–4.9 Qué se hizo

| Paso | Fichero | Nota |
| --- | --- | --- |
| 4.2 | `ui/Theme.kt`, `res/font`, `assets/licencias` | Barlow Condensed, Atkinson Hyperlegible y JetBrains Mono (OFL, ~870 KB) con sus licencias en el APK |
| 4.3 | `ui/ChatScreen.kt`, `MainActivity.kt` | `MainActivity` se queda con el ciclo de vida; barras del sistema oscuras |
| 4.4 | `ui/MarkdownText.kt` | Listas con el número en ámbar monoespaciado |
| 4.5 | `ui/SourcesRow.kt` | Chips que despliegan el fragmento en inglés con página y capítulo |
| 4.6 | `ui/SafetyCard.kt` | Franja de peligro dibujada; el chip "p. 18" despliega el texto de esa página del índice |
| 4.7 | `Assistant.phase` | "buscando…" y "procesando el manual… N s" desde que se pulsa |
| 4.8 | `Assistant.blocked` | Qué falta o no vale, los tres ficheros con ✓/✗ y el `adb push` exacto |
| 4.9 | `ChatScreen` | Un último desplazamiento animado al terminar de generar |

El botón sigue diciendo "Preguntar" con esa ortografía: `tools/estres-movil.ps1` y el script de
preguntas lo buscan por su texto. 58 tests JVM en verde tras cada paso.

## 4.10 Revisión visual

Capturas en `informes/2026-09-14-pantalla-taller-a53/`, tomadas con `screencap` tras terminar
cada respuesta (salvo la de "consultando", una sola captura durante el procesado de una pregunta
que no se usa como medida):

| Estado | Captura | Frente al mockup |
| --- | --- | --- |
| 1 · Listo | `1-listo.png` | Igual: cabecera, píldora "listo", aviso de preguntas independientes, sugerencia |
| 2 · Consultando | `2-consultando-teclado-abierto.png` | Fuentes y contador ("2 s") bien; **el teclado seguía abierto** y tapaba media pantalla |
| 3 · Respuesta | `3-respuesta.png` | Tras el arreglo: teclado cerrado, lista en ámbar, fuentes, métricas; el fragmento desplegado (p. 353) queda a la vista. Esa captura no se versiona: muestra texto del manual de ABB y el repo es público |
| 4 · Falta el manual | `4-falta-el-manual.png` | Igual: título, ✓/✗ de los tres ficheros y `adb push models/acs355.sqlite …/files/manuales/` (índice renombrado en el móvil y restaurado después) |
| Desplazamiento final | `5-desplazamiento-final.png` | Al terminar una respuesta larga la vista acaba abajo, con las métricas |

Arreglos de la revisión (commit `3a5a02f` y siguiente):

- Al pulsar "Preguntar" se cierran el teclado y el foco.
- Un fragmento desplegado pide a la vista que baje hasta él (sirve también para la tarjeta).
- El número de lista "10." se partía en dos líneas (columna de 24 dp): pasa a 32 dp.

**Pendiente de 4.9:** ver en pantalla la marca "— respuesta cortada —". Ninguna de las tres
preguntas largas de la revisión llegó al tope de 400 tokens (terminaron en 62, 300 y 7). El camino
está cubierto por los tests de `Conversation` y en E3 el tope salió en 2 de 20; se captura en la
tanda de estrés de E5 (5.3).

Para la rúbrica de E5, de esta revisión: la respuesta a la tensión del bus inventó "guarda el
resultado en la parametrización 0107" (en E3 no lo hacía); la de "el variador no arranca" repite
los pasos 6–10 en bucle hasta parar solo.

## Incidencias

1. **El script de capturas se colgó 10 minutos: el USB se desconectó y `adb logcat -c` espera al
   dispositivo sin límite.** Se supuso que el móvil seguía por USB como toda la tarde. Todas las
   órdenes con `-s R5CT30HSZTT` fallaron con `device not found` salvo `logcat`, que se quedó en
   `- waiting for device -`. Se detectó porque la tarea pasó a segundo plano al agotar los 10
   minutos; la salida del script y `adb devices` mostraron que solo quedaba el transporte wifi. No
   llegó a tocar el móvil (tampoco a renombrar el índice). Coste: ~15 min. Se paró la tarea, se
   cerraron los clientes de `adb` que seguían esperando (el servidor no) y se repitió por wifi.
   Lección: toda orden de ADB en un script, con límite de tiempo (`timeout`, `Wait-Job -Timeout`).
2. **El wifi de ADB también se cortó ~40 minutos.** Una pregunta lanzada a las 21:12 empezó a las
   21:52 (`- waiting for device -`), y la app se había recargado entretanto (carga 9,4 s, TTFT
   15,5 s: esa medida no vale). A partir de ahí las pruebas llevan límite de tiempo y comprueban
   `adb devices` antes de empezar.
3. **La revisión visual encontró tres defectos que los tests no ven** (teclado abierto, fragmento
   fuera de la vista, "10." partido): justo para eso está el paso 4.10.
