# -*- coding: utf-8 -*-
"""Renumeracion de tablas, figuras, fragmentos y salidas en orden de lectura.

Los nueve documentos de origen se escribieron en un orden distinto al de
lectura, de modo que su numeracion continua no es ascendente cuando se unen:
la primera tabla del documento unido es la "Tabla XXIX". En un solo documento
eso es un defecto, asi que se renumera todo por orden de aparicion y se
reescriben las referencias del texto para que sigan apuntando al sitio correcto.
"""
import re

RE_TABLA = re.compile(r"\bTabla\s+([IVXLC]+)\b")
RE_FIG = re.compile(r"\bFig\.\s*(\d+)\b")
RE_FRAG = re.compile(r"\bFragmento\s+(\d+)\b")
RE_SAL = re.compile(r"\bSalida\s+(\d+)\b")

VALORES = [(1000, "M"), (900, "CM"), (500, "D"), (400, "CD"), (100, "C"),
           (90, "XC"), (50, "L"), (40, "XL"), (10, "X"), (9, "IX"),
           (5, "V"), (4, "IV"), (1, "I")]


def a_romano(n):
    s = ""
    for v, r in VALORES:
        while n >= v:
            s += r
            n -= v
    return s


def de_romano(s):
    mapa = {"I": 1, "V": 5, "X": 10, "L": 50, "C": 100, "D": 500, "M": 1000}
    total, previo = 0, 0
    for c in reversed(s.upper()):
        v = mapa.get(c, 0)
        total += -v if v < previo else v
        previo = max(previo, v)
    return total


def construir_mapas(bloques):
    """Recorre los bloques en orden y devuelve los mapas viejo -> nuevo."""
    tablas, figuras, frags, salidas = {}, {}, {}, {}

    def registrar(mapa, clave):
        if clave not in mapa:
            mapa[clave] = len(mapa) + 1

    for b in bloques:
        tipo = b[0]
        if tipo == "tabla":
            m = RE_TABLA.search(b[1] or "")
            if m:
                registrar(tablas, m.group(1))
        elif tipo == "figura":
            m = RE_FIG.search(b[2] or "")
            if m:
                registrar(figuras, m.group(1))
        elif tipo == "codigo":
            pie = b[2] or ""
            m = RE_FRAG.search(pie)
            if m:
                registrar(frags, m.group(1))
            m = RE_SAL.search(pie)
            if m:
                registrar(salidas, m.group(1))
    return tablas, figuras, frags, salidas


def hacer_reescritor(tablas, figuras, frags, salidas):
    """Devuelve una funcion que reescribe todas las referencias de un texto."""

    def sub_tabla(m):
        nuevo = tablas.get(m.group(1))
        return "Tabla " + a_romano(nuevo) if nuevo else m.group(0)

    def sub_fig(m):
        nuevo = figuras.get(m.group(1))
        return "Fig. %d" % nuevo if nuevo else m.group(0)

    def sub_frag(m):
        nuevo = frags.get(m.group(1))
        return "Fragmento %d" % nuevo if nuevo else m.group(0)

    def sub_sal(m):
        nuevo = salidas.get(m.group(1))
        return "Salida %d" % nuevo if nuevo else m.group(0)

    def reescribir(texto):
        if not texto:
            return texto
        t = RE_TABLA.sub(sub_tabla, texto)
        t = RE_FIG.sub(sub_fig, t)
        t = RE_FRAG.sub(sub_frag, t)
        t = RE_SAL.sub(sub_sal, t)
        return t

    return reescribir


def aplicar(bloques, reescribir):
    """Reescribe las referencias en todos los bloques."""
    salida = []
    for b in bloques:
        tipo = b[0]
        if tipo in ("p", "abstract"):
            salida.append((tipo, [(reescribir(t), x, y, z) for t, x, y, z in b[1]]))
        elif tipo == "h1":
            salida.append((tipo, b[1], reescribir(b[2])))
        elif tipo in ("h2", "h3"):
            salida.append((tipo, reescribir(b[1])))
        elif tipo == "callout":
            salida.append((tipo, b[1],
                           [[(reescribir(t), x, y, z) for t, x, y, z in p] for p in b[2]]))
        elif tipo == "tabla":
            filas = [[reescribir(c) for c in f] for f in b[4]]
            salida.append((tipo, reescribir(b[1]), b[2], b[3], filas))
        elif tipo == "codigo":
            salida.append((tipo, b[1], reescribir(b[2])))
        elif tipo == "figura":
            salida.append((tipo, b[1], reescribir(b[2])))
        else:
            salida.append(b)
    return salida
