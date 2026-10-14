package com.personale.tastiera

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * Misura le coppie di parole su testi che non hanno contribuito ai conteggi (non fa parte dei test normali).
 * COPPIE_BANCO=cartella con coppie_it.txt e riservato_it.txt (vedi dizionari/genera_coppie.py).
 * Si guarda se il contesto fa correggere meglio i refusi e se prevede la parola successiva.
 */
class CoppieBancoTest {

    private fun coppieDelTesto(l: Lessico, file: File, massimo: Int): List<Pair<String, String>> {
        val risultato = ArrayList<Pair<String, String>>()
        file.bufferedReader().useLines { righe ->
            for (riga in righe) {
                // La stessa regola della tastiera: le coppie non attraversano la punteggiatura
                for (segmento in riga.lowercase().replace('’', '\'').split(Regex("[^\\p{L}'\\s]+"))) {
                    val t = segmento.split(' ').filter { it.isNotBlank() }
                    for (i in 0 until t.size - 1) {
                        if (l.conosciuta(t[i]) && l.conosciuta(t[i + 1])) risultato.add(t[i] to t[i + 1])
                    }
                }
                if (risultato.size >= massimo) break
            }
        }
        return risultato
    }

    private fun refuso(parola: String, v: Vicinanza, r: Random): String {
        val i = 1 + r.nextInt(parola.length - 1)
        return when (r.nextInt(4)) {
            0 -> { // tasto vicino
                val vicini = ('a'..'z').filter { v.vicini(parola[i], it) && it != parola[i] }
                if (vicini.isEmpty()) parola else parola.substring(0, i) + vicini[r.nextInt(vicini.size)] + parola.substring(i + 1)
            }
            1 -> if (i + 1 < parola.length) parola.substring(0, i) + parola[i + 1] + parola[i] + parola.substring(i + 2) else parola
            2 -> parola.substring(0, i) + parola.substring(i + 1)
            else -> parola.substring(0, i) + parola[i] + parola.substring(i)
        }
    }

    @Test
    fun misura() {
        val cartella = System.getenv("COPPIE_BANCO")?.let { File(it) }
        assumeTrue(cartella != null && cartella.isDirectory)
        LessicoTest.carica()
        val l = LessicoTest.it
        l.caricaCoppieBase(File(cartella, "coppie_it.txt").readLines().asSequence())
        val v = Vicinanza.da(Layout.righeLettere(Lingue.italiano))
        val coppie = coppieDelTesto(l, File(cartella, "riservato_it.txt"), 400_000)
        println("${coppie.size} coppie di prova")

        // Previsione della parola successiva (solo le coppie del corpus, nessuna parola scritta)
        val previsioni = coppie.take(100_000)
        val colpiti = previsioni.count { (a, b) -> b in l.successive(a, 3) }
        val primi = previsioni.count { (a, b) -> l.successive(a, 1).firstOrNull() == b }
        println("previsione: la parola giusta è fra le prime 3 nel %.1f%% dei casi, la prima nel %.1f%%".format(
            100.0 * colpiti / previsioni.size, 100.0 * primi / previsioni.size))

        // Refusi: con e senza la parola precedente
        val r = Random(7)
        val casi = coppie.filter { it.second.length >= 4 && '\'' !in it.second && '\'' !in it.first }.shuffled(r).take(30_000)
        var senza = Triple(0, 0, 0) // corretta da sola, sbagliata da sola, giusta fra le prime due
        var con = Triple(0, 0, 0)
        var n = 0
        for ((a, b) in casi) {
            val x = refuso(b, v, r)
            if (x == b || l.conosciuta(x)) continue
            n++
            fun valuta(c: List<Lessico.Correzione>, t: Triple<Int, Int, Int>): Triple<Int, Int, Int> {
                val prima = c.firstOrNull()
                val auto = prima?.takeIf { it.sicura }?.testo
                return Triple(
                    t.first + if (auto == b) 1 else 0,
                    t.second + if (auto != null && auto != b) 1 else 0,
                    t.third + if (c.any { it.testo == b }) 1 else 0,
                )
            }
            senza = valuta(l.correzioni(x, 2), senza)
            con = valuta(l.correzioni(x, 2, a), con)
        }
        fun riga(nome: String, t: Triple<Int, Int, Int>) = println(
            "%-8s corrette da sole %.1f%%, sbagliate da sole %.2f%%, giusta fra le prime due %.1f%%".format(
                nome, 100.0 * t.first / n, 100.0 * t.second / n, 100.0 * t.third / n,
            ),
        )
        println("$n refusi")
        riga("senza", senza)
        riga("con", con)
    }
}
