package com.keyjawn

data class Key(
    val label: String,
    val output: KeyOutput,
    val weight: Float = 1f
)

sealed class KeyOutput {
    data class Character(val char: String) : KeyOutput()
    data class KeyCode(val code: Int) : KeyOutput()
    object Shift : KeyOutput()
    object Backspace : KeyOutput()
    object Enter : KeyOutput()
    object Space : KeyOutput()
    object SymSwitch : KeyOutput()
    object Sym2Switch : KeyOutput()
    object AbcSwitch : KeyOutput()
    object Slash : KeyOutput()
    object QuickKey : KeyOutput()
}

typealias Row = List<Key>
typealias Layer = List<Row>

object KeyboardLayouts {

    val lowercase: Layer = listOf(
        "qwertyuiop".map { Key(it.toString(), KeyOutput.Character(it.toString())) },
        "asdfghjkl".map { Key(it.toString(), KeyOutput.Character(it.toString())) },
        listOf(
            Key("Shift", KeyOutput.Shift, weight = 1.5f),
            *"zxcvbnm".map { Key(it.toString(), KeyOutput.Character(it.toString())) }.toTypedArray(),
            Key("Del", KeyOutput.Backspace, weight = 1.5f)
        ),
        listOf(
            Key("?123", KeyOutput.SymSwitch, weight = 1.5f),
            Key(",", KeyOutput.Character(","), weight = 1f),
            Key(" ", KeyOutput.Space, weight = 3.5f),
            Key(".", KeyOutput.Character("."), weight = 1f),
            Key("/", KeyOutput.QuickKey),
            Key("Enter", KeyOutput.Enter, weight = 1.5f)
        )
    )

    val uppercase: Layer = listOf(
        "QWERTYUIOP".map { Key(it.toString(), KeyOutput.Character(it.toString())) },
        "ASDFGHJKL".map { Key(it.toString(), KeyOutput.Character(it.toString())) },
        listOf(
            Key("Shift", KeyOutput.Shift, weight = 1.5f),
            *"ZXCVBNM".map { Key(it.toString(), KeyOutput.Character(it.toString())) }.toTypedArray(),
            Key("Del", KeyOutput.Backspace, weight = 1.5f)
        ),
        listOf(
            Key("?123", KeyOutput.SymSwitch, weight = 1.5f),
            Key(",", KeyOutput.Character(","), weight = 1f),
            Key(" ", KeyOutput.Space, weight = 3.5f),
            Key(".", KeyOutput.Character("."), weight = 1f),
            Key("/", KeyOutput.QuickKey),
            Key("Enter", KeyOutput.Enter, weight = 1.5f)
        )
    )

    // Symbols page 1: common punctuation and operators
    val symbols: Layer = listOf(
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map {
            Key(it, KeyOutput.Character(it))
        },
        listOf("*", "\"", "'", ":", ";", "!", "?", "%", "=", "^").map {
            Key(it, KeyOutput.Character(it))
        },
        listOf(
            Key("1/2", KeyOutput.Sym2Switch, weight = 1.5f),
            *listOf(",", ".", "<", ">", "{", "}").map {
                Key(it, KeyOutput.Character(it))
            }.toTypedArray(),
            Key("Del", KeyOutput.Backspace, weight = 1.5f)
        ),
        listOf(
            Key("abc", KeyOutput.AbcSwitch, weight = 1.5f),
            Key(" ", KeyOutput.Space, weight = 3f),
            Key("slash", KeyOutput.Slash),
            Key("Enter", KeyOutput.Enter, weight = 1.5f)
        )
    )

    // Symbols page 2: brackets, programming, and less common
    val symbols2: Layer = listOf(
        listOf("~", "`", "|", "\\", "[", "]", "{", "}", "^", "/").map {
            Key(it, KeyOutput.Character(it))
        },
        listOf("\u2014", "\u2013", "\u2022", "\u00b0", "\u00a9", "\u00ae", "\u2026", "\u00ab", "\u00bb", "\u00a7").map {
            // em dash, en dash, bullet, degree, copyright, registered, ellipsis, guillemets, section
            Key(it, KeyOutput.Character(it))
        },
        listOf(
            Key("2/2", KeyOutput.SymSwitch, weight = 1.5f),
            *listOf("<", ">", "(", ")", "[", "]").map {
                Key(it, KeyOutput.Character(it))
            }.toTypedArray(),
            Key("Del", KeyOutput.Backspace, weight = 1.5f)
        ),
        listOf(
            Key("abc", KeyOutput.AbcSwitch, weight = 1.5f),
            Key(" ", KeyOutput.Space, weight = 3f),
            Key("slash", KeyOutput.Slash),
            Key("Enter", KeyOutput.Enter, weight = 1.5f)
        )
    )

    fun getLayer(index: Int): Layer = when (index) {
        0 -> lowercase
        1 -> uppercase
        2 -> symbols
        3 -> symbols2
        else -> lowercase
    }

    const val LAYER_LOWER = 0
    const val LAYER_UPPER = 1
    const val LAYER_SYMBOLS = 2
    const val LAYER_SYMBOLS2 = 3
}
