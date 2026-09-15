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
FONDO, TEXTO, LINEA = "#0E0C09", "#EEE6D8", "#3A3228"
AMBAR, PELIGRO, RAYA = "#F0A43A", "#E8B923", "#15110A"
TITULO = [("Donde no llega la red,", TEXTO), ("llega la IA local.", AMBAR)]


def fuente(nombre: str, tam: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FUENTES / f"{nombre}.ttf"), tam)


def ajustar(draw: ImageDraw.ImageDraw, textos: list[str], nombre: str, tam: int, ancho: int) -> ImageFont.FreeTypeFont:
    """La fuente más grande, desde `tam`, con la que todos los `textos` caben en `ancho`."""
    while max(draw.textlength(t, font=fuente(nombre, tam)) for t in textos) > ancho:
        tam -= 2
    return fuente(nombre, tam)


def espaciado(draw: ImageDraw.ImageDraw, x: float, y: float, texto: str, f, color: str, sep: float) -> None:
    """Texto con separación entre letras (Pillow no la tiene)."""
    for c in texto:
        draw.text((x, y), c, font=f, fill=color)
        x += draw.textlength(c, font=f) + sep


def main() -> None:
    img = Image.new("RGB", (ANCHO, ALTO), FONDO)
    d = ImageDraw.Draw(img)
    util = ANCHO - 2 * MARGEN
    raya_pie = ALTO - 92

    # Franja de peligro arriba, como la de la tarjeta de seguridad de la app.
    d.rectangle([0, 0, ANCHO, 14], fill=RAYA)
    for x in range(-36, ANCHO + 36, 36):
        d.polygon([(x, 14), (x + 18, 14), (x + 32, 0), (x + 14, 0)], fill=PELIGRO)

    espaciado(d, MARGEN, 76, "ZEROCLOUDASSIST · IA LOCAL, SIN CONEXIÓN", fuente("jetbrains_mono_semibold", 26), AMBAR, 3)

    # El título, lo más grande que quepa, centrado entre el antetítulo y la línea del pie.
    titulo = ajustar(d, [t for t, _ in TITULO], "barlow_condensed_bold", 170, util)
    arriba, abajo = 126, raya_pie - 30
    y = arriba + (abajo - arriba - len(TITULO) * titulo.size) // 2
    for texto, color in TITULO:
        d.text((MARGEN, y), texto, font=titulo, fill=color)
        y += titulo.size

    d.line([(MARGEN, raya_pie), (ANCHO - MARGEN, raya_pie)], fill=LINEA, width=2)
    dominio, fd = "zca.ialogia.es", fuente("jetbrains_mono_semibold", 24)
    d.text((ANCHO - MARGEN - d.textlength(dominio, font=fd), ALTO - 70), dominio, font=fd, fill=AMBAR)

    img.save(TRABAJO / "og.png", optimize=True)
    print(f"og.png {ANCHO}×{ALTO}, título a {titulo.size} px → {TRABAJO / 'og.png'}")


if __name__ == "__main__":
    main()
