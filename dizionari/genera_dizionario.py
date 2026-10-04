#!/usr/bin/env python3
"""
Crea il dizionario di una lingua per la tastiera.

Fonti:
  - frequenze: FrequencyWords di Hermit Dave (sottotitoli OpenSubtitles 2018), una riga "parola conteggio";
  - ortografia: dizionario Hunspell di LibreOffice (via github.com/wooorm/dictionaries), letto con spylls.

Una parola entra solo se Hunspell la riconosce: spariscono così gli errori tipici dei sottotitoli
(perche, piu, citta...). Se Hunspell accetta solo la forma con la maiuscola (Roma, Haus, I),
la parola viene salvata così e la tastiera la proporrà con la maiuscola.

Le liste in extra/<lingua>.txt aggiungono parole a mano (in ordine di importanza) e, con "-parola",
tolgono una forma sbagliata anche dalle parole imparate sul telefono.

Formato prodotto (UTF-8):
  # commenti
  parola<TAB>conteggio      (dalla più frequente)
  -parola                   (da togliere)

Uso:
  python3 genera_dizionario.py --lingua it --frequenze freq_it.txt --hunspell hun_it/index \
      --extra extra/it.txt --parole 70000 --uscita it.txt
"""
import argparse
import re
import sys
import unicodedata

from spylls.hunspell import Dictionary

LETTERE = re.compile(r"^[^\W\d_]+(?:['’][^\W\d_]+)*'?$")


def leggi_extra(percorso):
    aggiunte, togliere = [], []
    if not percorso:
        return aggiunte, togliere
    with open(percorso, encoding="utf-8") as f:
        for riga in f:
            if riga.startswith("#"):
                continue
            for t in riga.split():
                t = unicodedata.normalize("NFC", t.replace("’", "'"))
                if t.startswith("-"):
                    togliere.append(t[1:].lower())
                elif LETTERE.match(t):
                    aggiunte.append(t)
    return aggiunte, togliere


def main():
    a = argparse.ArgumentParser()
    a.add_argument("--lingua", required=True)
    a.add_argument("--frequenze", required=True)
    a.add_argument("--hunspell", required=True, help="percorso senza estensione (.aff/.dic)")
    a.add_argument("--extra")
    a.add_argument("--parole", type=int, default=70000)
    a.add_argument("--minimo", type=int, default=3, help="conteggio minimo nei sottotitoli")
    a.add_argument("--uscita", required=True)
    o = a.parse_args()

    hun = Dictionary.from_files(o.hunspell)
    aggiunte, togliere = leggi_extra(o.extra)
    da_togliere = set(togliere)

    parole = {}  # minuscola → [forma, conteggio]
    scartate = 0
    with open(o.frequenze, encoding="utf-8") as f:
        for riga in f:
            if len(parole) >= o.parole:
                break
            parti = riga.split()
            if len(parti) != 2:
                continue
            w, n = unicodedata.normalize("NFC", parti[0]), int(parti[1])
            if n < o.minimo:
                break
            # Le parole con apostrofo arrivano spezzate (c' + è): vengono solo dalla lista extra.
            if "'" in w or "’" in w or not LETTERE.match(w) or len(w) > 30:
                continue
            w = w.lower()
            if w in parole or w in da_togliere:
                continue
            if hun.lookup(w):
                forma = w
            elif hun.lookup(w[0].upper() + w[1:]):
                forma = w[0].upper() + w[1:]
            else:
                scartate += 1
                continue
            parole[w] = [forma, n]

    # Le aggiunte a mano stanno tra la parola n. 200 e la n. 8000 per frequenza (le prime più in alto),
    # oppure più su se sono già comuni. Una forma con maiuscole (I, I'm) diventa quella da proporre.
    conteggi = sorted((v[1] for v in parole.values()), reverse=True)
    alto = conteggi[min(200, len(conteggi) - 1)]
    basso = conteggi[min(8000, len(conteggi) - 1)]
    m = max(len(aggiunte), 1)
    for j, forma in enumerate(aggiunte):
        chiave = forma.lower()
        punti = int(basso + (alto - basso) * (m - j) / m)
        prima = parole.get(chiave)
        conteggio = max(prima[1] if prima else 0, punti)
        if prima and forma == chiave:
            forma = prima[0]
        parole[chiave] = [forma, conteggio]

    ordinate = sorted(parole.values(), key=lambda v: (-v[1], v[0]))
    with open(o.uscita, "w", encoding="utf-8") as f:
        f.write(f"# lingua: {o.lingua}\n")
        f.write("# Frequenze: FrequencyWords di Hermit Dave (OpenSubtitles 2018), CC BY-SA 4.0.\n")
        f.write("# Ortografia verificata con il dizionario Hunspell di LibreOffice per questa lingua.\n")
        f.write("# Formato: parola<TAB>conteggio, dalla più frequente; -parola = da togliere.\n")
        for forma, n in ordinate:
            f.write(f"{forma}\t{n}\n")
        for w in sorted(da_togliere):
            f.write(f"-{w}\n")
    print(f"{o.lingua}: {len(ordinate)} parole, {len(da_togliere)} da togliere, "
          f"{scartate} scartate da Hunspell", file=sys.stderr)


if __name__ == "__main__":
    main()
