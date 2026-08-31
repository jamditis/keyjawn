import SwiftUI
import UIKit

enum AppTheme {
    static let backgroundColor = UIColor(red: 10 / 255, green: 10 / 255, blue: 15 / 255, alpha: 1)
    static let surfaceColor = UIColor(red: 20 / 255, green: 20 / 255, blue: 28 / 255, alpha: 1)
    static let borderColor = UIColor(red: 30 / 255, green: 30 / 255, blue: 42 / 255, alpha: 1)
    static let textColor = UIColor(red: 228 / 255, green: 228 / 255, blue: 236 / 255, alpha: 1)
    static let secondaryTextColor = UIColor(red: 176 / 255, green: 176 / 255, blue: 196 / 255, alpha: 1)
    static let mintColor = UIColor(red: 108 / 255, green: 242 / 255, blue: 168 / 255, alpha: 1)
    static let warmColor = UIColor(red: 242 / 255, green: 108 / 255, blue: 138 / 255, alpha: 1)

    static var background: Color { Color(uiColor: backgroundColor) }
    static var surface: Color { Color(uiColor: surfaceColor) }
    static var border: Color { Color(uiColor: borderColor) }
    static var text: Color { Color(uiColor: textColor) }
    static var secondaryText: Color { Color(uiColor: secondaryTextColor) }
    static var mint: Color { Color(uiColor: mintColor) }
    static var warm: Color { Color(uiColor: warmColor) }
}

struct AppPromptMark: View {
    var compact = false

    var body: some View {
        HStack(spacing: 1) {
            Text(">")
                .foregroundStyle(AppTheme.mint)
            Text("_")
                .foregroundStyle(AppTheme.warm)
        }
        .font(.system(size: compact ? 20 : 30, weight: .bold, design: .monospaced))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("KeyJawn prompt")
        .accessibilityIdentifier("brand.prompt")
    }
}

private struct AppFormBackground: ViewModifier {
    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .background(AppTheme.background.ignoresSafeArea())
    }
}

extension View {
    func appFormBackground() -> some View {
        modifier(AppFormBackground())
    }
}
