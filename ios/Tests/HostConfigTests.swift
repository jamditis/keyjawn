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

    // MARK: - Host key scan command

    func testScanCommandOmitsThePortFlagForTheDefaultPort() {
        let host = HostConfig(label: "a", hostname: "example.internal", port: 22, username: "u")
        XCTAssertEqual(host.hostKeyScanCommand, "ssh-keyscan -t ed25519 example.internal")
    }

    /// Without `-p` the instructions scan port 22 of the same hostname, which is either
    /// nothing at all or a different service whose key gets pinned here — and then
    /// every later connection fails on a mismatch the user cannot explain.
    func testScanCommandCarriesANonDefaultPort() {
        let host = HostConfig(label: "a", hostname: "example.internal", port: 2222, username: "u")
        XCTAssertEqual(host.hostKeyScanCommand, "ssh-keyscan -p 2222 -t ed25519 example.internal")
    }

    func testScanCommandMatchesTheHostItIsBuiltFrom() {
        for port: UInt16 in [22, 22222, 2022, 1] {
            let host = HostConfig(label: "a", hostname: "h.internal", port: port, username: "u")
            XCTAssertEqual(host.hostKeyScanCommand,
                           HostConfig.hostKeyScanCommand(hostname: "h.internal", port: port))
        }
    }

    func testAuthMethodRawValuesAreStable() {
        // Persisted verbatim in the shared container; renaming a case would orphan
        // every host an existing install has saved.
        XCTAssertEqual(HostConfig.AuthMethod.key.rawValue, "key")
        XCTAssertEqual(HostConfig.AuthMethod.password.rawValue, "password")
    }
}
