package com.personale.tastiera

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Una sigla (es. "ind") che fa comparire un testo completo (es. il tuo indirizzo). */
data class Scorciatoia(val sigla: String, val testo: String)

class Scorciatoie private constructor(context: Context) {

    companion object {
        @Volatile
        private var istanza: Scorciatoie? = null

        fun get(context: Context): Scorciatoie = istanza ?: synchronized(this) {
            istanza ?: Scorciatoie(context.applicationContext).also { istanza = it }
        }
    }

    private val sp = context.getSharedPreferences("scorciatoie", Context.MODE_PRIVATE)

    var elenco: List<Scorciatoia> = leggi()
        private set

    init {
        // Al primo avvio mettiamo due esempi, così si vede subito come funziona.
        if (!sp.getBoolean("inizializzato", false)) {
            scrivi(listOf(Scorciatoia("grz", "Grazie mille!"), Scorciatoia("adp", "A dopo 👋")))
            sp.edit().putBoolean("inizializzato", true).apply()
        }
    }

    fun salva(nuova: Scorciatoia, siglaPrecedente: String? = null) {
        scrivi(
            elenco.filterNot { it.sigla.equals(nuova.sigla, ignoreCase = true) || it.sigla == siglaPrecedente } + nuova,
        )
    }

    fun elimina(sigla: String) = scrivi(elenco.filterNot { it.sigla == sigla })

    fun esatta(token: String): Scorciatoia? = elenco.firstOrNull { it.sigla.equals(token, ignoreCase = true) }

    /**
     * Sigle uguali a quello che stai scrivendo; da 2 caratteri in su anche quelle che iniziano così
     * (scrivendo "in" compare già "ind").
     */
    fun cerca(token: String): List<Scorciatoia> {
        if (token.isEmpty()) return emptyList()
        val esatte = elenco.filter { it.sigla.equals(token, ignoreCase = true) }
        val iniziano = if (token.length < 2) emptyList() else elenco.filter {
            it.sigla.length > token.length && it.sigla.startsWith(token, ignoreCase = true)
        }
        return esatte + iniziano
    }

    private fun leggi(): List<Scorciatoia> = try {
        val arr = JSONArray(sp.getString("elenco", null) ?: "[]")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Scorciatoia(o.getString("sigla"), o.getString("testo"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun scrivi(lista: List<Scorciatoia>) {
        val ordinata = lista.sortedBy { it.sigla.lowercase() }
        val arr = JSONArray()
        ordinata.forEach { arr.put(JSONObject().put("sigla", it.sigla).put("testo", it.testo)) }
        sp.edit().putString("elenco", arr.toString()).apply()
        elenco = ordinata
    }
}
