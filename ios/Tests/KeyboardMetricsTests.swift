import UIKit
import XCTest
@testable import KeyJawnKit

/// The keyboard used to be 322pt on every idiom. iPad must use the pad metrics
/// so keys are not a cramped phone strip on a 11/13-inch canvas.
final class KeyboardMetricsTests: XCTestCase {

    func testPadIsTallerThanPhonePortrait() {
        let phone = UITraitCollection(traitsFrom: [
            UITraitCollection(userInterfaceIdiom: .phone),
            UITraitCollection(verticalSizeClass: .regular),
        ])
        let pad = UITraitCollection(userInterfaceIdiom: .pad)
        XCTAssertEqual(KeyboardMetrics.current(for: phone), .phonePortrait)
        XCTAssertEqual(KeyboardMetrics.current(for: pad), .pad)
        XCTAssertEqual(KeyboardMetrics.phonePortrait.total, 322)
        XCTAssertEqual(KeyboardMetrics.pad.total, 426)
        XCTAssertGreaterThan(KeyboardMetrics.pad.total, KeyboardMetrics.phonePortrait.total)
    }

    func testPhoneLandscapeIsShorterThanPhonePortrait() {
        let landscape = UITraitCollection(traitsFrom: [
            UITraitCollection(userInterfaceIdiom: .phone),
            UITraitCollection(verticalSizeClass: .compact),
        ])
        XCTAssertEqual(KeyboardMetrics.current(for: landscape), .phoneLandscape)
        XCTAssertEqual(KeyboardMetrics.phoneLandscape.total, 228)
        XCTAssertLessThan(KeyboardMetrics.phoneLandscape.total, KeyboardMetrics.phonePortrait.total)
    }
}
