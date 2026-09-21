# -*- coding: utf-8 -*-
"""Extrae los SVG del informe y los rasteriza con Chrome sin interfaz.

Word no admite SVG en linea de forma fiable, asi que cada diagrama se convierte
a PNG a 2x para que conserve nitidez al imprimir.

El SVG se toma del TEXTO CRUDO del archivo, no del arbol analizado: el analizador
de HTML pasa los atributos a minusculas y SVG es sensible a mayusculas, de modo
que `viewBox` se convertiria en `viewbox` y el navegador lo ignoraria.
"""
import io
import os
import re
import subprocess

import lxml.html

INFORME = os.path.dirname(os.path.abspath(__file__))
SALIDA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "figuras")
CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe"
ESCALA = 2

ORDEN = [
    "01-07-planteamiento.html", "08-estado-del-arte.html", "10-diseno.html",
    "11-arquitectura.html", "12-16-pilares-kotlin.html", "17-multiplataforma.html",
    "09-18-tecnologias-e-ia.html", "19-pruebas.html", "20-22-resultados.html",
]

PLANTILLA = u"""<!doctype html><html><head><meta charset="utf-8"><style>
 html,body{{margin:0;padding:0;background:#FFFFFF;}}
 #c{{color:#101A2B;width:{w}px;height:{h}px;}}
 svg{{display:block;width:{w}px;height:{h}px;}}
</style></head><body><div id="c">{svg}</div></body></html>"""

RE_SVG = re.compile(r"<svg\b.*?</svg>", re.DOTALL | re.IGNORECASE)
RE_VB = re.compile(r'viewBox\s*=\s*"([^"]+)"', re.IGNORECASE)

if not os.path.isdir(SALIDA):
    os.makedirs(SALIDA)

n = 0
fallos = []
for archivo in ORDEN:
    ruta = os.path.join(INFORME, archivo)
    crudo = io.open(ruta, encoding="utf-8").read()
    bloques = RE_SVG.findall(crudo)

    # Los pies de figura se leen del arbol: ahi el texto no depende de mayusculas.
    doc = lxml.html.parse(ruta).getroot()
    pies = []
    for svg in doc.findall(".//svg"):
        padre = svg.getparent()
        while padre is not None and padre.tag != "figure":
            padre = padre.getparent()
        texto = ""
        if padre is not None:
            fc = padre.find(".//figcaption")
            if fc is not None:
                texto = " ".join(fc.text_content().split())
        pies.append(texto)

    if len(bloques) != len(pies):
        print("  AVISO: %s tiene %d svg en crudo y %d en el arbol" % (archivo, len(bloques), len(pies)))

    for i, svg in enumerate(bloques):
        n += 1
        m = RE_VB.search(svg)
        if not m:
            fallos.append((n, archivo, "sin viewBox"))
            print("  fig%02d  %-28s SIN viewBox -> se omite" % (n, archivo[:28]))
            continue
        partes = [float(x) for x in m.group(1).replace(",", " ").split()]
        ancho, alto = partes[2], partes[3]
        w, h = int(ancho * ESCALA), int(alto * ESCALA)

        html = PLANTILLA.format(w=w, h=h, svg=svg)
        tmp = os.path.join(SALIDA, "_tmp.html")
        io.open(tmp, "w", encoding="utf-8").write(html)

        png = os.path.join(SALIDA, "fig%02d.png" % n)
        if os.path.exists(png):
            os.remove(png)
        subprocess.run([
            CHROME, "--headless", "--disable-gpu", "--hide-scrollbars",
            "--default-background-color=FFFFFF",
            "--screenshot=" + png, "--window-size=%d,%d" % (w, h),
            "file:///" + tmp.replace("\\", "/"),
        ], capture_output=True, timeout=90)

        ok = os.path.exists(png) and os.path.getsize(png) > 800
        if not ok:
            fallos.append((n, archivo, "Chrome no genero el PNG"))
        pie = pies[i] if i < len(pies) else ""
        print("  fig%02d  %-26s %4dx%-4d -> %4dx%-4d  %s  %s" % (
            n, archivo[:26], ancho, alto, w, h, "OK " if ok else "FALLO", pie[:52]))

tmp = os.path.join(SALIDA, "_tmp.html")
if os.path.exists(tmp):
    os.remove(tmp)

print("")
print("%d figuras, %d correctas, %d fallidas" % (n, n - len(fallos), len(fallos)))
for f in fallos:
    print("  ! fig%02d %s: %s" % f)
