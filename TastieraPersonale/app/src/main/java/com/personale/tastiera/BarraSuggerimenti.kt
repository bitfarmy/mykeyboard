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

@SuppressLint("ViewConstructor")
class BarraSuggerimenti(
    context: Context,
    private val onScelta: (Suggerimento) -> Unit,
    private val onImpostazioni: () -> Unit,
) : LinearLayout(context) {

    private val densita = resources.displayMetrics.density
    private val caselle = List(3) { TextView(context) }
    private val ingranaggio = TextView(context)
    private var correnti: List<Suggerimento> = emptyList()
    private var tema = Temi.predefinito

    init {
        orientation = HORIZONTAL
        caselle.forEachIndexed { i, tv ->
            tv.gravity = Gravity.CENTER
            tv.textSize = 16f
            tv.setSingleLine(true)
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.setPadding(dp(10), 0, dp(10), 0)
            tv.setOnClickListener { correnti.getOrNull(i)?.let(onScelta) }
            addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        ingranaggio.text = "⚙"
        ingranaggio.textSize = 20f
        ingranaggio.gravity = Gravity.CENTER
        ingranaggio.setOnClickListener { onImpostazioni() }
        addView(ingranaggio, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
    }

    private fun dp(v: Int) = (v * densita).toInt()

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

    fun applicaTema(t: Tema) {
        tema = t
        setBackgroundColor(t.sfondo)
        ingranaggio.setTextColor(t.testoSecondario)
        mostra(correnti)
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
        dp(3), dp(6), dp(3), dp(6),
    )
}
