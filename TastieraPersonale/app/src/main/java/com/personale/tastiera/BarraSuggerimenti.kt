package com.personale.tastiera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
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
    // ⇧ , ' . formano un piccolo blocco a parte, con il suo sfondo
    private val blocco = LinearLayout(context)
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
        blocco.orientation = HORIZONTAL
        blocco.setPadding(dp(4), 0, dp(4), 0)
        aggiungiTasto(blocco, tastoShift, "⇧", 30) { onShift() }
        aggiungiTasto(blocco, tastoVirgola, ",", 24) { onTasto(",") }
        aggiungiTasto(blocco, tastoApostrofo, "'", 24) { onTasto("'") }
        aggiungiTasto(blocco, tastoPunto, ".", 24) { onTasto(".") }
        addView(blocco, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        aggiungiTasto(this, ingranaggio, "⚙", 38) { onImpostazioni() }
    }

    private fun dp(v: Int) = (v * densita).toInt()

    private fun aggiungiTasto(dove: LinearLayout, tv: TextView, testo: String, larghezza: Int, azione: () -> Unit) {
        tv.text = testo
        tv.textSize = 20f
        tv.setTypeface(null, Typeface.BOLD)
        tv.gravity = Gravity.CENTER
        tv.setOnClickListener { azione() }
        dove.addView(tv, LayoutParams(dp(larghezza), LayoutParams.MATCH_PARENT))
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
        // Il blocco ⇧ , ' . e l'ingranaggio sono "rilievi": ombra sotto, riflesso sopra
        blocco.background = rilievo(t.tastoSpeciale, 14, 1, 5)
        ingranaggio.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rilievo(t.premuto, 12, 3, 5))
            addState(intArrayOf(), rilievo(t.tastoSpeciale, 12, 3, 5))
        }
        for (tv in listOf(tastoShift, tastoVirgola, tastoApostrofo, tastoPunto)) {
            // Il tasto premuto si accende dentro al blocco
            tv.background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    InsetDrawable(
                        GradientDrawable().apply {
                            setColor(t.premuto)
                            cornerRadius = dp(10).toFloat()
                        },
                        dp(1), dp(7), dp(1), dp(9),
                    ),
                )
            }
        }
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

    private fun mescola(a: Int, b: Int, f: Float): Int = Color.rgb(
        (Color.red(a) * (1 - f) + Color.red(b) * f).toInt(),
        (Color.green(a) * (1 - f) + Color.green(b) * f).toInt(),
        (Color.blue(a) * (1 - f) + Color.blue(b) * f).toInt(),
    )

    /** Un tasto in rilievo: ombra sotto, sfumatura dall'alto, bordo chiaro sopra. [orizz]/[vert] sono i margini in dp. */
    private fun rilievo(colore: Int, raggioDp: Int, orizz: Int, vert: Int): Drawable {
        val raggio = dp(raggioDp).toFloat()
        val ombra = GradientDrawable().apply {
            setColor(mescola(colore, Color.BLACK, 0.4f))
            cornerRadius = raggio
        }
        val corpo = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(mescola(colore, Color.WHITE, 0.22f), colore, mescola(colore, Color.BLACK, 0.08f)),
        ).apply {
            cornerRadius = raggio
            setStroke(dp(1), mescola(colore, Color.WHITE, 0.35f))
        }
        val strati = LayerDrawable(arrayOf<Drawable>(ombra, corpo))
        strati.setLayerInset(0, 0, dp(2), 0, 0)
        strati.setLayerInset(1, 0, 0, 0, dp(2))
        return InsetDrawable(strati, dp(orizz), dp(vert), dp(orizz), dp(vert))
    }

    private fun pillola(colore: Int) = InsetDrawable(
        GradientDrawable().apply {
            setColor(colore)
            cornerRadius = dp(16).toFloat()
        },
        dp(1), dp(6), dp(1), dp(6),
    )
}
