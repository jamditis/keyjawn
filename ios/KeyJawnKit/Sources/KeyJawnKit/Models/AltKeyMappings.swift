import Foundation

public enum AltKeyMappings {

    // Accent alts for lowercase keys. Uppercase is derived automatically.
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
        ".": ["…"],
        "-": ["—", "–"],
        "?": ["¿"],
        "!": ["¡"],
    ]

    // Shifted symbols for the number row (key = digit label).
    public static let numberShifts: [String: String] = [
        "1": "!", "2": "@", "3": "#", "4": "$", "5": "%",
        "6": "^", "7": "&", "8": "*", "9": "(", "0": ")"
    ]

    // Returns the alt list for a given key label.
    // Uppercase keys return uppercased versions of the lowercase alts.
    public static func alts(for label: String) -> [String] {
        if let found = table[label] { return found }
        let lower = label.lowercased()
        if label != lower, let found = table[lower] {
            return found.map { $0.uppercased() }
        }
        return []
    }
}
