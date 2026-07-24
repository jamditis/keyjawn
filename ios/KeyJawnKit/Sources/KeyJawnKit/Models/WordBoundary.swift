import Foundation

/// Where the word before the cursor starts, for word-at-a-time deletion.
///
/// `UITextDocumentProxy` has no word-delete primitive, so a word delete is N calls to
/// `deleteBackward()` and N has to be computed from the text before the cursor. The
/// rule below is deliberately coarser than a linguistic word break: a word runs back
/// to the nearest whitespace, which keeps `src/main/App.kt`, `--no-verify` and
/// `~/.ssh/config` as single units. Splitting those on punctuation is what makes word
/// delete useless on a keyboard aimed at shell prompts.
public enum WordBoundary {

    /// How many characters to delete to remove the word immediately before the cursor.
    ///
    /// Trailing spaces and tabs are consumed first, then the run of non-whitespace
    /// before them. A newline is never crossed, so deleting back through a blank line
    /// stops at the line break instead of eating the previous line of a multi-line
    /// prompt; when the line break is all that is left, it is removed on its own so a
    /// held backspace still makes progress.
    ///
    /// - Parameter text: the document text immediately before the cursor. `UIKit` only
    ///   guarantees a limited window of context here, which is fine: the count is
    ///   applied to whatever it returned.
    /// - Returns: a count in `Character`s, clamped to the length of `text`.
    public static func deleteCount(before text: String) -> Int {
        var remaining = Array(text)
        var count = 0

        while let last = remaining.last, last == " " || last == "\t" {
            remaining.removeLast()
            count += 1
        }

        // Nothing but horizontal whitespace back to a line break (or to the start of
        // the available context). Consume the break itself only when the pass above
        // found nothing, so a run of trailing spaces is one step and the newline is
        // the next.
        if remaining.last == "\n" || remaining.isEmpty {
            if count > 0 { return count }
            return text.isEmpty ? 0 : 1
        }

        while let last = remaining.last, !last.isWhitespace {
            remaining.removeLast()
            count += 1
        }

        return count
    }
}
