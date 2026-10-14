#!/usr/bin/env python3
"""
Crea la grafica di presentazione del Play Store (1024 x 500) con l'icona e i colori dell'app.

    python3 docs/play/genera_grafica.py       # serve ImageMagick (convert); scrive docs/play/grafica_1024x500.png
"""
import base64
import subprocess
from pathlib import Path

QUI = Path(__file__).parent
ICONA = QUI.parent / "icona" / "icona_512.png"
FONT = "'Noto Sans','Liberation Sans','DejaVu Sans',Arial,sans-serif"


def svg(titolo, righe, suggerimenti):
    icona = base64.b64encode(ICONA.read_bytes()).decode()
    chip = ""
    x = 408
    for testo, largh, acceso in suggerimenti:
        fill = "#3DD6D0" if acceso else "#E6F6FF"
        col = "#06222E" if acceso else "#123F59"
        chip += (f'<rect x="{x}" y="360" width="{largh}" height="58" rx="29" fill="{fill}"/>'
                 f'<text x="{x + largh / 2}" y="398" font-size="26" font-weight="700" fill="{col}" '
                 f'text-anchor="middle">{testo}</text>')
        x += largh + 16
    sotto = "".join(
        f'<text x="408" y="{240 + i * 40}" font-size="27" fill="#BFE3F2">{r}</text>' for i, r in enumerate(righe)
    )
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500" font-family="{FONT}">
<defs>
<linearGradient id="sf" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1E6F96"/><stop offset="1" stop-color="#0A2536"/></linearGradient>
<clipPath id="ang"><rect x="84" y="108" width="284" height="284" rx="64"/></clipPath>
</defs>
<rect width="1024" height="500" fill="url(#sf)"/>
<circle cx="930" cy="70" r="210" fill="#3DD6D0" fill-opacity="0.07"/>
<circle cx="1000" cy="470" r="150" fill="#FFFFFF" fill-opacity="0.05"/>
<rect x="84" y="124" width="284" height="284" rx="64" fill="#000" fill-opacity="0.30"/>
<image href="data:image/png;base64,{icona}" x="84" y="108" width="284" height="284" clip-path="url(#ang)"/>
<text x="404" y="176" font-size="58" font-weight="800" fill="#FFFFFF">{titolo}</text>
{sotto}
{chip}
</svg>
'''


def crea(nome, titolo, righe, suggerimenti):
    sorgente = QUI / f"{nome}.svg"
    sorgente.write_text(svg(titolo, righe, suggerimenti), encoding="utf-8")
    png = QUI / f"{nome}.png"
    subprocess.run(["convert", "-background", "none", str(sorgente), f"PNG24:{png}"], check=True)
    sorgente.unlink()
    print("scritto", png.name)


if __name__ == "__main__":
    crea("grafica_1024x500", "La mia tastiera",
         ["Privata per scelta: nessun permesso Internet.", "Corregge, prevede e impara da te."],
         [("detto", 112, False), ("fatto", 118, True), ("bisogno", 158, False)])
    crea("grafica_1024x500_en", "My Keyboard",
         ["Private by design: no Internet permission.", "It corrects, predicts and learns from you."],
         [("detto", 112, False), ("fatto", 118, True), ("bisogno", 158, False)])
