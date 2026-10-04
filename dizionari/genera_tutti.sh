#!/usr/bin/env bash
# Rigenera tutti i dizionari e il catalogo della tastiera.
#
#   ./genera_tutti.sh [cartella-di-lavoro]
#
# Risultato:
#   - <lavoro>/uscita/<lingua>.txt          → da caricare come asset della release dei dizionari
#   - app/src/main/assets/parole_it.txt     → l'italiano, incluso nell'app
#   - app/.../CatalogoDizionari.kt           → impronte SHA-256 che l'app usa per verificare i file importati
#
# Dopo aver cambiato i dizionari: alza VERSIONE, pubblica i file con
#   gh release create v$VERSIONE <lavoro>/uscita/*.txt -R bitfarmy/mykeyboard-dizionari
# e ricompila l'app (il catalogo contiene le nuove impronte).
set -euo pipefail

VERSIONE=1
REPO_DIZIONARI="bitfarmy/mykeyboard-dizionari"
# Fonti fissate a un commit: lo stesso comando produce sempre gli stessi file.
FREQUENZE="https://raw.githubusercontent.com/hermitdave/FrequencyWords/525f9b560de45753a5ea01069454e72e9aa541c6/content/2018"
HUNSPELL="https://raw.githubusercontent.com/wooorm/dictionaries/8cfea406b505e4d7df52d5a19bce525df98c54ab/dictionaries"

QUI="$(cd "$(dirname "$0")" && pwd)"
APP="$QUI/../TastieraPersonale/app/src/main"
LAVORO="${1:-$QUI/.lavoro}"
mkdir -p "$LAVORO/fonti" "$LAVORO/uscita"

if [ ! -x "$LAVORO/venv/bin/python" ]; then
  python3 -m venv "$LAVORO/venv"
  "$LAVORO/venv/bin/pip" install -q spylls==0.1.7
fi

# lingua  file-frequenze  dizionario-hunspell  parole
LINGUE="it it it 70000
en en en 60000
es es es 70000
fr fr fr 70000
de de de 70000
pt pt_br pt 70000"

while read -r lingua freq hun parole; do
  f="$LAVORO/fonti/freq_$freq.txt"
  [ -s "$f" ] || curl -fsSL -o "$f" "$FREQUENZE/$freq/${freq}_full.txt"
  mkdir -p "$LAVORO/fonti/hun_$hun"
  for e in aff dic; do
    h="$LAVORO/fonti/hun_$hun/index.$e"
    [ -s "$h" ] || curl -fsSL -o "$h" "$HUNSPELL/$hun/index.$e"
  done
  "$LAVORO/venv/bin/python" "$QUI/genera_dizionario.py" --lingua "$lingua" --frequenze "$f" \
    --hunspell "$LAVORO/fonti/hun_$hun/index" --extra "$QUI/extra/$lingua.txt" \
    --parole "$parole" --uscita "$LAVORO/uscita/$lingua.txt" &
done <<< "$LINGUE"
wait

cp "$LAVORO/uscita/it.txt" "$APP/assets/parole_it.txt"

CATALOGO="$APP/java/com/personale/tastiera/CatalogoDizionari.kt"
{
  echo "package com.personale.tastiera"
  echo
  echo "// FILE GENERATO da dizionari/genera_tutti.sh: non modificarlo a mano."
  echo
  echo "/** Dove si scaricano i dizionari e l'impronta SHA-256 che ogni file deve avere. */"
  echo "object CatalogoDizionari {"
  echo "    const val VERSIONE = $VERSIONE"
  echo "    const val INDIRIZZO = \"https://github.com/$REPO_DIZIONARI/releases/download/v$VERSIONE/\""
  echo
  echo "    val file: Map<String, FileDizionario> = mapOf("
  while read -r lingua _; do
    [ "$lingua" = it ] && continue
    u="$LAVORO/uscita/$lingua.txt"
    echo "        \"$lingua\" to FileDizionario(\"$(sha256sum "$u" | cut -d' ' -f1)\", $(stat -c %s "$u")),"
  done <<< "$LINGUE"
  echo "    )"
  echo "}"
  echo
  echo "data class FileDizionario(val sha256: String, val byte: Long)"
} > "$CATALOGO"

echo "Fatto. Dizionari in $LAVORO/uscita, catalogo in $CATALOGO"
