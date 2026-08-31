import UIKit
import XCTest
@testable import KeyJawnKit

/// The keyboard used to be 322pt on every idiom. iPad must use the pad metrics
/// so keys are not a cramped phone strip on a 11/13-inch canvas.
@MainActor
final class KeyboardMetricsTests: XCTestCase {

    func testPadIsTallerThanPhonePortrait() {
        let phone = UITraitCollection { traits in
            traits.userInterfaceIdiom = .phone
            traits.verticalSizeClass = .regular
        }
        let pad = UITraitCollection { traits in
            traits.userInterfaceIdiom = .pad
        }
        XCTAssertEqual(KeyboardMetrics.current(for: phone), .phonePortrait)
        XCTAssertEqual(KeyboardMetrics.current(for: pad), .pad)
        XCTAssertEqual(KeyboardMetrics.phonePortrait.total, 322)
        XCTAssertEqual(KeyboardMetrics.pad.total, 426)
        XCTAssertGreaterThan(KeyboardMetrics.pad.total, KeyboardMetrics.phonePortrait.total)
    }

    func testPhoneLandscapeIsShorterThanPhonePortrait() {
        let landscape = UITraitCollection { traits in
            traits.userInterfaceIdiom = .phone
            traits.verticalSizeClass = .compact
        }
        XCTAssertEqual(KeyboardMetrics.current(for: landscape), .phoneLandscape)
        XCTAssertEqual(KeyboardMetrics.phoneLandscape.total, 228)
        XCTAssertLessThan(KeyboardMetrics.phoneLandscape.total, KeyboardMetrics.phonePortrait.total)
    }

    func testOnlyPadsWideEnoughForTenAccessibleKeysReserveTheAssistantArea() {
        let pad = UITraitCollection { traits in
            traits.userInterfaceIdiom = .pad
        }
        let phone = UITraitCollection { traits in
            traits.userInterfaceIdiom = .phone
        }

        XCTAssertEqual(KeyboardAssistantLayout.leadingInset(for: 599, traits: pad), 0)
        XCTAssertEqual(KeyboardAssistantLayout.leadingInset(for: 600, traits: pad), 0)
        XCTAssertEqual(KeyboardAssistantLayout.leadingInset(for: 639, traits: pad), 0)
        XCTAssertEqual(
            KeyboardAssistantLayout.leadingInset(for: 640, traits: pad),
            KeyboardAssistantLayout.leadingInset
        )
        XCTAssertEqual(
            KeyboardAssistantLayout.leadingInset(for: 820, traits: pad),
            KeyboardAssistantLayout.leadingInset
        )
        XCTAssertEqual(KeyboardAssistantLayout.leadingInset(for: 820, traits: phone), 0)
    }
}
