package com.personale.tastiera

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout

/**
 * Il "cervello" della tastiera. Android lo avvia quando tocchi un campo di testo.
 */
class TastieraService : InputMethodService(), TastieraView.Ascoltatore {

    private data class Correzione(val originale: String, val corretta: String, val separatore: String = "")

    private lateinit var prefs: Preferenze
    private lateinit var lingua: Lingua
    private lateinit var dizionario: Dizionario
    private lateinit var scorciatoie: Scorciatoie
    private var lingueAttive: List<Lingua> = emptyList()

    private var barra: BarraSuggerimenti? = null
    private var tastiera: TastieraView? = null
    private var pannelloEmoji: PannelloEmoji? = null

    private var pagina = Pagina.LETTERE
    private var shift = StatoShift.SPENTO
    private var ultimoShift = 0L
    private var ultimoSpazio = 0L

    private var campoDiTesto = true
    private var campoPassword = false
    private var suggerimentiParole = true  // suggerimenti e correzioni ammessi in questo campo
    private var puoImparare = true         // niente apprendimento in password e modalità incognito
    private var ultimaCorrezione: Correzione? = null
    private var spazioAutomatico = false

    /** Parole di cui hai annullato la correzione con ⌫: in questo campo di testo non vengono ricorrette. */
    private val rifiutate = HashSet<String>()

    private companion object {
        val PAROLA_FINALE = Regex("\\p{L}+$")
        /** Parola con apostrofo in fondo al testo: c', c'e, po', l'al */
        val PAROLA_CON_APOSTROFO = Regex("\\p{L}+(?:['’]\\p{L}*)+$")
        val TOKEN_FINALE = Regex("\\S+$")
        const val PUNTEGGIATURA = ".,;:!?"
    }

    // ---------- Ciclo di vita ----------

    override fun onCreate() {
        super.onCreate()
        prefs = Preferenze(this)
        scorciatoie = Scorciatoie.get(this)
        aggiornaLingua()
    }

    // ---------- Lingue ----------

    /** Legge dalle impostazioni le lingue attive e quella in uso (possono cambiare mentre la tastiera è aperta). */
    private fun aggiornaLingua() {
        lingueAttive = prefs.lingueAttive.mapNotNull { Lingue.perCodice(it) }
            .filter { Dizionario.installata(this, it) }
            .ifEmpty { listOf(Lingue.italiano) }
        val nuova = lingueAttive.firstOrNull { it.codice == prefs.linguaCorrente } ?: lingueAttive.first()
        // Stesso oggetto = stessa lingua e nessun dizionario reimportato nel frattempo dalle impostazioni
        val d = Dizionario.get(this, nuova)
        if (!::dizionario.isInitialized || d !== dizionario) {
            if (::dizionario.isInitialized) dizionario.salva()
            lingua = nuova
            dizionario = d
            d.quandoPronto { aggiornaSuggerimenti() }
        }
        tastiera?.locale = lingua.locale
    }

    private fun prossimaLingua() {
        if (lingueAttive.size < 2) return
        val i = lingueAttive.indexOf(lingua)
        prefs.linguaCorrente = lingueAttive[(i + 1) % lingueAttive.size].codice
        ultimaCorrezione = null
        aggiornaLingua()
        aggiornaTastiera()
        aggiornaSuggerimenti()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val b = BarraSuggerimenti(
            this, ::scegliSuggerimento, ::apriImpostazioni,
            onTasto = { onTocco(); onTesto(it) },
            onShift = { onTocco(); onSpeciale(Codici.SHIFT) },
        )
        val t = TastieraView(this, this)
        val e = PannelloEmoji(
            this, prefs,
            onEmoji = { inserisciEmoji(it) },
            onCanc = { onSpeciale(Codici.CANC) },
            onLettere = { mostraEmoji(false) },
        )
        e.visibility = View.GONE
        barra = b
        tastiera = t
        pannelloEmoji = e
        applicaPreferenze()
        aggiornaTastiera()

        val radice = LinearLayout(this)
        radice.orientation = LinearLayout.VERTICAL
        radice.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)))
        radice.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        radice.addView(e, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, t.altezzaCalcolata()))
        return radice
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val classe = info.inputType and InputType.TYPE_MASK_CLASS
        val variante = info.inputType and InputType.TYPE_MASK_VARIATION

        campoDiTesto = classe == InputType.TYPE_CLASS_TEXT
        campoPassword = when (classe) {
            InputType.TYPE_CLASS_TEXT ->
                variante == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variante == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variante == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variante == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        val campoTecnico = campoDiTesto && (
            variante == InputType.TYPE_TEXT_VARIATION_URI ||
                variante == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variante == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            )
        // Nomi, indirizzi e filtri di ricerca: suggerimenti sì, ma non si impara nulla (dati personali)
        val campoPersonale = campoDiTesto && (
            variante == InputType.TYPE_TEXT_VARIATION_PERSON_NAME ||
                variante == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS ||
                variante == InputType.TYPE_TEXT_VARIATION_FILTER
            )
        val nienteSuggerimenti = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val incognito = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

        suggerimentiParole = campoDiTesto && !campoPassword && !campoTecnico && !nienteSuggerimenti
        puoImparare = suggerimentiParole && !incognito && !campoPersonale

        pagina = when (classe) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Pagina.SIMBOLI
            else -> Pagina.LETTERE
        }
        shift = StatoShift.SPENTO
        ultimaCorrezione = null
        spazioAutomatico = false
        if (!restarting) rifiutate.clear()

        aggiornaLingua()
        applicaPreferenze()
        mostraEmoji(false)
        aggiornaTastiera()
        aggiornaShiftAutomatico()
        aggiornaSuggerimenti()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        dizionario.salva()
    }

    override fun onDestroy() {
        dizionario.salva()
        super.onDestroy()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        aggiornaShiftAutomatico()
        aggiornaSuggerimenti()
    }

    // ---------- Tasti ----------

    override fun onTocco() {
        if (prefs.vibrazione) tastiera?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (prefs.suono) {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
        }
    }

    override fun onTesto(testo: String) {
        val ic = currentInputConnection ?: return
        ultimaCorrezione = null
        val punteggiatura = testo.length == 1 && testo[0] in PUNTEGGIATURA
        var daInserire = testo
        var nuovoSpazioAutomatico = false

        ic.beginBatchEdit()
        if (punteggiatura) {
            if (spazioAutomatico && ic.getTextBeforeCursor(1, 0)?.toString() == " ") {
                // "ciao |" + "," → "ciao, |"
                ic.deleteSurroundingText(1, 0)
                daInserire = "$testo "
                nuovoSpazioAutomatico = true
            } else {
                correggiParolaCorrente(ic)
            }
        }
        ic.commitText(daInserire, 1)
        ic.endBatchEdit()

        ultimaCorrezione = ultimaCorrezione?.copy(separatore = daInserire)
        spazioAutomatico = nuovoSpazioAutomatico
        if (shift == StatoShift.ACCESO) {
            shift = StatoShift.SPENTO
            aggiornaTastiera()
        }
        aggiornaSuggerimenti()
    }

    override fun onSpeciale(codice: Int) {
        if (codice != Codici.CANC) ultimaCorrezione = null
        val ic = currentInputConnection
        when (codice) {
            Codici.SHIFT -> cambiaShift()
            Codici.CANC -> cancella(ic)
            Codici.INVIO -> invio(ic)
            Codici.SPAZIO -> spazio(ic)
            Codici.SIMBOLI -> cambiaPagina(Pagina.SIMBOLI)
            Codici.SIMBOLI2 -> cambiaPagina(Pagina.SIMBOLI2)
            Codici.LETTERE -> cambiaPagina(Pagina.LETTERE)
            Codici.EMOJI -> mostraEmoji(true)
            Codici.LINGUA -> prossimaLingua()
        }
    }

    override fun onPressioneLunga(codice: Int): Boolean = when (codice) {
        Codici.SPAZIO, Codici.LINGUA -> {
            // Tieni premuto lo spazio per passare a un'altra tastiera
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            true
        }
        else -> false
    }

    private fun cambiaShift() {
        val adesso = SystemClock.uptimeMillis()
        shift = when (shift) {
            StatoShift.SPENTO -> StatoShift.ACCESO
            StatoShift.ACCESO -> if (adesso - ultimoShift < 350) StatoShift.BLOCCATO else StatoShift.SPENTO
            StatoShift.BLOCCATO -> StatoShift.SPENTO
        }
        ultimoShift = adesso
        aggiornaTastiera()
    }

    private fun cambiaPagina(nuova: Pagina) {
        pagina = nuova
        aggiornaTastiera()
        if (nuova == Pagina.LETTERE) aggiornaShiftAutomatico()
    }

    private fun cancella(ic: InputConnection?) {
        if (ic == null) return
        val c = ultimaCorrezione
        ultimaCorrezione = null
        spazioAutomatico = false
        if (c != null && annullaCorrezione(ic, c)) return
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun invio(ic: InputConnection?) {
        if (ic == null) return
        correggiParolaCorrente(ic)
        ultimaCorrezione = null
        spazioAutomatico = false
        val info = currentInputEditorInfo ?: return
        val azione = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val senzaAzione = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!senzaAzione && azione != EditorInfo.IME_ACTION_NONE && azione != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(azione)
        } else {
            sendKeyChar('\n')
        }
    }

    private fun spazio(ic: InputConnection?) {
        if (ic == null) return
        val adesso = SystemClock.uptimeMillis()
        val prima = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""

        if (prefs.doppioSpazioPunto && adesso - ultimoSpazio < 400 &&
            prima.length == 2 && prima[1] == ' ' && prima[0].isLetterOrDigit()
        ) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            ultimoSpazio = 0L
            spazioAutomatico = false
            return
        }

        ultimoSpazio = adesso
        ic.beginBatchEdit()
        correggiParolaCorrente(ic)
        ic.commitText(" ", 1)
        ic.endBatchEdit()
        ultimaCorrezione = ultimaCorrezione?.copy(separatore = " ")
        spazioAutomatico = false
        if (pagina != Pagina.LETTERE && campoDiTesto) cambiaPagina(Pagina.LETTERE)
    }

    private fun inserisciEmoji(emoji: String) {
        val ic = currentInputConnection ?: return
        ultimaCorrezione = null
        spazioAutomatico = false
        ic.commitText(emoji, 1)
    }

    // ---------- Correzione e suggerimenti ----------

    /** Corregge (se serve) e impara la parola appena prima del cursore. */
    private fun correggiParolaCorrente(ic: InputConnection) {
        if (!suggerimentiParole) return
        val prima = ic.getTextBeforeCursor(48, 0)?.toString() ?: return
        val token = TOKEN_FINALE.find(prima)?.value ?: return
        if (scorciatoie.esatta(token) != null) return // è una sigla: non toccarla

        // Parole con apostrofo: si corregge solo l'accento o l'apostrofo (c'e → c'è)
        val conApostrofo = PAROLA_CON_APOSTROFO.find(prima)?.value
        if (conApostrofo != null && prefs.autocorrezione && !rifiutata(conApostrofo)) {
            val corretta = dizionario.correggiAccenti(conApostrofo)
            if (corretta != null) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(conApostrofo.length, 0)
                ic.commitText(corretta, 1)
                ic.endBatchEdit()
                ultimaCorrezione = Correzione(conApostrofo, corretta)
                return
            }
        }

        val parola = PAROLA_FINALE.find(prima)?.value ?: return
        val precedente = prima.getOrNull(prima.length - parola.length - 1)
        if (precedente != null && (precedente.isDigit() || precedente in "@#_/")) return

        // Solo le correzioni sicure si applicano da sole; mai sui nomi propri a metà frase
        // né sulle parole di cui hai appena annullato la correzione
        val rifiutata = rifiutata(parola)
        val proposta = dizionario.correzioni(parola, 1).firstOrNull()
        val corretta = if (prefs.autocorrezione && !rifiutata && !nomeProprio(prima, parola)) {
            proposta?.takeIf { it.sicura }?.testo
        } else {
            null
        }
        if (corretta != null) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(parola.length, 0)
            ic.commitText(corretta, 1)
            ic.endBatchEdit()
            ultimaCorrezione = Correzione(parola, corretta)
            if (puoImparare) dizionario.impara(corretta)
        } else if (puoImparare && proposta == null) {
            // Si impara da sola solo una parola che non somiglia a nessuna del dizionario (un nome, un termine
            // tuo). Un refuso non corretto non diventa mai "parola tua": per insegnarla servono due ⌫.
            dizionario.impara(parola)
        }
    }

    private fun rifiutata(parola: String) = parola.lowercase(lingua.locale) in rifiutate

    /** ⌫ subito dopo una correzione la annulla e impara la tua parola. */
    private fun annullaCorrezione(ic: InputConnection, c: Correzione): Boolean {
        val atteso = c.corretta + c.separatore
        if (ic.getTextBeforeCursor(atteso.length, 0)?.toString() != atteso) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(atteso.length, 0)
        ic.commitText(c.originale, 1)
        ic.endBatchEdit()
        // Conta come un uso: dopo due annullamenti (anche in momenti diversi) diventa "parola tua"
        rifiutate.add(c.originale.lowercase(lingua.locale))
        if (puoImparare) dizionario.impara(c.originale)
        return true
    }

    /**
     * Una parola con la maiuscola a metà frase è probabilmente un nome (Marta, Fiat):
     * si propone la correzione ma non la si applica da sola.
     */
    private fun nomeProprio(testoPrima: String, parola: String): Boolean {
        if (parola.firstOrNull()?.isUpperCase() != true) return false
        val primaDellaParola = testoPrima.dropLast(parola.length).trimEnd()
        return primaDellaParola.isNotEmpty() && primaDellaParola.last() !in ".!?\n¿¡"
    }

    private fun aggiornaSuggerimenti() {
        val b = barra ?: return
        if (pannelloEmoji?.visibility == View.VISIBLE) return
        val ic = currentInputConnection
        if (ic == null || campoPassword) {
            b.mostra(emptyList())
            return
        }
        val prima = ic.getTextBeforeCursor(48, 0)?.toString() ?: ""
        val token = TOKEN_FINALE.find(prima)?.value ?: ""
        val lista = ArrayList<Suggerimento>()

        if (token.isNotEmpty()) {
            scorciatoie.cerca(token).take(2).forEach {
                lista.add(Suggerimento(it.testo, token, TipoSuggerimento.SCORCIATOIA))
            }
            val parola = PAROLA_FINALE.find(prima)?.value
            val conApostrofo = PAROLA_CON_APOSTROFO.find(prima)?.value
            if (suggerimentiParole && prefs.suggerimenti && (parola != null || conApostrofo != null)) {
                val sigla = scorciatoie.esatta(token) != null
                // Correzioni: prima la parola intera con apostrofo (c'e → c'è), poi l'ultima parola.
                // In evidenza solo quella che lo spazio applicherà; le altre sono semplici proposte.
                val intera = if (conApostrofo != null && !sigla && !rifiutata(conApostrofo)) dizionario.correggiAccenti(conApostrofo) else null
                val proposte = if (intera == null && parola != null && !sigla) dizionario.correzioni(parola, 2) else emptyList()
                val automatica = prefs.autocorrezione && parola != null && !nomeProprio(prima, parola) && !rifiutata(parola)
                if (intera != null && conApostrofo != null) {
                    val tipo = if (prefs.autocorrezione) TipoSuggerimento.CORREZIONE else TipoSuggerimento.PAROLA
                    lista.add(Suggerimento(intera, conApostrofo, tipo))
                }
                val prima0 = proposte.firstOrNull()
                if (prima0 != null && prima0.sicura && automatica) {
                    lista.add(Suggerimento(prima0.testo, parola!!, TipoSuggerimento.CORREZIONE))
                }
                // Completamenti: forme con apostrofo (c' → c'è), poi la parola dopo l'apostrofo (l'al → altro)
                if (conApostrofo != null) {
                    dizionario.completamenti(conApostrofo, 3).forEach {
                        lista.add(Suggerimento(it, conApostrofo, TipoSuggerimento.PAROLA))
                    }
                }
                if (parola != null) {
                    dizionario.completamenti(parola, 3).forEach {
                        lista.add(Suggerimento(it, parola, TipoSuggerimento.PAROLA))
                    }
                    // Correzioni incerte: dopo i primi completamenti (forse stai ancora scrivendo la parola),
                    // ma sempre entro i tre suggerimenti visibili
                    val pos = minOf(lista.size, 2)
                    proposte.filter { it !== prima0 || !it.sicura || !automatica }.forEachIndexed { k, c ->
                        lista.add(minOf(pos + k, lista.size), Suggerimento(c.testo, parola, TipoSuggerimento.PAROLA))
                    }
                }
            }
        }
        // Due suggerimenti che porterebbero allo stesso testo finale contano come uno solo
        b.mostra(lista.distinctBy { prima.dropLast(it.daSostituire.length) + it.testo }.take(3))
    }

    private fun scegliSuggerimento(s: Suggerimento) {
        val ic = currentInputConnection ?: return
        ultimaCorrezione = null
        val prima = ic.getTextBeforeCursor(s.daSostituire.length, 0)?.toString()
        val finale = if (s.testo.endsWith("\n")) s.testo else s.testo + " "

        ic.beginBatchEdit()
        if (prima == s.daSostituire) ic.deleteSurroundingText(s.daSostituire.length, 0)
        ic.commitText(finale, 1)
        ic.endBatchEdit()

        spazioAutomatico = finale.endsWith(" ")
        if (s.tipo != TipoSuggerimento.SCORCIATOIA && puoImparare) dizionario.impara(s.testo)
        if (shift == StatoShift.ACCESO) {
            shift = StatoShift.SPENTO
            aggiornaTastiera()
        }
    }

    // ---------- Aspetto ----------

    private fun applicaPreferenze() {
        val tema = Temi.perId(prefs.tema)
        tastiera?.tema = tema
        tastiera?.altezzaTastoDp = when (prefs.altezzaTasti) {
            0 -> 46f
            2 -> 60f
            else -> 53f
        }
        barra?.applicaTema(tema)
        pannelloEmoji?.applicaTema(tema)
    }

    private fun aggiornaTastiera() {
        val t = tastiera ?: return
        val tastoLingua = lingueAttive.size > 1
        val righe = when (pagina) {
            Pagina.LETTERE -> Layout.lettere(lingua, prefs.numeriSempreVisibili, tastoLingua, prefs.tastieraClassica)
            Pagina.SIMBOLI -> Layout.simboli(lingua, tastoLingua, prefs.tastieraClassica)
            Pagina.SIMBOLI2 -> Layout.simboli2(lingua, tastoLingua, prefs.tastieraClassica)
        }
        t.locale = lingua.locale
        if (t.righe !== righe) t.righe = righe
        t.statoShift = if (pagina == Pagina.LETTERE) shift else StatoShift.SPENTO
        barra?.aggiornaShift(t.statoShift)
    }

    private fun aggiornaShiftAutomatico() {
        if (shift == StatoShift.BLOCCATO || pagina != Pagina.LETTERE) return
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val maiuscola = prefs.maiuscoleAutomatiche &&
            info.inputType != InputType.TYPE_NULL &&
            ic.getCursorCapsMode(info.inputType) != 0
        val nuovo = if (maiuscola) StatoShift.ACCESO else StatoShift.SPENTO
        if (nuovo != shift) {
            shift = nuovo
            aggiornaTastiera()
        }
    }

    private fun mostraEmoji(mostra: Boolean) {
        val t = tastiera ?: return
        val e = pannelloEmoji ?: return
        if (mostra) {
            val altezza = if (t.height > 0) t.height else t.altezzaCalcolata()
            e.layoutParams?.let {
                it.height = altezza
                e.layoutParams = it
            }
            barra?.mostra(emptyList())
        }
        t.visibility = if (mostra) View.GONE else View.VISIBLE
        e.visibility = if (mostra) View.VISIBLE else View.GONE
        if (!mostra) aggiornaSuggerimenti()
    }

    private fun apriImpostazioni() {
        startActivity(Intent(this, ImpostazioniActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        requestHideSelf(0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
