package com.personale.tastiera

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * Le parole di una lingua (con quanto sono comuni) e le regole per completarle e correggerle.
 * Non usa nulla di Android: i test in src/test lo provano direttamente sul computer.
 * Va usato da un thread solo.
 */
class Lessico(
    val locale: Locale,
    private val vicinanza: Vicinanza = Vicinanza.NESSUNA,
    var parametri: Parametri = Parametri(),
) {

    /** Una correzione. [sicura] = abbastanza certa da applicarla da sola premendo spazio. */
    data class Correzione(val testo: String, val sicura: Boolean)

    /**
     * Le manopole del correttore, tarate sul banco di prova (vedi BancoDiProvaTest).
     * Il correttore confronta due ipotesi per una parola sconosciuta: "hai sbagliato a scrivere una
     * parola del dizionario" contro "è una parola vera che non conosco" (vedi [ModelloLettere]).
     */
    data class Parametri(
        /** Quanto "costa" un errore di battitura: ogni unità di costo divide la probabilità per e^penalità. */
        val penalita: Double = 10.0,
        /** ln della probabilità che una parola scritta apposta non sia nel dizionario. */
        val lnSconosciuta: Double = -4.0,
        /** Probabilità minima della correzione per applicarla da sola. */
        val soglia: Double = 0.9,
    )

    private class Voce(val chiave: String, val parola: String) {
        val maschera = maschera(chiave)
    }

    private class Candidato(val parola: String, val costo: Float, val punti: Double)

    companion object {
        private val SEGNI_DIACRITICI = Regex("\\p{Mn}+")
        private val SPAZI = Regex("\\s+")

        /** Quante proposte si tengono per stimare la probabilità della prima. */
        private const val CANDIDATI = 6

        // Costi degli errori (1 = una lettera sbagliata a caso)
        private const val VICINO = 0.6f       // tasto accanto a quello giusto
        private const val LONTANO = 1.0f
        private const val SCAMBIO = 0.6f      // due lettere invertite: "pre" → "per"
        private const val DOPPIA_PERSA = 0.4f // "tuto" → "tutto"
        private const val MANCANTE = 0.9f

        fun normalizza(s: String, locale: Locale = Locale.ROOT): String =
            Normalizer.normalize(s.lowercase(locale).replace('’', '\''), Normalizer.Form.NFD)
                .replace(SEGNI_DIACRITICI, "")

        /** Una parola valida: lettere, eventualmente con apostrofi (c'è, po', l'altro, I'm). */
        fun eParola(s: String): Boolean =
            s.any { it.isLetter() } && s.all { it.isLetter() || it == '\'' || it == '’' }

        private fun haAccenti(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).any {
            Character.getType(it) == Character.NON_SPACING_MARK.toInt()
        }

        /** Un bit per lettera presente: due parole a pochi errori di distanza hanno maschere simili. */
        private fun maschera(s: String): Int {
            var m = 0
            for (c in s) m = m or (1 shl (if (c in 'a'..'z') c - 'a' else 26 + c.code % 6))
            return m
        }

        private fun punteggioBase(conteggio: Long) = (100 * ln(conteggio.toDouble() + 1)).toInt()

        /** Le parole che usi salgono in classifica: 2 volte ≈ una parola comune, 20 volte ≈ molto comune. */
        private fun bonus(volte: Int) = if (volte <= 0) 0 else (250 * ln(1.0 + min(volte, 1000))).toInt()
    }

    private val base = HashMap<String, Int>()      // minuscola → punteggio (100 × ln conteggio)
    private val forme = HashMap<String, String>()  // minuscola → come si scrive, se diverso (i → I, haus → Haus)
    private val utente = HashMap<String, Int>()    // minuscola → quante volte l'hai usata
    private val daTogliere = HashSet<String>()
    private var indice = ArrayList<Voce>()         // ordinato per chiave normalizzata, poi parola
    private val perLunghezza = HashMap<Int, ArrayList<Voce>>() // solo parole senza apostrofo
    private var conApostrofo = HashMap<String, MutableList<String>>() // "ce" → [c'è]
    private var modello = ModelloLettere()
    private var lnTotale = 0.0 // ln della somma dei conteggi: serve a confrontare parole e non-parole
    private val confronto = compareBy<Voce>({ it.chiave }, { it.parola })

    private var versione = 0
    private var cacheChiave = ""
    private var cacheVersione = -1
    private var cacheRisultato: List<Correzione> = emptyList()

    val numeroParole: Int get() = base.size
    val numeroImparate: Int get() = utente.size
    val vuoto: Boolean get() = indice.isEmpty()

    // ---------- Caricamento ----------

    /**
     * Legge un dizionario. Righe accettate: "parola<TAB>conteggio", "parola conteggio",
     * più parole separate da spazi (ordinate dalla più comune), "-parola" (da togliere), "# commento".
     */
    fun caricaDizionario(righe: Sequence<String>) {
        var posizione = 0
        for (riga in righe) {
            if (riga.isBlank() || riga.startsWith("#")) continue
            val parti = riga.trim().split(SPAZI)
            val conteggio = if (parti.size == 2) parti[1].toLongOrNull() else null
            val parole = if (conteggio != null) listOf(parti[0]) else parti
            for (t in parole) {
                if (t.toLongOrNull() != null) continue
                if (t.startsWith("-")) {
                    val w = t.substring(1).lowercase(locale).replace('’', '\'')
                    if (eParola(w)) daTogliere.add(w)
                    continue
                }
                val forma = t.replace('’', '\'')
                if (!eParola(forma) || forma.length > 30) continue
                val w = forma.lowercase(locale)
                // Senza conteggi vale la posizione: la frequenza delle parole scende circa come 1 / posizione.
                val punti = punteggioBase(conteggio ?: (10_000_000L / (posizione + 10)))
                posizione++
                if (base.containsKey(w)) continue
                base[w] = punti
                if (forma != w) forme[w] = forma
            }
        }
        daTogliere.forEach { base.remove(it); forme.remove(it) }
    }

    /** Righe "parola<TAB>volte", come le scrive [paroleImparate]. */
    fun caricaImparate(righe: Sequence<String>) {
        for (riga in righe) {
            val parti = riga.split('\t')
            val volte = parti.getOrNull(1)?.toIntOrNull() ?: continue
            if (parti.size == 2 && parti[0] !in daTogliere) utente[parti[0]] = (utente[parti[0]] ?: 0) + volte
        }
    }

    /** Da chiamare dopo aver caricato tutto: prepara gli indici di ricerca. */
    fun prepara() {
        val tutte = HashSet<String>(base.keys).apply { addAll(utente.keys) }
        val nuovo = ArrayList<Voce>(tutte.size)
        tutte.mapTo(nuovo) { Voce(normalizza(it, locale), it) }
        nuovo.sortWith(confronto)
        indice = nuovo
        perLunghezza.clear()
        nuovo.forEach { aggiungiPerLunghezza(it) }
        val mappa = HashMap<String, MutableList<String>>()
        base.keys.filter { '\'' in it }.forEach { w ->
            mappa.getOrPut(normalizza(w, locale).replace("'", "")) { ArrayList() }.add(w)
        }
        conApostrofo = mappa
        modello = ModelloLettere().apply { addestra(tutte.filter { '\'' !in it && conosciuta(it) }) }
        lnTotale = ln(base.values.sumOf { exp(it / 100.0) } + 1.0)
        versione++
    }

    fun paroleImparate(): Map<String, Int> = HashMap(utente)

    fun cancellaImparate() {
        utente.clear()
        indice.removeAll { !base.containsKey(it.parola) }
        perLunghezza.values.forEach { lista -> lista.removeAll { !base.containsKey(it.parola) } }
        versione++
    }

    // ---------- Uso ----------

    /** Nel dizionario, oppure usata da te almeno due volte (così un errore singolo non viene imparato). */
    fun conosciuta(parola: String): Boolean {
        val w = parola.lowercase(locale).replace('’', '\'')
        return base.containsKey(w) || (utente[w] ?: 0) >= 2
    }

    fun impara(parola: String, volte: Int = 1) {
        val w = parola.lowercase(locale)
        if (w.length < 2 || w.length > 30 || !w.all { it.isLetter() } || w in daTogliere) return
        val nuova = !base.containsKey(w) && !utente.containsKey(w)
        utente[w] = (utente[w] ?: 0) + volte
        if (nuova) inserisci(w)
        versione++
    }

    /** Fino a [quanti] parole che iniziano con [scritto], dalla più probabile. */
    fun completamenti(scritto: String, quanti: Int): List<String> {
        val chiave = normalizza(scritto, locale)
        if (chiave.isEmpty()) return emptyList()
        val minuscolo = scritto.lowercase(locale).replace('’', '\'')
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
        return migliori.map { adattaMaiuscole(scritto, forma(it)) }
    }

    /**
     * Solo accenti, apostrofi e maiuscole obbligatorie: "perche" → "perché", "c'e" → "c'è",
     * "po" → "po'", "i" → "I" (inglese), "haus" → "Haus" (tedesco). Mai cambi di lettere.
     */
    fun correggiAccenti(scritta: String): String? {
        val w = scritta.lowercase(locale).replace('’', '\'')
        if (!eParola(w) || indice.isEmpty()) return null
        if (conosciuta(w)) {
            // Parola giusta ma con la minuscola dove ci vuole la maiuscola
            val f = forme[w] ?: return null
            return if (scritta == w && f != w) f else null
        }
        if (w.length < 2) return null
        val chiave = normalizza(w, locale)

        val candidati = ArrayList<String>()
        var i = primoIndice(chiave)
        while (i < indice.size && indice[i].chiave == chiave) {
            candidati.add(indice[i].parola)
            i++
        }
        conApostrofo[chiave.replace("'", "")]?.let { candidati.addAll(it) }
        var validi = candidati.filter { it != w && conosciuta(it) }
        // Hai messo un accento (perchè, cosí): l'accento giusto è più probabile di nessun accento
        if (haAccenti(w) && validi.any { haAccenti(it) }) validi = validi.filter { haAccenti(it) }
        val migliore = validi.maxByOrNull { punteggio(it) } ?: return null
        return adattaMaiuscole(scritta, forma(migliore))
    }

    /** La correzione migliore per [scritta], oppure null se la parola va bene così. */
    fun correggi(scritta: String): Correzione? = correzioni(scritta, 1).firstOrNull()

    /**
     * Fino a [quante] correzioni, dalla più probabile. Solo la prima può essere [Correzione.sicura].
     */
    fun correzioni(scritta: String, quante: Int = 2): List<Correzione> {
        if (scritta == cacheChiave && versione == cacheVersione && cacheRisultato.size >= quante) {
            return cacheRisultato.take(quante)
        }
        val risultato = calcolaCorrezioni(scritta, quante)
        cacheChiave = scritta
        cacheVersione = versione
        cacheRisultato = risultato
        return risultato
    }

    private fun calcolaCorrezioni(scritta: String, quante: Int): List<Correzione> {
        val w = scritta.lowercase(locale).replace('’', '\'')
        if (!eParola(w) || indice.isEmpty()) return emptyList()
        // Maiuscole in mezzo alla parola (iPhone, McDonald): è un nome scritto apposta così
        val tuttoMaiuscolo = scritta.filter { it.isLetter() }.all { it.isUpperCase() }
        if (!tuttoMaiuscolo && scritta.drop(1).any { it.isUpperCase() }) return emptyList()

        correggiAccenti(scritta)?.let { return listOf(Correzione(it, true)) }
        if (conosciuta(w)) return emptyList()
        return refusi(scritta, w, quante)
    }

    // ---------- Errori di battitura ----------

    private fun refusi(scritta: String, w: String, quante: Int): List<Correzione> {
        val chiave = normalizza(w, locale)
        val p = parametri
        val migliori = candidati(chiave, maxOf(quante, CANDIDATI), p.penalita)
        val primo = migliori.firstOrNull() ?: return emptyList()
        // Probabilità della prima proposta contro le altre e contro "parola vera che non conosco"
        var somma = exp(p.lnSconosciuta + punteggioSconosciuta(w) - primo.punti)
        for (c in migliori) somma += exp(c.punti - primo.punti)
        val sicura = 1.0 / somma >= p.soglia
        return migliori.take(quante).mapIndexed { i, c ->
            Correzione(adattaMaiuscole(scritta, forma(c.parola)), i == 0 && sicura)
        }
    }

    /** ln P(parola sconosciuta scritta così), senza la probabilità a priori [Parametri.lnSconosciuta]. */
    private fun punteggioSconosciuta(w: String) = lnTotale + modello.logProb(w)

    /** Le parole del dizionario vicine a [chiave], dalla più probabile; punti = ln frequenza − penalità × costo. */
    private fun candidati(chiave: String, tenere: Int, penalita: Double): List<Candidato> {
        if ('\'' in chiave) return emptyList()
        val n = chiave.length
        val limite = when {
            n <= 2 -> return emptyList()
            n == 3 -> MANCANTE // parole corte: un tasto vicino, due lettere invertite o una lettera dimenticata
            n <= 5 -> 1.0f
            n <= 7 -> 1.6f
            else -> 2.0f
        }
        // Ogni errore che cambia l'insieme delle lettere costa almeno VICINO e cambia al massimo 2 bit
        val bitMassimi = 2 * (limite / VICINO).toInt()
        val differenzaMassima = min(2, (limite / DOPPIA_PERSA).toInt())
        val m = maschera(chiave)
        val a0 = chiave[0]
        val a1 = chiave[1]
        val migliori = ArrayList<Candidato>(tenere + 1)

        for (lunghezza in maxOf(2, n - differenzaMassima)..n + differenzaMassima) {
            val lista = perLunghezza[lunghezza] ?: continue
            for (v in lista) {
                val b = v.chiave
                // Raramente si sbagliano entrambe le prime due lettere: filtro velocissimo
                if (b[0] != a0 && b[1] != a1 && b[0] != a1 && b[1] != a0) continue
                if (Integer.bitCount(m xor v.maschera) > bitMassimi) continue
                if (!conosciuta(v.parola)) continue
                val c = costo(chiave, b, limite)
                if (c > limite) continue
                val punti = punteggio(v.parola) / 100.0 - penalita * c
                var pos = migliori.size
                while (pos > 0 && migliori[pos - 1].punti < punti) pos--
                if (pos < tenere) {
                    migliori.add(pos, Candidato(v.parola, c, punti))
                    if (migliori.size > tenere) migliori.removeAt(migliori.size - 1)
                }
            }
        }
        return migliori
    }

    /** Solo per la taratura nei test: candidati (parola, costo, ln frequenza) e punteggio "sconosciuta". */
    internal fun perTaratura(scritta: String): Pair<List<Triple<String, Float, Double>>, Double> {
        val w = scritta.lowercase(locale)
        val lista = candidati(normalizza(w, locale), 300, 0.0).map { Triple(it.parola, it.costo, it.punti) }
        return lista to punteggioSconosciuta(w)
    }

    private var riga0 = FloatArray(32)
    private var riga1 = FloatArray(32)
    private var riga2 = FloatArray(32)

    /**
     * Quanto costa passare da [scritto] a [parola] (distanza di Damerau-Levenshtein pesata
     * secondo la tastiera). Si ferma appena supera [limite].
     */
    internal fun costo(scritto: String, parola: String, limite: Float): Float {
        val n = scritto.length
        val m = parola.length
        if (riga0.size <= m) {
            riga0 = FloatArray(m + 1)
            riga1 = FloatArray(m + 1)
            riga2 = FloatArray(m + 1)
        }
        var primaPrima = riga2 // riga i-2
        var prima = riga0      // riga i-1
        var ora = riga1        // riga i
        prima[0] = 0f
        for (j in 1..m) prima[j] = prima[j - 1] + costoMancante(parola, j - 1)
        var minimoPrima = 0f
        for (i in 1..n) {
            val a = scritto[i - 1]
            val inPiu = costoInPiu(scritto, i - 1)
            ora[0] = prima[0] + inPiu
            var minimo = ora[0]
            for (j in 1..m) {
                val b = parola[j - 1]
                val sostituzione = if (a == b) 0f else if (vicinanza.vicini(a, b)) VICINO else LONTANO
                var v = min(prima[j - 1] + sostituzione, min(prima[j] + inPiu, ora[j - 1] + costoMancante(parola, j - 1)))
                if (i > 1 && j > 1 && a == parola[j - 2] && scritto[i - 2] == b && a != b) {
                    v = min(v, primaPrima[j - 2] + SCAMBIO)
                }
                ora[j] = v
                if (v < minimo) minimo = v
            }
            // Ci si ferma solo se anche la riga prima supera il limite: uno scambio riparte da lì
            if (minimo > limite && minimoPrima > limite) return Float.MAX_VALUE
            minimoPrima = minimo
            val t = primaPrima
            primaPrima = prima
            prima = ora
            ora = t
        }
        return prima[m]
    }

    /** Una lettera battuta in più costa poco se è doppia o se è un tasto accanto a quello vicino. */
    private fun costoInPiu(s: String, i: Int): Float {
        val c = s[i]
        val prima = s.getOrNull(i - 1)
        val dopo = s.getOrNull(i + 1)
        val facile = (prima != null && (prima == c || vicinanza.vicini(prima, c))) ||
            (dopo != null && (dopo == c || vicinanza.vicini(dopo, c)))
        return if (facile) VICINO else LONTANO
    }

    /** Una lettera dimenticata: costa poco se era una doppia ("tuto" → "tutto"). */
    private fun costoMancante(parola: String, j: Int): Float =
        if (j > 0 && parola[j - 1] == parola[j]) DOPPIA_PERSA else MANCANTE

    // ---------- Interni ----------

    private fun punteggio(p: String) = (base[p] ?: 0) + bonus(utente[p] ?: 0)

    private fun forma(p: String) = forme[p] ?: p

    /** Copia maiuscole/minuscole da quello che hai scritto: "Cia" + "ciao" → "Ciao". */
    fun adattaMaiuscole(modello: String, parola: String): String {
        val lettere = modello.filter { it.isLetter() }
        return when {
            lettere.length > 1 && lettere.all { it.isUpperCase() } -> parola.uppercase(locale)
            lettere.firstOrNull()?.isUpperCase() == true -> parola.replaceFirstChar { it.titlecase(locale) }
            else -> parola
        }
    }

    private fun inserisci(parola: String) {
        val voce = Voce(normalizza(parola, locale), parola)
        val pos = indice.binarySearch(voce, confronto)
        if (pos < 0) indice.add(-pos - 1, voce)
        aggiungiPerLunghezza(voce)
    }

    private fun aggiungiPerLunghezza(v: Voce) {
        if ('\'' in v.chiave) return
        perLunghezza.getOrPut(v.chiave.length) { ArrayList() }.add(v)
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
}

/** Quali tasti stanno accanto: un errore su un tasto vicino è molto più probabile. */
class Vicinanza private constructor(private val mappa: Map<Char, Set<Char>>) {

    fun vicini(a: Char, b: Char): Boolean = mappa[a]?.contains(b) == true

    companion object {
        val NESSUNA = Vicinanza(emptyMap())

        /** Calcola i vicini dalle righe di lettere della tastiera (stessa riga o righe adiacenti). */
        fun da(righe: List<List<Tasto>>): Vicinanza {
            data class Pos(val c: Char, val riga: Int, val x: Float)
            val posizioni = ArrayList<Pos>()
            righe.forEachIndexed { r, riga ->
                val totale = riga.sumOf { it.larghezza.toDouble() }.toFloat()
                var x = 0f
                for (t in riga) {
                    val testo = Lessico.normalizza(t.etichetta)
                    if (t.codice == Codici.TESTO && testo.length == 1 && testo[0].isLetter()) {
                        posizioni.add(Pos(testo[0], r, (x + t.larghezza / 2) / totale))
                    }
                    x += t.larghezza
                }
            }
            val mappa = HashMap<Char, MutableSet<Char>>()
            for (a in posizioni) for (b in posizioni) {
                if (a.c == b.c) continue
                val dx = abs(a.x - b.x)
                val vicini = when (abs(a.riga - b.riga)) {
                    0 -> dx < 0.15f
                    1 -> dx < 0.11f
                    else -> false
                }
                if (vicini) mappa.getOrPut(a.c) { HashSet() }.add(b.c)
            }
            return Vicinanza(mappa)
        }
    }

    override fun toString(): String = mappa.entries.sortedBy { it.key }
        .joinToString(" ") { (k, v) -> "$k:${v.sorted().joinToString("")}" }
}

/**
 * Quanto una sequenza di lettere "sembra" una parola della lingua (modello a trigrammi di lettere).
 * "rwcentemente" ha sequenze rare (rwc), "constatando" no: la seconda è probabilmente una parola vera
 * che il dizionario non conosce, e non va corretta.
 */
class ModelloLettere {
    private val tri = HashMap<Long, Int>()
    private val contesti2 = HashMap<Long, Int>()
    private val bi = HashMap<Long, Int>()
    private val contesti1 = HashMap<Char, Int>()
    private val uni = HashMap<Char, Int>()
    private var totale = 0

    private fun chiave(a: Char, b: Char, c: Char = '\u0000') =
        (a.code.toLong() shl 32) or (b.code.toLong() shl 16) or c.code.toLong()

    fun addestra(parole: Collection<String>) {
        for (w in parole) {
            val s = "^^$w$"
            for (i in 2 until s.length) {
                val a = s[i - 2]
                val b = s[i - 1]
                val c = s[i]
                tri.merge(chiave(a, b, c), 1, Int::plus)
                contesti2.merge(chiave(a, b), 1, Int::plus)
                bi.merge(chiave(b, c), 1, Int::plus)
                contesti1.merge(b, 1, Int::plus)
                uni.merge(c, 1, Int::plus)
                totale++
            }
        }
    }

    /** ln della probabilità della parola, lettera per lettera (fine parola compresa). */
    fun logProb(w: String): Double {
        if (totale == 0) return 0.0
        val s = "^^$w$"
        val alfabeto = uni.size + 1.0
        var lp = 0.0
        for (i in 2 until s.length) {
            val a = s[i - 2]
            val b = s[i - 1]
            val c = s[i]
            val p3 = contesti2[chiave(a, b)]?.let { (tri[chiave(a, b, c)] ?: 0).toDouble() / it } ?: 0.0
            val p2 = contesti1[b]?.let { (bi[chiave(b, c)] ?: 0).toDouble() / it } ?: 0.0
            val p1 = (uni[c] ?: 0).toDouble() / totale
            lp += ln(0.6 * p3 + 0.25 * p2 + 0.1 * p1 + 0.05 / alfabeto)
        }
        return lp
    }
}
