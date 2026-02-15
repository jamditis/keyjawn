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
            Key(" ", KeyOutput.Space, weight = 5f),
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
            Key(" ", KeyOutput.Space, weight = 5f),
            Key("/", KeyOutput.QuickKey),
            Key("Enter", KeyOutput.Enter, weight = 1.5f)
        )
    )

    val symbols: Layer = listOf(
        listOf("-", "_", "=", "+", ".", "\\", "|", "~", "`").map {
            Key(it, KeyOutput.Character(it))
        },
        listOf("!", "@", "#", "$", "%", "&", "*", "(", ")").map {
            Key(it, KeyOutput.Character(it))
        },
        listOf("[", "]", "{", "}", "<", ">", "^", "\"", "'").map {
            Key(it, KeyOutput.Character(it))
        },
        listOf(
            Key("abc", KeyOutput.AbcSwitch, weight = 1.5f),
            Key(" ", KeyOutput.Space, weight = 3.5f),
            Key("/", KeyOutput.Character("/")),
            Key("slash", KeyOutput.Slash),
            Key("Enter", KeyOutput.Enter, weight = 1.5f)
        )
    )

    fun getLayer(index: Int): Layer = when (index) {
        0 -> lowercase
        1 -> uppercase
        2 -> symbols
        else -> lowercase
    }

    const val LAYER_LOWER = 0
    const val LAYER_UPPER = 1
    const val LAYER_SYMBOLS = 2
}
