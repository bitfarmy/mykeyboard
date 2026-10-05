#!/usr/bin/env python3
"""
Genera le illustrazioni animate del README in stile Windows 95.

GitHub non esegue CSS o JavaScript nei README, ma mostra le immagini SVG con le loro
animazioni (SMIL). Tutto è disegnato qui con forme semplici: niente font o immagini esterne.

    python3 docs/img/genera_svg.py      # versioni italiane (*.svg) e inglesi (*_en.svg)
"""
from pathlib import Path

QUI = Path(__file__).parent
INGLESE = False  # True: genera le versioni *_en.svg per README.en.md


def t(it, en):
    """Il testo nella lingua dell'illustrazione."""
    return en if INGLESE else it
FONT = "'MS Sans Serif','Microsoft Sans Serif',Tahoma,'Segoe UI',Arial,sans-serif"
MONO = "'Courier New','Lucida Console','DejaVu Sans Mono',monospace"
GRIGIO, BIANCO, CHIARO, SCURO, NERO = "#c0c0c0", "#ffffff", "#dfdfdf", "#808080", "#000000"
BLU, TEAL = "#000080", "#008080"


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def rilievo(x, y, w, h, colore=GRIGIO, infossato=False, extra=""):
    """Un riquadro con il bordo smussato di Windows 95 (in rilievo o infossato)."""
    a, b, c, d = (SCURO, NERO, BIANCO, CHIARO) if infossato else (BIANCO, CHIARO, NERO, SCURO)
    return (
        f'<g shape-rendering="crispEdges" {extra}>'
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{colore}"/>'
        f'<path d="M{x + .5},{y + h - 1} V{y + .5} H{x + w - 1}" stroke="{a}" fill="none"/>'
        f'<path d="M{x + 1.5},{y + h - 2} V{y + 1.5} H{x + w - 2}" stroke="{b}" fill="none"/>'
        f'<path d="M{x},{y + h - .5} H{x + w - .5} V{y}" stroke="{c}" fill="none"/>'
        f'<path d="M{x + 1},{y + h - 1.5} H{x + w - 1.5} V{y + 1}" stroke="{d}" fill="none"/>'
        f"</g>"
    )


def testo(x, y, s, size=12, colore=NERO, peso="normal", anchor="start", font=FONT, extra=""):
    return (
        f'<text x="{x}" y="{y}" font-family="{font}" font-size="{size}" fill="{colore}" '
        f'font-weight="{peso}" text-anchor="{anchor}" xml:space="preserve" {extra}>{esc(s)}</text>'
    )


def finestra(x, y, w, h, titolo, attiva=True, pulsanti=("_", "□", "×")):
    """Finestra con barra del titolo sfumata e pulsanti."""
    sfumatura = "url(#titolo)" if attiva else SCURO
    out = [rilievo(x, y, w, h)]
    out.append(f'<rect x="{x + 3}" y="{y + 3}" width="{w - 6}" height="18" fill="{sfumatura}"/>')
    out.append(testo(x + 8, y + 16, titolo, 12, BIANCO, "bold"))
    bx = x + w - 5 - 16
    for p in reversed(pulsanti):
        out.append(rilievo(bx, y + 5, 16, 14))
        out.append(testo(bx + 8, y + 16, p, 11, NERO, "bold", "middle"))
        bx -= 18 if p != "×" else 20
    return "".join(out)


def svg(w, h, corpo, titolo):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" '
        f'role="img" aria-label="{esc(titolo)}">'
        f"<title>{esc(titolo)}</title>"
        '<defs><linearGradient id="titolo" x1="0" x2="1">'
        f'<stop offset="0" stop-color="{BLU}"/><stop offset="1" stop-color="#1084d0"/>'
        "</linearGradient></defs>"
        f"{corpo}</svg>\n"
    )


def discreto(attributo, valori, tempi, durata, extra=""):
    """Animazione a scatti che si ripete: valori[i] dal tempo tempi[i] (secondi)."""
    kt = ";".join(f"{t / durata:.4f}" for t in tempi)
    return (
        f'<animate attributeName="{attributo}" values="{";".join(map(str, valori))}" keyTimes="{kt}" '
        f'dur="{durata}s" calcMode="discrete" repeatCount="indefinite" {extra}/>'
    )


# ---------------------------------------------------------------------------
# 1. Copertina: il desktop, la tastiera che scrive e si corregge da sola
# ---------------------------------------------------------------------------
def copertina():
    W, H, D = 860, 436, 9.0
    o = [f'<rect width="{W}" height="{H}" fill="{TEAL}"/>']

    # Icone sul desktop
    def icona(x, y, etichetta, tipo):
        g = []
        if tipo == "pc":
            g.append(rilievo(x + 6, y, 36, 26, "#c0c0c0"))
            g.append(f'<rect x="{x + 10}" y="{y + 4}" width="28" height="18" fill="{TEAL}"/>')
            g.append(rilievo(x + 2, y + 30, 44, 8))
        elif tipo == "cartella":
            g.append(f'<path d="M{x + 4},{y + 8} h14 l4,4 h22 v26 h-40z" fill="#ffd94a" stroke="{NERO}"/>')
            g.append(f'<path d="M{x + 4},{y + 16} h40" stroke="#b08a00"/>')
        else:
            g.append(f'<path d="M{x + 10},{y} h20 l8,8 v30 h-28z" fill="{BIANCO}" stroke="{NERO}"/>')
            g.append(f'<path d="M{x + 30},{y} v8 h8" fill="none" stroke="{NERO}"/>')
            for i in range(4):
                g.append(f'<rect x="{x + 14}" y="{y + 14 + i * 5}" width="{18 - (i % 2) * 6}" height="2" fill="{BLU}"/>')
        g.append(testo(x + 24, y + 54, etichetta, 11, BIANCO, anchor="middle"))
        return "".join(g)

    o.append(icona(20, 24, t("Risorse del", "My"), "pc"))
    o.append(testo(44, 92, t("computer", "Computer"), 11, BIANCO, anchor="middle"))
    o.append(icona(20, 118, t("Dizionari", "Dictionaries"), "cartella"))
    o.append(icona(20, 200, t("LEGGIMI.txt", "README.txt"), "doc"))

    # Finestra principale
    x0, y0, w0, h0 = 110, 18, 720, 376
    o.append(finestra(x0, y0, w0, h0, t("⌨  La mia tastiera 2.0.1", "⌨  My Keyboard 2.0.1")))
    menu = t(["File", "Modifica", "Visualizza", "Lingue", "?"], ["File", "Edit", "View", "Languages", "Help"])
    mx = x0 + 10
    for m in menu:
        o.append(testo(mx, y0 + 38, m, 12))
        o.append(f'<rect x="{mx}" y="{y0 + 40}" width="6" height="1" fill="{NERO}"/>')
        mx += len(m) * 7 + 18

    # Campo di testo infossato con la frase che si scrive da sola
    fx, fy, fw, fh = x0 + 12, y0 + 50, w0 - 24, 70
    o.append(rilievo(fx, fy, fw, fh, BIANCO, infossato=True))
    prefisso, parola, corretta = t(("Ciao! Domani vieni anche tu, ", "perche", "perché"),
                                   ("See you tomorrow, my best ", "freind", "friend"))
    frase = prefisso + parola
    tx, ty = fx + 12, fy + 30
    ciclo = 9.0
    passo = 0.11
    tempi = [0.0] + [0.4 + i * passo for i in range(1, len(frase) + 1)]
    fine_battitura = tempi[-1]
    t_correzione = fine_battitura + 0.6
    t_reset = 8.4
    larghezze = [0] + [i * D for i in range(1, len(frase) + 1)]
    o.append(
        '<clipPath id="battitura"><rect x="%s" y="%s" height="30" width="0">%s</rect></clipPath>'
        % (tx, ty - 20, discreto("width", larghezze + [0], tempi + [t_reset], ciclo))
    )
    o.append(
        f'<g clip-path="url(#battitura)">'
        + testo(tx, ty, prefisso, 15, NERO, font=MONO, extra=f'textLength="{len(prefisso) * D}" lengthAdjust="spacing"')
        + "</g>"
    )
    px = tx + len(prefisso) * D
    # "perche" sbagliato: visibile finché lo spazio non lo corregge
    o.append(
        f'<g clip-path="url(#battitura)"><g>'
        + testo(px, ty, parola, 15, NERO, font=MONO, extra=f'textLength="{len(parola) * D}" lengthAdjust="spacing"')
        + discreto("opacity", [1, 0, 1], [0, t_correzione, t_reset], ciclo)
        + "</g></g>"
    )
    # sottolineatura rossa ondulata sotto la parola sbagliata
    onda = " ".join(f"{px + i * 3},{ty + 4 + (i % 2) * 2}" for i in range(int(len(parola) * D / 3) + 1))
    o.append(
        f'<polyline points="{onda}" fill="none" stroke="#ff0000" opacity="0">'
        + discreto("opacity", [0, 1, 0, 0], [0, fine_battitura, t_correzione, t_reset], ciclo)
        + "</polyline>"
    )
    # "perché" corretto, evidenziato come una selezione di Windows
    o.append(
        f'<g opacity="0"><rect x="{px - 1}" y="{ty - 15}" width="{len(corretta) * D + 2}" height="20" fill="{BLU}"/>'
        + testo(px, ty, corretta, 15, BIANCO, font=MONO, extra=f'textLength="{len(corretta) * D}" lengthAdjust="spacing"')
        + discreto("opacity", [0, 1, 0], [0, t_correzione, t_reset], ciclo)
        + "</g>"
    )
    # cursore lampeggiante che segue la battitura
    xs = [tx + l for l in larghezze]
    o.append(
        f'<rect x="{tx}" y="{ty - 15}" width="2" height="19" fill="{NERO}">'
        + discreto("x", xs + [px + len(corretta) * D + 9, tx], tempi + [t_correzione, t_reset], ciclo)
        + '<animate attributeName="opacity" values="1;0" dur="1s" calcMode="discrete" repeatCount="indefinite"/>'
        + "</rect>"
    )

    # Barra dei suggerimenti
    by = fy + fh + 8
    o.append(rilievo(fx, by, fw, 30, GRIGIO, infossato=True))
    suggerimenti = t([("perché", True), ("per", False), ("perciò", False)],
                     [("friend", True), ("friends", False), ("find", False)])
    sx = fx + 8
    gruppo = []
    for s, evid in suggerimenti:
        larg = 120
        if evid:
            gruppo.append(f'<rect x="{sx}" y="{by + 5}" width="{larg}" height="20" fill="{BLU}"/>')
        gruppo.append(testo(sx + larg / 2, by + 20, s, 13, BIANCO if evid else NERO, "bold" if evid else "normal", "middle"))
        sx += larg + 14
    t_sugg = tempi[len(prefisso) + 2]
    o.append(
        '<g opacity="0">' + "".join(gruppo)
        + discreto("opacity", [0, 1, 0], [0, t_sugg, t_correzione], ciclo) + "</g>"
    )
    for i, s in enumerate([",", "'", ".", "⚙"]):
        o.append(testo(fx + fw - 110 + i * 26, by + 21, s, 15, NERO, "bold", "middle"))

    # Tastiera: tasti in rilievo che si "abbassano" quando vengono premuti
    righe = ["qwertyuiop", "asdfghjkl", t("zxcvbnmè", "zxcvbnm")]
    kw, kh, gap = 50, 38, 6
    ky0 = by + 42
    premuti = {}
    for i, ch in enumerate(frase.lower()):
        premuti.setdefault(" " if ch in " ,!" else ch, []).append(tempi[i + 1])
    premuti.setdefault(" ", []).append(fine_battitura + 0.5)
    tasti_x = {}
    for r, riga in enumerate(righe):
        larghezza_riga = len(riga) * kw + (len(riga) - 1) * gap
        rx = fx + (fw - larghezza_riga) / 2 + (r == 2) * -40
        for c, ch in enumerate(riga):
            tasti_x[ch] = (rx + c * (kw + gap), ky0 + r * (kh + gap), kw)
    tasti_x[" "] = (fx + fw / 2 - 170, ky0 + 3 * (kh + gap), 340)
    ultima = tasti_x[righe[2][-1]]
    tasti_x["⌫"] = (ultima[0] + kw + gap, ky0 + 2 * (kh + gap), 70)

    def tasto(ch, x, y, w):
        etichetta = {" ": t("italiano", "english"), "⌫": "⌫"}.get(ch, ch)
        g = [rilievo(x, y, w, kh)]
        g.append(testo(x + w / 2, y + 25, etichetta, 15 if len(etichetta) == 1 else 12, NERO, anchor="middle"))
        momenti = premuti.get(ch)
        if momenti:
            vals, ts = [0], [0]
            for tm in sorted(momenti):
                vals += [1, 0]
                ts += [tm, min(tm + 0.12, ciclo - 0.01)]
            g.append(
                f'<g opacity="0">{rilievo(x, y, w, kh, "#a8a8a8", infossato=True)}'
                + testo(x + w / 2 + 1, y + 26, etichetta, 15 if len(etichetta) == 1 else 12, NERO, anchor="middle")
                + discreto("opacity", vals, ts, ciclo) + "</g>"
            )
        return "".join(g)

    for ch, (x, y, w) in tasti_x.items():
        o.append(tasto(ch, x, y, w))
    o.append(tasto("?123", fx + fw / 2 - 260, ky0 + 3 * (kh + gap), 80))
    invio_x = max(tasti_x["⌫"][0], fx + fw / 2 + 170 + gap)  # mai sopra la barra spaziatrice
    o.append(rilievo(invio_x, ky0 + 3 * (kh + gap), 70, kh, "#c0c0c0"))
    o.append(testo(invio_x + 35, ky0 + 3 * (kh + gap) + 25, "↵", 16, NERO, "bold", "middle"))

    # Barra di stato della finestra
    sy = y0 + h0 - 24
    o.append(rilievo(x0 + 4, sy, 420, 20, GRIGIO, infossato=True))
    o.append(rilievo(x0 + 428, sy, w0 - 432, 20, GRIGIO, infossato=True))
    o.append(
        '<g>' + testo(x0 + 10, sy + 15, t("Pronto.", "Ready."), 12)
        + discreto("opacity", [1, 0, 1], [0, t_correzione, t_reset], ciclo) + "</g>"
    )
    o.append(
        '<g opacity="0">' + testo(x0 + 10, sy + 15, t("Corretto: perche → perché  (⌫ per annullare)", "Corrected: freind → friend  (⌫ to undo)"), 12)
        + discreto("opacity", [0, 1, 0], [0, t_correzione, t_reset], ciclo) + "</g>"
    )
    o.append(testo(x0 + 436, sy + 15, t("🔒 Offline · nessun permesso Internet", "🔒 Offline · no Internet permission"), 12))

    # Barra delle applicazioni
    o.append(rilievo(0, H - 30, W, 30))
    o.append(rilievo(3, H - 27, 74, 24))
    o.append(f'<g transform="translate(9,{H - 22})">'
             '<rect width="6" height="6" fill="#ff0000"/><rect x="7" width="6" height="6" fill="#00a000"/>'
             '<rect y="7" width="6" height="6" fill="#0000ff"/><rect x="7" y="7" width="6" height="6" fill="#ffd000"/></g>')
    o.append(testo(46, H - 10, t("Avvio", "Start"), 12, NERO, "bold", "middle"))
    o.append(rilievo(84, H - 27, 190, 24, CHIARO, infossato=True))
    o.append(testo(94, H - 10, t("⌨ La mia tastiera 2.0.1", "⌨ My Keyboard 2.0.1"), 12, NERO, "bold"))
    o.append(rilievo(W - 84, H - 27, 80, 24, GRIGIO, infossato=True))
    o.append(testo(W - 44, H - 10, "09:35", 12, NERO, anchor="middle"))
    o.append(testo(W - 72, H - 10, "🔇", 11))
    return svg(W, H, "".join(o), t("La mia tastiera: finestra in stile Windows 95 che scrive e corregge perche in perché",
                                   "My Keyboard: a Windows 95 style window that types and corrects freind into friend"))


# ---------------------------------------------------------------------------
# 2. Copia dei dizionari: i fogli che volano da una cartella all'altra
# ---------------------------------------------------------------------------
def copia():
    W, H = 520, 200
    o = [finestra(0, 0, W, H, t("Copia dei dizionari in corso...", "Copying dictionaries..."), pulsanti=("×",))]

    def cartella(x, y, aperta=False):
        g = f'<path d="M{x},{y + 6} h14 l4,4 h26 v28 h-44z" fill="#ffd94a" stroke="{NERO}"/>'
        if aperta:
            g += f'<path d="M{x},{y + 38} l8,-20 h44 l-8,20z" fill="#ffe680" stroke="{NERO}"/>'
        return g

    o.append(cartella(40, 40, aperta=True))
    o.append(cartella(430, 40))
    # fogli che volano lungo un arco, sfasati nel tempo
    for i in range(4):
        foglio = (
            f'<g><path d="M0,0 h12 l5,5 v15 h-17z" fill="{BIANCO}" stroke="{NERO}"/>'
            f'<path d="M3,8 h9 M3,12 h11 M3,16 h7" stroke="{BLU}"/>'
            f'<animateMotion dur="2s" begin="{i * 0.5}s" repeatCount="indefinite" '
            f'path="M70,50 Q250,-10 430,50" keyPoints="0;1" keyTimes="0;1" calcMode="linear"/></g>'
        )
        o.append(foglio)
    lingue = ["en.txt", "es.txt", "fr.txt", "de.txt", "pt.txt"]
    o.append(testo(20, 112, t("Da:  ", "From: ") + "github.com/bitfarmy/mykeyboard-dizionari", 12))
    o.append(testo(20, 130, t("A:   Telefono\\Tastiera\\Dizionari", "To:   Phone\\Keyboard\\Dictionaries"), 12))
    for i, l in enumerate(lingue):
        o.append(
            f'<g opacity="0">{testo(330, 112, t("Copia di ", "Copying ") + l + " ...", 12, BLU, "bold")}'
            + discreto("opacity", [1 if j == i else 0 for j in range(len(lingue))] + [0],
                       [j * 1.2 for j in range(len(lingue))] + [6], 6)
            + "</g>"
        )
    # barra di avanzamento a blocchi blu
    bx, by, bw = 20, 142, W - 140
    o.append(rilievo(bx, by, bw, 22, BIANCO, infossato=True))
    blocchi = 26
    for i in range(blocchi):
        x = bx + 4 + i * ((bw - 8) / blocchi)
        tb = 0.2 + i * (5.2 / blocchi)
        o.append(
            f'<rect x="{x:.1f}" y="{by + 4}" width="{(bw - 8) / blocchi - 2:.1f}" height="14" fill="{BLU}" opacity="0">'
            + discreto("opacity", [0, 1, 0], [0, tb, 5.9], 6) + "</rect>"
        )
    o.append(rilievo(W - 108, by - 2, 88, 26))
    o.append(testo(W - 64, by + 15, t("Annulla", "Cancel"), 12, NERO, anchor="middle"))
    o.append(testo(20, 186, t("SHA-256 verificato per ogni file  ✔", "SHA-256 verified for every file  ✔"), 11, "#006000", "bold"))
    return svg(W, H, "".join(o), t("Copia dei dizionari: fogli che volano tra due cartelle e barra di avanzamento",
                                   "Copying dictionaries: sheets flying between two folders and a progress bar"))


# ---------------------------------------------------------------------------
# 3. Finestra di dialogo sulla privacy
# ---------------------------------------------------------------------------
def privacy():
    W, H = 520, 170
    o = [finestra(0, 0, W, H, "Privacy", pulsanti=("×",))]
    o.append(f'<circle cx="46" cy="62" r="20" fill="{BLU}" stroke="{NERO}"/>')
    o.append(testo(46, 71, "i", 26, BIANCO, "bold", "middle", font="Georgia,serif"))
    righe = t([
        "La mia tastiera non ha il permesso di usare Internet.",
        "Quello che scrivi resta sul telefono: niente server, niente",
        "statistiche, niente backup nel cloud. Le password e la modalità",
        "incognito non vengono mai imparate.",
    ], [
        "My Keyboard has no permission to use the Internet.",
        "What you type stays on your phone: no servers, no analytics,",
        "no cloud backups. Passwords and incognito mode are never",
        "learned.",
    ])
    for i, r in enumerate(righe):
        o.append(testo(84, 50 + i * 18, r, 12, NERO, "bold" if i == 0 else "normal"))
    # OK con il bordo tratteggiato del pulsante attivo, che ogni tanto viene premuto
    ox, oy = W / 2 - 100, H - 42
    o.append(f'<rect x="{ox - 1}" y="{oy - 1}" width="90" height="28" fill="{NERO}"/>')
    o.append(rilievo(ox, oy, 88, 26))
    o.append(
        f'<g opacity="0">{rilievo(ox, oy, 88, 26, GRIGIO, infossato=True)}'
        + discreto("opacity", [0, 1, 0], [0, 3.0, 3.2], 4) + "</g>"
    )
    o.append(f'<rect x="{ox + 5}" y="{oy + 5}" width="78" height="16" fill="none" stroke="{NERO}" stroke-dasharray="1,1"/>')
    o.append(testo(ox + 44, oy + 17, "OK", 12, NERO, anchor="middle"))
    o.append(rilievo(W / 2 + 12, oy, 100, 26))
    o.append(testo(W / 2 + 62, oy + 17, t("Dettagli >>", "Details >>"), 12, NERO, anchor="middle"))
    return svg(W, H, "".join(o), t("Finestra di dialogo: la tastiera non ha il permesso di usare Internet",
                                   "Dialog box: the keyboard has no permission to use the Internet"))


# ---------------------------------------------------------------------------
# 4. Statistiche del correttore: barre che crescono
# ---------------------------------------------------------------------------
def statistiche():
    W, H = 640, 300
    o = [finestra(0, 0, W, H, t("Banco di prova — Statistiche del correttore", "Test bench — Autocorrect statistics"))]
    o.append(rilievo(10, 28, W - 20, H - 66, BIANCO, infossato=True))
    voci = t([
        ("Correzioni sbagliate", 12.5, 1.4, "meno è meglio"),
        ("Parole giuste rovinate", 31.5, 21.7, "meno è meglio"),
        ("Refusi corretti da soli", 86.6, 77.5, "+18,1% proposti"),
    ], [
        ("Wrong corrections", 12.5, 1.4, "lower is better"),
        ("Valid words ruined", 31.5, 21.7, "lower is better"),
        ("Typos auto-fixed", 86.6, 77.5, "+18.1% suggested"),
    ])
    scala = 3.4
    y = 56
    for nome, vecchio, nuovo, nota in voci:
        o.append(testo(24, y + 10, nome, 12, NERO, "bold"))
        o.append(testo(24, y + 26, nota, 11, SCURO))
        for j, (val, col, etich) in enumerate([(vecchio, "#808080", "v0.1"), (nuovo, BLU, "v2.0.1")]):
            by = y + j * 22
            o.append(testo(196, by + 14, etich, 11, NERO, anchor="end"))
            w = val * scala
            o.append(
                f'<rect x="202" y="{by + 2}" width="0" height="16" fill="{col}" shape-rendering="crispEdges">'
                f'<animate attributeName="width" from="0" to="{w:.1f}" dur="1.4s" begin="{0.3 + j * 0.3}s" fill="freeze" '
                f'calcMode="spline" keySplines="0.2 0.8 0.2 1" keyTimes="0;1"/></rect>'
            )
            o.append(
                f'<g opacity="0">{testo(208 + w, by + 14, f"{val:.1f}%".replace(".", t(",", ".")), 11, NERO, "bold")}'
                f'<animate attributeName="opacity" from="0" to="1" dur="0.3s" begin="{1.6 + j * 0.3}s" fill="freeze"/></g>'
            )
        y += 66
    sy = H - 32
    o.append(rilievo(6, sy, W - 12, 24, GRIGIO, infossato=True))
    o.append(testo(14, sy + 16, t("3.000 refusi e 3.000 parole rare mai viste durante la taratura · seme 2026",
                                  "3,000 typos and 3,000 rare words never seen during tuning · seed 2026"), 11))
    return svg(W, H, "".join(o), t("Statistiche: correzioni sbagliate da 12,5% a 1,4%", "Statistics: wrong corrections from 12.5% to 1.4%"))


# ---------------------------------------------------------------------------
# 5. Esplora risorse: le lingue, con la selezione che passa da una all'altra
# ---------------------------------------------------------------------------
def lingue():
    W, H = 640, 230
    o = [finestra(0, 0, W, H, t("C:\\Tastiera\\Lingue", "C:\\Keyboard\\Languages"))]
    o.append(rilievo(6, 26, W - 12, 26))
    o.append(testo(14, 44, t("Indirizzo", "Address"), 12))
    o.append(rilievo(74, 29, W - 90, 20, BIANCO, infossato=True))
    o.append(testo(80, 44, t("C:\\Tastiera\\Lingue", "C:\\Keyboard\\Languages"), 12))
    o.append(rilievo(6, 56, W - 12, H - 88, BIANCO, infossato=True))
    voci = [
        ("it.txt", "Italiano", t("incluso", "built in"), "#009246"),
        ("en.txt", "English", "QWERTY", "#012169"),
        ("es.txt", "Español", t("con ñ", "with ñ"), "#c60b1e"),
        ("fr.txt", "Français", "AZERTY", "#0055a4"),
        ("de.txt", "Deutsch", "QWERTZ", "#222222"),
        ("pt.txt", "Português", "Brasil", "#009c3b"),
    ]
    n = len(voci)
    for i, (file, nome, nota, col) in enumerate(voci):
        x = 22 + i * 102
        y = 70
        icona = (
            f'<path d="M{x + 30},{y} h26 l10,10 v40 h-36z" fill="{BIANCO}" stroke="{NERO}"/>'
            f'<path d="M{x + 56},{y} v10 h10" fill="none" stroke="{NERO}"/>'
            f'<rect x="{x + 30}" y="{y + 30}" width="36" height="10" fill="{col}"/>'
        )
        for k in range(3):
            icona += f'<rect x="{x + 35}" y="{y + 14 + k * 5}" width="{20 - k * 4}" height="2" fill="{SCURO}"/>'
        o.append(icona)
        # selezione blu che si sposta da un file all'altro
        o.append(
            f'<rect x="{x + 6}" y="{y + 56}" width="84" height="16" fill="{BLU}" opacity="0">'
            + discreto("opacity", [1 if j == i else 0 for j in range(n)] + [0], [j * 1.0 for j in range(n)] + [n], n)
            + "</rect>"
        )
        colore = discreto("fill", [BIANCO if j == i else NERO for j in range(n)] + [NERO], [j * 1.0 for j in range(n)] + [n], n)
        o.append(testo(x + 48, y + 68, file, 12, NERO, anchor="middle").replace("</text>", colore + "</text>"))
        o.append(testo(x + 48, y + 90, nome, 11, NERO, "bold", "middle"))
        o.append(testo(x + 48, y + 104, nota, 11, SCURO, anchor="middle"))
    sy = H - 28
    o.append(rilievo(6, sy, 200, 22, GRIGIO, infossato=True))
    o.append(testo(14, sy + 15, t("6 oggetti", "6 object(s)"), 12))
    o.append(rilievo(210, sy, W - 216, 22, GRIGIO, infossato=True))
    o.append(testo(218, sy + 15, t("Tasto 🌐 per passare da una lingua all'altra", "Tap 🌐 to switch between languages"), 12))
    return svg(W, H, "".join(o), t("Esplora risorse con i sei dizionari delle lingue", "Explorer window with the six language dictionaries"))


# ---------------------------------------------------------------------------
# 6. Barra in fondo: testo che scorre come lo screensaver "Testo scorrevole"
# ---------------------------------------------------------------------------
def scorrevole():
    W, H = 860, 54
    frase = t("✦ Grazie per aver usato La mia tastiera ✦ Nessun dato lascia il telefono ✦ "
              "Premi ⌫ subito dopo una correzione per annullarla ✦ ",
              "✦ Thank you for using My Keyboard ✦ No data ever leaves your phone ✦ "
              "Press ⌫ right after a correction to undo it ✦ ")
    o = [f'<rect width="{W}" height="{H}" fill="{NERO}"/>']
    largh = len(frase) * 11
    o.append(
        f'<g><animateTransform attributeName="transform" type="translate" from="{W},0" to="{-largh},0" '
        f'dur="22s" repeatCount="indefinite"/>'
        + testo(0, 35, frase, 20, "#00ff00", "bold", font=MONO)
        + "</g>"
    )
    return svg(W, H, "".join(o), t("Testo scorrevole: grazie per aver usato La mia tastiera", "Scrolling marquee: thank you for using My Keyboard"))


if __name__ == "__main__":
    for inglese in (False, True):
        INGLESE = inglese
        for nome, f in [("copertina", copertina), ("copia", copia), ("privacy", privacy),
                        ("statistiche", statistiche), ("lingue", lingue), ("scorrevole", scorrevole)]:
            file = QUI / f"{nome}{'_en' if inglese else ''}.svg"
            file.write_text(f(), encoding="utf-8")
            print("scritto", file.name)
