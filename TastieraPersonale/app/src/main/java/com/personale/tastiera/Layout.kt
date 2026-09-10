package com.personale.tastiera

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

object Layout {

    // Tieni premuto un tasto per scegliere una variante. Modifica liberamente!
    private val ALTERNATIVE = mapOf(
        "q" to "1", "w" to "2", "e" to "3 è é", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7 ù ú", "i" to "8 ì í", "o" to "9 ò ó", "p" to "0",
        "a" to "à á", "c" to "ç", "n" to "ñ",
        "." to "? ! ; : …", "," to "' \"",
        "-" to "_ – —", "'" to "‘ ’", "\"" to "« » “ ”",
        "€" to "\$ £ ¥", "?" to "¿", "!" to "¡", "%" to "‰",
    )

    /** Crea una riga da una stringa con i tasti separati da spazi. */
    private fun riga(tasti: String): List<Tasto> =
        tasti.split(" ").map { Tasto(it, alternative = ALTERNATIVE[it]?.split(" ") ?: emptyList()) }

    private val vuoto = Tasto("", Codici.VUOTO, 0.5f)

    private fun rigaFinale(tastoPagina: Tasto): List<Tasto> = listOf(
        tastoPagina,
        Tasto("🙂", Codici.EMOJI),
        riga(",")[0],
        Tasto("italiano", Codici.SPAZIO, 4f),
        riga(".")[0],
        Tasto("↵", Codici.INVIO, 1.5f),
    )

    val lettere: List<List<Tasto>> = listOf(
        riga("q w e r t y u i o p"),
        listOf(vuoto) + riga("a s d f g h j k l") + vuoto,
        listOf(Tasto("⇧", Codici.SHIFT, 1.5f)) + riga("z x c v b n m") + Tasto("⌫", Codici.CANC, 1.5f),
        rigaFinale(Tasto("?123", Codici.SIMBOLI, 1.5f)),
    )

    val simboli: List<List<Tasto>> = listOf(
        riga("1 2 3 4 5 6 7 8 9 0"),
        riga("@ # € _ & - + ( ) /"),
        listOf(Tasto("=\\<", Codici.SIMBOLI2, 1.5f)) + riga("* \" ' : ; ! ?") + Tasto("⌫", Codici.CANC, 1.5f),
        rigaFinale(Tasto("ABC", Codici.LETTERE, 1.5f)),
    )

    val simboli2: List<List<Tasto>> = listOf(
        riga("~ ` | • ° π ÷ × § ∆"),
        riga("£ \$ ¥ ^ = { } [ ] %"),
        listOf(Tasto("?123", Codici.SIMBOLI, 1.5f)) + riga("\\ < > © ® ™ ✓") + Tasto("⌫", Codici.CANC, 1.5f),
        rigaFinale(Tasto("ABC", Codici.LETTERE, 1.5f)),
    )
}
