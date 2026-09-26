package com.personale.tastiera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

enum class TipoSuggerimento { SCORCIATOIA, CORREZIONE, PAROLA }

/** [daSostituire] è il testo già scritto che verrà rimpiazzato da [testo]. */
data class Suggerimento(val testo: String, val daSostituire: String, val tipo: TipoSuggerimento)

/**
 * Barra sopra la tastiera: tre suggerimenti, poi i tasti rapidi ⇧ , ' . e l'ingranaggio.
 */
@SuppressLint("ViewConstructor")
class BarraSuggerimenti(
    context: Context,
    private val onScelta: (Suggerimento) -> Unit,
    private val onImpostazioni: () -> Unit,
    private val onTasto: (String) -> Unit,
    private val onShift: () -> Unit,
) : LinearLayout(context) {

    private val densita = resources.displayMetrics.density
    private val caselle = List(3) { TextView(context) }
    private val tastoShift = TextView(context)
    private val tastoVirgola = TextView(context)
    private val tastoApostrofo = TextView(context)
    private val tastoPunto = TextView(context)
    private val ingranaggio = TextView(context)
    private var correnti: List<Suggerimento> = emptyList()
    private var tema = Temi.predefinito
    private var statoShift = StatoShift.SPENTO

    init {
        orientation = HORIZONTAL
        caselle.forEachIndexed { i, tv ->
            tv.gravity = Gravity.CENTER
            tv.textSize = 16f
            tv.setSingleLine(true)
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.setPadding(dp(4), 0, dp(4), 0) // spazio ridotto tra un suggerimento e l'altro
            tv.setOnClickListener { correnti.getOrNull(i)?.let(onScelta) }
            addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        aggiungiTasto(tastoShift, "⇧") { onShift() }
        aggiungiTasto(tastoVirgola, ",") { onTasto(",") }
        aggiungiTasto(tastoApostrofo, "'") { onTasto("'") }
        aggiungiTasto(tastoPunto, ".") { onTasto(".") }
        aggiungiTasto(ingranaggio, "⚙") { onImpostazioni() }
    }

    private fun dp(v: Int) = (v * densita).toInt()

    private fun aggiungiTasto(tv: TextView, testo: String, azione: () -> Unit) {
        tv.text = testo
        tv.textSize = 20f
        tv.setTypeface(null, Typeface.BOLD)
        tv.gravity = Gravity.CENTER
        tv.setOnClickListener { azione() }
        addView(tv, LayoutParams(dp(38), LayoutParams.MATCH_PARENT))
    }

    fun mostra(lista: List<Suggerimento>) {
        correnti = lista
        caselle.forEachIndexed { i, tv ->
            val s = lista.getOrNull(i)
            tv.text = if (s == null) "" else when (s.tipo) {
                TipoSuggerimento.SCORCIATOIA -> "⚡ " + s.testo.lineSequence().first()
                else -> s.testo
            }
            tv.isClickable = s != null
            stile(tv, s)
        }
    }

    fun aggiornaShift(stato: StatoShift) {
        statoShift = stato
        coloraTasti()
    }

    fun applicaTema(t: Tema) {
        tema = t
        setBackgroundColor(t.sfondo)
        coloraTasti()
        mostra(correnti)
    }

    private fun coloraTasti() {
        tastoVirgola.setTextColor(tema.testo)
        tastoApostrofo.setTextColor(tema.testo)
        tastoPunto.setTextColor(tema.testo)
        ingranaggio.setTextColor(tema.testoSecondario)
        tastoShift.text = if (statoShift == StatoShift.BLOCCATO) "⇪" else "⇧"
        tastoShift.setTextColor(if (statoShift == StatoShift.SPENTO) tema.testo else tema.accento)
    }

    private fun stile(tv: TextView, s: Suggerimento?) {
        when (s?.tipo) {
            TipoSuggerimento.CORREZIONE -> {
                // La correzione evidenziata è quella che verrà applicata premendo spazio.
                tv.setTypeface(null, Typeface.BOLD)
                tv.setTextColor(tema.testoSuAccento)
                tv.background = pillola(tema.accento)
            }
            TipoSuggerimento.SCORCIATOIA -> {
                tv.setTypeface(null, Typeface.BOLD)
                tv.setTextColor(tema.accento)
                tv.background = pillola(tema.tasto)
            }
            else -> {
                tv.setTypeface(null, Typeface.NORMAL)
                tv.setTextColor(tema.testo)
                tv.background = null
            }
        }
    }

    private fun pillola(colore: Int) = InsetDrawable(
        GradientDrawable().apply {
            setColor(colore)
            cornerRadius = dp(16).toFloat()
        },
        dp(1), dp(6), dp(1), dp(6),
    )
}
