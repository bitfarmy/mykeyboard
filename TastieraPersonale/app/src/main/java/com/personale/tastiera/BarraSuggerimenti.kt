package com.personale.tastiera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.text.TextUtils
import android.view.Gravity
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
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
    private val tastoShift = ImageView(context)
    private val tastoVirgola = TastoTesto(context)
    private val tastoApostrofo = TastoTesto(context)
    private val tastoPunto = TastoTesto(context)
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
        tastoShift.scaleType = ImageView.ScaleType.FIT_CENTER
        tastoShift.setPadding(dp(5), dp(9), dp(5), dp(11)) // l'icona sta sopra l'ombra del rilievo
        tastoShift.setOnClickListener { onShift() }
        blocco.addView(tastoShift, LayoutParams(dp(28), LayoutParams.MATCH_PARENT))
        aggiungiPunteggiatura(tastoVirgola, ",") { onTasto(",") }
        aggiungiPunteggiatura(tastoApostrofo, "'") { onTasto("'") }
        aggiungiPunteggiatura(tastoPunto, ".") { onTasto(".") }
        // Blocco e ingranaggio con lo stesso margine ai lati, così stanno centrati nel loro spazio
        addView(blocco, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(4), 0, dp(2), 0)
        })
        aggiungiTasto(this, ingranaggio, "⚙", 38) { onImpostazioni() }
        (ingranaggio.layoutParams as LayoutParams).setMargins(dp(2), 0, dp(4), 0)
    }

    private fun dp(v: Int) = (v * densita).toInt()

    private fun aggiungiPunteggiatura(tasto: TastoTesto, testo: String, azione: () -> Unit) {
        tasto.testo = testo
        tasto.setOnClickListener { azione() }
        blocco.addView(tasto, LayoutParams(dp(19), LayoutParams.MATCH_PARENT))
    }

    private fun aggiungiTasto(dove: LinearLayout, tv: TextView, testo: String, larghezza: Int, azione: () -> Unit) {
        tv.text = testo
        tv.textSize = 22f
        tv.setTypeface(null, Typeface.BOLD)
        tv.includeFontPadding = false
        tv.gravity = Gravity.CENTER
        // L'ombra del rilievo occupa 2 dp in basso: il simbolo si centra sulla parte alta
        tv.setPadding(0, 0, 0, dp(2))
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
        tastoVirgola.colore = tema.testo
        tastoApostrofo.colore = tema.testo
        tastoPunto.colore = tema.testo
        ingranaggio.setTextColor(tema.testoSecondario)
        tastoShift.setImageResource(
            when (statoShift) {
                StatoShift.SPENTO -> R.drawable.ic_maiuscole
                StatoShift.ACCESO -> R.drawable.ic_maiuscole_accese
                StatoShift.BLOCCATO -> R.drawable.ic_maiuscole_bloccate
            },
        )
        tastoShift.setColorFilter(if (statoShift == StatoShift.SPENTO) tema.testo else tema.accento)
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

/**
 * Un segno di punteggiatura grande e marcato. Lo disegna da sé su una riga di base fissa:
 * la virgola e il punto stanno in basso, l'apostrofo in alto, come sulla tastiera di un computer.
 */
private class TastoTesto(context: Context) : View(context) {
    var testo = ""
    var colore = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    private val pennello = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 26f, context.resources.displayMetrics)
    }

    override fun onDraw(canvas: Canvas) {
        pennello.color = colore
        val ombra = 2 * resources.displayMetrics.density // l'ombra del rilievo sta in basso
        val centro = (height - ombra) / 2f
        // La riga di base sta sotto il centro di mezza altezza delle maiuscole
        canvas.drawText(testo, width / 2f, centro + pennello.textSize * 0.3f, pennello)
    }
}
