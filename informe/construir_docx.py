# -*- coding: utf-8 -*-
"""Une los nueve documentos HTML del informe en un solo .docx con formato IEEE.

No modifica ni borra los archivos de origen: solo los lee.

Formato aplicado (plantilla de conferencia IEEE):
  - Carta, margenes 0,75" arriba / 1" abajo / 0,625" a los lados
  - Dos columnas de 3,5" con 0,2" de medianil
  - Times New Roman 10 pt, justificado
  - Titulos de seccion en numeracion romana, versalitas, centrados
  - Subtitulos "A." en cursiva; sub-subtitulos "1)"
  - Pies de tabla ENCIMA, pies de figura DEBAJO
  - Las tablas anchas, las figuras y el codigo ocupan el ancho completo,
    como los entornos table* y figure* de IEEEtran
"""
import io
import os
import re

import lxml.html
import bibliografia
import renumerar
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor

ANALIZADOR = lxml.html.HTMLParser(encoding="utf-8")

BASE = "C:/Users/wildermangt/Desktop/6to semestre/Desarrollo Movil - Optativa/Nexcode Control de Gastos Personales"
INFORME = os.path.join(BASE, "informe")
FIGURAS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "figuras")
DESTINO = os.path.join(BASE, "informe", "Informe-IEEE-Nexcode-completo.docx")

SERIF = "Times New Roman"
MONO = "Consolas"

ANCHO_COL = 3.4      # pulgadas utiles de una columna
ANCHO_TOTAL = 7.0    # pulgadas utiles a doble columna
MAX_CHAR_COL = 48    # caracteres de codigo que caben en una columna

# (archivo, indices de <section>; None = todas | nivel base | titulo sintetico)
#
# El archivo de arquitectura se redacto como capitulo independiente, con su
# propia numeracion romana I-IX. Al unirlo, todo el es la seccion 11: se le
# antepone un titulo propio y sus secciones internas bajan un nivel.
ORDEN = [
    ("01-07-planteamiento.html", None, 1, None),
    ("08-estado-del-arte.html", None, 1, None),
    ("09-18-tecnologias-e-ia.html", [0], 1, None),
    ("10-diseno.html", None, 1, None),
    ("11-arquitectura.html", None, 2, u"Arquitectura de la aplicación"),
    ("12-16-pilares-kotlin.html", None, 1, None),
    ("17-multiplataforma.html", None, 1, None),
    ("09-18-tecnologias-e-ia.html", [1], 1, None),
    ("19-pruebas.html", None, 1, None),
    ("20-22-resultados.html", None, 1, None),
]

# Las secciones 1 (titulo) y 2 (autores) son el encabezado del documento,
# de modo que los titulos numerados empiezan en la III.
PRIMERA_SECCION = 3

# Las figuras se rasterizaron en este orden (ver render_svg.py).
ORDEN_FIGURAS = [
    "01-07-planteamiento.html", "08-estado-del-arte.html", "10-diseno.html",
    "11-arquitectura.html", "12-16-pilares-kotlin.html", "17-multiplataforma.html",
    "09-18-tecnologias-e-ia.html", "19-pruebas.html", "20-22-resultados.html",
]

ROMANOS = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI",
           "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI", "XXII"]
LETRAS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"


# ---------------------------------------------------------------- utilidades

def limpiar(t):
    return " ".join((t or "").split())


def mapa_figuras():
    """Asocia cada archivo con la lista de PNG que le corresponden, en orden."""
    RE_SVG = re.compile(r"<svg\b.*?</svg>", re.DOTALL | re.IGNORECASE)
    mapa, n = {}, 0
    for archivo in ORDEN_FIGURAS:
        crudo = io.open(os.path.join(INFORME, archivo), encoding="utf-8").read()
        cuantas = len(RE_SVG.findall(crudo))
        mapa[archivo] = []
        for _ in range(cuantas):
            n += 1
            mapa[archivo].append(os.path.join(FIGURAS, "fig%02d.png" % n))
    return mapa


def sacar_runs(el):
    """Convierte un elemento en una lista de (texto, negrita, cursiva, monoespaciada)."""
    runs = []

    def visitar(nodo, b, i, m):
        etiqueta = nodo.tag if isinstance(nodo.tag, str) else ""
        clases = (nodo.get("class") or "")
        nb = b or etiqueta in ("strong", "b")
        ni = i or etiqueta in ("em", "i")
        nm = m or etiqueta == "code" or "mono" in clases
        if nodo.text:
            runs.append((nodo.text, nb, ni, nm))
        for hijo in nodo:
            visitar(hijo, nb, ni, nm)
            if hijo.tail:
                runs.append((hijo.tail, b, i, m))

    if el.text:
        runs.append((el.text, False, False, False))
    for hijo in el:
        visitar(hijo, False, False, False)
        if hijo.tail:
            runs.append((hijo.tail, False, False, False))

    # Colapsa espacios sin perder los separadores entre runs.
    limpios = []
    for t, b, i, m in runs:
        t = re.sub(r"\s+", " ", t)
        if t:
            limpios.append((t, b, i, m))
    return limpios


def ancho_codigo(texto):
    return max((len(l) for l in texto.split("\n")), default=0)


# ---------------------------------------------------------------- extraccion

def extraer(archivo, indices, figs, base=1, sintetico=None):
    """Devuelve la lista de bloques de un archivo, en orden de lectura.

    `base` indica el nivel de los <section> del archivo: 1 = seccion numerada
    del informe, 2 = subseccion (para el capitulo de arquitectura).
    `sintetico` antepone un titulo de seccion que el archivo no trae.
    """
    ruta = os.path.join(INFORME, archivo)
    doc = lxml.html.parse(ruta, ANALIZADOR).getroot()
    secciones = doc.findall(".//section")
    if indices is not None:
        secciones = [secciones[i] for i in indices]

    # Reparto de figuras: se asignan por orden de aparicion dentro del archivo.
    pendientes = list(figs.get(archivo, []))
    if indices is not None and archivo == "09-18-tecnologias-e-ia.html":
        # El archivo tiene 2 svg: el primero pertenece a la seccion 18 (indice 1).
        # Se comprueba contando los svg de cada <section>.
        todas = doc.findall(".//section")
        antes = sum(len(s.findall(".//svg")) for s in todas[:indices[0]])
        cuantas = sum(len(s.findall(".//svg")) for s in secciones)
        pendientes = pendientes[antes:antes + cuantas]

    bloques = []
    if sintetico:
        bloques.append(("h1", "", sintetico))

    for sec in secciones:
        es_refs = "refs" in (sec.get("class") or "")
        cab = sec.find(".//div[@class='sec-head']")
        num, titulo = "", None
        if cab is not None:
            sn = cab.find(".//span[@class='sec-num']")
            h2 = cab.find(".//h2")
            num = limpiar(sn.text_content()) if sn is not None else ""
            titulo = limpiar(h2.text_content()) if h2 is not None else ""

        # Una seccion sin numero propio (p. ej. "Referencias de esta seccion")
        # no es una de las 22: se integra como subseccion de la anterior.
        nivel = base if num.isdigit() else max(base, 2)
        if titulo:
            bloques.append(("h1" if nivel == 1 else "h2", num, titulo)
                           if nivel == 1 else ("h2", titulo))

        nivel_h3 = 2 if nivel == 1 else 3

        # De las secciones de referencias solo se conserva el titulo de la 22:
        # su contenido lo sustituye la bibliografia unificada del final.
        if es_refs:
            continue

        for el in sec.iter():
            etiqueta = el.tag if isinstance(el.tag, str) else ""
            clases = el.get("class") or ""

            if etiqueta == "h3":
                texto_h3 = re.sub(r"^[A-Z]\.\s+", "", limpiar(el.text_content()))
                bloques.append(("h2" if nivel_h3 == 2 else "h3", texto_h3))

            elif etiqueta == "p" and el.getparent() is not None and \
                    "body" in (el.getparent().get("class") or ""):
                runs = sacar_runs(el)
                if runs:
                    bloques.append(("p", runs))

            elif etiqueta == "p" and re.match(
                    r"(resumen|abstract)", (el.getparent().get("class") or "")):
                bloques.append(("abstract", sacar_runs(el)))

            elif etiqueta == "div" and "callout" in clases:
                lead = el.find(".//span[@class='lead']")
                texto_lead = limpiar(lead.text_content()) if lead is not None else ""
                paras = [sacar_runs(p) for p in el.findall(".//p")]
                bloques.append(("callout", texto_lead, paras))

            elif etiqueta == "div" and "metrics" in clases:
                pares = []
                for m in el.findall(".//div[@class='metric']"):
                    c = m.find(".//span[@class='cifra']")
                    e = m.find(".//span[@class='etiqueta']")
                    if c is not None and e is not None:
                        pares.append((limpiar(c.text_content()), limpiar(e.text_content())))
                if pares:
                    bloques.append(("metricas", pares))

            elif etiqueta == "table":
                envoltorio = el.getparent()
                cap = None
                if envoltorio is not None:
                    c = envoltorio.find(".//p[@class='tbl-cap']")
                    if c is not None:
                        cap = c
                etiqueta_tab, titulo_tab = "", ""
                if cap is not None:
                    b = cap.find("b")
                    etiqueta_tab = limpiar(b.text_content()) if b is not None else ""
                    titulo_tab = limpiar(cap.text_content().replace(etiqueta_tab, "", 1))
                encab = [limpiar(th.text_content()) for th in el.findall(".//thead//th")]
                filas = []
                for tr in el.findall(".//tbody//tr"):
                    filas.append([limpiar(td.text_content()) for td in tr.findall("./td")])
                if filas:
                    bloques.append(("tabla", etiqueta_tab, titulo_tab, encab, filas))

            elif etiqueta == "pre":
                # Los archivos vienen con fin de linea CRLF: el \r sobrante
                # provocaba un salto de linea extra dentro de Word.
                texto = el.text_content().replace("\r\n", "\n").replace("\r", "\n")
                texto = texto.strip("\n")
                siguiente = el.getnext()
                cap = ""
                if siguiente is not None and "caption-code" in (siguiente.get("class") or ""):
                    cap = limpiar(siguiente.text_content())
                bloques.append(("codigo", texto, cap))

            elif etiqueta == "figure":
                fc = el.find(".//figcaption")
                cap = limpiar(fc.text_content()) if fc is not None else ""
                png = pendientes.pop(0) if pendientes else None
                bloques.append(("figura", png, cap))

            elif etiqueta == "ol":
                items = [limpiar(li.text_content()) for li in el.findall("./li")]
                if items:
                    bloques.append(("lista", items))

    return bloques


# ---------------------------------------------------------------- documento

def columnas(seccion, n, medianil=288):
    sectPr = seccion._sectPr
    cols = sectPr.find(qn("w:cols"))
    if cols is None:
        cols = OxmlElement("w:cols")
        sectPr.append(cols)
    cols.set(qn("w:num"), str(n))
    cols.set(qn("w:space"), str(medianil))
    cols.set(qn("w:equalWidth"), "1")


def sombrear(parrafo, color):
    pPr = parrafo._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), color)
    pPr.append(shd)


def borde_izq(parrafo, color="7F7F7F"):
    pPr = parrafo._p.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    left = OxmlElement("w:left")
    left.set(qn("w:val"), "single")
    left.set(qn("w:sz"), "12")
    left.set(qn("w:space"), "6")
    left.set(qn("w:color"), color)
    pbdr.append(left)
    pPr.append(pbdr)


class Constructor(object):
    def __init__(self):
        self.doc = Document()
        self._preparar()
        self.n_cols = 1
        self.contador_seccion = PRIMERA_SECCION - 1

    def _preparar(self):
        s = self.doc.sections[0]
        s.page_width, s.page_height = Inches(8.5), Inches(11)
        s.top_margin, s.bottom_margin = Inches(0.75), Inches(1.0)
        s.left_margin = s.right_margin = Inches(0.625)
        columnas(s, 1)

        normal = self.doc.styles["Normal"]
        normal.font.name = SERIF
        normal.font.size = Pt(10)
        rpr = normal.element.get_or_add_rPr()
        rf = rpr.find(qn("w:rFonts"))
        if rf is None:
            rf = OxmlElement("w:rFonts")
            rpr.append(rf)
        for a in ("w:ascii", "w:hAnsi", "w:cs"):
            rf.set(qn(a), SERIF)
        lang = OxmlElement("w:lang")
        lang.set(qn("w:val"), "es-CO")
        rpr.append(lang)
        pf = normal.paragraph_format
        pf.space_before, pf.space_after = Pt(0), Pt(0)
        pf.line_spacing = 1.0

    # -- control de columnas -------------------------------------------------
    def cambiar(self, n):
        if n == self.n_cols:
            return
        s = self.doc.add_section(WD_SECTION.CONTINUOUS)
        s.page_width, s.page_height = Inches(8.5), Inches(11)
        s.top_margin, s.bottom_margin = Inches(0.75), Inches(1.0)
        s.left_margin = s.right_margin = Inches(0.625)
        columnas(s, n)
        self.n_cols = n

    # -- piezas --------------------------------------------------------------
    def parrafo(self, alineacion=WD_ALIGN_PARAGRAPH.JUSTIFY, sangria=0.0,
                antes=0, despues=0):
        p = self.doc.add_paragraph()
        p.alignment = alineacion
        pf = p.paragraph_format
        pf.first_line_indent = Inches(sangria)
        pf.space_before, pf.space_after = Pt(antes), Pt(despues)
        return p

    def escribir(self, p, runs, tam=10, color=None):
        for texto, b, i, m in runs:
            r = p.add_run(texto)
            r.font.name = MONO if m else SERIF
            r.font.size = Pt(tam - 1.5 if m else tam)
            r.bold, r.italic = b, i
            if color:
                r.font.color.rgb = color
        return p

    def portada(self, titulo, subtitulo, contexto, autores):
        """Titulo y bloque de autores, a una sola columna como en IEEE."""
        p = self.parrafo(WD_ALIGN_PARAGRAPH.CENTER, despues=6)
        r = p.add_run(titulo)
        r.font.size, r.font.name = Pt(24), SERIF

        if subtitulo:
            p = self.parrafo(WD_ALIGN_PARAGRAPH.CENTER, despues=8)
            r = p.add_run(subtitulo)
            r.font.size, r.italic = Pt(11), True

        for linea in contexto:
            p = self.parrafo(WD_ALIGN_PARAGRAPH.CENTER, despues=0)
            r = p.add_run(linea)
            r.font.size = Pt(10)

        # Los autores van en columnas, uno al lado del otro: es la disposicion
        # de la plantilla de conferencia IEEE. Se usa una tabla sin bordes.
        if autores:
            self.parrafo(despues=4)
            t = self.doc.add_table(rows=1, cols=len(autores))
            t.alignment = WD_TABLE_ALIGNMENT.CENTER
            for celda, a in zip(t.rows[0].cells, autores):
                celda.width = Inches(ANCHO_TOTAL / len(autores))
                primero = True
                for texto, tam, cursiva, mono in a:
                    cp = celda.paragraphs[0] if primero else celda.add_paragraph()
                    primero = False
                    cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    cp.paragraph_format.space_before = Pt(0)
                    cp.paragraph_format.space_after = Pt(0)
                    r = cp.add_run(texto)
                    r.font.size, r.italic = Pt(tam), cursiva
                    r.font.name = MONO if mono else SERIF
        self.parrafo(despues=10)

    def h1(self, numero, texto):
        self.contador_seccion += 1
        romano = ROMANOS[self.contador_seccion] if self.contador_seccion < len(ROMANOS) else str(self.contador_seccion)
        self.letra_sub = 0
        p = self.parrafo(WD_ALIGN_PARAGRAPH.CENTER, antes=12, despues=4)
        r = p.add_run("%s. %s" % (romano, texto))
        r.font.size, r.font.small_caps = Pt(10), True

    def h2(self, texto):
        self.letra_sub = getattr(self, "letra_sub", 0)
        letra = LETRAS[self.letra_sub % 26]
        self.letra_sub += 1
        self.n_subsub = 0
        p = self.parrafo(WD_ALIGN_PARAGRAPH.LEFT, antes=8, despues=2)
        r = p.add_run("%s. %s" % (letra, texto))
        r.font.size, r.italic = Pt(10), True

    def h3(self, texto):
        self.n_subsub = getattr(self, "n_subsub", 0) + 1
        p = self.parrafo(WD_ALIGN_PARAGRAPH.LEFT, sangria=0.2, antes=5, despues=1)
        r = p.add_run("%d) %s:" % (self.n_subsub, texto))
        r.font.size, r.italic = Pt(10), True

    def cuerpo(self, runs):
        p = self.parrafo(sangria=0.2, despues=0)
        self.escribir(p, runs, 10)

    def resumen(self, runs, etiqueta):
        p = self.parrafo(sangria=0.0, despues=4)
        r = p.add_run(etiqueta)
        r.font.size, r.bold, r.italic = Pt(9), True, True
        for texto, b, i, m in runs:
            r = p.add_run(texto)
            r.font.size, r.bold, r.italic = Pt(9), True, True
            r.font.name = MONO if m else SERIF

    def aviso(self, lead, paras):
        if lead:
            p = self.parrafo(WD_ALIGN_PARAGRAPH.LEFT, antes=5, despues=1)
            p.paragraph_format.left_indent = Inches(0.12)
            borde_izq(p)
            r = p.add_run(lead)
            r.font.size, r.bold, r.font.small_caps = Pt(8), True, True
        for runs in paras:
            p = self.parrafo(despues=1)
            p.paragraph_format.left_indent = Inches(0.12)
            borde_izq(p)
            self.escribir(p, runs, 9)
        self.parrafo(despues=3)

    def codigo(self, texto, pie, ancho_completo):
        self.cambiar(1 if ancho_completo else 2)
        for linea in texto.split("\n"):
            p = self.parrafo(WD_ALIGN_PARAGRAPH.LEFT, despues=0)
            p.paragraph_format.left_indent = Inches(0.08)
            sombrear(p, "F2F2F2")
            r = p.add_run(linea if linea.strip() else " ")
            r.font.name, r.font.size = MONO, Pt(7.5)
        if pie:
            p = self.parrafo(WD_ALIGN_PARAGRAPH.LEFT, antes=2, despues=6)
            r = p.add_run(pie)
            r.font.size, r.italic = Pt(8), True

    def figura(self, png, pie):
        if not png or not os.path.exists(png):
            return
        self.cambiar(1)
        p = self.parrafo(WD_ALIGN_PARAGRAPH.CENTER, antes=6, despues=2)
        p.add_run().add_picture(png, width=Inches(ANCHO_TOTAL))
        if pie:
            p = self.parrafo(WD_ALIGN_PARAGRAPH.CENTER, despues=8)
            r = p.add_run(pie)
            r.font.size = Pt(8)

    def tabla(self, etiqueta, titulo, encab, filas, ancho_completo):
        self.cambiar(1 if ancho_completo else 2)
        ancho = ANCHO_TOTAL if ancho_completo else ANCHO_COL

        if etiqueta:
            p = self.parrafo(WD_ALIGN_PARAGRAPH.CENTER, antes=8, despues=0)
            r = p.add_run(etiqueta.rstrip("."))
            r.font.size = Pt(8)
        if titulo:
            p = self.parrafo(WD_ALIGN_PARAGRAPH.CENTER, despues=2)
            r = p.add_run(titulo)
            r.font.size, r.font.small_caps = Pt(8), True

        ncols = max(len(encab), max(len(f) for f in filas))
        t = self.doc.add_table(rows=0, cols=ncols)
        t.style = "Table Grid"
        t.alignment = WD_TABLE_ALIGNMENT.CENTER

        if encab:
            celdas = t.add_row().cells
            for j in range(ncols):
                texto = encab[j] if j < len(encab) else ""
                cp = celdas[j].paragraphs[0]
                cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
                cp.paragraph_format.space_after = Pt(0)
                r = cp.add_run(texto)
                r.font.size, r.bold, r.font.name = Pt(7.5), True, SERIF
        for fila in filas:
            celdas = t.add_row().cells
            for j in range(ncols):
                texto = fila[j] if j < len(fila) else ""
                cp = celdas[j].paragraphs[0]
                cp.paragraph_format.space_after = Pt(0)
                r = cp.add_run(texto)
                r.font.size, r.font.name = Pt(7.5), SERIF
        for fila in t.columns:
            for c in fila.cells:
                c.width = Inches(ancho / ncols)
        self.parrafo(despues=6)

    def metricas(self, pares):
        self.cambiar(1)
        p = self.parrafo(WD_ALIGN_PARAGRAPH.CENTER, antes=6, despues=2)
        r = p.add_run("MAGNITUDES DEL PRODUCTO")
        r.font.size, r.bold = Pt(8), True
        ncols = 3
        filas = [pares[i:i + ncols] for i in range(0, len(pares), ncols)]
        t = self.doc.add_table(rows=0, cols=ncols)
        t.style = "Table Grid"
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        for fila in filas:
            celdas = t.add_row().cells
            for j in range(ncols):
                cp = celdas[j].paragraphs[0]
                cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
                cp.paragraph_format.space_after = Pt(0)
                if j < len(fila):
                    r = cp.add_run(fila[j][0] + "\n")
                    r.font.size, r.bold = Pt(11), True
                    r2 = cp.add_run(fila[j][1])
                    r2.font.size = Pt(7.5)
                celdas[j].width = Inches(ANCHO_TOTAL / ncols)
        self.parrafo(despues=6)

    def bibliografia(self, entradas):
        """Lista unica, numerada y con sangria francesa, en 8 pt como IEEE."""
        self.cambiar(2)
        for numero, texto in entradas:
            p = self.parrafo(WD_ALIGN_PARAGRAPH.LEFT, despues=2)
            p.paragraph_format.left_indent = Inches(0.26)
            p.paragraph_format.first_line_indent = Inches(-0.26)
            r = p.add_run("[%d]\t" % numero)
            r.font.size = Pt(8)
            r = p.add_run(texto)
            r.font.size = Pt(8)

    def vinetas(self, items):
        self.cambiar(2)
        for texto in items:
            p = self.parrafo(despues=1)
            p.paragraph_format.left_indent = Inches(0.24)
            p.paragraph_format.first_line_indent = Inches(-0.12)
            r = p.add_run(u"— " + texto)
            r.font.size = Pt(9.5)


# ---------------------------------------------------------------- ensamblaje

def main():
    figs = mapa_figuras()

    # ---- portada, tomada del encabezado de 01-07 --------------------------
    doc0 = lxml.html.parse(os.path.join(INFORME, "01-07-planteamiento.html"), ANALIZADOR).getroot()
    h1 = doc0.find(".//h1")
    titulo = limpiar(h1.text_content()) if h1 is not None else "Nexcode"

    def texto_de(clase):
        el = doc0.find(".//p[@class='%s']" % clase)
        return limpiar(el.text_content()) if el is not None else ""

    subtitulo = texto_de("sub")
    contexto = [t for t in (texto_de("institucion"), texto_de("asignatura")) if t]

    # Los autores son una secuencia plana de span: cada 'nombre' abre uno nuevo.
    autores = []
    cont = doc0.find(".//div[@class='autores']")
    if cont is not None:
        actual = None
        for span in cont.iter("span"):
            clase = span.get("class") or ""
            texto = limpiar(span.text_content())
            if not texto:
                continue
            if "nombre" in clase:
                actual = [(texto, 11, False, False)]
                autores.append(actual)
            elif actual is not None:
                if "correo" in clase:
                    actual.append((texto, 9, False, True))
                else:
                    actual.append((texto, 9, True, False))

    # ---- primera pasada: reunir todos los bloques -------------------------
    bloques = []
    for archivo, indices, base, sintetico in ORDEN:
        bloques.extend(extraer(archivo, indices, figs, base, sintetico))

    # ---- renumeracion en orden de lectura ---------------------------------
    mapas = renumerar.construir_mapas(bloques)
    reescribir = renumerar.hacer_reescritor(*mapas)
    bloques = renumerar.aplicar(bloques, reescribir)
    print("  renumerados: %d tablas, %d figuras, %d fragmentos, %d salidas"
          % tuple(len(m) for m in mapas))

    # ---- citas bibliograficas ---------------------------------------------
    # Primero se registran las citas que ya venian escritas, para no repetir
    # la fuente; despues se recorre el texto anadiendo las que faltaban.
    citador = bibliografia.Citador()
    for b in bloques:
        if b[0] in ("p", "abstract"):
            citador.marcar_existentes(" ".join(t for t, _, _, _ in b[1]))

    # El resumen y las palabras clave se dejan sin citas: en IEEE el resumen
    # debe sostenerse solo, y los terminos de indice no se citan. Ademas, si se
    # citara ahi, la primera aparicion de cada tecnologia se gastaria en una
    # lista de palabras sueltas en vez de en el texto que la explica.
    SIN_CITAS = (u"Resumen", u"Palabras clave")
    seccion_actual = u""
    con_citas = []
    for b in bloques:
        if b[0] == "h1":
            seccion_actual = b[2]
        if b[0] in ("p", "abstract") and seccion_actual not in SIN_CITAS:
            con_citas.append((b[0], citador.procesar(b[1])))
        else:
            con_citas.append(b)
    bloques = con_citas
    bloques.append(("bibliografia", citador.lista_final()))

    faltan = citador.sin_citar()
    print("  citas insertadas: %d   bibliografia: %d entradas"
          % (citador.insertadas, len(citador.lista_final())))
    if faltan:
        print("  referencias sin citar (excluidas): " + ", ".join(faltan))

    # ---- segunda pasada: escribir el documento ----------------------------
    c = Constructor()
    c.portada(titulo, subtitulo, contexto, autores)
    c.cambiar(2)

    total = {"h1": 0, "tabla": 0, "figura": 0, "codigo": 0, "p": 0}

    for b in bloques:
        tipo = b[0]
        if tipo == "h1":
            c.cambiar(2)
            c.h1(b[1], b[2])
            total["h1"] += 1
        elif tipo == "h2":
            c.cambiar(2)
            c.h2(b[1])
        elif tipo == "h3":
            c.cambiar(2)
            c.h3(b[1])
        elif tipo == "p":
            c.cambiar(2)
            c.cuerpo(b[1])
            total["p"] += 1
        elif tipo == "abstract":
            c.cambiar(2)
            c.resumen(b[1], "")
        elif tipo == "callout":
            c.cambiar(2)
            c.aviso(b[1], b[2])
        elif tipo == "metricas":
            c.metricas(b[1])
        elif tipo == "tabla":
            ncols = max(len(b[3]), max(len(f) for f in b[4]))
            largo = max((len(x) for f in b[4] for x in f), default=0)
            ancho_completo = ncols >= 5 or (ncols == 4 and largo > 32) or largo > 58
            c.tabla(b[1], b[2], b[3], b[4], ancho_completo)
            total["tabla"] += 1
        elif tipo == "codigo":
            c.codigo(b[1], b[2], ancho_codigo(b[1]) > MAX_CHAR_COL)
            total["codigo"] += 1
        elif tipo == "figura":
            c.figura(b[1], b[2])
            total["figura"] += 1
        elif tipo == "lista":
            c.vinetas(b[1])
        elif tipo == "bibliografia":
            c.bibliografia(b[1])

    c.cambiar(2)
    c.doc.save(DESTINO)

    kb = os.path.getsize(DESTINO) / 1024.0
    print("Generado: %s" % DESTINO)
    print("  %.0f KB" % kb)
    print("  secciones=%d  parrafos=%d  tablas=%d  figuras=%d  bloques de codigo=%d"
          % (total["h1"], total["p"], total["tabla"], total["figura"], total["codigo"]))


if __name__ == "__main__":
    main()
