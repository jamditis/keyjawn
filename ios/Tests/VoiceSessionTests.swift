import XCTest
@testable import KeyJawnKit

final class VoiceSessionTests: XCTestCase {

    func testCommitEmitsUTF8AndCancelEmitsNothing() {
        var session = VoiceSession()
        session.start()
        session.updatePartial("hello")
        XCTAssertEqual(session.commit(), "hello")
        XCTAssertEqual(Array("hello".utf8), Array("hello".utf8))

        session.start()
        session.updatePartial("discard me")
        session.cancel()
        XCTAssertNil(session.commit())
        XCTAssertEqual(session.transcript, "")
        XCTAssertFalse(session.isListening)
    }

    func testCommitWithoutSpeechInsertsNothing() {
        var session = VoiceSession()
        session.start()
        XCTAssertNil(session.commit())
    }

    func testPartialUpdatesAreIgnoredWhenNotListening() {
        var session = VoiceSession()
        session.updatePartial("nope")
        XCTAssertEqual(session.transcript, "")
        XCTAssertNil(session.commit())
    }
}
