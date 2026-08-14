import SwiftUI
import UIKit
import KeyJawnKit

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
            VStack(alignment: .leading, spacing: 20) {
                let current = OnboardingCopy.pages[page]
                Text(current.title)
                    .font(.title2)
                    .fontWeight(.semibold)
                Text(current.body)
                    .foregroundStyle(.secondary)
                Spacer()
                if page == 1 {
                    Button(OnboardingCopy.openSettingsTitle) {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                    .buttonStyle(.bordered)
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
                    .accessibilityIdentifier(
                        page == OnboardingCopy.pages.count - 1
                            ? "onboarding.done"
                            : "onboarding.continue"
                    )
                }
            }
            .padding()
            .navigationTitle("Set up")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func finish() {
        prefs.hasCompletedOnboarding = true
        onFinished()
    }
}
