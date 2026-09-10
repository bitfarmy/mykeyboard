package com.personale.tastiera

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** L'app che si apre dall'icona: da qui attivi la tastiera e la personalizzi. */
class ImpostazioniActivity : Activity() {

    private lateinit var prefs: Preferenze
    private lateinit var stato: TextView
    private lateinit var elencoScorciatoie: LinearLayout
    private lateinit var infoDizionario: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferenze(this)
        title = getString(R.string.app_name)

        val colonna = LinearLayout(this)
        colonna.orientation = LinearLayout.VERTICAL
        colonna.setPadding(dp(20), dp(4), dp(20), dp(48))
        val scorrimento = ScrollView(this)
        scorrimento.addView(colonna)
        setContentView(scorrimento)

        // Attivazione
        colonna.addView(titolo("Attiva la tastiera"))
        stato = testo("")
        colonna.addView(stato)
        colonna.addView(pulsante("1. Abilitala nelle impostazioni") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        colonna.addView(pulsante("2. Sceglila come tastiera attiva") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        val prova = EditText(this)
        prova.hint = "Tocca qui per provarla"
        prova.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        prova.minLines = 2
        colonna.addView(prova)

        // Tema
        colonna.addView(titolo("Tema"))
        val gruppoTemi = RadioGroup(this)
        Temi.tutti.forEach { tema ->
            val scelta = RadioButton(this)
            scelta.id = View.generateViewId()
            scelta.text = tema.nome
            scelta.textSize = 16f
            scelta.setPadding(dp(8), dp(10), dp(8), dp(10))
            scelta.isChecked = tema.id == prefs.tema
            scelta.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, anteprima(tema), null)
            scelta.setOnCheckedChangeListener { _, attivo -> if (attivo) prefs.tema = tema.id }
            gruppoTemi.addView(
                scelta,
                RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        colonna.addView(gruppoTemi)

        // Tasti e scrittura
        colonna.addView(titolo("Tasti e scrittura"))
        colonna.addView(testo("Altezza dei tasti"))
        val gruppoAltezza = RadioGroup(this)
        gruppoAltezza.orientation = LinearLayout.HORIZONTAL
        listOf("Bassi", "Medi", "Alti").forEachIndexed { i, nome ->
            val scelta = RadioButton(this)
            scelta.id = View.generateViewId()
            scelta.text = nome
            scelta.isChecked = prefs.altezzaTasti == i
            scelta.setOnCheckedChangeListener { _, attivo -> if (attivo) prefs.altezzaTasti = i }
            gruppoAltezza.addView(scelta, RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        colonna.addView(gruppoAltezza)

        colonna.addView(interruttore("Suggerimenti", "Mostra le parole mentre scrivi.", prefs.suggerimenti) {
            prefs.suggerimenti = it
        })
        colonna.addView(interruttore(
            "Autocorrezione",
            "Con il dizionario base sistema gli accenti (perche → perché). " +
                "Con un dizionario completo corregge anche gli errori di battitura. " +
                "Premi ⌫ subito dopo una correzione per annullarla.",
            prefs.autocorrezione,
        ) { prefs.autocorrezione = it })
        colonna.addView(interruttore("Maiuscole automatiche", "A inizio frase, se l'app lo consente.", prefs.maiuscoleAutomatiche) {
            prefs.maiuscoleAutomatiche = it
        })
        colonna.addView(interruttore("Doppio spazio per il punto", "Due spazi veloci scrivono \". \"", prefs.doppioSpazioPunto) {
            prefs.doppioSpazioPunto = it
        })
        colonna.addView(interruttore("Vibrazione", "Segue anche l'impostazione di sistema per il feedback al tocco.", prefs.vibrazione) {
            prefs.vibrazione = it
        })
        colonna.addView(interruttore("Suono dei tasti", null, prefs.suono) { prefs.suono = it })

        // Scorciatoie
        colonna.addView(titolo("Scorciatoie"))
        colonna.addView(testo(
            "Collega una lettera o una sigla a una parola o a una frase. " +
                "Quando scrivi la sigla, il testo completo compare con ⚡ sopra la tastiera: toccalo per inserirlo.",
        ))
        elencoScorciatoie = LinearLayout(this)
        elencoScorciatoie.orientation = LinearLayout.VERTICAL
        colonna.addView(elencoScorciatoie)
        colonna.addView(pulsante("Aggiungi scorciatoia") { modificaScorciatoia(null) })
        aggiornaScorciatoie()

        // Dizionario
        colonna.addView(titolo("Dizionario"))
        infoDizionario = testo("")
        colonna.addView(infoDizionario)
        colonna.addView(pulsante("Cancella le parole imparate") {
            AlertDialog.Builder(this)
                .setTitle("Cancellare le parole imparate?")
                .setMessage("Il dizionario base resta. Perdi solo le parole che la tastiera ha imparato da te.")
                .setPositiveButton("Cancella") { _, _ ->
                    Dizionario.get(this).cancellaImparate()
                    aggiornaInfoDizionario()
                }
                .setNegativeButton("Annulla", null)
                .show()
        })
    }

    override fun onResume() {
        super.onResume()
        aggiornaStato()
        aggiornaInfoDizionario()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) aggiornaStato() // dopo aver chiuso il selettore delle tastiere
    }

    private fun aggiornaStato() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val abilitata = imm.enabledInputMethodList.any { it.packageName == packageName }
        val attiva = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        stato.text = when {
            attiva -> "✅ È la tua tastiera attiva."
            abilitata -> "Abilitata. Ora sceglila come tastiera attiva."
            else -> "Non ancora abilitata: inizia dal primo pulsante."
        }
    }

    private fun aggiornaInfoDizionario() {
        val d = Dizionario.get(this)
        if (!d.pronto) {
            infoDizionario.text = "Caricamento del dizionario…"
            d.quandoPronto { aggiornaInfoDizionario() }
            return
        }
        val tipo = if (d.numeroParole >= Dizionario.PAROLE_PER_CORREZIONE_COMPLETA) "completo" else "base"
        infoDizionario.text = "Dizionario $tipo con ${d.numeroParole} parole. Parole imparate da te: ${d.numeroImparate}."
    }

    // ---------- Scorciatoie ----------

    private fun aggiornaScorciatoie() {
        elencoScorciatoie.removeAllViews()
        val elenco = Scorciatoie.get(this).elenco
        if (elenco.isEmpty()) {
            elencoScorciatoie.addView(testo("Nessuna scorciatoia. Aggiungine una qui sotto."))
            return
        }
        elenco.forEach { s ->
            val riga = LinearLayout(this)
            riga.orientation = LinearLayout.HORIZONTAL
            riga.gravity = Gravity.CENTER_VERTICAL
            riga.setPadding(0, dp(6), 0, dp(6))

            val sigla = TextView(this)
            sigla.text = s.sigla
            sigla.textSize = 16f
            sigla.setTypeface(sigla.typeface, Typeface.BOLD)
            sigla.setPadding(0, 0, dp(12), 0)
            riga.addView(sigla)

            val contenuto = TextView(this)
            contenuto.text = s.testo.lineSequence().first()
            contenuto.textSize = 16f
            contenuto.setSingleLine(true)
            contenuto.ellipsize = TextUtils.TruncateAt.END
            riga.addView(contenuto, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val elimina = TextView(this)
            elimina.text = "✕"
            elimina.textSize = 18f
            elimina.contentDescription = "Elimina ${s.sigla}"
            elimina.setPadding(dp(16), dp(4), dp(8), dp(4))
            elimina.setOnClickListener {
                Scorciatoie.get(this).elimina(s.sigla)
                aggiornaScorciatoie()
            }
            riga.addView(elimina)

            riga.setOnClickListener { modificaScorciatoia(s) }
            elencoScorciatoie.addView(riga)
        }
    }

    private fun modificaScorciatoia(esistente: Scorciatoia?) {
        val campoSigla = EditText(this)
        campoSigla.hint = "Sigla, per esempio ind oppure @m"
        campoSigla.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        campoSigla.setText(esistente?.sigla ?: "")

        val campoTesto = EditText(this)
        campoTesto.hint = "Testo completo"
        campoTesto.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        campoTesto.minLines = 2
        campoTesto.setText(esistente?.testo ?: "")

        val contenitore = LinearLayout(this)
        contenitore.orientation = LinearLayout.VERTICAL
        contenitore.setPadding(dp(20), dp(8), dp(20), 0)
        contenitore.addView(campoSigla)
        contenitore.addView(campoTesto)

        AlertDialog.Builder(this)
            .setTitle(if (esistente == null) "Nuova scorciatoia" else "Modifica scorciatoia")
            .setView(contenitore)
            .setPositiveButton("Salva") { _, _ ->
                val sigla = campoSigla.text.toString().trim()
                val testo = campoTesto.text.toString()
                when {
                    sigla.isEmpty() || testo.isBlank() -> avviso("Scrivi sia la sigla sia il testo.")
                    sigla.any { it.isWhitespace() } -> avviso("La sigla non può contenere spazi.")
                    else -> {
                        Scorciatoie.get(this).salva(Scorciatoia(sigla, testo), esistente?.sigla)
                        aggiornaScorciatoie()
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ---------- Piccoli aiutanti per costruire la schermata ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun titolo(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = 20f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(0, dp(28), 0, dp(6))
        return tv
    }

    private fun testo(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = 14f
        tv.alpha = 0.75f
        tv.setPadding(0, dp(2), 0, dp(8))
        return tv
    }

    private fun pulsante(s: String, azione: () -> Unit): Button {
        val b = Button(this)
        b.text = s
        b.setAllCaps(false)
        b.setOnClickListener { azione() }
        return b
    }

    private fun interruttore(nome: String, descrizione: String?, valore: Boolean, cambia: (Boolean) -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(8), 0, dp(4))
        val sw = Switch(this)
        sw.text = nome
        sw.textSize = 16f
        sw.isChecked = valore
        sw.setOnCheckedChangeListener { _, attivo -> cambia(attivo) }
        box.addView(sw)
        if (descrizione != null) box.addView(testo(descrizione))
        return box
    }

    private fun anteprima(t: Tema): GradientDrawable {
        val d = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(t.sfondo, t.tasto, t.accento))
        d.cornerRadius = dp(8).toFloat()
        d.setSize(dp(84), dp(28))
        d.setStroke(dp(1), 0x33000000)
        return d
    }

    private fun avviso(messaggio: String) {
        Toast.makeText(this, messaggio, Toast.LENGTH_LONG).show()
    }
}
