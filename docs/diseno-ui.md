# Diseño de la pantalla de taller

Tokens del mockup aprobado por Francisco el 2026-09-14 (plan 02, paso 4.1): cuatro estados
(listo, consultando, respuesta, falta un fichero). Tema **solo oscuro y cálido**, sin burbujas,
acento ámbar, monoespaciada para códigos, parámetros, páginas y comandos. Si cambia algo, se
cambia aquí y en `ui/Theme.kt`.

## Colores

| Token | Hex | Uso |
| --- | --- | --- |
| `ground` | `#14110D` | Fondo de la pantalla |
| `surface` | `#1E1A15` | Campo de texto, fragmento desplegado, caja de comando, "procesando" |
| `raised` | `#29231C` | Botón desactivado, pista de la barra de progreso |
| `line` | `#3A3228` | Separadores y bordes |
| `text` | `#EEE6D8` | Texto principal |
| `muted` | `#A3988A` | Texto secundario, aviso de preguntas independientes |
| `faint` | `#6F665A` | Métricas, marcador del campo vacío, etiqueta "Fuentes" |
| `amber` | `#F0A43A` | Acción: botón "Preguntar", chips de página, códigos, números de lista |
| `amberInk` | `#1B1206` | Texto sobre el botón ámbar |
| `hazard` | `#E8B923` | Señal de peligro: franja rayada, título y chip de la tarjeta de seguridad |
| `hazardGround` | `#2B2210` | Fondo de la tarjeta de seguridad |
| `ok` | `#7FB77E` | Estado "listo" |
| `missing` | `#E2725B` | Estado y título "falta …", fichero ausente |

El ámbar de acción y el amarillo de peligro son distintos a propósito: la tarjeta de seguridad no
debe parecer un botón.

## Tipos

| Rol | Fuente (OFL) | Uso |
| --- | --- | --- |
| Rótulo | Barlow Condensed 600–700 | Cabecera "ZCA · ABB ACS355", botón, título de la tarjeta y de "falta …" |
| Texto | Atkinson Hyperlegible 400/700 | Pregunta (700), respuesta, avisos |
| Datos | JetBrains Mono 400/600 | Códigos, parámetros, páginas, fragmento del manual, métricas, comandos |

Alternativa aceptada si no se empaquetan fuentes en el APK: las del sistema y
`FontFamily.Monospace`, con los mismos colores.

## Piezas

- **Cabecera:** nombre (ZCA en ámbar), estado en píldora (listo / pensando / sin manual) y, debajo,
  el manual y la versión del índice en monoespaciada.
- **Hilo sin burbujas:** la pregunta en negrita, la respuesta a todo el ancho con los números de
  lista en ámbar.
- **Tarjeta de seguridad:** encima de la respuesta; franja rayada (amarillo `hazard` y casi
  negro) arriba, título "Riesgo eléctrico", texto del aviso y chip "p. 18".
- **Fuentes:** etiqueta "Fuentes" y un chip por página; el chip abierto se rellena de ámbar suave
  y despliega el fragmento en inglés con "p. NNN · capítulo".
- **Consultando:** "procesando el manual…" con el contador de segundos y una barra de progreso
  indeterminada (quieta si el sistema pide reducir el movimiento).
- **Respuesta cortada:** línea discontinua y "— respuesta cortada: llegó al límite de longitud —";
  la vista baja hasta ella.
- **Falta un fichero:** lista de los tres ficheros con ✓ / ✗, el `adb push` exacto y
  `tools/cargar-movil.ps1`.
- **Métricas:** una línea pequeña en `faint` al pie de cada respuesta.

Después del MVP (plan §8): cada referencia a página, en los chips y en la tarjeta, abrirá el PDF
en esa página.
