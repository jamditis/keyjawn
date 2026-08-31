import KeyJawnKit
import SwiftUI
import UIKit

/// First-launch keyboard setup. Skippable. Reopened from Settings.
///
/// Completion is written to `KeyboardPrefs.hasCompletedOnboarding` in the
/// `group.com.keyjawn` suite. Skip and Done use the same write.
struct OnboardingView: View {

    var prefs: KeyboardPrefs = .shared
    var onFinished: () -> Void

    @State private var page = 0

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 24) {
                let current = OnboardingCopy.pages[page]
                HStack(spacing: 12) {
                    AppPromptMark()
                    VStack(alignment: .leading, spacing: 2) {
                        Text("KeyJawn")
                            .font(.title2.bold().monospaced())
                            .foregroundStyle(AppTheme.text)
                        Text("Terminal keys for work on the move")
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.secondaryText)
                    }
                }

                HStack(spacing: 8) {
                    ForEach(OnboardingCopy.pages.indices, id: \.self) { index in
                        Capsule()
                            .fill(index <= page ? AppTheme.mint : AppTheme.border)
                            .frame(height: 4)
                    }
                }
                .accessibilityHidden(true)

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        Text(current.title)
                            .font(.title.bold())
                            .foregroundStyle(AppTheme.text)
                        Text(current.body)
                            .foregroundStyle(AppTheme.secondaryText)
                        if page == 1 {
                            Button(OnboardingCopy.openSettingsTitle) {
                                if let url = URL(string: UIApplication.openSettingsURLString) {
                                    UIApplication.shared.open(url)
                                }
                            }
                            .buttonStyle(.bordered)
                            .tint(AppTheme.mint)
                        }
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(AppTheme.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .stroke(AppTheme.border, lineWidth: 1)
                    }
                }
                HStack {
                    Button(OnboardingCopy.skipTitle) {
                        finish()
                    }
                    .accessibilityIdentifier("onboarding.skip")
                    Spacer()
                    Button(page == OnboardingCopy.pages.count - 1
                           ? OnboardingCopy.doneTitle
                           : OnboardingCopy.continueTitle) {
                        if page == OnboardingCopy.pages.count - 1 {
                            finish()
                        } else {
                            page += 1
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.mint)
                    .accessibilityIdentifier(
                        page == OnboardingCopy.pages.count - 1
                            ? "onboarding.done"
                            : "onboarding.continue"
                    )
                }
            }
            .padding(24)
            .background(AppTheme.background.ignoresSafeArea())
            .navigationTitle("Set up")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func finish() {
        prefs.hasCompletedOnboarding = true
        onFinished()
    }
}
