package com.personale.tastiera

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.min

/**
 * Dizionario base (assets/parole_it.txt, parole ordinate dalla più usata)
 * + parole imparate da te (salvate solo sul telefono, in parole_imparate.txt).
 *
 * Va usato solo dal thread principale; il caricamento avviene in background.
 */
class Dizionario private constructor(private val ctx: Context) {

    companion object {
        @Volatile
        private var istanza: Dizionario? = null

        fun get(context: Context): Dizionario = istanza ?: synchronized(this) {
            istanza ?: Dizionario(context.applicationContext).also {
                istanza = it
                it.carica()
            }
        }

        /** Sotto questa soglia il dizionario è "base": correggiamo solo gli accenti. */
        const val PAROLE_PER_CORREZIONE_COMPLETA = 10_000

        private const val BONUS_UTENTE = 150
        private val SEGNI_DIACRITICI = Regex("\\p{Mn}+")
        private val SPAZI = Regex("\\s+")
        private val ITALIANO: Locale = Locale.ITALIAN

        /** "Perché" → "perche": serve a trovare parole anche senza accenti. */
        fun normalizza(s: String): String =
            Normalizer.normalize(s.lowercase(ITALIANO), Normalizer.Form.NFD).replace(SEGNI_DIACRITICI, "")

        /** Copia maiuscole/minuscole da quello che hai scritto: "Cia" + "ciao" → "Ciao". */
        fun adattaMaiuscole(modello: String, parola: String): String = when {
            modello.length > 1 && modello.all { it.isUpperCase() } -> parola.uppercase(ITALIANO)
            modello.firstOrNull()?.isUpperCase() == true -> parola.replaceFirstChar { it.titlecase(ITALIANO) }
            else -> parola
        }
    }

    private data class Voce(val chiave: String, val parola: String)

    private val confronto = compareBy<Voce>({ it.chiave }, { it.parola })
    private val base = HashMap<String, Int>()     // parola → punteggio (più alto = più comune)
    private val utente = HashMap<String, Int>()   // parola → quante volte l'hai usata
    private var indice = ArrayList<Voce>()        // ordinato per chiave normalizzata
    private val file = File(ctx.filesDir, "parole_imparate.txt")
    private val principale = Handler(Looper.getMainLooper())
    private val inAttesa = ArrayList<() -> Unit>()
    private var modificato = false

    var pronto = false
        private set

    val numeroParole: Int get() = base.size
    val numeroImparate: Int get() = utente.size

    fun quandoPronto(azione: () -> Unit) {
        if (pronto) azione() else inAttesa.add(azione)
    }

    // ---------- Caricamento e salvataggio ----------

    private fun carica() {
        thread(name = "carica-dizionario") {
            val parole = ArrayList<String>()
            try {
                ctx.assets.open("parole_it.txt").bufferedReader(Charsets.UTF_8).useLines { righe ->
                    righe.forEach { riga ->
                        if (!riga.startsWith("#")) {
                            // Accetta sia "parola" sia "parola 12345" (i numeri vengono ignorati)
                            riga.trim().split(SPAZI).forEach { t ->
                                if (t.isNotEmpty() && t.all { it.isLetter() }) parole.add(t.lowercase(ITALIANO))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Nessun dizionario: funzioneranno solo le parole imparate.
            }

            val nuovaBase = HashMap<String, Int>(parole.size * 2)
            val n = parole.size
            parole.forEachIndexed { i, p ->
                if (!nuovaBase.containsKey(p)) nuovaBase[p] = 1 + 1000 * (n - i) / n
            }

            val nuovoUtente = HashMap<String, Int>()
            try {
                if (file.exists()) {
                    file.forEachLine(Charsets.UTF_8) { riga ->
                        val parti = riga.split('\t')
                        val volte = parti.getOrNull(1)?.toIntOrNull()
                        if (parti.size == 2 && volte != null) nuovoUtente[parti[0]] = volte
                    }
                }
            } catch (e: Exception) {
                // File rovinato: ripartiamo da zero.
            }

            val tutte = HashSet<String>(nuovaBase.keys).apply { addAll(nuovoUtente.keys) }
            val nuovoIndice = ArrayList<Voce>(tutte.size)
            tutte.mapTo(nuovoIndice) { Voce(normalizza(it), it) }
            nuovoIndice.sortWith(confronto)

            principale.post {
                base.putAll(nuovaBase)
                nuovoUtente.forEach { (p, v) -> utente[p] = (utente[p] ?: 0) + v }
                val imparateNelFrattempo = utente.keys.filter { it !in tutte }
                indice = nuovoIndice
                imparateNelFrattempo.forEach { inserisci(it) }
                pronto = true
                inAttesa.forEach { it() }
                inAttesa.clear()
            }
        }
    }

    fun salva() {
        if (!modificato || !pronto) return
        modificato = false
        val copia = HashMap(utente)
        thread(name = "salva-parole") {
            synchronized(file) {
                try {
                    val temporaneo = File(file.parentFile, file.name + ".tmp")
                    temporaneo.bufferedWriter(Charsets.UTF_8).use { out ->
                        copia.forEach { (p, v) ->
                            out.write(p)
                            out.write("\t")
                            out.write(v.toString())
                            out.newLine()
                        }
                    }
                    temporaneo.renameTo(file)
                } catch (e: Exception) {
                    // Riproveremo al prossimo salvataggio.
                }
            }
        }
    }

    fun cancellaImparate() {
        utente.clear()
        indice.removeAll { !base.containsKey(it.parola) }
        modificato = false
        thread { synchronized(file) { file.delete() } }
    }

    // ---------- Uso ----------

    fun impara(parola: String, volte: Int = 1) {
        val w = parola.lowercase(ITALIANO)
        if (w.length < 2 || w.length > 30 || !w.all { it.isLetter() }) return
        val nuova = !base.containsKey(w) && !utente.containsKey(w)
        utente[w] = volteUtente(w) + volte
        if (nuova) inserisci(w)
        modificato = true
    }

    /** Fino a [quanti] parole che iniziano con [scritto], dalla più probabile. */
    fun completamenti(scritto: String, quanti: Int): List<String> {
        val chiave = normalizza(scritto)
        if (chiave.isEmpty()) return emptyList()
        val minuscolo = scritto.lowercase(ITALIANO)
        val migliori = ArrayList<String>(quanti + 1)

        var i = primoIndice(chiave)
        while (i < indice.size && indice[i].chiave.startsWith(chiave)) {
            val p = indice[i].parola
            if (p != minuscolo && conosciuta(p)) {
                val punti = punteggio(p)
                var pos = migliori.size
                while (pos > 0 && punteggio(migliori[pos - 1]) < punti) pos--
                if (pos < quanti) {
                    migliori.add(pos, p)
                    if (migliori.size > quanti) migliori.removeAt(migliori.size - 1)
                }
            }
            i++
        }
        return migliori.map { adattaMaiuscole(scritto, it) }
    }

    /** La correzione per [scritta], oppure null se la parola va bene così. */
    fun correggi(scritta: String): String? {
        val w = scritta.lowercase(ITALIANO)
        if (w.length < 2 || !w.all { it.isLetter() } || conosciuta(w) || indice.isEmpty()) return null
        val chiave = normalizza(w)
        var migliore: String? = null
        var punti = -1

        // 1) Stesse lettere ma accenti diversi: "perche" → "perché", "citta" → "città"
        var i = primoIndice(chiave)
        while (i < indice.size && indice[i].chiave == chiave) {
            val p = indice[i].parola
            if (p != w && conosciuta(p) && punteggio(p) > punti) {
                migliore = p
                punti = punteggio(p)
            }
            i++
        }
        if (migliore != null) return adattaMaiuscole(scritta, migliore)

        // 2) Errori di battitura: solo con un dizionario grande, altrimenti rovineremmo parole giuste
        if (base.size < PAROLE_PER_CORREZIONE_COMPLETA || chiave.length < 4) return null
        for (v in indice) {
            if (abs(v.chiave.length - chiave.length) > 1 || !conosciuta(v.parola)) continue
            if (distanzaUno(chiave, v.chiave)) {
                val pt = punteggio(v.parola)
                if (pt > punti) {
                    migliore = v.parola
                    punti = pt
                }
            }
        }
        return migliore?.let { adattaMaiuscole(scritta, it) }
    }

    // ---------- Interni ----------

    private fun volteUtente(p: String) = utente[p] ?: 0

    /** Nel dizionario base, oppure usata da te almeno due volte (così un errore singolo non viene imparato). */
    private fun conosciuta(p: String) = base.containsKey(p) || volteUtente(p) >= 2

    private fun punteggio(p: String) = (base[p] ?: 0) + min(volteUtente(p), 20) * BONUS_UTENTE

    private fun inserisci(parola: String) {
        val voce = Voce(normalizza(parola), parola)
        val pos = indice.binarySearch(voce, confronto)
        if (pos < 0) indice.add(-pos - 1, voce)
    }

    private fun primoIndice(chiave: String): Int {
        var basso = 0
        var alto = indice.size
        while (basso < alto) {
            val medio = (basso + alto) ushr 1
            if (indice[medio].chiave < chiave) basso = medio + 1 else alto = medio
        }
        return basso
    }

    /** Vero se a e b differiscono per una sola lettera sbagliata, mancante, in più o due lettere scambiate. */
    private fun distanzaUno(a: String, b: String): Boolean {
        if (a == b) return false
        if (a.length == b.length) {
            var i = 0
            while (i < a.length && a[i] == b[i]) i++
            val scambio = i + 1 < a.length && a[i] == b[i + 1] && a[i + 1] == b[i] &&
                a.regionMatches(i + 2, b, i + 2, a.length - i - 2)
            return scambio || a.regionMatches(i + 1, b, i + 1, a.length - i - 1)
        }
        val corta = if (a.length < b.length) a else b
        val lunga = if (a.length < b.length) b else a
        if (lunga.length - corta.length != 1) return false
        var i = 0
        while (i < corta.length && corta[i] == lunga[i]) i++
        return corta.regionMatches(i, lunga, i + 1, corta.length - i)
    }
}
