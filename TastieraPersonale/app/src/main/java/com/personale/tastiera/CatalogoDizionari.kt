package com.personale.tastiera

// FILE GENERATO da dizionari/genera_tutti.sh: non modificarlo a mano.

/** Dove si scaricano i dizionari e l'impronta SHA-256 che ogni file deve avere. */
object CatalogoDizionari {
    const val VERSIONE = 1
    const val INDIRIZZO = "https://github.com/bitfarmy/mykeyboard-dizionari/releases/download/v1/"

    val file: Map<String, FileDizionario> = mapOf(
        "en" to FileDizionario("8b9a0776aab8c36642c7c6da465c280cde1e3c0c814ebd7d4176555e3c270f98", 772986),
        "es" to FileDizionario("1c0625b25d26fe96e0af5b7f8e128c3aeca4efdc7e65f1cb338c73e762d55022", 959598),
        "fr" to FileDizionario("7bce2500b2bc15576595fc7a2ae4997b5f76a3002086f8aa453a466d0eab92b2", 937661),
        "de" to FileDizionario("8891d7f3b75391ad2bd00cbbe8766abfb2f6fd76008827daa41fc15e380ccee7", 1012360),
        "pt" to FileDizionario("9e1d1cb0eeb71c8f7ff8486e25ac9e95783549027ac11fa0068c73018ad617e3", 940713),
    )
}

data class FileDizionario(val sha256: String, val byte: Long)
