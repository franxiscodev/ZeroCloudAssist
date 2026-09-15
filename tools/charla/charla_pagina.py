"""Mete las respuestas reales (`demo-data.json`) en `plantilla.html` → página web de la charla.

    uv run python charla/charla_pagina.py [carpeta]    # desde tools/ → <carpeta>/index.html

La carpeta (por defecto `models/charla`) tiene el `demo-data.json` de `demo_data.py`. Sale un
`index.html` con los datos dentro (solo carga las fuentes de Google), para subir a zca.ialogia.es
junto a `og.png` (`og_imagen.py`) y `manual-acs355.pdf`, el PDF del manual al que enlazan las
páginas citadas. Lleva texto del manual de ABB: ver `docs/licencias.md` antes de publicarla.
"""

import hashlib
import json
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parents[1]
TRABAJO = Path(sys.argv[1]) if len(sys.argv) > 1 else RAIZ / "models" / "charla"

# Pregunta tal como llegó al móvil → grupo, texto a mostrar (None: el mismo), destacada, nota y si
# es un límite conocido. Las de E3 llegaron sin tildes por ADB; las de E5, pegadas a mano, con ellas.
EDICION = {
    # Batería de la etapa 5 (15-09-2026): las 6 de la demo.
    "El variador muestra el fallo F0009. ¿Qué significa y qué reviso?": ("Códigos de fallo", None, True, None, False),
    "Me sale el fallo F0016, ¿qué puede ser?": ("Códigos de fallo", None, True, None, False),
    "Aparece F0007 en la pantalla del variador": ("Códigos de fallo", None, True, None, False),
    "¿Cómo mido la tensión del bus de continua?": ("Seguridad", None, True,
        "La tarjeta de seguridad la pone la app, no el modelo. La respuesta mezcla el multímetro con la "
        "lectura en marcha del parámetro 0107; lo que manda es la tarjeta: cortar, esperar 5 minutos y medir.", False),
    "¿Dónde se ajusta el tiempo que tarda el motor en acelerar?": ("Parámetros y funciones", None, True, None, False),
    "¿Qué aceite lleva el reductor de la cinta transportadora?": ("Fuera del manual", None, True,
        "Bien: el manual no habla de reductores ni de aceites, y el modelo lo dice en vez de inventar.", False),
    # Etapa 3 (14-09-2026).
    "El variador muestra el fallo F0009. Que significa y que reviso": ("Códigos de fallo", "El variador muestra el fallo F0009. ¿Qué significa y qué reviso?", True, None, False),
    "Que significa el fallo F0009 y que reviso": ("Códigos de fallo", "¿Qué significa el fallo F0009 y qué reviso?", False, None, False),
    "El variador muestra la alarma A2001 que hago": ("Códigos de fallo", "El variador muestra la alarma A2001, ¿qué hago?", True,
        "Límite conocido: la alarma sí está en el manual (p. 353), pero con esta redacción la búsqueda no trae su fragmento y el modelo, bien, no inventa.", True),
    "Me sale el error 0016 al arrancar": ("Códigos de fallo", "Me sale el error 0016 al arrancar", False,
        "Acierta el fallo (EARTH FAULT, p. 363), pero cita «la página 387» aunque el prompt le pide no escribir páginas.", False),
    "Que significa el fallo F0001": ("Códigos de fallo", "¿Qué significa el fallo F0001?", False, None, False),
    "El armario esta muy caliente y el variador se para. Que compruebo": ("Síntomas", "El armario está muy caliente y el variador se para. ¿Qué compruebo?", True,
        "Consejos razonables, pero sin la entrada DEV OVERTEMP (p. 360): lo coloquial es el punto débil de la búsqueda.", False),
    "El armario esta muy caliente y el variador se para": ("Síntomas", "El armario está muy caliente y el variador se para", False, None, False),
    "El motor gira al reves que compruebo": ("Síntomas", "El motor gira al revés, ¿qué compruebo?", False,
        "Límite conocido: la respuesta (parámetro 9914) está en el manual, pero la búsqueda no la encuentra.", True),
    "Que hago si el variador no arranca y no muestra ningun fallo": ("Síntomas", "¿Qué hago si el variador no arranca y no muestra ningún fallo?", False, None, False),
    "Que hago si aparece sobretension en el bus": ("Síntomas", "¿Qué hago si aparece sobretensión en el bus?", True, None, False),
    "Como mido la tension del bus de continua": ("Seguridad", "¿Cómo mido la tensión del bus de continua?", True,
        "La tarjeta de seguridad la pone la app, no el modelo: aquí la respuesta no dice lo de cortar la alimentación, la tarjeta sí.", False),
    "Voy a desmontar el cable del motor que precauciones tomo": ("Seguridad", "Voy a desmontar el cable del motor, ¿qué precauciones tomo?", True, None, False),
    "Para que sirve el parametro 9905": ("Parámetros y funciones", "¿Para qué sirve el parámetro 9905?", False, None, False),
    "Para que sirve el parametro 1202": ("Parámetros y funciones", "¿Para qué sirve el parámetro 1202?", False, None, False),
    "Como ajusto la rampa de aceleracion": ("Parámetros y funciones", "¿Cómo ajusto la rampa de aceleración?", True, None, False),
    "Como configuro la entrada analogica AI1": ("Parámetros y funciones", "¿Cómo configuro la entrada analógica AI1?", False, None, False),
    "Que es la funcion STO": ("Parámetros y funciones", "¿Qué es la función STO?", False, None, False),
    "Como cambio el ventilador del variador": ("Procedimientos", "¿Cómo cambio el ventilador del variador?", True, None, False),
    "Como reseteo un fallo desde el panel": ("Procedimientos", "¿Cómo reseteo un fallo desde el panel?", False, None, False),
    "Que mantenimiento necesita el variador": ("Procedimientos", "¿Qué mantenimiento necesita el variador?", False, None, False),
    "El variador tiene conexion Bluetooth": ("Fuera del manual", "¿El variador tiene conexión Bluetooth?", True,
        "Bien: el manual no habla de Bluetooth y el modelo lo dice en vez de inventar.", False),
    "Cuanto cuesta el variador ACS355": ("Fuera del manual", "¿Cuánto cuesta el variador ACS355?", False,
        "Bien: el precio no está en el manual y no se lo inventa.", False),
}


def main() -> None:
    datos = json.loads((TRABAJO / "demo-data.json").read_text(encoding="utf-8"))
    vistas, salida = set(), []
    for d in datos:
        # Una pregunta nueva sin editar sale en "Todas", con su texto tal cual.
        grupo, mostrada, destacada, nota, limite = EDICION.get(d["pregunta"], ("Procedimientos", None, False, None, False))
        destacada = destacada and d["pregunta"] not in vistas  # destacada solo la primera vez
        vistas.add(d["pregunta"])
        fuentes = []
        for k, pagina in enumerate(d["paginas_movil"]):
            # Solo el fragmento que el PC reconstruye en la misma página que eligió el móvil.
            frag = d["fragmentos"][k] if k < len(d["fragmentos"]) and d["fragmentos"][k]["pagina"] == pagina else None
            fuentes.append({"pagina": pagina, "capitulo": frag["capitulo"] if frag else "", "texto": frag["texto"] if frag else ""})
        salida.append({
            "hora": d["hora"], "grupo": grupo, "pregunta_mostrada": mostrada or d["pregunta"], "destacada": destacada,
            "respuesta": d["respuesta"], "aviso": d["aviso"], "cortada": d["cortada"], "fuentes": fuentes,
            "ttft": d["ttft"], "toks": d["toks"], "busqueda": d["busqueda"], "manual": d["manual"], "tokens": d["tokens"],
            "nota": nota, "nota_buena": grupo == "Fuera del manual", "limite": limite,
        })

    plantilla = (AQUI / "plantilla.html").read_text(encoding="utf-8")
    json_seguro = json.dumps(salida, ensure_ascii=False).replace("</", "<\\/")
    # Huella de og.png para su URL (?v=): las redes guardan la imagen por URL y así la renuevan.
    og = TRABAJO / "og.png"
    version = hashlib.sha256(og.read_bytes()).hexdigest()[:8] if og.exists() else "sin-og"
    pagina = plantilla.replace("__DATA__", json_seguro).replace("__OG_V__", version)
    (TRABAJO / "index.html").write_text(pagina, encoding="utf-8")
    print(f"{len(salida)} respuestas, {sum(s['destacada'] for s in salida)} destacadas, "
          f"{sum(1 for s in salida for f in s['fuentes'] if not f['texto'])} fuentes sin fragmento → "
          f"{TRABAJO / 'index.html'}")


if __name__ == "__main__":
    main()
