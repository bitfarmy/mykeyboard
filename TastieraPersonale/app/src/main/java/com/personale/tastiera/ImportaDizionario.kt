package com.personale.tastiera

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * Installa un dizionario scaricato con il browser. Il file viene accettato solo se la sua impronta
 * SHA-256 è una di quelle in [CatalogoDizionari]: così la lingua si riconosce da sola e un file
 * rovinato o modificato non entra mai nella tastiera.
 */
object ImportaDizionario {

    sealed class Esito {
        data class Installato(val lingua: Lingua) : Esito()
        data class Errore(val messaggio: String) : Esito()
    }

    fun importa(context: Context, uri: Uri): Esito {
        val massimo = (CatalogoDizionari.file.values.maxOfOrNull { it.byte } ?: 0L) + 1
        val cartella = File(context.filesDir, "dizionari").apply { mkdirs() }
        val temporaneo = File(cartella, "importazione.tmp")
        try {
            val sha = MessageDigest.getInstance("SHA-256")
            var letti = 0L
            val ingresso = context.contentResolver.openInputStream(uri)
                ?: return Esito.Errore("Non riesco ad aprire il file.")
            ingresso.use { inp ->
                temporaneo.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buffer)
                        if (n < 0) break
                        letti += n
                        if (letti > massimo) {
                            return Esito.Errore("Il file è troppo grande per essere un dizionario della tastiera.")
                        }
                        sha.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                    }
                }
            }
            val impronta = sha.digest().joinToString("") { "%02x".format(it) }
            val codice = CatalogoDizionari.file.entries.firstOrNull { it.value.sha256 == impronta }?.key
            val lingua = codice?.let { Lingue.perCodice(it) }
                ?: return Esito.Errore(
                    "Questo file non è un dizionario della tastiera, oppure è di una versione diversa o è stato " +
                        "modificato. Scaricalo di nuovo con il pulsante \"Scarica\" della lingua che ti serve.",
                )
            val destinazione = Dizionario.fileScaricato(context, lingua.codice)
            if (!temporaneo.renameTo(destinazione)) return Esito.Errore("Non riesco a salvare il dizionario.")
            return Esito.Installato(lingua)
        } catch (e: SecurityException) {
            return Esito.Errore("Permesso negato per leggere il file.")
        } catch (e: Exception) {
            return Esito.Errore("Errore durante la lettura del file: ${e.message}")
        } finally {
            temporaneo.delete()
        }
    }
}
