import XCTest
@testable import KeyJawnKit

/// `HostConfig` is the wire format between the app and the keyboard extension: the app
/// encodes it into the shared container and the extension decodes it there. A field
/// that stops round-tripping shows up as an empty host list in the SCP panel.
final class HostConfigTests: XCTestCase {

    private let sample = HostConfig(
        label: "houseofjawn",
        hostname: "example.internal",
        port: 2222,
        username: "jawn",
        authMethod: .key,
        hostPublicKey: "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIExample",
        uploadPath: "/srv/uploads"
    )

    func testRoundTripsThroughJSON() throws {
        let data = try JSONEncoder().encode(sample)
        let decoded = try JSONDecoder().decode(HostConfig.self, from: data)
        XCTAssertEqual(decoded, sample)
    }

    func testArrayRoundTripsThroughJSON() throws {
        let hosts = [sample, HostConfig(label: "b", hostname: "b.internal", username: "root")]
        let data = try JSONEncoder().encode(hosts)
        XCTAssertEqual(try JSONDecoder().decode([HostConfig].self, from: data), hosts)
    }

    func testIdentitySurvivesEncoding() throws {
        let data = try JSONEncoder().encode(sample)
        let decoded = try JSONDecoder().decode(HostConfig.self, from: data)
        // The id is what HostStore.update matches on, so an edit that lost it would
        // append a duplicate host instead of replacing the original.
        XCTAssertEqual(decoded.id, sample.id)
    }

    func testDefaults() {
        let host = HostConfig(label: "minimal", hostname: "h", username: "u")
        XCTAssertEqual(host.port, 22)
        XCTAssertEqual(host.authMethod, .key)
        XCTAssertNil(host.hostPublicKey)
        XCTAssertEqual(host.uploadPath, "/tmp")
    }

    func testValidation() {
        XCTAssertTrue(HostConfig(label: "a", hostname: "h", username: "u").isValid)
        XCTAssertFalse(HostConfig(label: "a", hostname: "", username: "u").isValid)
        XCTAssertFalse(HostConfig(label: "a", hostname: "h", username: "").isValid)
        XCTAssertFalse(HostConfig(label: "a", hostname: "   ", username: "u").isValid)
        XCTAssertFalse(HostConfig(label: "a", hostname: "h", port: 0, username: "u").isValid)
    }

    func testAuthMethodRawValuesAreStable() {
        // Persisted verbatim in the shared container; renaming a case would orphan
        // every host an existing install has saved.
        XCTAssertEqual(HostConfig.AuthMethod.key.rawValue, "key")
        XCTAssertEqual(HostConfig.AuthMethod.password.rawValue, "password")
    }
}
