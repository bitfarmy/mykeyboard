package com.personale.tastiera

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.concurrent.thread

/**
 * Il dizionario di una lingua: quello incluso (assets/parole_it.txt) o quello importato
 * (files/dizionari/<lingua>.txt), più le parole imparate da te (files/parole_imparate_<lingua>.txt,
 * solo sul telefono). Le regole di suggerimento e correzione stanno in [Lessico].
 *
 * Va usato solo dal thread principale; il caricamento avviene in background.
 */
class Dizionario private constructor(private val ctx: Context, val lingua: Lingua) {

    companion object {
        /** Al massimo due lingue in memoria: un dizionario completo occupa una decina di MB. */
        private const val MASSIMO_IN_MEMORIA = 2

        private val istanze = object : LinkedHashMap<String, Dizionario>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Dizionario>): Boolean {
                if (size <= MASSIMO_IN_MEMORIA) return false
                eldest.value.salva()
                return true
            }
        }

        fun get(context: Context, lingua: Lingua): Dizionario = synchronized(istanze) {
            istanze.getOrPut(lingua.codice) {
                Dizionario(context.applicationContext, lingua).also { it.carica() }
            }
        }

        /** Dopo aver installato o tolto un dizionario: alla prossima richiesta viene ricaricato. */
        fun dimentica(codice: String) = synchronized(istanze) {
            istanze.remove(codice)?.salva()
        }

        fun fileScaricato(context: Context, codice: String) =
            File(File(context.filesDir, "dizionari"), "$codice.txt")

        fun installata(context: Context, lingua: Lingua) =
            lingua.inclusa || fileScaricato(context, lingua.codice).isFile

        private fun fileImparate(context: Context, codice: String) =
            File(context.filesDir, "parole_imparate_$codice.txt")

        private fun fileCoppie(context: Context, codice: String) =
            File(context.filesDir, "coppie_imparate_$codice.txt")

        fun cancellaTutteLeImparate(context: Context) {
            synchronized(istanze) { istanze.values.forEach { it.cancellaImparate() } }
            thread {
                synchronized(Companion) {
                    Lingue.tutte.forEach {
                        fileImparate(context, it.codice).delete()
                        fileCoppie(context, it.codice).delete()
                    }
                }
            }
        }
    }

    private var lessico = Lessico(lingua.locale)
    private val file = fileImparate(ctx, lingua.codice)
    private val fileDelleCoppie = fileCoppie(ctx, lingua.codice)
    private val principale = Handler(Looper.getMainLooper())
    private val inAttesa = ArrayList<() -> Unit>()
    private val imparateNelFrattempo = HashMap<String, Int>()
    private var modificato = false

    var pronto = false
        private set

    val numeroParole: Int get() = lessico.numeroParole
    val numeroImparate: Int get() = lessico.numeroImparate

    fun quandoPronto(azione: () -> Unit) {
        if (pronto) azione() else inAttesa.add(azione)
    }

    // ---------- Caricamento e salvataggio ----------

    private fun carica() {
        thread(name = "carica-dizionario-${lingua.codice}") {
            val nuovo = Lessico(lingua.locale, Layout.configurazione(lingua, Preferenze(ctx)).let { c ->
                Vicinanza.da(Layout.righeLettere(c.lingua, true, c.classica, c.qerty))
            })
            try {
                if (lingua.inclusa) {
                    ctx.assets.open("parole_${lingua.codice}.txt").bufferedReader(Charsets.UTF_8)
                        .useLines { nuovo.caricaDizionario(it) }
                } else {
                    fileScaricato(ctx, lingua.codice).bufferedReader(Charsets.UTF_8)
                        .useLines { nuovo.caricaDizionario(it) }
                }
            } catch (e: Exception) {
                // Nessun dizionario: funzioneranno solo le parole imparate.
            }
            try {
                migraVecchioFile()
                synchronized(Companion) {
                    if (file.exists()) file.bufferedReader(Charsets.UTF_8).useLines { nuovo.caricaImparate(it) }
                }
            } catch (e: Exception) {
                // File rovinato: ripartiamo da zero.
            }
            try {
                // Le coppie di parole del corpus (solo per le lingue che le hanno incluse)
                ctx.assets.open("coppie_${lingua.codice}.txt").bufferedReader(Charsets.UTF_8)
                    .useLines { nuovo.caricaCoppieBase(it) }
            } catch (e: Exception) {
                // Nessun file: restano le coppie imparate da te.
            }
            try {
                synchronized(Companion) {
                    if (fileDelleCoppie.exists()) fileDelleCoppie.bufferedReader(Charsets.UTF_8).useLines { nuovo.caricaCoppie(it) }
                }
            } catch (e: Exception) {
                // File rovinato: ripartiamo da zero.
            }
            nuovo.prepara()

            principale.post {
                imparateNelFrattempo.forEach { (p, v) -> nuovo.impara(p, v) }
                imparateNelFrattempo.clear()
                lessico = nuovo
                pronto = true
                inAttesa.forEach { it() }
                inAttesa.clear()
            }
        }
    }

    /** Le versioni 0.1 salvavano solo l'italiano, in parole_imparate.txt. */
    private fun migraVecchioFile() {
        if (lingua.codice != "it") return
        val vecchio = File(ctx.filesDir, "parole_imparate.txt")
        if (vecchio.exists() && !file.exists()) vecchio.renameTo(file)
    }

    fun salva() {
        if (!modificato || !pronto) return
        modificato = false
        val copia = lessico.paroleImparate()
        val coppie = lessico.coppieImparate()
        thread(name = "salva-parole-${lingua.codice}") {
            synchronized(Companion) {
                try {
                    val temporaneo = File(file.parentFile, file.name + ".tmp")
                    temporaneo.bufferedWriter(Charsets.UTF_8).use { out ->
                        copia.forEach { (p, v) ->
                            out.write(p)
                            out.write("\t")
                            out.write(v.toString())
                            out.newLine()
                        }
                    }
                    temporaneo.renameTo(file)
                    val temporaneoCoppie = File(fileDelleCoppie.parentFile, fileDelleCoppie.name + ".tmp")
                    temporaneoCoppie.bufferedWriter(Charsets.UTF_8).use { out ->
                        coppie.forEach { (a, b, v) ->
                            out.write("$a\t$b\t$v")
                            out.newLine()
                        }
                    }
                    temporaneoCoppie.renameTo(fileDelleCoppie)
                } catch (e: Exception) {
                    // Riproveremo al prossimo salvataggio.
                }
            }
        }
    }

    private fun cancellaImparate() {
        principale.post {
            lessico.cancellaImparate()
            imparateNelFrattempo.clear()
            modificato = false
        }
    }

    // ---------- Uso (vedi Lessico) ----------

    fun impara(parola: String, volte: Int = 1) {
        if (!pronto) {
            val w = parola.lowercase(lingua.locale)
            imparateNelFrattempo[w] = (imparateNelFrattempo[w] ?: 0) + volte
            return
        }
        lessico.impara(parola, volte)
        modificato = true
    }

    /** [prima] = la parola scritta subito prima: aiuta a scegliere fra più parole possibili. */
    fun imparaCoppia(prima: String, dopo: String) {
        if (!pronto) return
        lessico.imparaCoppia(prima, dopo)
        modificato = true
    }

    fun conosciuta(parola: String) = lessico.conosciuta(parola)
    fun completamenti(scritto: String, quanti: Int, prima: String? = null) = lessico.completamenti(scritto, quanti, prima)
    fun successive(prima: String, quanti: Int) = lessico.successive(prima, quanti)
    fun correggiAccenti(scritta: String, prima: String? = null) = lessico.correggiAccenti(scritta, prima)
    fun correzioni(scritta: String, quante: Int = 2, prima: String? = null) = lessico.correzioni(scritta, quante, prima)
}
