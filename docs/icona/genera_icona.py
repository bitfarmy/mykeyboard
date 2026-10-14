#!/usr/bin/env python3
"""
Disegna l'icona dell'app una volta sola e ne ricava tutte le versioni:

  - icona adattiva Android (sfondo, primo piano e versione monocromatica per le icone a tema)
  - docs/icona/icona.svg e docs/icona/icona_512.png (l'icona da 512x512 chiesta dal Play Store)

Una tastiera vista dall'alto: corpo chiaro con il suo spessore, tasti blu, la barra spaziatrice in
verde acqua (è lo spazio che applica la correzione). I colori sono quelli del tema "Mare" dell'app.

    python3 docs/icona/genera_icona.py        # serve ImageMagick (convert) per il PNG
"""
import subprocess
from pathlib import Path

QUI = Path(__file__).parent
RES = QUI.parent.parent / "TastieraPersonale" / "app" / "src" / "main" / "res"

# Tutto è disegnato su una tela di 108 x 108 (la griglia delle icone adattive): la parte che resta sempre
# visibile è il cerchio di 66 al centro, quindi la tastiera sta lì dentro.
SFONDO = [(0.0, "#1E6F96"), (1.0, "#0A2536")]            # dall'alto in basso
CORPO = [(0.0, "#FFFFFF"), (1.0, "#CBE5F0")]
SPESSORE = [(0.0, "#86C0D5"), (1.0, "#4A93B0")]            # il lato del corpo, sotto
RIFLESSO = [(0.0, "#80FFFFFF"), (1.0, "#00FFFFFF")]
TASTO = [(0.0, "#1F6489"), (1.0, "#123F59")]
TASTO_LATO = "#0A2A3D"
BARRA = [(0.0, "#3BE0D3"), (1.0, "#0FA395")]
BARRA_LATO = "#0A766C"
OMBRA = "#59000000"

# Il corpo: x, y, larghezza, altezza, raggio
CORPO_R = (26, 35, 56, 37, 8)
SPESSORE_R = (26, 38, 56, 37, 8)
OMBRA_R = (26, 43, 56, 37, 8)
MARGINE = 4.4
TASTO_L, TASTO_A, SPAZIO = 7.68, 7.6, 2.2
LATO = 1.4                                                # quanto sporge il lato di ogni tasto


def f(n):
    return f"{n:g}"


def rettangolo(x, y, w, h, r):
    """Rettangolo con gli angoli arrotondati, come percorso."""
    return (f"M{f(x + r)},{f(y)}h{f(w - 2 * r)}a{f(r)},{f(r)} 0 0 1 {f(r)},{f(r)}v{f(h - 2 * r)}"
            f"a{f(r)},{f(r)} 0 0 1 -{f(r)},{f(r)}h-{f(w - 2 * r)}a{f(r)},{f(r)} 0 0 1 -{f(r)},-{f(r)}"
            f"v-{f(h - 2 * r)}a{f(r)},{f(r)} 0 0 1 {f(r)},-{f(r)}z")


def tasti():
    """(x, y, larghezza) di ogni tasto normale e della barra spaziatrice."""
    cx, cy, cw = CORPO_R[0] + MARGINE, CORPO_R[1] + MARGINE, CORPO_R[2] - 2 * MARGINE
    passo = TASTO_L + SPAZIO
    normali = []
    for i in range(5):
        normali.append((cx + i * passo, cy, TASTO_L))
    for i in range(4):
        normali.append((cx + passo / 2 + i * passo, cy + TASTO_A + SPAZIO, TASTO_L))
    y3 = cy + 2 * (TASTO_A + SPAZIO)
    normali.append((cx, y3, TASTO_L))
    normali.append((cx + cw - TASTO_L, y3, TASTO_L))
    barra = (cx + passo, y3, cw - 2 * passo)
    return normali, barra


def forme():
    """Il primo piano: elenco di (percorso, riempimento, tratto, spessore del tratto).
    Un riempimento è un colore "#AARRGGBB" oppure (punti, x1, y1, x2, y2) per una sfumatura."""
    cx, cy, cw, ch, cr = CORPO_R
    normali, barra = tasti()
    lato = " ".join(rettangolo(x, y + LATO, w, TASTO_A, 1.8) for x, y, w in normali)
    faccia = " ".join(rettangolo(x, y, w, TASTO_A, 1.8) for x, y, w in normali)
    bx, by, bw = barra
    return [
        (rettangolo(*OMBRA_R), OMBRA, None, None),
        (rettangolo(*SPESSORE_R), (SPESSORE, 54, 38, 54, 75), None, None),
        (rettangolo(*CORPO_R), (CORPO, 54, cy, 54, cy + ch), None, None),
        (rettangolo(cx + 1.2, cy + 1.2, cw - 2.4, 13, cr - 1.5), (RIFLESSO, 54, cy + 1, 54, cy + 14), None, None),
        (lato, TASTO_LATO, None, None),
        (faccia, (TASTO, 54, cy + MARGINE, 54, cy + ch - MARGINE), None, None),
        (rettangolo(bx, by + LATO, bw, TASTO_A, 1.8), BARRA_LATO, None, None),
        (rettangolo(bx, by, bw, TASTO_A, 1.8), (BARRA, 54, by, 54, by + TASTO_A), None, None),
    ]


def monocromatica():
    """Solo la forma, per le icone a tema: il sistema la tinge."""
    cx, cy, cw, ch, cr = CORPO_R
    normali, barra = tasti()
    tutti = " ".join(rettangolo(x, y, w, TASTO_A, 1.8) for x, y, w in normali + [barra])
    nero = "#FF000000"
    return [
        (rettangolo(cx + 1.3, cy + 1.3, cw - 2.6, ch - 2.6, cr - 1.3), None, nero, 2.6),
        (tutti, nero, None, None),
    ]


# ---------- Android ----------

def gradiente_xml(nome, g):
    stops, x1, y1, x2, y2 = g
    voci = "".join(f'<item android:offset="{o:g}" android:color="{c}"/>' for o, c in stops)
    return (f'<aapt:attr name="android:{nome}"><gradient android:startX="{f(x1)}" android:startY="{f(y1)}" '
            f'android:endX="{f(x2)}" android:endY="{f(y2)}" android:type="linear">{voci}</gradient></aapt:attr>')


def xml_forma(d, riempimento, tratto, spessore):
    attr = f'android:pathData="{d}"'
    interno = ""
    if isinstance(riempimento, str):
        attr += f' android:fillColor="{riempimento}"'
    elif riempimento:
        interno += gradiente_xml("fillColor", riempimento)
    if tratto:
        attr += (f' android:strokeColor="{tratto}" android:strokeWidth="{f(spessore)}"'
                 ' android:strokeLineCap="round" android:strokeLineJoin="round"')
    return f"    <path {attr}>{interno}</path>\n" if interno else f"    <path {attr} />\n"


def xml_vettore(corpo):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generato da docs/icona/genera_icona.py: non modificare a mano. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            + corpo + "</vector>\n")


def scrivi(file, testo):
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(testo, encoding="utf-8")
    print("scritto", file.relative_to(QUI.parent.parent))


def android():
    sfondo = xml_forma("M0,0h108v108h-108z", (SFONDO, 54, 0, 54, 108), None, None)
    scrivi(RES / "drawable" / "ic_launcher_background.xml", xml_vettore(sfondo))
    scrivi(RES / "drawable" / "ic_launcher_foreground.xml", xml_vettore("".join(xml_forma(*s) for s in forme())))
    scrivi(RES / "drawable" / "ic_launcher_monochrome.xml",
           xml_vettore("".join(xml_forma(*s) for s in monocromatica())))
    adattiva = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@drawable/ic_launcher_background" />\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
                '</adaptive-icon>\n')
    scrivi(RES / "mipmap-anydpi-v26" / "ic_launcher.xml", adattiva)
    scrivi(RES / "mipmap-anydpi-v26" / "ic_launcher_round.xml", adattiva)


# ---------- SVG e PNG ----------

def colore_svg(c):
    """#AARRGGBB → (#RRGGBB, opacità)."""
    if len(c) == 9:
        return "#" + c[3:], int(c[1:3], 16) / 255
    return c, 1.0


def svg():
    defs = []
    contatore = [0]

    def riempi(r):
        if isinstance(r, str):
            col, op = colore_svg(r)
            return f'fill="{col}" fill-opacity="{op:.2f}"'
        stops, x1, y1, x2, y2 = r
        contatore[0] += 1
        i = f"g{contatore[0]}"
        voci = "".join(
            f'<stop offset="{o:g}" stop-color="{colore_svg(c)[0]}" stop-opacity="{colore_svg(c)[1]:.2f}"/>'
            for o, c in stops
        )
        defs.append(f'<linearGradient id="{i}" gradientUnits="userSpaceOnUse" x1="{f(x1)}" y1="{f(y1)}" '
                    f'x2="{f(x2)}" y2="{f(y2)}">{voci}</linearGradient>')
        return f'fill="url(#{i})"'

    pezzi = [f'<rect width="108" height="108" {riempi((SFONDO, 54, 0, 54, 108))}/>']
    for d, r, tratto, sp in forme():
        pezzi.append(f'<path d="{d}" {riempi(r)}/>')
    testo = ('<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">'
             "<defs>" + "".join(defs) + "</defs>" + "".join(pezzi) + "</svg>\n")
    scrivi(QUI / "icona.svg", testo)
    # Il Play Store vuole 512x512, 32 bit, a tutto riquadro (gli angoli li arrotonda lui)
    png = QUI / "icona_512.png"
    subprocess.run(["convert", "-background", "none", "-density", "384", str(QUI / "icona.svg"),
                    "-resize", "512x512", f"PNG32:{png}"], check=True)
    print("scritto", png.relative_to(QUI.parent.parent))


if __name__ == "__main__":
    android()
    svg()
