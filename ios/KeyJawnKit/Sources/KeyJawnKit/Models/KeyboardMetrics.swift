import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Row heights for the keyboard extension. Phone portrait is the original 322pt
/// stack. Landscape shrinks so the host app is not a sliver. Pad is taller so
/// keys are not a phone strip on an 11/13-inch canvas.
public struct KeyboardMetrics: Equatable, Sendable {
    public let extraRow: CGFloat
    public let numberRow: CGFloat
    public let gap: CGFloat
    public let qwerty: CGFloat

    public var total: CGFloat { extraRow + gap + numberRow + gap + qwerty }

    public static let phonePortrait  = KeyboardMetrics(extraRow: 52, numberRow: 42, gap: 4, qwerty: 220)
    public static let phoneLandscape = KeyboardMetrics(extraRow: 40, numberRow: 32, gap: 3, qwerty: 150)
    public static let pad            = KeyboardMetrics(extraRow: 62, numberRow: 52, gap: 6, qwerty: 300)

#if canImport(UIKit)
    public static func current(for traits: UITraitCollection) -> KeyboardMetrics {
        if traits.userInterfaceIdiom == .pad { return .pad }
        return traits.verticalSizeClass == .compact ? .phoneLandscape : .phonePortrait
    }
#endif
}

/// Horizontal space used by the iPad system assistant controls.
///
/// Some host apps place undo, redo, and paste over a third-party keyboard's
/// leading edge. Full-width iPad layouts have enough room to reserve that area
/// without shrinking terminal controls below usable touch targets. Compact
/// split views and phones keep the complete row width.
public enum KeyboardAssistantLayout {
    /// Ten 44-point controls, nine 4-point gaps, 12 points of outer padding, and
    /// the 152-point assistant reservation require at least 640 points.
    public static let fullWidthPadMinimum: CGFloat = 640
    public static let leadingInset: CGFloat = 152

    #if canImport(UIKit)
        public static func leadingInset(for width: CGFloat, traits: UITraitCollection) -> CGFloat {
            guard traits.userInterfaceIdiom == .pad, width >= fullWidthPadMinimum else { return 0 }
            return leadingInset
        }
    #endif
}
