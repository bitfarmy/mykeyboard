package com.personale.tastiera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.max

enum class StatoShift { SPENTO, ACCESO, BLOCCATO }

/**
 * La tastiera vera e propria: disegna i tasti e gestisce i tocchi.
 * Non sa nulla dell'app in cui scrivi: passa tutto all'[Ascoltatore] (il servizio).
 */
@SuppressLint("ViewConstructor")
class TastieraView(context: Context, private val ascoltatore: Ascoltatore) : View(context) {

    interface Ascoltatore {
        fun onTesto(testo: String)
        fun onSpeciale(codice: Int)
        fun onPressioneLunga(codice: Int): Boolean
        fun onTocco()
    }

    private class Posizionato(val tasto: Tasto, val rect: RectF)

    private class Popup(
        val voci: List<String>,
        val rect: RectF,
        val larghezzaVoce: Float,
        val invertito: Boolean,
    ) {
        var indice = 0

        fun aggiorna(x: Float) {
            val pos = ((x - rect.left) / larghezzaVoce).toInt().coerceIn(0, voci.size - 1)
            indice = if (invertito) voci.size - 1 - pos else pos
        }

        fun rettangolo(i: Int): RectF {
            val pos = if (invertito) voci.size - 1 - i else i
            val sinistra = rect.left + pos * larghezzaVoce
            return RectF(sinistra, rect.top, sinistra + larghezzaVoce, rect.bottom)
        }
    }

    private val densita = resources.displayMetrics.density
    private fun dp(v: Float) = v * densita

    var righe: List<List<Tasto>> = emptyList()
        set(value) {
            field = value
            requestLayout()
            calcola()
            invalidate()
        }

    var tema: Tema = Temi.predefinito
        set(value) {
            field = value
            invalidate()
        }

    var statoShift = StatoShift.SPENTO
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var etichettaInvio = "↵"
        set(value) {
            field = value
            invalidate()
        }

    var altezzaTastoDp = 53f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var numeriSempreVisibili = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val margineOrizzontale = dp(3f)
    private val margineVerticale = dp(6f)
    private val spazioTasti = dp(5f)
    private val spazioRighe = dp(9f)
    private val raggio = dp(9f)
    private val caratteriLettere: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val caratteriSpeciali: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val rettangoloOmbra = RectF()
    private val rettangoloAnteprima = RectF()

    /** Colore a metà strada tra [a] e [b] (f = 0 → a, f = 1 → b). */
    private fun mescola(a: Int, b: Int, f: Float): Int = Color.rgb(
        (Color.red(a) * (1 - f) + Color.red(b) * f).toInt(),
        (Color.green(a) * (1 - f) + Color.green(b) * f).toInt(),
        (Color.blue(a) * (1 - f) + Color.blue(b) * f).toInt(),
    )

    private val pennello = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pennelloTesto = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pennelloAlternativa = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }

    private val gestore = Handler(Looper.getMainLooper())
    private var posizionati: List<Posizionato> = emptyList()
    private var tastoPremuto: Posizionato? = null
    private var puntatore = -1
    private var popup: Popup? = null
    private var lungaEseguita = false

    private val azioneLunga = Runnable {
        val k = tastoPremuto ?: return@Runnable
        if (k.tasto.codice == Codici.TESTO && k.tasto.alternative.isNotEmpty()) {
            popup = creaPopup(k)
            lungaEseguita = true
            ascoltatore.onTocco()
            invalidate()
        } else if (k.tasto.codice != Codici.TESTO && ascoltatore.onPressioneLunga(k.tasto.codice)) {
            lungaEseguita = true
        }
    }

    private val azioneRipeti = object : Runnable {
        override fun run() {
            if (tastoPremuto?.tasto?.codice == Codici.CANC) {
                ascoltatore.onSpeciale(Codici.CANC)
                gestore.postDelayed(this, 50)
            }
        }
    }

    // ---------- Misure ----------

    fun altezzaCalcolata(): Int = (margineVerticale * 2 + righe.size * dp(altezzaTastoDp)).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), altezzaCalcolata())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        calcola()
    }

    private fun calcola() {
        if (width == 0) return
        val altezzaRiga = dp(altezzaTastoDp)
        val lista = ArrayList<Posizionato>()
        righe.forEachIndexed { r, riga ->
            val totale = riga.sumOf { it.larghezza.toDouble() }.toFloat()
            val unita = (width - margineOrizzontale * 2) / totale
            val alto = margineVerticale + r * altezzaRiga
            var x = margineOrizzontale
            for (t in riga) {
                val w = t.larghezza * unita
                if (t.codice != Codici.VUOTO) {
                    lista.add(
                        Posizionato(
                            t,
                            RectF(
                                x + spazioTasti / 2, alto + spazioRighe / 2,
                                x + w - spazioTasti / 2, alto + altezzaRiga - spazioRighe / 2,
                            ),
                        ),
                    )
                }
                x += w
            }
        }
        posizionati = lista
    }

    // ---------- Disegno ----------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(tema.sfondo)
        val h = dp(altezzaTastoDp) - spazioRighe
        val ombra = mescola(tema.sfondo, Color.BLACK, 0.35f)

        for (p in posizionati) {
            val t = p.tasto
            val premuto = p === tastoPremuto
            val evidenziato = t.codice == Codici.INVIO ||
                (t.codice == Codici.SHIFT && statoShift != StatoShift.SPENTO)

            pennello.color = when {
                premuto -> tema.premuto
                evidenziato -> tema.accento
                t.codice == Codici.TESTO || t.codice == Codici.SPAZIO -> tema.tasto
                else -> tema.tastoSpeciale
            }
            // Leggera ombra sotto ogni tasto: dà profondità senza appesantire
            if (!premuto) {
                val colore = pennello.color
                pennello.color = ombra
                rettangoloOmbra.set(p.rect.left, p.rect.top + dp(1.5f), p.rect.right, p.rect.bottom + dp(1.5f))
                canvas.drawRoundRect(rettangoloOmbra, raggio, raggio, pennello)
                pennello.color = colore
            }
            canvas.drawRoundRect(p.rect, raggio, raggio, pennello)

            val etichetta = etichetta(t)
            val spazio = t.codice == Codici.SPAZIO
            pennelloTesto.color = when {
                evidenziato && !premuto -> tema.testoSuAccento
                spazio -> tema.testoSecondario
                else -> tema.testo
            }
            pennelloTesto.typeface = if (t.codice == Codici.TESTO) caratteriLettere else caratteriSpeciali
            pennelloTesto.textSize = when {
                spazio -> h * 0.26f
                etichetta.codePointCount(0, etichetta.length) == 1 -> h * 0.42f
                else -> h * 0.3f
            }
            val disponibile = p.rect.width() - dp(8f)
            val larghezzaTesto = pennelloTesto.measureText(etichetta)
            if (larghezzaTesto > disponibile) pennelloTesto.textSize *= disponibile / larghezzaTesto

            val base = p.rect.centerY() - (pennelloTesto.descent() + pennelloTesto.ascent()) / 2
            canvas.drawText(etichetta, p.rect.centerX(), base, pennelloTesto)

            if (numeriSempreVisibili && t.codice == Codici.TESTO && t.alternative.isNotEmpty()) {
                pennelloAlternativa.color = tema.testoSecondario
                pennelloAlternativa.textSize = h * 0.22f
                canvas.drawText(
                    applicaShift(t.alternative[0]),
                    p.rect.right - dp(4f),
                    p.rect.top + pennelloAlternativa.textSize + dp(1f),
                    pennelloAlternativa,
                )
            }
        }

        val premutoOra = tastoPremuto
        if (premutoOra != null && popup == null && premutoOra.tasto.codice == Codici.TESTO) {
            disegnaAnteprima(canvas, premutoOra, ombra)
        }
        popup?.let { disegnaPopup(canvas, it) }
    }

    /** La lettera ingrandita sopra il dito mentre premi un tasto. */
    private fun disegnaAnteprima(canvas: Canvas, k: Posizionato, ombra: Int) {
        val larghezza = k.rect.width() * 1.3f
        val altezza = k.rect.height() * 1.2f
        val massimo = max(margineOrizzontale, width - margineOrizzontale - larghezza)
        val sinistra = (k.rect.centerX() - larghezza / 2).coerceIn(margineOrizzontale, massimo)
        val alto = max(dp(2f), k.rect.top - altezza - dp(6f))
        rettangoloAnteprima.set(sinistra, alto, sinistra + larghezza, alto + altezza)

        pennello.color = ombra
        rettangoloOmbra.set(sinistra, alto + dp(2f), sinistra + larghezza, alto + altezza + dp(2f))
        canvas.drawRoundRect(rettangoloOmbra, raggio, raggio, pennello)
        pennello.color = tema.tasto
        canvas.drawRoundRect(rettangoloAnteprima, raggio, raggio, pennello)

        pennelloTesto.color = tema.testo
        pennelloTesto.typeface = caratteriLettere
        pennelloTesto.textSize = altezza * 0.5f
        val base = rettangoloAnteprima.centerY() - (pennelloTesto.descent() + pennelloTesto.ascent()) / 2
        canvas.drawText(etichetta(k.tasto), rettangoloAnteprima.centerX(), base, pennelloTesto)
    }

    private fun disegnaPopup(canvas: Canvas, p: Popup) {
        val bordo = dp(4f)
        pennello.color = tema.tastoSpeciale
        canvas.drawRoundRect(
            RectF(p.rect.left - bordo, p.rect.top - bordo, p.rect.right + bordo, p.rect.bottom + bordo),
            raggio, raggio, pennello,
        )
        pennelloTesto.textSize = p.rect.height() * 0.45f
        p.voci.forEachIndexed { i, voce ->
            val r = p.rettangolo(i)
            if (i == p.indice) {
                pennello.color = tema.accento
                canvas.drawRoundRect(r, raggio, raggio, pennello)
            }
            pennelloTesto.color = if (i == p.indice) tema.testoSuAccento else tema.testo
            val base = r.centerY() - (pennelloTesto.descent() + pennelloTesto.ascent()) / 2
            canvas.drawText(applicaShift(voce), r.centerX(), base, pennelloTesto)
        }
    }

    private fun etichetta(t: Tasto): String = when (t.codice) {
        Codici.TESTO -> applicaShift(t.etichetta)
        Codici.SHIFT -> if (statoShift == StatoShift.BLOCCATO) "⇪" else "⇧"
        Codici.INVIO -> etichettaInvio
        else -> t.etichetta
    }

    private fun applicaShift(s: String) =
        if (statoShift != StatoShift.SPENTO) s.uppercase(Locale.ITALIAN) else s

    // ---------- Tocchi ----------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Digitazione veloce: se un altro dito sta ancora premendo, quel tasto vale subito.
                if (tastoPremuto != null) rilascia(conferma = true)
                val i = e.actionIndex
                trova(e.getX(i), e.getY(i))?.let { premi(it, e.getPointerId(i)) }
            }
            MotionEvent.ACTION_MOVE -> {
                val p = popup
                val i = e.findPointerIndex(puntatore)
                if (p != null && i >= 0) {
                    val prima = p.indice
                    p.aggiorna(e.getX(i))
                    if (p.indice != prima) invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == puntatore) rilascia(conferma = true)
            }
            MotionEvent.ACTION_CANCEL -> rilascia(conferma = false)
        }
        return true
    }

    /** Il tasto più vicino al dito (così anche i bordi tra un tasto e l'altro funzionano). */
    private fun trova(x: Float, y: Float): Posizionato? = posizionati.minByOrNull { p ->
        val dx = max(0f, max(p.rect.left - x, x - p.rect.right))
        val dy = max(0f, max(p.rect.top - y, y - p.rect.bottom))
        dx * dx + dy * dy
    }

    private fun premi(k: Posizionato, id: Int) {
        tastoPremuto = k
        puntatore = id
        lungaEseguita = false
        popup = null
        ascoltatore.onTocco()
        if (k.tasto.codice == Codici.CANC) {
            ascoltatore.onSpeciale(Codici.CANC)
            gestore.postDelayed(azioneRipeti, 400)
        } else {
            gestore.postDelayed(azioneLunga, 350)
        }
        invalidate()
    }

    private fun rilascia(conferma: Boolean) {
        gestore.removeCallbacks(azioneLunga)
        gestore.removeCallbacks(azioneRipeti)
        val k = tastoPremuto
        val p = popup
        val lunga = lungaEseguita
        tastoPremuto = null
        puntatore = -1
        popup = null
        lungaEseguita = false
        invalidate()
        if (!conferma || k == null) return

        when {
            p != null -> p.voci.getOrNull(p.indice)?.let { ascoltatore.onTesto(applicaShift(it)) }
            lunga -> Unit
            k.tasto.codice == Codici.CANC -> Unit // già gestito alla pressione
            k.tasto.codice == Codici.TESTO -> ascoltatore.onTesto(applicaShift(k.tasto.etichetta))
            else -> ascoltatore.onSpeciale(k.tasto.codice)
        }
    }

    private fun creaPopup(k: Posizionato): Popup {
        val n = k.tasto.alternative.size
        val larghezzaVoce = k.rect.width()
        val larghezza = larghezzaVoce * n
        val altezza = k.rect.height()
        var sinistra = k.rect.left
        var invertito = false
        if (sinistra + larghezza > width - margineOrizzontale) {
            sinistra = k.rect.right - larghezza
            invertito = true
        }
        sinistra = max(sinistra, margineOrizzontale)
        val alto = max(dp(4f), k.rect.top - altezza - dp(8f))
        return Popup(k.tasto.alternative, RectF(sinistra, alto, sinistra + larghezza, alto + altezza), larghezzaVoce, invertito)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gestore.removeCallbacksAndMessages(null)
        tastoPremuto = null
        popup = null
    }
}
