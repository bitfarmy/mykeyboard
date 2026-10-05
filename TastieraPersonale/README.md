# La mia tastiera

Tastiera Android personale in Kotlin, senza librerie esterne.

## Cosa fa (versione 2.0.2)

- Tastiera con pagine di simboli, maiuscole automatiche, blocco maiuscole (doppio tocco su ⇧) e cancellazione continua tenendo premuto ⌫.
- Sei lingue: italiano (incluso), inglese, spagnolo, francese (AZERTY), tedesco (QWERTZ) e portoghese, da scaricare quando servono. Con più lingue attive compare il tasto 🌐 per passare dall'una all'altra.
- Tieni premuto un tasto per le varianti: numeri sulla prima riga, lettere accentate della lingua in uso.
- Barra dei suggerimenti con completamento delle parole e autocorrezione. La correzione evidenziata è quella che lo spazio applicherà; quando la tastiera non è sicura, propone senza imporre. Premi ⌫ subito dopo una correzione per annullarla: in quel campo non verrà riproposta, e alla seconda volta che la annulli la tastiera impara la tua parola. Un refuso non corretto non viene mai imparato di nascosto.
- Scorciatoie: una sigla (anche una sola lettera) fa comparire una frase salvata, segnata con ⚡.
- Pannello emoji con categorie e recenti.
- Sei temi colore e tre altezze dei tasti.
- Tieni premuto lo spazio (o 🌐) per passare a un'altra tastiera.
- Nei campi password e in modalità incognito non impara nulla, e nemmeno da nomi e indirizzi. Le parole imparate restano sul telefono: l'app non ha il permesso di usare Internet e non finisce nei backup.

## Installarla sul telefono

1. Installa Android Studio e apri questa cartella con File > Open. Aspetta la fine della sincronizzazione Gradle; se propone di aggiornare Gradle o il plugin Android, accetta.
2. Sul telefono attiva le Opzioni sviluppatore e il Debug USB, poi collegalo al computer.
3. Premi il pulsante Run ▶ in Android Studio.
4. Sul telefono si apre "La mia tastiera": tocca i due pulsanti per abilitarla e sceglierla.

## Dove mettere le mani

| Cosa vuoi cambiare | File |
|---|---|
| Disposizione dei tasti, varianti e lingue | `Layout.kt` |
| Colori e temi | `Temi.kt` |
| Emoji disponibili | `PannelloEmoji.kt` (in fondo) |
| Aspetto dei tasti | `TastieraView.kt` |
| Logica di scrittura | `TastieraService.kt` |
| Suggerimenti e correzioni | `Lessico.kt` (regole), `Dizionario.kt` (file e caricamento) |
| Importazione dei dizionari | `ImportaDizionario.kt`, `CatalogoDizionari.kt` (generato) |
| Schermata delle impostazioni | `ImpostazioniActivity.kt` |

## Lingue e dizionari

L'italiano è incluso nell'app. Le altre lingue si attivano dalle impostazioni, sezione **Lingue**:

1. tocca **Scarica**: si apre il browser e scarica il dizionario dal repository pubblico [bitfarmy/mykeyboard-dizionari](https://github.com/bitfarmy/mykeyboard-dizionari/releases);
2. torna nelle impostazioni e tocca **Importa**, poi scegli il file appena scaricato (di solito nella cartella Download).

La tastiera **non ha il permesso di usare Internet** e non lo avrà: per questo il download passa dal browser. Prima di installare un file la tastiera ne calcola l'impronta SHA-256 e la confronta con quelle scritte nell'app (`CatalogoDizionari.kt`): un file rovinato, modificato o di un'altra versione viene rifiutato.

### Da dove vengono le parole

Ogni dizionario contiene 60–70.000 parole con la loro frequenza, create da `dizionari/genera_tutti.sh`:

- le frequenze vengono dai sottotitoli di OpenSubtitles 2018 (progetto [FrequencyWords](https://github.com/hermitdave/FrequencyWords), CC BY-SA 4.0);
- ogni parola è controllata con il dizionario ortografico Hunspell di LibreOffice della sua lingua: restano fuori gli errori tipici dei sottotitoli (perche, piu, citta…), e le parole che vogliono la maiuscola la mantengono (Roma, Haus, I);
- le liste in `dizionari/extra/` aggiungono a mano forme con apostrofo (c'è, I'm, c'est) e parole di tutti i giorni, e con `-parola` tolgono le forme sbagliate.

Per rigenerarli: `dizionari/genera_tutti.sh`. Poi, se sono cambiati, alza `VERSIONE` nello script, pubblica i file in una nuova release di `mykeyboard-dizionari` e ricompila l'app (il catalogo con le impronte si aggiorna da solo).

### Come corregge

Per una parola che non conosce, la tastiera confronta due ipotesi: "hai sbagliato a battere una parola del dizionario" e "è una parola vera che il dizionario non conosce".

- La prima ipotesi tiene conto di quanto è comune la parola e di quanto è probabile l'errore: un tasto vicino, due lettere invertite o una doppia dimenticata (tuto → tutto) costano poco.
- La seconda usa un modello delle sequenze di lettere della lingua: "rwcentemente" non sembra italiano, "constatando" sì.

La correzione si applica da sola solo se è probabile almeno al 90%, altrimenti compare tra i suggerimenti. Non tocca i nomi con la maiuscola a metà frase.

Su un banco di prova di 3.000 errori di battitura e 3.000 parole giuste ma rare (dati mai usati per tarare il correttore):

| | v0.1 | v2.0.2 |
|---|---|---|
| Refusi corretti da soli | 86,6% | 77,5% (+18,1% proposti nella barra) |
| Refusi corretti nel modo sbagliato | 12,5% | 1,4% |
| Parole giuste ma rare rovinate | 31,5% | 21,7% |

I test del correttore sono in `app/src/test` e girano a ogni compilazione su GitHub (`gradle testDebugUnitTest`).

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
