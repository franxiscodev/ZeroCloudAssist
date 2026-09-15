"""Imagen para compartir la página de la charla (`og:image`, 1200 × 630 px).

    uv run --with pillow python charla/og_imagen.py [carpeta]    # desde tools/ → <carpeta>/og.png

Colores de la app (`docs/diseno-ui.md`) y sus fuentes OFL, de `android/app/src/main/res/font`.
Pillow no es dependencia del proyecto: `--with` la trae solo para esta ejecución.
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parents[1]
TRABAJO = Path(sys.argv[1]) if len(sys.argv) > 1 else RAIZ / "models" / "charla"
FUENTES = RAIZ / "android" / "app" / "src" / "main" / "res" / "font"

ANCHO, ALTO, MARGEN = 1200, 630, 72
FONDO, TEXTO, APAGADO, LINEA = "#0E0C09", "#EEE6D8", "#A3988A", "#3A3228"
AMBAR, PELIGRO, RAYA = "#F0A43A", "#E8B923", "#15110A"


def fuente(nombre: str, tam: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FUENTES / f"{nombre}.ttf"), tam)


def ajustar(draw: ImageDraw.ImageDraw, texto: str, nombre: str, tam: int, ancho: int) -> ImageFont.FreeTypeFont:
    """La fuente más grande, desde `tam`, con la que `texto` cabe en `ancho`."""
    while draw.textlength(texto, font=fuente(nombre, tam)) > ancho:
        tam -= 2
    return fuente(nombre, tam)


def espaciado(draw: ImageDraw.ImageDraw, x: float, y: float, texto: str, f, color: str, sep: float) -> None:
    """Texto con separación entre letras (Pillow no la tiene)."""
    for c in texto:
        draw.text((x, y), c, font=f, fill=color)
        x += draw.textlength(c, font=f) + sep


def partir(draw: ImageDraw.ImageDraw, texto: str, f, ancho: int) -> list[str]:
    lineas, actual = [], ""
    for palabra in texto.split():
        prueba = f"{actual} {palabra}".strip()
        if draw.textlength(prueba, font=f) <= ancho:
            actual = prueba
        else:
            lineas.append(actual)
            actual = palabra
    return lineas + [actual]


def main() -> None:
    img = Image.new("RGB", (ANCHO, ALTO), FONDO)
    d = ImageDraw.Draw(img)
    util = ANCHO - 2 * MARGEN

    # Franja de peligro arriba, como la de la tarjeta de seguridad de la app.
    d.rectangle([0, 0, ANCHO, 14], fill=RAYA)
    for x in range(-36, ANCHO + 36, 36):
        d.polygon([(x, 14), (x + 18, 14), (x + 32, 0), (x + 14, 0)], fill=PELIGRO)

    espaciado(d, MARGEN, 76, "ZEROCLOUDASSIST · IA LOCAL, SIN CONEXIÓN", fuente("jetbrains_mono_semibold", 24), AMBAR, 3)

    titulo = ajustar(d, "Donde no llega la red,", "barlow_condensed_bold", 132, util)
    y = 124
    d.text((MARGEN, y), "Donde no llega la red,", font=titulo, fill=TEXTO)
    y += titulo.size
    d.text((MARGEN, y), "llega la IA.", font=titulo, fill=AMBAR)
    y += titulo.size + 44  # la cola de la "g" de "llega" baja casi un tercio del tamaño

    bajada = fuente("atkinson_hyperlegible_regular", 31)
    texto = ("Un móvil de gama media de 2022 responde en español con el manual del variador "
             "ABB ACS355, al pie de la máquina y sin Internet.")
    for linea in partir(d, texto, bajada, util):
        d.text((MARGEN, y), linea, font=bajada, fill=APAGADO)
        y += 44

    d.line([(MARGEN, ALTO - 92), (ANCHO - MARGEN, ALTO - 92)], fill=LINEA, width=2)
    d.text((MARGEN, ALTO - 70), "0 conexiones a Internet · Galaxy A53 de 2022",
           font=fuente("jetbrains_mono_regular", 23), fill=APAGADO)
    dominio, fd = "zca.ialogia.es", fuente("jetbrains_mono_semibold", 23)
    d.text((ANCHO - MARGEN - d.textlength(dominio, font=fd), ALTO - 70), dominio, font=fd, fill=AMBAR)

    img.save(TRABAJO / "og.png", optimize=True)
    print(f"og.png {ANCHO}×{ALTO}, título a {titulo.size} px → {TRABAJO / 'og.png'}")


if __name__ == "__main__":
    main()
