"""Respuestas reales del móvil + los fragmentos que usó cada una, para la página de la charla.

Lee las líneas `ZCA_RESPUESTA` y `ZCA_METRICS` de un volcado de logcat y repite en el PC la misma
búsqueda híbrida que la app (gemela de `rag/ManualSearch.kt`) para recuperar el texto de cada
fragmento: el log solo guarda las páginas. Necesita e5 en `llama-server --embedding` (puerto 8090).

    adb logcat -d -v time -s ZCA_RESPUESTA ZCA_METRICS ZCA > models/charla/demo-log.txt
    uv run python charla/demo_data.py            # desde tools/ → models/charla/demo-data.json

La salida lleva texto del manual de ABB: se queda en `models/` (ignorado por git), no se versiona.
"""

import json
import re
import sqlite3
import sys
from contextlib import closing
from pathlib import Path

import numpy as np

from zca_tools.evaluar import priorizar, rrf, seleccionar
from zca_tools.terminos import clase_literal, consulta_fts, terminos_literales
from zca_tools.vectorizar import vectorizar

RAIZ = Path(__file__).resolve().parents[2]
TRABAJO = Path(sys.argv[1]) if len(sys.argv) > 1 else RAIZ / "models" / "charla"
INDICE = RAIZ / "models" / "acs355.sqlite"
E5 = "http://127.0.0.1:8090"


def num(patron: str, texto: str) -> float:
    return float(re.search(patron, texto).group(1).replace(",", "."))


def main() -> None:
    log = (TRABAJO / "demo-log.txt").read_text(encoding="utf-8").splitlines()
    # Cada ZCA_METRICS va justo detrás de su ZCA_RESPUESTA; una respuesta sin tokens no tiene métricas.
    pares = []
    for l in log:
        if "ZCA_RESPUESTA" in l:
            pares.append([l[:18], json.loads(l.split("ZCA_RESPUESTA", 1)[1].split(": ", 1)[1]), None])
        elif "ZCA_METRICS" in l and pares and pares[-1][2] is None:
            pares[-1][2] = l.split("ZCA_METRICS", 1)[1].split(": ", 1)[1]
    for hora, r, m in pares:
        if m is None:
            print(f"{hora}  sin métricas (respuesta vacía), se omite: {r['pregunta']}")
    respuestas = [(hora, r) for hora, r, m in pares if m is not None]
    metricas = [m for _, _, m in pares if m is not None]

    with closing(sqlite3.connect(f"file:{INDICE.as_posix()}?mode=ro", uri=True)) as con:
        filas = con.execute("SELECT id, pagina, tokens, vector, texto, capitulo FROM chunks ORDER BY id").fetchall()
        ids = [f[0] for f in filas]
        pagina = {f[0]: f[1] for f in filas}
        tokens = {f[0]: f[2] for f in filas}
        textos = {f[0]: f[4] for f in filas}
        capitulos = {f[0]: f[5] for f in filas}
        matriz = np.stack([np.frombuffer(f[3], dtype="<f4") for f in filas])
        glosario = [(tuple(es.split("|")), tuple(en.split("|")))
                    for es, en in con.execute("SELECT es, en FROM glosario ORDER BY rowid")]
        meta = dict(con.execute("SELECT clave, valor FROM meta"))
        preferido = {"codigo": meta.get("capitulo_codigos"), "parametro": meta.get("capitulo_parametros")}
        consultas = vectorizar([f"query: {r['pregunta']}" for _, r in respuestas], E5)

        salida, iguales = [], 0
        for (hora, r), m, q in zip(respuestas, metricas, consultas):
            fq = consulta_fts(r["pregunta"], glosario)
            todos = [x[0] for x in con.execute(
                "SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY rank", (fq,))] if fq else []
            fts = priorizar(todos, textos, capitulos, terminos_literales(r["pregunta"]),
                            preferido.get(clase_literal(r["pregunta"])))[:10]
            vec = [ids[i] for i in np.argsort(-(matriz @ q))[:10]]
            elegidos = seleccionar(rrf([fts, vec]), tokens)
            paginas_pc = [pagina[i] for i in elegidos]
            iguales += paginas_pc == r["paginas"]
            salida.append({
                "hora": hora, "pregunta": r["pregunta"], "respuesta": r["respuesta"],
                "aviso": r["aviso"], "cortada": r["cortada"],
                "paginas_movil": r["paginas"], "paginas_pc": paginas_pc,
                "ttft": num(r"TTFT ([\d,]+) s", m), "toks": num(r"([\d,]+) tok/s", m),
                "busqueda": num(r"squeda ([\d,]+) s", m), "manual": int(num(r"manual (\d+) tok", m)),
                "tokens": int(num(r"(\d+) tokens", m)),
                "fragmentos": [{"pagina": pagina[i], "capitulo": capitulos[i], "texto": textos[i]} for i in elegidos],
            })

    (TRABAJO / "demo-data.json").write_text(json.dumps(salida, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(salida)} respuestas; páginas iguales móvil/PC: {iguales}/{len(salida)}")
    for s in salida:
        marca = "" if s["paginas_pc"] == s["paginas_movil"] else f"  ← PC {s['paginas_pc']}"
        print(f"{s['hora']}  {s['paginas_movil']}{marca}  aviso={s['aviso']}  {s['ttft']} s  {s['pregunta']}")


if __name__ == "__main__":
    main()
