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
- Nei campi password e in modalità incognito non impara nulla, e nemmeno da nomi e indirizzi. Le parole imparate restano sul telefono: l'app non ha il permesso di usare Internet e non finisce nei backup.

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

1. Ogni modifica caricata su GitHub avvia la compilazione: la trovi nella scheda **Actions** (circa 5 minuti).
2. Apri l'esecuzione completata e scarica **la-mia-tastiera** in fondo alla pagina. È uno zip che contiene `app-release.apk`.
3. Copia l'APK sui telefoni e aprilo, permettendo l'installazione da origini sconosciute.

## Chiave di firma

L'APK è firmato con una chiave personale che **non sta nel repository**: solo chi ha la chiave può pubblicare aggiornamenti che Android accetta sopra la versione installata.

- Sul computer la chiave sta in `~/.android-chiavi/` (`tastiera.jks` + `keystore.properties`). **Fanne un backup** (per esempio nel password manager): se la perdi, potrai solo disinstallare e reinstallare, perdendo parole imparate e scorciatoie.
- Su GitHub la chiave sta nei secret del repository (Settings → Secrets and variables → Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Per caricarli:

```
D=~/.android-chiavi; R=bitfarmy/mykeyboard
base64 -w0 $D/tastiera.jks | gh secret set KEYSTORE_BASE64 -R $R
grep '^storePassword=' $D/keystore.properties | cut -d= -f2- | tr -d '\n' | gh secret set KEYSTORE_PASSWORD -R $R
grep '^keyPassword=' $D/keystore.properties | cut -d= -f2- | tr -d '\n' | gh secret set KEY_PASSWORD -R $R
printf tastiera | gh secret set KEY_ALIAS -R $R
```

- In Android Studio la chiave viene trovata da sola in `~/.android-chiavi/keystore.properties` (oppure in un `keystore.properties` nella cartella del progetto, ignorato da git). Senza chiave, la versione di debug usa la chiave di debug di Android.
