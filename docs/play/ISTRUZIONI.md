# Pubblicare La mia tastiera su Google Play: istruzioni passo passo

Tutto quello che serve è già pronto in questa cartella e in `docs/`:

| Cosa | Dove |
|---|---|
| Pacchetto da caricare (`.aab`) | artifact **`la-mia-tastiera-aab`** dell'ultima esecuzione di GitHub Actions (oppure `gradle bundleRelease` → `TastieraPersonale/app/build/outputs/bundle/release/app-release.aab`) |
| Icona 512×512 | `docs/icona/icona_512.png` |
| Grafica di presentazione 1024×500 | `docs/play/grafica_1024x500.png` (inglese: `…_en.png`) |
| Screenshot (6, 1080×2160) | `docs/play/screenshot/` |
| Testi della scheda (IT/EN) | `docs/play/testi.md` |
| Informativa sulla privacy | `docs/privacy/index.html` → da pubblicare su `https://trama.wtf/mykeyboard/privacy` |

---

## 0. Prima di iniziare

1. **Pubblica l'informativa** su `https://trama.wtf/mykeyboard/privacy` e **compila i campi gialli** (nome e email di contatto) in `docs/privacy/index.html`. Apri l'indirizzo da un browser senza accesso: deve essere pubblico, senza login. La Play Console lo controlla.
2. **Account sviluppatore**: https://play.google.com/console → registrazione (25 USD una tantum, verifica dell'identità con documento e indirizzo, verifica del telefono).
   - **Account personale** creato dopo il 13 novembre 2023: prima di poter pubblicare devi fare un **test chiuso con almeno 12 tester per 14 giorni consecutivi** (vedi punto 5).
   - **Account organizzazione**: esente da questo requisito, ma richiede un numero D-U-N-S.
3. Il repository GitHub può diventare privato, **ma `bitfarmy/mykeyboard-dizionari` deve restare pubblico**: da lì l'app fa scaricare i dizionari aggiuntivi.

---

## 1. Crea l'app

Console → **Crea app**

| Campo | Valore |
|---|---|
| Nome | La mia tastiera |
| Lingua predefinita | Italiano – it-IT |
| App o gioco | **App** |
| Gratuita o a pagamento | **Gratuita** (non si può cambiare dopo) |
| Dichiarazioni | spunta Norme del programma per gli sviluppatori e Leggi sull'esportazione USA |

Il **nome del pacchetto** è `com.personale.tastiera`: non si potrà più cambiare dopo la prima pubblicazione.

---

## 2. Contenuti dell'app (Dashboard → *Configura l'app*)

Si compila una volta, in qualsiasi ordine. Risposte consigliate:

| Sezione | Cosa rispondere |
|---|---|
| **Norme sulla privacy** | `https://trama.wtf/mykeyboard/privacy` |
| **Accesso all'app** | *Tutte le funzionalità sono disponibili senza accesso speciale.* Nelle istruzioni scrivi: «Dopo l'installazione apri l'app e tocca i due pulsanti per abilitare la tastiera e sceglierla come tastiera attiva.» |
| **Annunci** | **No**, l'app non contiene annunci |
| **Classificazione dei contenuti** | Questionario IARC. Email: la tua. Categoria: **Utilità, produttività, comunicazione o altro**. Rispondi **No** a tutto (violenza, sesso, linguaggio, droghe, gioco d'azzardo, contenuti generati dagli utenti, condivisione della posizione, acquisti). Risultato atteso: PEGI 3 / Everyone |
| **Pubblico di destinazione** | Fascia **18 anni e oltre**. Così non scattano gli obblighi delle norme per le famiglie. L'app non si rivolge ai bambini |
| **App di notizie** | No |
| **App di tracciamento dei contatti / stato di salute** | No |
| **Funzionalità finanziarie** | *L'app non offre funzionalità finanziarie* |
| **ID pubblicità** | **No**, l'app non usa l'ID pubblicità |
| **Sicurezza dei dati** | vedi sotto |

### Sicurezza dei dati

1. *La tua app raccoglie o condivide tipi di dati utente richiesti?* → **No**.
2. Conferma e invia. Se la procedura chiede *crittografia in transito* o *eliminazione dei dati*, non compare: non raccogli nulla.

Questo è coerente con l'app: nessun permesso, nessuna rete, tutto resta sul dispositivo.

> Le tastiere vedono ciò che l'utente digita. Google lo sa e guarda soprattutto che l'informativa sia chiara e che i dati non escano dal telefono: per questo la nostra dice esplicitamente che non c'è accesso a Internet.

---

## 3. Scheda dello store (Presenza sullo store → *Scheda dello Store principale*)

Copia i testi da `docs/play/testi.md`.

| Campo | File |
|---|---|
| Nome, breve descrizione, descrizione completa | `testi.md` |
| Icona app | `docs/icona/icona_512.png` |
| Grafica di presentazione | `docs/play/grafica_1024x500.png` |
| Screenshot telefono (minimo 2, massimo 8) | `docs/play/screenshot/01…06` |
| Categoria | **Strumenti** |
| Email di contatto | la tua |

Poi **Gestisci traduzioni → Inglese** e incolla la versione inglese (con `grafica_1024x500_en.png`). Gli screenshot si possono riusare.

---

## 4. Firma: usa la tua chiave attuale (consigliato)

Con **Play App Signing** Google firma l'app che gli utenti scaricano dallo store. Se fai importare la **tua chiave attuale**, l'app dello store avrà **la stessa firma** degli APK di GitHub, e i due canali potranno aggiornarsi a vicenda. Se invece lasci che Google ne generi una nuova, chi ha installato da GitHub dovrà disinstallare prima di passare allo store.

1. Console → **Release → Test chiuso → Crea nuova release**. Alla sezione *Firma app* scegli **Usa la firma di app Play** e poi *Esporta e carica una chiave da un keystore Java* (la scritta esatta può cambiare).
2. Scarica da quella pagina **`pepk.jar`** e la **chiave pubblica di cifratura** (`encryption_public_key.pem`).
3. Esegui sul PC (con Java 17 o superiore):

```bash
java -jar pepk.jar \
  --keystore=/home/bitfarmy/.android-chiavi/tastiera.jks \
  --alias=tastiera \
  --output=chiave-per-play.zip \
  --include-cert \
  --rsa-aes-encryption \
  --encryption-key-path=encryption_public_key.pem
```

   Chiede la password del keystore e quella della chiave: sono in `~/.android-chiavi/keystore.properties`.
4. Carica `chiave-per-play.zip` nella Play Console.
5. Come *chiave di caricamento* puoi usare di nuovo la stessa. È la scelta più semplice; più avanti, volendo, ne crei una dedicata dal menu *Integrità dell'app*.

⚠️ **Non perdere `tastiera.jks`** e le sue password: fai una copia su un supporto sicuro. Se la perdi, chi ha l'app non potrà più aggiornarla dallo stesso canale.

---

## 5. Test chiuso (obbligatorio per i nuovi account personali)

1. **Test → Test chiuso → Crea traccia** (o usa quella predefinita). **Testers**: crea una lista di indirizzi **Gmail/Google** oppure un **gruppo Google**. Servono **almeno 12 tester** (meglio 15–20, perché se uno esce il conteggio si ferma).
2. Carica `app-release.aab` nella release, scrivi le note («Prima versione») e avvia la release.
3. Condividi con i tester il **link di adesione** che la Console mostra nella traccia. Ognuno deve **accettare l'invito e installare l'app**, e **restare iscritto per 14 giorni di seguito**.
4. Passati i 14 giorni, nella Dashboard compare **Richiedi l'accesso alla produzione**: rispondi al breve questionario (come hai trovato i tester, cosa hai corretto in base ai loro commenti). Google risponde di solito entro pochi giorni.

Mentre aspetti, controlla in **Pre-lancio → Rapporto** i risultati dei test automatici sui dispositivi.

---

## 6. Produzione

Dopo l'approvazione: **Release → Produzione → Crea nuova release** → carica l'`.aab` → **Rivedi e pubblica**. La prima revisione può durare qualche giorno. Sceglierai i Paesi (Italia e, se vuoi, tutti gli altri).

---

## Per ogni aggiornamento successivo

- Aumenta `versionCode` (e `versionName`) in `TastieraPersonale/app/build.gradle.kts`. Google rifiuta un `versionCode` già usato.
- Fai il push: GitHub Actions compila l'`.aab`; scaricalo dall'artifact `la-mia-tastiera-aab`.
- Console → **Release → Produzione → Crea nuova release** → carica → note di versione → pubblica.

## Cose specifiche di questa app

- **Utenti da abilitare a mano**: una tastiera va attivata dalle impostazioni di Android. L'app ha già due pulsanti che aprono le schermate giuste; ricordalo nelle note di versione e nella descrizione.
- **Target API 36**: richiesto da Google dal 31 agosto 2026. Quando uscirà il livello successivo, si dovrà aggiornare `compileSdk`/`targetSdk` (e probabilmente il plugin Android Gradle).
- **Informativa**: se un giorno l'app raccogliesse dati (non è previsto), si aggiornerebbero informativa, modulo *Sicurezza dei dati* e scheda *prima* della pubblicazione.
