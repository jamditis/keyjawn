import XCTest
@testable import KeyJawnKit
@testable import KeyJawn

/// `HostConfig` is the wire format between the app and the keyboard extension: the app
/// encodes it into the shared container and the extension decodes it there. A field
/// that stops round-tripping shows up as an empty host list in the SCP panel.
final class HostConfigTests: XCTestCase {

    private let knownHostKey =
        "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJfkNV4OS33ImTXvorZr72q4v5XhVEQKfvqsxOEJ/XaR"
    private let differentHostKey =
        "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKqG9GzSGc2k1eXNzrkfoE2ag8NtdHfV7fMuWq5GxY8a"

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

    // MARK: - Host key trust

    func testMissingOrBlankHostKeyIsNotPinned() {
        XCTAssertFalse(HostConfig(label: "a", hostname: "h", username: "u").hasPinnedHostKey)
        XCTAssertFalse(
            HostConfig(
                label: "a",
                hostname: "h",
                username: "u",
                hostPublicKey: " \n\t "
            ).hasPinnedHostKey
        )
        XCTAssertTrue(
            HostConfig(
                label: "a",
                hostname: "h",
                username: "u",
                hostPublicKey: knownHostKey
            ).hasPinnedHostKey
        )
    }

    func testPresentedHostKeyUsesTheOpenSSHSHA256Fingerprint() throws {
        let presented = try PresentedHostKey(openSSHKey: knownHostKey)

        XCTAssertEqual(presented.openSSHKey, knownHostKey)
        XCTAssertEqual(
            presented.fingerprint,
            "SHA256:BFlAu0a4IRDePBZATpvzbeWrjzjd9h2/tKqd//EWd1Q"
        )
    }

    func testAcceptedPresentedKeyCanBePersistedAndMatchesLater() throws {
        let presented = try PresentedHostKey(openSSHKey: knownHostKey)
        var host = HostConfig(label: "a", hostname: "h", username: "u")

        host.hostPublicKey = presented.openSSHKey

        XCTAssertTrue(host.hasPinnedHostKey)
        XCTAssertTrue(try presented.matches(openSSHKey: XCTUnwrap(host.hostPublicKey)))
    }

    func testPresentedKeyDoesNotMatchAChangedHostKey() throws {
        let presented = try PresentedHostKey(openSSHKey: knownHostKey)

        XCTAssertFalse(try presented.matches(openSSHKey: differentHostKey))
    }

    func testPresentedKeyCanonicalizesCommentsAndWhitespace() throws {
        let presented = try PresentedHostKey(
            openSSHKey: "  \(knownHostKey) host@example  \n"
        )

        XCTAssertEqual(presented.openSSHKey, knownHostKey)
        XCTAssertTrue(try presented.matches(openSSHKey: "\(knownHostKey) another-comment"))
    }

    func testPresentedKeyRejectsMalformedOpenSSHData() {
        XCTAssertThrowsError(try PresentedHostKey(openSSHKey: "ssh-ed25519 not-base64"))
        XCTAssertThrowsError(try PresentedHostKey(openSSHKey: "not-a-key"))
    }

    func testFirstUseValidatorCapturesTheKeyAndAbortsTheProbe() {
        let captured = LockedBox<PresentedHostKey?>(nil)
        let probeClosed = LockedBox(false)
        let validator = TOFUHostKeyValidator(
            captureHostKey: { presentedKey in captured.set(presentedKey) },
            closeProbe: { probeClosed.set(true) }
        )

        let result = validator.captureAndReject(openSSHKey: knownHostKey)

        XCTAssertEqual(captured.get()?.openSSHKey, knownHostKey)
        XCTAssertEqual(result, .trustRequired)
        XCTAssertTrue(probeClosed.get())
    }

    func testFirstUseValidatorRejectsMalformedKeysWithoutPrompting() {
        let captured = LockedBox<PresentedHostKey?>(nil)
        let probeClosed = LockedBox(false)
        let validator = TOFUHostKeyValidator(
            captureHostKey: { presentedKey in captured.set(presentedKey) },
            closeProbe: { probeClosed.set(true) }
        )

        XCTAssertEqual(
            validator.captureAndReject(openSSHKey: "ssh-ed25519 not-base64"),
            .invalidPresentedKey
        )
        XCTAssertNil(captured.get())
        XCTAssertTrue(probeClosed.get())
    }

    func testFirstUseProbeTimesOutAndClosesTheOwnedChannel() async {
        let probeClosed = LockedBox(false)
        let (presentedKeys, presentedKeyContinuation) =
            AsyncThrowingStream<PresentedHostKey, Error>.makeStream()

        do {
            _ = try await nextPresentedHostKey(
                from: presentedKeys,
                timeout: .milliseconds(10),
                onTimeout: {
                    probeClosed.set(true)
                    presentedKeyContinuation.finish(
                        throwing: HostKeyTrustError.probeTimedOut
                    )
                }
            )
            XCTFail("A stalled host-key probe should time out")
        } catch {
            XCTAssertEqual(error as? HostKeyTrustError, .probeTimedOut)
        }

        XCTAssertTrue(probeClosed.get())
    }
}

private final class LockedBox<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value

    init(_ value: Value) {
        self.value = value
    }

    func get() -> Value {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func set(_ value: Value) {
        lock.lock()
        self.value = value
        lock.unlock()
    }
}

final class SSHIdentityKeyMigrationTests: XCTestCase {

    func testExtensionPrefersTheSharedKeyOverTheLegacyMirror() {
        let sharedKey = Data([1, 2, 3])
        let legacyKey = Data([4, 5, 6])

        XCTAssertEqual(
            SSHIdentityKeyMigration.extensionReadableKey(
                shared: sharedKey,
                legacyMirror: legacyKey
            ),
            sharedKey
        )
    }

    func testExtensionCanReadTheLegacyMirrorBeforeAppMigration() {
        let legacyKey = Data([7, 8, 9])

        XCTAssertEqual(
            SSHIdentityKeyMigration.extensionReadableKey(
                shared: nil,
                legacyMirror: legacyKey
            ),
            legacyKey
        )
    }

    func testExistingSharedKeyWinsAndRemovesLegacyCopies() {
        let sharedKey = Data([1, 2, 3])
        var legacyKeychain: Data? = Data([4, 5, 6])
        var legacyFile: Data? = Data([7, 8, 9])
        var storeCalls = 0

        let result = SSHIdentityKeyMigration.resolve(
            loadShared: { sharedKey },
            loadLegacyKeychain: { legacyKeychain },
            loadLegacyFile: { legacyFile },
            storeShared: { _ in
                storeCalls += 1
                return true
            },
            deleteLegacyKeychain: { legacyKeychain = nil },
            deleteLegacyFile: { legacyFile = nil }
        )

        XCTAssertEqual(result, sharedKey)
        XCTAssertEqual(storeCalls, 0)
        XCTAssertNil(legacyKeychain)
        XCTAssertNil(legacyFile)
    }

    func testLegacyKeychainKeyIsCopiedAndVerifiedBeforeCleanup() {
        let legacyKey = Data([10, 11, 12])
        var sharedKey: Data?
        var legacyKeychain: Data? = legacyKey
        var legacyFile: Data? = Data([13, 14, 15])

        let result = SSHIdentityKeyMigration.resolve(
            loadShared: { sharedKey },
            loadLegacyKeychain: { legacyKeychain },
            loadLegacyFile: { legacyFile },
            storeShared: { data in
                sharedKey = data
                return true
            },
            deleteLegacyKeychain: { legacyKeychain = nil },
            deleteLegacyFile: { legacyFile = nil }
        )

        XCTAssertEqual(result, legacyKey)
        XCTAssertEqual(sharedKey, legacyKey)
        XCTAssertNil(legacyKeychain)
        XCTAssertNil(legacyFile)
    }

    func testLegacyFileIsUsedWhenTheProcessCannotReadTheOldKeychainItem() {
        let legacyFileKey = Data([16, 17, 18])
        var sharedKey: Data?
        var legacyFile: Data? = legacyFileKey

        let result = SSHIdentityKeyMigration.resolve(
            loadShared: { sharedKey },
            loadLegacyKeychain: { nil },
            loadLegacyFile: { legacyFile },
            storeShared: { data in
                sharedKey = data
                return true
            },
            deleteLegacyKeychain: {},
            deleteLegacyFile: { legacyFile = nil }
        )

        XCTAssertEqual(result, legacyFileKey)
        XCTAssertEqual(sharedKey, legacyFileKey)
        XCTAssertNil(legacyFile)
    }

    func testFailedOrUnverifiedSharedWritePreservesLegacyCopies() {
        let legacyKey = Data([19, 20, 21])
        var legacyKeychain: Data? = legacyKey
        var legacyFile: Data? = legacyKey

        let result = SSHIdentityKeyMigration.resolve(
            loadShared: { nil },
            loadLegacyKeychain: { legacyKeychain },
            loadLegacyFile: { legacyFile },
            storeShared: { _ in true },
            deleteLegacyKeychain: { legacyKeychain = nil },
            deleteLegacyFile: { legacyFile = nil }
        )

        XCTAssertNil(result)
        XCTAssertEqual(legacyKeychain, legacyKey)
        XCTAssertEqual(legacyFile, legacyKey)
    }
}
