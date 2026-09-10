# La mia tastiera

Tastiera Android personale in Kotlin, senza librerie esterne.

## Cosa fa (versione 0.1)

- Tastiera italiana QWERTY con pagine di simboli, maiuscole automatiche, blocco maiuscole (doppio tocco su ⇧) e cancellazione continua tenendo premuto ⌫.
- Tieni premuto un tasto per le varianti: numeri sulla prima riga, lettere accentate su e, a, i, o, u.
- Barra dei suggerimenti con completamento delle parole e autocorrezione. Premi ⌫ subito dopo una correzione per annullarla: la tastiera impara la tua parola.
- Scorciatoie: una sigla (anche una sola lettera) fa comparire una frase salvata, segnata con ⚡.
- Pannello emoji con categorie e recenti.
- Sei temi colore e tre altezze dei tasti.
- Tieni premuto lo spazio per passare a un'altra tastiera.
- Nei campi password e in modalità incognito non impara nulla. Le parole imparate restano sul telefono.

## Installarla sul telefono

1. Installa Android Studio e apri questa cartella con File > Open. Aspetta la fine della sincronizzazione Gradle; se propone di aggiornare Gradle o il plugin Android, accetta.
2. Sul telefono attiva le Opzioni sviluppatore e il Debug USB, poi collegalo al computer.
3. Premi il pulsante Run ▶ in Android Studio.
4. Sul telefono si apre "La mia tastiera": tocca i due pulsanti per abilitarla e sceglierla.

## Dove mettere le mani

| Cosa vuoi cambiare | File |
|---|---|
| Disposizione dei tasti e varianti | `Layout.kt` |
| Colori e temi | `Temi.kt` |
| Emoji disponibili | `PannelloEmoji.kt` (in fondo) |
| Aspetto dei tasti | `TastieraView.kt` |
| Logica di scrittura | `TastieraService.kt` |
| Suggerimenti e correzioni | `Dizionario.kt` |
| Schermata delle impostazioni | `ImpostazioniActivity.kt` |

## Un dizionario più grande

Il file `app/src/main/assets/parole_it.txt` contiene circa 800 parole comuni. Con queste la tastiera suggerisce e sistema gli accenti (perche → perché), ma non corregge gli errori di battitura, perché scambierebbe per errori troppe parole giuste.

Per l'autocorrezione completa sostituisci il file con una lista di almeno 10.000 parole ordinate dalla più frequente. Vanno bene una parola per riga oppure righe nel formato `parola 12345`: i numeri vengono ignorati. Una fonte possibile è il progetto FrequencyWords su GitHub (file italiano `it_50k.txt`); verifica la licenza prima di usarlo.

## Ottenere l'APK senza Android Studio (GitHub)

1. Crea un repository **privato** su github.com e carica tutto il contenuto di questa cartella, compresa `.github`.
2. Il caricamento avvia da solo la compilazione: la trovi nella scheda **Actions** (circa 5 minuti).
3. Apri l'esecuzione completata e scarica **la-mia-tastiera** in fondo alla pagina. È uno zip che contiene `app-debug.apk`.
4. Copia l'APK sui telefoni e aprilo, permettendo l'installazione da origini sconosciute.

Se la cartella `.github` non viene caricata (è nascosta su alcuni computer), vai su Actions → "set up a workflow yourself" e incolla il contenuto di `.github/workflows/compila-apk.yml`.

Ogni volta che modifichi un file su GitHub parte una nuova compilazione. L'APK è firmato con la chiave in `firma/`, quindi si installa sopra la versione precedente.
