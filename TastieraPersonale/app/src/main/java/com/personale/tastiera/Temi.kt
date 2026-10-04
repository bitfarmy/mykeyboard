package com.personale.tastiera

import android.content.Context

data class Tema(
    val id: String,
    val nome: String,
    val sfondo: Int,
    val tasto: Int,
    val tastoSpeciale: Int,
    val premuto: Int,
    val testo: Int,
    val testoSecondario: Int,
    val accento: Int,
    val testoSuAccento: Int,
)

/** Aggiungi qui i tuoi temi: compariranno da soli nelle impostazioni. */
object Temi {
    val tutti: List<Tema> = listOf(
        Tema(
            "notte", "Notte",
            sfondo = 0xFF1B1B1F.toInt(), tasto = 0xFF3A3A40.toInt(), tastoSpeciale = 0xFF2A2A30.toInt(),
            premuto = 0xFF5A5A62.toInt(), testo = 0xFFF2F2F2.toInt(), testoSecondario = 0xFF9A9AA3.toInt(),
            accento = 0xFF7C9CFF.toInt(), testoSuAccento = 0xFF10131F.toInt(),
        ),
        Tema(
            "giorno", "Giorno",
            sfondo = 0xFFE8EAED.toInt(), tasto = 0xFFFFFFFF.toInt(), tastoSpeciale = 0xFFCDD1D6.toInt(),
            premuto = 0xFFB8BDC4.toInt(), testo = 0xFF1F1F1F.toInt(), testoSecondario = 0xFF6B6F75.toInt(),
            accento = 0xFF1A73E8.toInt(), testoSuAccento = 0xFFFFFFFF.toInt(),
        ),
        Tema(
            "oceano", "Oceano",
            sfondo = 0xFF0B2A3C.toInt(), tasto = 0xFF14435E.toInt(), tastoSpeciale = 0xFF0F3549.toInt(),
            premuto = 0xFF1E6187.toInt(), testo = 0xFFE6F6FF.toInt(), testoSecondario = 0xFF8BB8CF.toInt(),
            accento = 0xFF3DD6D0.toInt(), testoSuAccento = 0xFF06222E.toInt(),
        ),
        Tema(
            "menta", "Menta",
            sfondo = 0xFFDDEFE6.toInt(), tasto = 0xFFF7FFFA.toInt(), tastoSpeciale = 0xFFBFDCCD.toInt(),
            premuto = 0xFFA6CDB9.toInt(), testo = 0xFF173A2B.toInt(), testoSecondario = 0xFF5E8171.toInt(),
            accento = 0xFF2E9E6E.toInt(), testoSuAccento = 0xFFFFFFFF.toInt(),
        ),
        Tema(
            "tramonto", "Tramonto",
            sfondo = 0xFF2B1B2E.toInt(), tasto = 0xFF4A2C4F.toInt(), tastoSpeciale = 0xFF3A2340.toInt(),
            premuto = 0xFF6B3F70.toInt(), testo = 0xFFFFEDE3.toInt(), testoSecondario = 0xFFC99BB0.toInt(),
            accento = 0xFFFF8A5B.toInt(), testoSuAccento = 0xFF2B1B2E.toInt(),
        ),
        Tema(
            "inchiostro", "Inchiostro",
            sfondo = 0xFFEDEFF4.toInt(), tasto = 0xFFFFFFFF.toInt(), tastoSpeciale = 0xFFD5DAE5.toInt(),
            premuto = 0xFFBCC4D6.toInt(), testo = 0xFF1C2638.toInt(), testoSecondario = 0xFF65708A.toInt(),
            accento = 0xFF2F4B7C.toInt(), testoSuAccento = 0xFFFFFFFF.toInt(),
        ),
        Tema(
            "cyberpunk", "Cyberpunk",
            sfondo = 0xFF0D0B14.toInt(), tasto = 0xFF1F1730.toInt(), tastoSpeciale = 0xFF150F22.toInt(),
            premuto = 0xFF3A2B5C.toInt(), testo = 0xFF00F0FF.toInt(), testoSecondario = 0xFF9C7FC9.toInt(),
            accento = 0xFFFF2CDF.toInt(), testoSuAccento = 0xFF0D0B14.toInt(),
        ),
        Tema(
            "neon", "Neon",
            sfondo = 0xFF05080A.toInt(), tasto = 0xFF10161A.toInt(), tastoSpeciale = 0xFF0A0F12.toInt(),
            premuto = 0xFF1C2A2E.toInt(), testo = 0xFF39FF14.toInt(), testoSecondario = 0xFF4FA8A0.toInt(),
            accento = 0xFF00E5FF.toInt(), testoSuAccento = 0xFF05080A.toInt(),
        ),
    )

    val predefinito: Tema get() = tutti.first()

    fun perId(id: String): Tema = tutti.firstOrNull { it.id == id } ?: predefinito
}

/** Tutte le impostazioni in un posto solo. */
class Preferenze(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("impostazioni", Context.MODE_PRIVATE)

    var tema: String
        get() = sp.getString("tema", null) ?: "notte"
        set(valore) = sp.edit().putString("tema", valore).apply()

    var suggerimenti: Boolean
        get() = sp.getBoolean("suggerimenti", true)
        set(valore) = scrivi("suggerimenti", valore)

    var autocorrezione: Boolean
        get() = sp.getBoolean("autocorrezione", true)
        set(valore) = scrivi("autocorrezione", valore)

    var maiuscoleAutomatiche: Boolean
        get() = sp.getBoolean("maiuscole_automatiche", true)
        set(valore) = scrivi("maiuscole_automatiche", valore)

    var doppioSpazioPunto: Boolean
        get() = sp.getBoolean("doppio_spazio_punto", true)
        set(valore) = scrivi("doppio_spazio_punto", valore)

    var vibrazione: Boolean
        get() = sp.getBoolean("vibrazione", true)
        set(valore) = scrivi("vibrazione", valore)

    var suono: Boolean
        get() = sp.getBoolean("suono", false)
        set(valore) = scrivi("suono", valore)

    /** Il numero piccolo in alto a destra sui tasti q-p, sempre visibile o solo tenendo premuto. */
    var numeriSempreVisibili: Boolean
        get() = sp.getBoolean("numeri_sempre_visibili", true)
        set(valore) = scrivi("numeri_sempre_visibili", valore)

    /** 0 = bassa, 1 = media, 2 = alta */
    var altezzaTasti: Int
        get() = sp.getInt("altezza_tasti", 1)
        set(valore) = sp.edit().putInt("altezza_tasti", valore).apply()

    /** Codici delle lingue tra cui passi con il tasto 🌐, in ordine. */
    var lingueAttive: List<String>
        get() = (sp.getString("lingue_attive", null) ?: "it").split(",").filter { it.isNotEmpty() }
        set(valore) = sp.edit().putString("lingue_attive", valore.distinct().joinToString(",")).apply()

    var linguaCorrente: String
        get() = sp.getString("lingua_corrente", null) ?: "it"
        set(valore) = sp.edit().putString("lingua_corrente", valore).apply()

    var emojiRecenti: List<String>
        get() = (sp.getString("emoji_recenti", null) ?: "").split(SEPARATORE).filter { it.isNotEmpty() }
        set(valore) = sp.edit().putString("emoji_recenti", valore.joinToString(SEPARATORE)).apply()

    private fun scrivi(chiave: String, valore: Boolean) = sp.edit().putBoolean(chiave, valore).apply()

    private companion object {
        const val SEPARATORE = "\u001F"
    }
}
