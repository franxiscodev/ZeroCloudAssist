"""Mete las respuestas reales (`demo-data.json`) en `plantilla.html` → página de la charla.

    uv run python charla/charla_pagina.py        # desde tools/ → models/charla/charla-zca.html

Un solo fichero HTML con los datos dentro (solo carga las fuentes de Google): se puede proyectar,
publicar como artifact o subir a un hosting. Lleva texto del manual de ABB: ver
`docs/licencias.md` antes de publicarla fuera de la demo.
"""

import json
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parents[1]
TRABAJO = Path(sys.argv[1]) if len(sys.argv) > 1 else RAIZ / "models" / "charla"

# Grupo, texto con tildes para mostrar (el móvil las recibió sin tildes por ADB), destacada y nota.
EDICION = {
    "El variador muestra el fallo F0009. Que significa y que reviso": ("Códigos de fallo", "El variador muestra el fallo F0009. ¿Qué significa y qué reviso?", True, None),
    "Que significa el fallo F0009 y que reviso": ("Códigos de fallo", "¿Qué significa el fallo F0009 y qué reviso?", False, None),
    "El variador muestra la alarma A2001 que hago": ("Códigos de fallo", "El variador muestra la alarma A2001, ¿qué hago?", True,
        "Límite conocido: la alarma sí está en el manual (p. 353), pero con esta redacción la búsqueda no trae su fragmento y el modelo, bien, no inventa."),
    "Me sale el error 0016 al arrancar": ("Códigos de fallo", "Me sale el error 0016 al arrancar", False,
        "Acierta el fallo (EARTH FAULT, p. 363), pero cita «la página 387» aunque el prompt le pide no escribir páginas."),
    "Que significa el fallo F0001": ("Códigos de fallo", "¿Qué significa el fallo F0001?", False, None),
    "El armario esta muy caliente y el variador se para. Que compruebo": ("Síntomas", "El armario está muy caliente y el variador se para. ¿Qué compruebo?", True,
        "Consejos razonables, pero sin la entrada DEV OVERTEMP (p. 360): lo coloquial es el punto débil de la búsqueda."),
    "El armario esta muy caliente y el variador se para": ("Síntomas", "El armario está muy caliente y el variador se para", False, None),
    "El motor gira al reves que compruebo": ("Síntomas", "El motor gira al revés, ¿qué compruebo?", False,
        "Límite conocido: la respuesta (parámetro 9914) está en el manual, pero la búsqueda no la encuentra."),
    "Que hago si el variador no arranca y no muestra ningun fallo": ("Síntomas", "¿Qué hago si el variador no arranca y no muestra ningún fallo?", False, None),
    "Que hago si aparece sobretension en el bus": ("Síntomas", "¿Qué hago si aparece sobretensión en el bus?", True, None),
    "Como mido la tension del bus de continua": ("Seguridad", "¿Cómo mido la tensión del bus de continua?", True,
        "La tarjeta de seguridad la pone la app, no el modelo: aquí la respuesta no dice lo de cortar la alimentación, la tarjeta sí."),
    "Voy a desmontar el cable del motor que precauciones tomo": ("Seguridad", "Voy a desmontar el cable del motor, ¿qué precauciones tomo?", True, None),
    "Para que sirve el parametro 9905": ("Parámetros y funciones", "¿Para qué sirve el parámetro 9905?", False, None),
    "Para que sirve el parametro 1202": ("Parámetros y funciones", "¿Para qué sirve el parámetro 1202?", False, None),
    "Como ajusto la rampa de aceleracion": ("Parámetros y funciones", "¿Cómo ajusto la rampa de aceleración?", True, None),
    "Como configuro la entrada analogica AI1": ("Parámetros y funciones", "¿Cómo configuro la entrada analógica AI1?", False, None),
    "Que es la funcion STO": ("Parámetros y funciones", "¿Qué es la función STO?", False, None),
    "Como cambio el ventilador del variador": ("Procedimientos", "¿Cómo cambio el ventilador del variador?", True, None),
    "Como reseteo un fallo desde el panel": ("Procedimientos", "¿Cómo reseteo un fallo desde el panel?", False, None),
    "Que mantenimiento necesita el variador": ("Procedimientos", "¿Qué mantenimiento necesita el variador?", False, None),
    "El variador tiene conexion Bluetooth": ("Fuera del manual", "¿El variador tiene conexión Bluetooth?", True,
        "Bien: el manual no habla de Bluetooth y el modelo lo dice en vez de inventar."),
    "Cuanto cuesta el variador ACS355": ("Fuera del manual", "¿Cuánto cuesta el variador ACS355?", False,
        "Bien: el precio no está en el manual y no se lo inventa."),
}
LIMITES = {"El variador muestra la alarma A2001 que hago", "El motor gira al reves que compruebo"}
BUENAS = {"El variador tiene conexion Bluetooth", "Cuanto cuesta el variador ACS355"}


def main() -> None:
    datos = json.loads((TRABAJO / "demo-data.json").read_text(encoding="utf-8"))
    vistas, salida = set(), []
    for d in datos:
        # Una pregunta nueva sin editar sale en "Todas", con su texto tal cual.
        grupo, mostrada, destacada, nota = EDICION.get(d["pregunta"], ("Procedimientos", d["pregunta"], False, None))
        destacada = destacada and d["pregunta"] not in vistas  # destacada solo la primera vez
        vistas.add(d["pregunta"])
        fuentes = []
        for k, pagina in enumerate(d["paginas_movil"]):
            # Solo el fragmento que el PC reconstruye en la misma página que eligió el móvil.
            frag = d["fragmentos"][k] if k < len(d["fragmentos"]) and d["fragmentos"][k]["pagina"] == pagina else None
            fuentes.append({"pagina": pagina, "capitulo": frag["capitulo"] if frag else "", "texto": frag["texto"] if frag else ""})
        salida.append({
            "hora": d["hora"], "grupo": grupo, "pregunta_mostrada": mostrada, "destacada": destacada,
            "respuesta": d["respuesta"], "aviso": d["aviso"], "cortada": d["cortada"], "fuentes": fuentes,
            "ttft": d["ttft"], "toks": d["toks"], "busqueda": d["busqueda"], "manual": d["manual"], "tokens": d["tokens"],
            "nota": nota, "nota_buena": d["pregunta"] in BUENAS, "limite": d["pregunta"] in LIMITES,
        })

    plantilla = (AQUI / "plantilla.html").read_text(encoding="utf-8")
    json_seguro = json.dumps(salida, ensure_ascii=False).replace("</", "<\\/")
    (TRABAJO / "charla-zca.html").write_text(plantilla.replace("__DATA__", json_seguro), encoding="utf-8")
    print(f"{len(salida)} respuestas, {sum(s['destacada'] for s in salida)} destacadas, "
          f"{sum(1 for s in salida for f in s['fuentes'] if not f['texto'])} fuentes sin fragmento → "
          f"{TRABAJO / 'charla-zca.html'}")


if __name__ == "__main__":
    main()
