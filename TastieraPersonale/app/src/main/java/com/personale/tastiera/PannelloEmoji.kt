package com.personale.tastiera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

@SuppressLint("ViewConstructor")
class PannelloEmoji(
    context: Context,
    private val prefs: Preferenze,
    private val onEmoji: (String) -> Unit,
    private val onCanc: () -> Unit,
    private val onLettere: () -> Unit,
) : LinearLayout(context) {

    private val densita = resources.displayMetrics.density
    private val categorie = listOf(
        "🕘" to "",
        "😀" to FACCINE,
        "👍" to GESTI,
        "❤️" to CUORI,
        "🐶" to NATURA,
        "🍕" to CIBO,
        "⚽" to OGGETTI,
    )
    private val schede = LinearLayout(context)
    private val icone = ArrayList<TextView>()
    private val scorrimento = ScrollView(context)
    private val griglia = LinearLayout(context)
    private val pulsanti = ArrayList<TextView>()
    private var categoria = 1
    private var tema = Temi.predefinito

    init {
        orientation = VERTICAL

        schede.orientation = HORIZONTAL
        categorie.forEachIndexed { i, (icona, _) ->
            val tv = TextView(context)
            tv.text = icona
            tv.textSize = 20f
            tv.gravity = Gravity.CENTER
            tv.setOnClickListener { mostraCategoria(i) }
            icone.add(tv)
            schede.addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addView(schede, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))

        griglia.orientation = VERTICAL
        scorrimento.addView(
            griglia,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        addView(scorrimento, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val fondo = LinearLayout(context)
        fondo.orientation = HORIZONTAL
        fondo.addView(pulsante("ABC", onLettere), parametri(1.5f))
        fondo.addView(pulsante("spazio") { onEmoji(" ") }, parametri(5f))
        fondo.addView(pulsante("⌫", onCanc), parametri(1.5f))
        addView(fondo, LayoutParams(LayoutParams.MATCH_PARENT, dp(50)))

        mostraCategoria(if (prefs.emojiRecenti.isEmpty()) 1 else 0)
    }

    private fun dp(v: Int) = (v * densita).toInt()

    fun applicaTema(t: Tema) {
        tema = t
        setBackgroundColor(t.sfondo)
        pulsanti.forEach {
            it.setTextColor(t.testo)
            it.background = pillola(t.tastoSpeciale)
        }
        mostraCategoria(categoria)
    }

    private fun mostraCategoria(i: Int) {
        categoria = i
        griglia.removeAllViews()
        val lista = if (i == 0) prefs.emojiRecenti else categorie[i].second.split(" ")

        if (lista.isEmpty()) {
            val vuoto = TextView(context)
            vuoto.text = "Qui compariranno le emoji che usi più spesso"
            vuoto.gravity = Gravity.CENTER
            vuoto.setTextColor(tema.testoSecondario)
            vuoto.setPadding(0, dp(32), 0, 0)
            griglia.addView(vuoto)
        }

        lista.chunked(COLONNE).forEach { gruppo ->
            val riga = LinearLayout(context)
            riga.orientation = HORIZONTAL
            for (c in 0 until COLONNE) {
                val emoji = gruppo.getOrNull(c)
                val cella = TextView(context)
                cella.text = emoji ?: ""
                cella.textSize = 26f
                cella.gravity = Gravity.CENTER
                if (emoji != null) cella.setOnClickListener { usa(emoji) }
                riga.addView(cella, LayoutParams(0, dp(48), 1f))
            }
            griglia.addView(riga, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        scorrimento.scrollTo(0, 0)

        icone.forEachIndexed { indice, tv ->
            tv.alpha = if (indice == categoria) 1f else 0.5f
            tv.background = if (indice == categoria) pillola(tema.tasto) else null
        }
    }

    private fun usa(emoji: String) {
        onEmoji(emoji)
        prefs.emojiRecenti = (listOf(emoji) + prefs.emojiRecenti.filter { it != emoji }).take(32)
    }

    private fun pulsante(testo: String, azione: () -> Unit): TextView {
        val tv = TextView(context)
        tv.text = testo
        tv.textSize = 15f
        tv.gravity = Gravity.CENTER
        tv.setOnClickListener { azione() }
        pulsanti.add(tv)
        return tv
    }

    private fun parametri(peso: Float) = LayoutParams(0, LayoutParams.MATCH_PARENT, peso).apply {
        setMargins(dp(3), dp(4), dp(3), dp(6))
    }

    private fun pillola(colore: Int) = InsetDrawable(
        GradientDrawable().apply {
            setColor(colore)
            cornerRadius = dp(10).toFloat()
        },
        dp(2),
    )

    private companion object {
        const val COLONNE = 8

        // Aggiungi o togli emoji: basta separarle con uno spazio.
        const val FACCINE = "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👻 💀 🤖 💩"
        const val GESTI = "👍 👎 👌 🤌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 👏 🙌 👐 🤲 🙏 🤝 💪 ✍️ 💅 🤳 👀 👁️ 👄 🧠"
        const val CUORI = "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ✨ 🔥 💯 ✅ ❌ ⭐ 🌟 💥 💫 🎉 🎊 🎁 🎈 💤 💬 ❗ ❓ ⚠️ 🔔 🎵 🎶"
        const val NATURA = "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🦆 🦉 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍 🐙 🐬 🐳 🐟 🌸 🌹 🌻 🌷 🌱 🌲 🍀 🍁 🌈 ☀️ 🌙 ⛅ 🌧️ ❄️ 🌊"
        const val CIBO = "🍕 🍝 🍔 🍟 🌭 🥪 🌮 🥗 🍣 🍜 🍩 🍪 🎂 🍰 🍫 🍦 🍿 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥝 🍅 🥑 🥕 🌽 🥖 🧀 🥚 ☕ 🍵 🍺 🍷 🥂 🍾 🍹 🥤 💧"
        const val OGGETTI = "⚽ 🏀 🏐 🎾 🏓 🚴 🏃 🏊 🎮 🎲 🎨 🎬 🎤 🎧 📚 ✏️ 📱 💻 ⌚ 📷 💡 🔑 🏠 🚗 🚲 ✈️ 🚀 🏖️ ⛰️ 🗓️ ⏰ 💰 💳 📌 📎 ✂️ 🛒 🎓 💼 🇮🇹"
    }
}
