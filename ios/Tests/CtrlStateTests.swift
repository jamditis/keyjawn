import XCTest
@testable import KeyJawnKit

@MainActor
final class CtrlStateTests: XCTestCase {

    func testTapCyclesOffArmedLockedOff() {
        let ctrl = CtrlState()
        XCTAssertEqual(ctrl.state, .off)
        ctrl.toggle()
        XCTAssertEqual(ctrl.state, .armed)
        ctrl.toggle()
        XCTAssertEqual(ctrl.state, .locked)
        ctrl.toggle()
        XCTAssertEqual(ctrl.state, .off)
    }

    func testConsumeReleasesArmedAfterOneKey() {
        let ctrl = CtrlState()
        ctrl.toggle()
        ctrl.consume()
        XCTAssertEqual(ctrl.state, .off)
    }

    /// The whole point of the locked state: it survives keypresses until tapped off.
    func testConsumeLeavesLockedAlone() {
        let ctrl = CtrlState()
        ctrl.toggle()
        ctrl.toggle()
        ctrl.consume()
        ctrl.consume()
        XCTAssertEqual(ctrl.state, .locked)
    }

    func testConsumeFromOffIsANoOp() {
        let ctrl = CtrlState()
        var notifications = 0
        ctrl.onChange = { _ in notifications += 1 }
        ctrl.consume()
        XCTAssertEqual(ctrl.state, .off)
        XCTAssertEqual(notifications, 0, "consume from off should not tell the UI to redraw")
    }

    func testIsActiveCoversArmedAndLocked() {
        let ctrl = CtrlState()
        XCTAssertFalse(ctrl.isActive)
        ctrl.toggle()
        XCTAssertTrue(ctrl.isActive)
        ctrl.toggle()
        XCTAssertTrue(ctrl.isActive)
    }

    func testOnChangeReportsEveryTransition() {
        let ctrl = CtrlState()
        var seen: [CtrlState.State] = []
        ctrl.onChange = { seen.append($0) }
        ctrl.toggle()
        ctrl.toggle()
        ctrl.consume()   // locked, no change
        ctrl.toggle()
        XCTAssertEqual(seen, [.armed, .locked, .off])
    }
}
