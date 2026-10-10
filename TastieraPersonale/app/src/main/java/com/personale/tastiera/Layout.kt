package com.personale.tastiera

import java.util.Locale

/** Codici dei tasti. TESTO = il tasto scrive la sua etichetta; gli altri sono comandi. */
object Codici {
    const val TESTO = 0
    const val SHIFT = -1
    const val CANC = -2
    const val INVIO = -3
    const val SIMBOLI = -4
    const val LETTERE = -5
    const val SIMBOLI2 = -6
    const val EMOJI = -7
    const val SPAZIO = -8
    const val LINGUA = -9
    const val VUOTO = -99 // spazio invisibile, serve solo a centrare le righe
}

data class Tasto(
    val etichetta: String,
    val codice: Int = Codici.TESTO,
    val larghezza: Float = 1f,
    /** Varianti che compaiono tenendo premuto (la prima è anche il piccolo suggerimento in alto). */
    val alternative: List<String> = emptyList(),
)

enum class Pagina { LETTERE, SIMBOLI, SIMBOLI2 }

/**
 * Una lingua della tastiera: disposizione delle lettere, varianti con gli accenti e dizionario.
 * Per aggiungerne una: una voce qui sotto in [Lingue.tutte], una riga in dizionari/genera_tutti.sh.
 */
data class Lingua(
    val codice: String,
    /** Il nome nella lingua stessa, come compare sullo spazio. */
    val nome: String,
    /** Il nome in italiano, per le impostazioni. */
    val nomeItaliano: String,
    val locale: Locale,
    /** Le tre righe di lettere, tasti separati da spazi. */
    val righe: List<String>,
    /** Lettera → varianti con gli accenti (tenendo premuto), separate da spazi. */
    val accenti: Map<String, String>,
    /** Il dizionario è dentro l'app (true) oppure si scarica (false). */
    val inclusa: Boolean = false,
)

object Lingue {
    val italiano = Lingua(
        "it", "italiano", "Italiano", Locale.ITALIAN,
        listOf("q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m"),
        mapOf(
            "e" to "è é", "u" to "ù ú", "i" to "ì í", "o" to "ò ó", "a" to "à á",
            "c" to "ç", "n" to "ñ",
        ),
        inclusa = true,
    )

    val tutte: List<Lingua> = listOf(
        italiano,
        Lingua(
            "en", "english", "Inglese", Locale.ENGLISH,
            listOf("q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m"),
            mapOf(
                "e" to "é è ê ë", "a" to "à á â ä", "i" to "í ì î ï", "o" to "ó ò ô ö",
                "u" to "ú ù û ü", "c" to "ç", "n" to "ñ",
            ),
        ),
        Lingua(
            "es", "español", "Spagnolo", Locale("es"),
            listOf("q w e r t y u i o p", "a s d f g h j k l ñ", "z x c v b n m"),
            mapOf(
                "a" to "á à", "e" to "é è", "i" to "í ï", "o" to "ó ò", "u" to "ú ü",
                "n" to "ñ", "c" to "ç",
            ),
        ),
        Lingua(
            "fr", "français", "Francese", Locale.FRENCH,
            listOf("a z e r t y u i o p", "q s d f g h j k l m", "w x c v b n é"),
            mapOf(
                "e" to "é è ê ë", "a" to "à â æ", "u" to "ù û ü", "i" to "î ï", "o" to "ô œ",
                "c" to "ç", "y" to "ÿ", "é" to "è ê ë à ù",
            ),
        ),
        Lingua(
            "de", "deutsch", "Tedesco", Locale.GERMAN,
            listOf("q w e r t z u i o p ü", "a s d f g h j k l ö ä", "y x c v b n m ß"),
            mapOf("a" to "ä à á", "o" to "ö ó ò", "u" to "ü ú ù", "s" to "ß", "e" to "é è"),
        ),
        Lingua(
            "pt", "português", "Portoghese (Brasile)", Locale("pt", "BR"),
            listOf("q w e r t y u i o p", "a s d f g h j k l ç", "z x c v b n m"),
            mapOf(
                "a" to "á ã â à", "e" to "é ê è", "i" to "í", "o" to "ó õ ô", "u" to "ú ü",
                "c" to "ç",
            ),
        ),
    )

    fun perCodice(codice: String): Lingua? = tutte.firstOrNull { it.codice == codice }
}

object Layout {

    private val NUMERI = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    // Varianti dei simboli, uguali per tutte le lingue. Modifica liberamente!
    private val SIMBOLI_ALTERNATIVI = mapOf(
        "." to "? ! ; : …", "," to "' \"",
        "-" to "_ – —", "'" to "‘ ’", "\"" to "« » “ ”",
        "€" to "\$ £ ¥", "?" to "¿", "!" to "¡", "%" to "‰",
    )

    /** Crea una riga da una stringa con i tasti separati da spazi. */
    private fun riga(tasti: String, varianti: Map<String, String> = SIMBOLI_ALTERNATIVI): List<Tasto> =
        tasti.split(" ").map { Tasto(it, alternative = varianti[it]?.split(" ") ?: emptyList()) }

    private fun vuoto(larghezza: Float) = Tasto("", Codici.VUOTO, larghezza)

    private val rigaNumeri: List<Tasto> = riga("1 2 3 4 5 6 7 8 9 0")

    // Virgola, punto e maiuscole stanno nella barra in alto: qui lo spazio si allarga.
    // Nella disposizione Qerty cancella sta in basso a destra, accanto a invio; nella Qwerty in fondo alla terza riga.
    private fun rigaFinale(tastoPagina: Tasto, nomeLingua: String, tastoLingua: Boolean, classica: Boolean): List<Tasto> =
        listOfNotNull(
            tastoPagina,
            if (tastoLingua) Tasto("🌐", Codici.LINGUA) else null,
            Tasto("🙂", Codici.EMOJI),
            Tasto(nomeLingua, Codici.SPAZIO, (if (tastoLingua) 3.5f else 4.5f) + if (classica) 1.5f else 0f),
            if (classica) null else Tasto("⌫", Codici.CANC, 1.5f),
            Tasto("↵", Codici.INVIO, 1.5f),
        )

    private fun tastoSimboli(classica: Boolean) = Tasto(if (classica) "?123" else "@123", Codici.SIMBOLI, 1.5f)

    /** Nella Qwerty cancella è l'ultimo tasto della terza riga; nella Qerty lì c'è uno spazio vuoto. */
    private fun fineTerzaRiga(classica: Boolean): Tasto = if (classica) Tasto("⌫", Codici.CANC, 1.5f) else vuoto(1.5f)

    /** La lingua con le righe giuste per la disposizione scelta, e come disegnare ⌫ e ?. */
    class Configurazione(val lingua: Lingua, val classica: Boolean, val qerty: Boolean)

    fun configurazione(lingua: Lingua, prefs: Preferenze): Configurazione = when (prefs.disposizione) {
        "qwerty" -> Configurazione(lingua, classica = true, qerty = false)
        "personale" -> {
            val righe = prefs.righePersonali(lingua.codice)
            Configurazione(
                if (righe != null) lingua.copy(righe = righe) else lingua,
                classica = prefs.cancellaPersonaleInFondo,
                qerty = false,
            )
        }
        else -> Configurazione(lingua, classica = false, qerty = true)
    }

    // Sempre la stessa lista per la stessa tastiera: la vista ridisegna solo se cambia davvero
    private val cache = HashMap<String, List<List<Tasto>>>()

    /**
     * Solo le tre righe di lettere: servono anche per sapere quali tasti sono vicini.
     * Con [numeriSulleLettere] i numeri compaiono tenendo premuta la prima riga.
     * Con [classica] la disposizione è la Qwerty di sempre, senza ⌫: la aggiunge [lettere].
     */
    fun righeLettere(
        lingua: Lingua,
        numeriSulleLettere: Boolean = true,
        classica: Boolean = false,
        qerty: Boolean = !classica,
    ): List<List<Tasto>> {
        val primaOriginale = lingua.righe[0].split(" ")
        // Qerty: la w sta in basso a sinistra, accanto alla z (dove la terza riga non ce l'ha già)
        val spostaW = qerty && "w" in primaOriginale && "w" !in lingua.righe[2].split(" ")
        val prima = if (spostaW) primaOriginale - "w" else primaOriginale
        val terza = if (spostaW) listOf("w") + lingua.righe[2].split(" ") else lingua.righe[2].split(" ")
        val seconda = lingua.righe[1].split(" ")
        // Qerty: in basso a sinistra, prima della w, il punto di domanda (tenendo premuto, il punto esclamativo)
        val terzaConPunto = if (qerty) listOf("?") + terza else terza

        // Varianti: il numero della posizione originale sulla prima riga, poi le lettere accentate
        val varianti = HashMap<String, String>()
        varianti["?"] = "! ¿"
        (prima + seconda + terza).forEach { lettera ->
            val numero = if (numeriSulleLettere) primaOriginale.indexOf(lettera).takeIf { it in NUMERI.indices }?.let { NUMERI[it] } else null
            val tutte = listOfNotNull(numero, lingua.accenti[lettera]).joinToString(" ")
            if (tutte.isNotEmpty()) varianti[lettera] = tutte
        }
        // La riga più lunga riempie tutta la larghezza: meno tasti per riga = tasti più larghi
        val larghezza = maxOf(prima.size, seconda.size, if (classica) terza.size + 3 else terzaConPunto.size).toFloat()

        fun centrata(lettere: List<Tasto>): List<Tasto> {
            val margine = (larghezza - lettere.size) / 2
            return if (margine > 0) listOf(vuoto(margine)) + lettere + vuoto(margine) else lettere
        }
        val r1 = centrata(riga(prima.joinToString(" "), varianti))
        val r2 = centrata(riga(seconda.joinToString(" "), varianti))
        val r3 = if (classica) {
            // Come sempre: lettere un po' a sinistra, ⌫ largo a destra (lo aggiunge lettere())
            val lettere3 = riga(terza.joinToString(" "), varianti)
            val resto = larghezza - lettere3.size - 1.5f
            val margine = if (resto <= 0.5f) resto.coerceAtLeast(0f) else resto / 2
            (if (margine > 0) listOf(vuoto(margine)) else emptyList()) + lettere3 +
                Tasto("⌫", Codici.CANC, 1.5f + (resto - margine).coerceAtLeast(0f))
        } else {
            centrata(riga(terzaConPunto.joinToString(" "), varianti))
        }
        return listOf(r1, r2, r3)
    }

    fun lettere(
        lingua: Lingua,
        numeriSempreVisibili: Boolean,
        tastoLingua: Boolean,
        classica: Boolean = false,
        qerty: Boolean = !classica,
    ): List<List<Tasto>> =
        cache.getOrPut("${lingua.codice}/${lingua.righe}/$numeriSempreVisibili/$tastoLingua/$classica/$qerty") {
            val righe = righeLettere(lingua, !numeriSempreVisibili, classica, qerty) +
                listOf(rigaFinale(tastoSimboli(classica), lingua.nome, tastoLingua, classica))
            if (numeriSempreVisibili) listOf(rigaNumeri) + righe else righe
        }

    fun simboli(lingua: Lingua, tastoLingua: Boolean, classica: Boolean = false): List<List<Tasto>> =
        cache.getOrPut("simboli/${lingua.codice}/$tastoLingua/$classica") {
            listOf(
                riga("1 2 3 4 5 6 7 8 9 0"),
                riga("@ # € _ & - + ( ) /"),
                listOf(Tasto("=\\<", Codici.SIMBOLI2, 1.5f)) + riga("* \" ' : ; ! ?") + fineTerzaRiga(classica),
                rigaFinale(Tasto("ABC", Codici.LETTERE, 1.5f), lingua.nome, tastoLingua, classica),
            )
        }

    fun simboli2(lingua: Lingua, tastoLingua: Boolean, classica: Boolean = false): List<List<Tasto>> =
        cache.getOrPut("simboli2/${lingua.codice}/$tastoLingua/$classica") {
            listOf(
                riga("~ ` | • ° π ÷ × § ∆"),
                riga("£ \$ ¥ ^ = { } [ ] %"),
                listOf(tastoSimboli(classica)) + riga("\\ < > © ® ™ ✓") + fineTerzaRiga(classica),
                rigaFinale(Tasto("ABC", Codici.LETTERE, 1.5f), lingua.nome, tastoLingua, classica),
            )
        }
}
