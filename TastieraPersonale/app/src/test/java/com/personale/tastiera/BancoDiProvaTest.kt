package com.personale.tastiera

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Misura il correttore su un banco di prova (non fa parte dei test normali).
 * BANCO_DI_PROVA=cartella con refusi_it.tsv ("refuso<TAB>parola giusta") e rare_it.txt
 * (parole giuste che non stanno nel dizionario).
 */
class BancoDiProvaTest {

    @Test
    fun misura() {
        val cartella = System.getenv("BANCO_DI_PROVA")?.let { File(it) }
        assumeTrue(cartella != null && cartella.isDirectory)
        LessicoTest.carica()
        val l = LessicoTest.it

        val casi = File(cartella, "refusi_it.tsv").readLines().map { it.split('\t') }
        var corretti = 0
        var sbagliati = 0
        var proposti = 0
        val inizio = System.nanoTime()
        for ((refuso, giusta) in casi) {
            val c = l.correzioni(refuso, 2)
            val auto = c.firstOrNull()?.takeIf { it.sicura }?.testo
            when {
                auto == giusta -> corretti++
                auto != null -> { sbagliati++; if (System.getenv("ESEMPI") != null) println("SBAGLIATO $refuso → $auto (giusta: $giusta)") }
                c.any { it.testo == giusta } -> proposti++
                else -> if (System.getenv("ESEMPI") != null) println("MANCATO $refuso (giusta: $giusta) proposte: ${c.map { it.testo }}")
            }
        }
        val ms = (System.nanoTime() - inizio) / 1e6 / casi.size
        val rare = File(cartella, "rare_it.txt").readLines().filter { it.isNotBlank() }
        val rovinate = rare.count { w -> l.correzioni(w, 1).firstOrNull()?.sicura == true }
        val n = casi.size.toDouble()
        println(
            "NUOVA    refusi: corretti %.1f%%  sbagliati %.1f%%  lasciati %.1f%% (di cui proposti %.1f%%)   parole rare giuste rovinate: %.1f%%   %.2f ms a parola".format(
                100 * corretti / n, 100 * sbagliati / n, 100 * (n - corretti - sbagliati) / n, 100 * proposti / n,
                100.0 * rovinate / rare.size, ms,
            ),
        )
    }

    /** TARATURA=1: prova molte combinazioni di parametri e stampa le migliori. */
    @Test
    fun taratura() {
        val cartella = System.getenv("BANCO_DI_PROVA")?.let { File(it) }
        assumeTrue(cartella != null && cartella.isDirectory && System.getenv("TARATURA") != null)
        LessicoTest.carica()
        val l = LessicoTest.it
        val casi = File(cartella, "refusi_it.tsv").readLines().map { it.split('\t') }
            .map { (r, g) -> Triple(r, g, l.perTaratura(r)) }
        val rare = File(cartella, "rare_it.txt").readLines().filter { it.isNotBlank() }.map { it to l.perTaratura(it) }

        fun decidi(n: Int, dati: Pair<List<Triple<String, Float, Double>>, Double>, pen: Double, lnS: Double, soglia: Double): String? {
            val punti = dati.first.map { (p, c, f) -> Triple(p, c, f - pen * c) }.sortedByDescending { it.third }
            val primo = punti.firstOrNull() ?: return null
            var somma = Math.exp(lnS + dati.second - primo.third)
            for (c in punti.take(6)) somma += Math.exp(c.third - primo.third)
            return if (1 / somma >= soglia) primo.first else null
        }

        val risultati = ArrayList<Pair<Double, String>>()
        for (pen in listOf(3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0)) for (lnS in listOf(-10.0, -8.0, -6.0, -4.0, -2.0, 0.0, 2.0))
            for (soglia in listOf(0.5, 0.65, 0.8, 0.9, 0.95, 0.98)) {
                var ok = 0
                var sb = 0
                for ((r, g, d) in casi) {
                    val a = decidi(r.length, d, pen, lnS, soglia)
                    if (a == g) ok++ else if (a != null) sb++
                }
                val rov = rare.count { (w, d) -> decidi(w.length, d, pen, lnS, soglia) != null }
                val pOk = 100.0 * ok / casi.size
                val pSb = 100.0 * sb / casi.size
                val pRov = 100.0 * rov / rare.size
                // Costo atteso per 100 parole scritte: 5% con un refuso, 0,33% giuste ma fuori dal dizionario;
                // una parola rovinata dalla tastiera pesa 3 volte un refuso lasciato.
                val obiettivo = -(0.05 * (100 - pOk) + 3 * (0.05 * pSb + 0.0033 * pRov))
                risultati.add(obiettivo to "pen=%.0f lnS=%.0f soglia=%.2f  corretti %.1f%%  sbagliati %.1f%%  rovinate %.1f%%  → costo %.2f".format(pen, lnS, soglia, pOk, pSb, pRov, obiettivo))
            }
        risultati.sortedByDescending { it.first }.take(10).forEach { println("TARATURA " + it.second) }
    }
}
