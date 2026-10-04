package com.personale.tastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Prove del correttore sul dizionario italiano vero (assets/parole_it.txt). */
class LessicoTest {

    companion object {
        lateinit var it: Lessico

        @BeforeClass
        @JvmStatic
        fun carica() {
            it = Lessico(Lingue.italiano.locale, Vicinanza.da(Layout.righeLettere(Lingue.italiano)))
            File("src/main/assets/parole_it.txt").bufferedReader().useLines { righe -> it.caricaDizionario(righe) }
            it.prepara()
        }

        fun piccolo(lingua: Lingua, testo: String) = Lessico(lingua.locale, Vicinanza.da(Layout.righeLettere(lingua))).apply {
            caricaDizionario(testo.lineSequence())
            prepara()
        }
    }

    private fun sicura(scritta: String) = it.correggi(scritta)?.takeIf { c -> c.sicura }?.testo

    @Test fun dizionarioGrande() = assertTrue(it.numeroParole > 50_000)

    @Test fun accenti() {
        assertEquals("perché", sicura("perche"))
        assertEquals("perché", sicura("perchè"))
        assertEquals("città", sicura("citta"))
        assertEquals("Perché", sicura("Perche"))
        assertEquals("PERCHÉ", sicura("PERCHE"))
    }

    @Test fun apostrofi() {
        assertEquals("c'è", it.correggiAccenti("c'e"))
        assertEquals("po'", it.correggiAccenti("po"))
        assertFalse(it.conosciuta("un'altro"))
    }

    @Test fun refusiComuni() {
        assertEquals("tutto", sicura("tuto"))        // doppia dimenticata
        assertEquals("grazie", sicura("grazei"))     // lettere invertite
        assertEquals("questo", sicura("qeusto"))
        assertEquals("domani", sicura("domami"))     // tasto vicino
        assertEquals("andiamo", sicura("andaimo"))
        assertEquals("ciao", sicura("ciap"))
        assertEquals("come", sicura("comr"))
        // "ciai" sembra una parola vera (come "sai", "hai"): "ciao" è proposto, non imposto
        assertEquals("ciao", it.correggi("ciai")?.testo)
        assertEquals("sempre", sicura("semppre"))    // lettera in più
        assertEquals("Domani", sicura("Domami"))
    }

    @Test fun maiuscoleObbligatorie() {
        assertEquals("Roma", sicura("roma"))
    }

    @Test fun paroleGiusteNonSiToccano() {
        for (p in listOf("ciao", "sono", "casa", "zaino", "tutti", "Roma", "e", "è", "perché", "c'è", "anche")) {
            assertNull(p, it.correggi(p))
        }
    }

    @Test fun nelDubbioNonCorregge() {
        // "tutt" può essere tutto, tutti, tutta: si propone ma non si impone
        val c = it.correzioni("tutt", 2)
        assertTrue(c.isNotEmpty())
        assertFalse(c[0].sicura)
    }

    @Test fun nomiConMaiuscoleInterne() = assertNull(it.correggi("iPhonr"))

    @Test fun abbreviazioniDaChatNonSiToccano() {
        for (p in listOf("nn", "xke", "cmq", "qst")) assertNull(p, sicura(p))
    }

    @Test fun completamenti() {
        assertTrue(it.completamenti("buong", 3).contains("buongiorno"))
        assertEquals("Buongiorno", it.completamenti("Buong", 3).first())
    }

    @Test fun imparaDopoDueVolte() {
        val l = piccolo(Lingue.italiano, "ciao\t100\ncasa\t50")
        l.impara("zibaldone")
        assertFalse(l.conosciuta("zibaldone"))
        l.impara("zibaldone")
        assertTrue(l.conosciuta("zibaldone"))
        assertTrue(l.completamenti("ziba", 3).contains("zibaldone"))
    }

    @Test fun costi() {
        assertEquals(0.4f, it.costo("tuto", "tutto", 2f), 0.001f)
        assertEquals(0.6f, it.costo("grazei", "grazie", 2f), 0.001f)
        assertEquals(0.6f, it.costo("domami", "domani", 2f), 0.001f)
        assertEquals(1.0f, it.costo("domazi", "domani", 2f), 0.001f)
    }

    @Test fun vicinanza() {
        val v = Vicinanza.da(Layout.righeLettere(Lingue.italiano))
        assertTrue(v.vicini('a', 's'))
        assertTrue(v.vicini('a', 'q'))
        assertTrue(v.vicini('o', 'p'))
        assertTrue(v.vicini('n', 'm'))
        assertTrue(v.vicini('z', 'a'))
        assertFalse(v.vicini('a', 'p'))
        assertFalse(v.vicini('q', 'e'))
    }

    @Test fun inglese() {
        val en = piccolo(
            Lingue.perCodice("en")!!,
            "the\t5000000\nI\t4000000\nyou\t3000000\nI'm\t400000\ndon't\t350000\nhouse\t30000\nhorse\t10000\nwhere\t200000",
        )
        assertEquals("I", en.correggiAccenti("i"))
        assertEquals("I'm", en.correggiAccenti("im"))
        assertEquals("don't", en.correggiAccenti("dont"))
        assertEquals("the", en.correggi("teh")?.testo)
        assertEquals("house", en.correggi("hoyse")?.testo)
    }

    @Test fun tedesco() {
        val de = piccolo(Lingue.perCodice("de")!!, "ich\t900000\nHaus\t50000\nessen\t40000\nEssen\t30000")
        assertEquals("Haus", de.correggiAccenti("haus"))
        assertNull(de.correggiAccenti("essen"))
        assertEquals("Haus", de.completamenti("ha", 3).first())
    }

    @Test fun vecchioFormatoSenzaConteggi() {
        val l = piccolo(Lingue.italiano, "ciao casa\n# commento\nperché\n-perche")
        assertTrue(l.conosciuta("casa"))
        assertEquals("perché", l.correggiAccenti("perche"))
    }

    @Test fun tutteLeLingueHannoTreRighe() {
        for (l in Lingue.tutte) {
            assertEquals(l.codice, 3, Layout.righeLettere(l).size)
            assertTrue(l.codice, l.inclusa || CatalogoDizionari.file.containsKey(l.codice))
        }
    }
}
