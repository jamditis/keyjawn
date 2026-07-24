import Foundation

public enum AltKeyMappings {

    /// Long-press alternates, keyed by the primary key's label.
    ///
    /// Accented vowels come first because they are what a long-press means on any
    /// other keyboard. The punctuation entries are the ones that earn their place on
    /// a keyboard pointed at a shell: the character people reach for while typing a
    /// path or a flag, one press away from the key they are already on, so the second
    /// symbols page stays a fallback rather than a toll on every command.
    static let table: [String: [String]] = [
        "a": ["á", "à", "â", "ä", "å", "æ"],
        "e": ["é", "è", "ê", "ë"],
        "i": ["í", "ì", "î", "ï"],
        "o": ["ó", "ò", "ô", "ö", "ø"],
        "u": ["ú", "ù", "û", "ü"],
        "n": ["ñ"],
        "c": ["ç"],
        "s": ["ß"],
        "y": ["ÿ"],

        // Underscore leads: `my_file` outruns an em dash by a wide margin here.
        "-": ["_", "—", "–"],
        "/": ["\\", "|"],
        // Brackets and braces from the first symbols page, so `[]`, `{}` and `<>`
        // never require a trip to the second.
        "(": ["[", "{", "<"],
        ")": ["]", "}", ">"],
        ".": ["…", ".."],
        ";": [":"],
        ":": [";"],
        "'": ["`", "\u{2018}", "\u{2019}"],
        "\"": ["`", "\u{201C}", "\u{201D}"],
        "$": ["€", "£", "¥"],
        "?": ["¿"],
        "!": ["¡"],
    ]

    // Shifted symbols for the number row (key = digit label).
    public static let numberShifts: [String: String] = [
        "1": "!", "2": "@", "3": "#", "4": "$", "5": "%",
        "6": "^", "7": "&", "8": "*", "9": "(", "0": ")"
    ]

    /// The alternates for a key label, or an empty array when it has none.
    ///
    /// An uppercase letter falls back to the uppercased form of its lowercase
    /// alternates, so the table only has to carry one case.
    public static func alts(for label: String) -> [String] {
        if let found = table[label] { return found }
        let lower = label.lowercased()
        if label != lower, let found = table[lower] {
            return found.map { $0.uppercased() }
        }
        return []
    }
}
