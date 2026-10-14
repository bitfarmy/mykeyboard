#!/usr/bin/env python3
"""
Crea l'elenco delle coppie di parole (quale parola segue quale) per la tastiera.

Fonte: i sottotitoli OpenSubtitles 2018 (OPUS), un testo per riga, lo stesso corpus da cui
viene il dizionario delle parole (vedi genera_dizionario.py).

Si contano solo le parole che stanno nel dizionario. Una forma con apostrofo che non è nel dizionario
(dell'uomo) conta come la parola dopo l'apostrofo (uomo). Punteggiatura, numeri e parole sconosciute
interrompono la frase: le coppie non attraversano mai un segno di punteggiatura, come nella tastiera.

Un blocco di righe ogni 50 viene tenuto da parte (--riservato) per misurare il correttore su testi
che non hanno contribuito ai conteggi.

Formato prodotto (UTF-8):
  # commenti
  parola<TAB>seguente:pmi seguente:pmi ...     (dal seguente più frequente)
dove pmi = 100 · ln( P(parola seguente | parola) / P(seguente) ): quanto il contesto rende più
probabile quella parola (0 = nessun legame).

Uso:
  python3 genera_coppie.py --corpus it.txt.gz --dizionario parole_it.txt \
      --uscita coppie_it.txt --riservato riservato_it.txt
"""
import argparse
import collections
import gzip
import math
import multiprocessing as mp
import re
import sys

import numpy as np

# Nei sottotitoli "è" spesso è scritto "e": dopo queste parole quella "e" quasi sempre è un verbo
E_SENZA_ACCENTO = {"non", "che", "ci", "mi", "ti", "si", "ecco", "perché", "chi", "cosa", "dove", "come", "cos", "dov", "com"}

BLOCCO = 10_000        # righe: si riserva un blocco ogni RISERVA, così i film restano interi
RISERVA = 50
PEZZO = 400_000        # righe per ogni lavoro dei processi
SEPARA = re.compile(r"[^\w'\s]|\d|_")

vocabolario = {}


def carica_dizionario(percorso):
    parole = {}
    tolte = set()
    with open(percorso, encoding="utf-8") as f:
        for riga in f:
            riga = riga.strip()
            if not riga or riga.startswith("#"):
                continue
            parti = riga.split()
            for t in ([parti[0]] if len(parti) == 2 and parti[1].isdigit() else parti):
                if t.isdigit():
                    continue
                t = t.replace("’", "'")
                if t.startswith("-"):
                    tolte.add(t[1:].lower())
                    continue
                w = t.lower()
                if w not in parole:
                    parole[w] = len(parole)
    for w in tolte:
        parole.pop(w, None)
    # ri-numera dopo le eventuali parole tolte
    return {w: i for i, w in enumerate(parole)}


def identifica(tok):
    i = vocabolario.get(tok)
    if i is not None:
        return i
    if "'" in tok:
        return vocabolario.get(tok.rsplit("'", 1)[1])
    return None


def lavora(righe):
    n = len(vocabolario)
    coppie = []
    uni = np.zeros(n, dtype=np.int64)
    for riga in righe:
        testo = riga.lower().replace("’", "'")
        for seg in SEPARA.split(testo):
            ids = []
            for tok in seg.split():
                tok = tok.strip("'")
                i = identifica(tok) if tok else None
                if i is None:
                    if len(ids) > 1:
                        coppie.extend(a * n + b for a, b in zip(ids, ids[1:]))
                    for x in ids:
                        uni[x] += 1
                    ids = []
                else:
                    ids.append(i)
            if len(ids) > 1:
                coppie.extend(a * n + b for a, b in zip(ids, ids[1:]))
            for x in ids:
                uni[x] += 1
    if coppie:
        chiavi, volte = np.unique(np.array(coppie, dtype=np.int64), return_counts=True)
    else:
        chiavi, volte = np.zeros(0, np.int64), np.zeros(0, np.int64)
    return chiavi, volte, uni


def unisci(chiavi, volte, nuovi, nvolte, potare):
    k = np.concatenate([chiavi, nuovi])
    v = np.concatenate([volte, nvolte])
    ordine = np.argsort(k, kind="stable")
    k, v = k[ordine], v[ordine]
    inizio = np.concatenate([[True], k[1:] != k[:-1]]) if len(k) else np.zeros(0, bool)
    posizioni = np.flatnonzero(inizio)
    k2 = k[posizioni]
    v2 = np.add.reduceat(v, posizioni) if len(k) else v
    if potare and len(k2) > potare:
        tieni = v2 >= 2
        k2, v2 = k2[tieni], v2[tieni]
    return k2, v2


def pezzi(percorso, riservato):
    """Produce liste di righe; scrive le righe riservate su file."""
    corrente = []
    indice = 0
    with gzip.open(percorso, "rt", encoding="utf-8", errors="replace") as f, \
            open(riservato, "w", encoding="utf-8") as ris:
        for riga in f:
            if (indice // BLOCCO) % RISERVA == 0:
                ris.write(riga if riga.endswith("\n") else riga + "\n")
            else:
                corrente.append(riga)
                if len(corrente) >= PEZZO:
                    yield corrente
                    corrente = []
            indice += 1
    if corrente:
        yield corrente


def main():
    global vocabolario
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", required=True)
    ap.add_argument("--dizionario", required=True)
    ap.add_argument("--uscita", required=True)
    ap.add_argument("--riservato", required=True)
    ap.add_argument("--processi", type=int, default=18)
    ap.add_argument("--minimo", type=int, default=40, help="occorrenze minime di una coppia")
    ap.add_argument("--per-parola", type=int, default=8, help="seguenti tenuti per ogni parola")
    ap.add_argument("--massimo-righe", type=int, default=30000, help="parole con elenco nel file")
    args = ap.parse_args()

    vocabolario = carica_dizionario(args.dizionario)
    n = len(vocabolario)
    print(f"{n} parole nel dizionario", file=sys.stderr)

    chiavi = np.zeros(0, np.int64)
    volte = np.zeros(0, np.int64)
    uni = np.zeros(n, dtype=np.int64)
    fatti = 0
    def raccogli(risultato):
        nonlocal chiavi, volte, fatti
        k, v, u = risultato.get()
        chiavi, volte = unisci(chiavi, volte, k, v, potare=45_000_000)
        uni.__iadd__(u)
        fatti += 1
        if fatti % 10 == 0:
            print(f"{fatti * PEZZO / 1e6:.0f}M righe, {len(chiavi) / 1e6:.1f}M coppie", file=sys.stderr)

    # Pochi lavori in coda alla volta: il corpus non deve stare tutto in memoria
    in_coda = collections.deque()
    with mp.get_context("fork").Pool(args.processi) as pool:
        for pezzo in pezzi(args.corpus, args.riservato):
            in_coda.append(pool.apply_async(lavora, (pezzo,)))
            while len(in_coda) >= args.processi * 2:
                raccogli(in_coda.popleft())
        while in_coda:
            raccogli(in_coda.popleft())

    totale = int(uni.sum())
    parole = [None] * n
    for w, i in vocabolario.items():
        parole[i] = w
    a = chiavi // n
    b = chiavi % n
    # tiene le coppie frequenti, poi per ogni prima parola le più frequenti
    tieni = volte >= args.minimo
    a, b, c = a[tieni], b[tieni], volte[tieni]
    pmi = np.log(c * totale / (uni[a].astype(np.float64) * uni[b]))
    ordine = np.lexsort((-c, a))
    a, b, c, pmi = a[ordine], b[ordine], c[ordine], pmi[ordine]

    elenchi = []
    inizio = 0
    while inizio < len(a):
        fine = inizio
        while fine < len(a) and a[fine] == a[inizio]:
            fine += 1
        prima = parole[a[inizio]]
        sel = [(int(b[j]), float(pmi[j]), int(c[j])) for j in range(inizio, fine)
               if pmi[j] > 0.2 and not (parole[b[j]] == "e" and prima in E_SENZA_ACCENTO)][: args.per_parola]
        if sel:
            elenchi.append((int(uni[a[inizio]]), parole[a[inizio]], sel))
        inizio = fine
    elenchi.sort(reverse=True)
    elenchi = elenchi[: args.massimo_righe]
    elenchi.sort(key=lambda e: e[1])

    with open(args.uscita, "w", encoding="utf-8") as out:
        out.write("# lingua: it\n")
        out.write("# Coppie di parole dai sottotitoli OpenSubtitles 2018 (OPUS), CC-BY-SA 4.0 come il dizionario.\n")
        out.write("# Formato: parola<TAB>seguente:pmi ... (dal più frequente); pmi = 100*ln(P(seguente|parola)/P(seguente)).\n")
        for _, p, sel in elenchi:
            out.write(p + "\t" + " ".join(f"{parole[x]}:{round(100 * m)}" for x, m, _ in sel) + "\n")
    print(f"{len(elenchi)} parole, {sum(len(s) for _, _, s in elenchi)} coppie, {totale / 1e6:.0f}M parole contate", file=sys.stderr)


if __name__ == "__main__":
    main()
