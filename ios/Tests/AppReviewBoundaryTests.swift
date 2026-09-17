import Foundation
import XCTest

/// Keeps the App review 2.5.2 boundary visible in the test suite.
final class AppReviewBoundaryTests: XCTestCase {

    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
    }

    private var repositoryRoot: URL {
        iosRoot.deletingLastPathComponent()
    }

    func testFirstPartyPrivacyManifestDeclaresEveryUserDefaultsReason() throws {
        let manifestURL = iosRoot.appendingPathComponent("Shared/PrivacyInfo.xcprivacy")
        let data = try Data(contentsOf: manifestURL)
        let manifest = try XCTUnwrap(
            PropertyListSerialization.propertyList(from: data, format: nil)
                as? [String: Any]
        )
        let accessedAPIs = try XCTUnwrap(
            manifest["NSPrivacyAccessedAPITypes"] as? [[String: Any]]
        )
        let defaults = try XCTUnwrap(
            accessedAPIs.first {
                $0["NSPrivacyAccessedAPIType"] as? String
                    == "NSPrivacyAccessedAPICategoryUserDefaults"
            }
        )
        let reasons = try XCTUnwrap(
            defaults["NSPrivacyAccessedAPITypeReasons"] as? [String]
        )

        XCTAssertEqual(Set(reasons), ["CA92.1", "1C8F.1"])

        let generatedProject = try source(at: "KeyJawn.xcodeproj/project.pbxproj")
        XCTAssertGreaterThanOrEqual(
            generatedProject.occurrences(of: "PrivacyInfo.xcprivacy in Resources"),
            2,
            "the app and keyboard extension must each copy the shared manifest"
        )
    }

    func testBothShippingTargetsExplicitlySupportIPhoneAndIPad() throws {
        let project = try source(at: "project.yml")
        XCTAssertEqual(
            project.occurrences(of: "TARGETED_DEVICE_FAMILY: \"1,2\""),
            2,
            "the app and keyboard extension must declare their device families"
        )
    }

    func testCurrentReleaseSourcesRecordApprovalPendingDeveloperRelease() throws {
        let currentStateSources = [
            "CHANGELOG.md",
            "CLAUDE.md",
            "README.md",
            "SOCIAL.md",
            "docs/app-store-v1.0-metadata.md",
            "docs/claude/worker-readme.md",
            "docs/ios-app-review.md",
            "docs/release-style-guide.md",
            "worker/worker/content.py",
        ]

        for path in currentStateSources {
            let source = try repositorySource(at: path)
                .lowercased()
                .split(whereSeparator: \Character.isWhitespace)
                .joined(separator: " ")
                .replacingOccurrences(of: "_", with: " ")
            XCTAssertTrue(source.contains("build 9"), "\(path) must identify the build")
            XCTAssertTrue(source.contains("manual release"), "\(path) must record the release mode")
            XCTAssertTrue(source.contains("approved"), "\(path) must record Apple's approval")
            XCTAssertTrue(
                source.contains("pending developer release"),
                "\(path) must record the manual release state"
            )
            XCTAssertTrue(
                source.contains("not publicly available"),
                "\(path) must not claim public availability"
            )
            XCTAssertFalse(
                source.contains("waiting for review") || source.contains("not approved"),
                "\(path) has the superseded review state"
            )
            XCTAssertFalse(
                source.contains("build 9 is not selected"),
                "\(path) has the pre-selection build 9 state"
            )
            XCTAssertFalse(
                source.contains("build 9 is not uploaded")
                    || source.contains("build 9 remains not uploaded"),
                "\(path) has the pre-upload build 9 state"
            )
            XCTAssertFalse(
                source.contains("needs a fresh archive"),
                "\(path) has the superseded pre-archive state"
            )
            XCTAssertFalse(source.contains("did not archive"), "\(path) has stale archive state")
            XCTAssertFalse(source.contains("not archived"), "\(path) has stale archive state")
            XCTAssertFalse(source.contains("was not archived"), "\(path) has stale archive state")
        }
    }

    func testWebsiteDeploymentActionsArePinnedToReviewedCommits() throws {
        let workflow = try repositorySource(at: ".github/workflows/deploy-site.yml")
        let actionLines = workflow.split(separator: "\n").filter { $0.contains("uses:") }
        XCTAssertEqual(actionLines.count, 3)

        for line in actionLines {
            XCTAssertNotNil(
                line.range(
                    of: #"uses:\s+[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}(?:\s+#.*)?$"#,
                    options: .regularExpression
                ),
                "deployment action is not pinned: \(line)"
            )
        }
    }

    func testWebsiteNavigationHasOneChangelogDestination() throws {
        let navigation = try repositorySource(at: "website/src/components/Nav.astro")
        XCTAssertEqual(navigation.occurrences(of: "href: '/changelog'"), 1)
        XCTAssertEqual(navigation.occurrences(of: "href=\"/changelog\""), 0)
    }

    func testScreenshotFlowsWaitForTheStateTheyCapture() throws {
        let onboarding = try source(at: "UITests/LaunchFlowTests.swift")
        XCTAssertTrue(
            onboarding.contains("app.staticTexts[\"Turn on the keyboard\"].waitForExistence")
        )

        let keyboard = try source(at: "UITests/KeyboardIMETests.swift")
        let tlsControl = try XCTUnwrap(keyboard.range(of: "switches[\"TLS tunnel\"]"))
        let screenshot = try XCTUnwrap(
            keyboard.range(of: "attachScreenshot(named: screenshotName)")
        )
        XCTAssertLessThan(tlsControl.lowerBound, screenshot.lowerBound)
    }

    func testReviewDocumentsDiscloseVoiceAndShortcutAccessBoundaries() throws {
        let evidence = try String(
            contentsOf: repositoryRoot.appendingPathComponent("docs/ios-app-review.md"),
            encoding: .utf8
        )
        let metadata = try String(
            contentsOf: repositoryRoot.appendingPathComponent(
                "docs/app-store-v1.0-metadata.md"
            ),
            encoding: .utf8
        )

        for document in [evidence, metadata] {
            XCTAssertTrue(document.contains("Apple's Speech framework"))
            XCTAssertTrue(document.contains("does not use the microphone"))
            XCTAssertTrue(document.contains("User-created shortcuts"))
            XCTAssertTrue(document.contains("require Full Access"))
        }
        XCTAssertTrue(metadata.contains("built-in text shortcuts work without Allow Full Access"))
    }

    func testReviewerSetsUploadPathAfterSwitchingToKeyAuthentication() throws {
        let metadata = try repositorySource(at: "docs/app-store-v1.0-metadata.md")
        let switchToKey = try XCTUnwrap(
            metadata.range(of: "select SSH key authentication")
        )
        let setUploadPath = try XCTUnwrap(
            metadata.range(of: "set Upload path to the writable remote directory above")
        )
        let testUpload = try XCTUnwrap(
            metadata.range(of: "Copy a small image, tap the SCP key")
        )

        XCTAssertLessThan(switchToKey.lowerBound, setUploadPath.lowerBound)
        XCTAssertLessThan(setUploadPath.lowerBound, testUpload.lowerBound)
    }

    func testReleaseDocumentsProtectPublicationPrivacyAndComplianceGates() throws {
        let releaseDocuments = [
            "CLAUDE.md",
            "docs/handoffs/2026-08-27-app-review-remediation-pause.md",
            "docs/ios-app-review.md",
            "tasks/todo.md",
        ]

        for path in releaseDocuments {
            let document = try repositorySource(at: path)
            XCTAssertTrue(
                document.contains("website/**") && document.contains("publication"),
                "\(path) must state that a main-branch website change publishes the site"
            )
        }

        let evidence = try repositorySource(at: "docs/ios-app-review.md")
        XCTAssertTrue(evidence.contains("ITSAppUsesNonExemptEncryption"))
        XCTAssertTrue(evidence.contains("export-compliance determination"))
        XCTAssertTrue(evidence.contains("App Privacy"))
        XCTAssertTrue(evidence.contains("Publish"))
    }

    func testCIExecutesTheSystemKeyboardSuiteOnIPad() throws {
        let workflow = try repositorySource(at: ".github/workflows/ios-test.yml")
        XCTAssertTrue(workflow.contains("iPad"))
        XCTAssertTrue(workflow.contains("-only-testing:KeyJawnUITests/KeyboardIMETests"))
    }

    func testCIRunsTheSystemKeyboardSuiteOnlyOnThePinnedIPad() throws {
        let workflow = try repositorySource(at: ".github/workflows/ios-test.yml")
        let iPhoneStepStart = try XCTUnwrap(workflow.range(of: "- name: Test KeyJawn"))
        let iPadPickerStart = try XCTUnwrap(workflow.range(of: "- name: Pick iPad simulator"))
        let iPhoneStep = workflow[iPhoneStepStart.lowerBound..<iPadPickerStart.lowerBound]

        XCTAssertTrue(
            iPhoneStep.contains("-skip-testing:KeyJawnUITests/KeyboardIMETests"),
            "The pinned iPad step must own the stateful system-keyboard suite"
        )
    }

    func testSystemKeyboardSuiteCannotSilentlySkipSettingsNavigation() throws {
        let systemKeyboardSuite = try repositorySource(
            at: "ios/UITests/KeyboardIMETests.swift"
        )

        XCTAssertFalse(
            systemKeyboardSuite.contains("XCTSkip"),
            "A supported Settings navigation failure must fail the system keyboard suite"
        )
    }

    func testCIPinsTheIPadKeyboardSuiteToAResolvedSimulatorID() throws {
        let workflow = try repositorySource(at: ".github/workflows/ios-test.yml")
        let pickerStart = try XCTUnwrap(workflow.range(of: "id: ipad-sim"))
        let testStart = try XCTUnwrap(
            workflow.range(of: "- name: Test iPad system keyboard")
        )
        let picker = workflow[pickerStart.lowerBound..<testStart.lowerBound]

        XCTAssertTrue(picker.contains("udid="))
        XCTAssertFalse(picker.contains("name="))
        XCTAssertTrue(
            workflow.contains(
                "-destination \"platform=iOS Simulator,id=${{ steps.ipad-sim.outputs.udid }}\""
            )
        )
    }

    func testPublicWebsiteDoesNotPromoteAClosedTestFlightInvitation() throws {
        let websiteSource = repositoryRoot.appendingPathComponent("website/src")
        let enumerator = try XCTUnwrap(
            FileManager.default.enumerator(
                at: websiteSource,
                includingPropertiesForKeys: [.isRegularFileKey]
            )
        )
        let pages = enumerator.compactMap { $0 as? URL }.filter { $0.pathExtension == "astro" }

        for page in pages {
            let text = try String(contentsOf: page, encoding: .utf8)
            XCTAssertFalse(
                text.contains("testflight.apple.com/join")
                    || text.contains("available through TestFlight")
                    || text.contains("Get the iOS beta")
                    || text.contains("Join the iOS beta"),
                "\(page.lastPathComponent) promotes a TestFlight invitation that is not accepting testers"
            )
        }
    }

    func testShippingUploadInstructionsUseTheScpKeyLabel() throws {
        for file in try shippingSwiftFiles() {
            let text = try String(contentsOf: file, encoding: .utf8)
            XCTAssertFalse(
                text.contains("tap Upload"),
                "\(relativePath(of: file)) must name the visible SCP key"
            )
        }
    }

    func testPublicFullAccessCopyNamesUserCreatedShortcuts() throws {
        let publicCopyPaths = [
            "README.md",
            "website/public/llms.txt",
            "website/src/pages/features.astro",
            "website/src/pages/index.astro",
            "website/src/pages/manual.astro",
            "website/src/pages/privacy.astro",
            "website/src/pages/support.astro",
        ]

        for path in publicCopyPaths {
            let text = try repositorySource(at: path).lowercased()
            XCTAssertTrue(
                text.contains("user-created shortcuts"),
                "\(path) must disclose the Full Access boundary for user-created shortcuts"
            )
        }
    }

    func testReleaseDraftMatchesTheScreenshotSetAndInputFix() throws {
        let metadata = try repositorySource(at: "docs/app-store-v1.0-metadata.md")
        let captionStart = try XCTUnwrap(metadata.range(of: "## Screenshot captions"))
        let checksStart = try XCTUnwrap(metadata.range(of: "## Submission checks"))
        let captionBlock = metadata[captionStart.upperBound..<checksStart.lowerBound]
        let captions = captionBlock.split(separator: "\n").filter { line in
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            return !trimmed.isEmpty && trimmed != "```text" && trimmed != "```"
        }

        XCTAssertEqual(captions.count, 5)
        XCTAssertTrue(captions[1].contains("Full Access"))

        let changelog = try repositorySource(at: "CHANGELOG.md")
        XCTAssertTrue(changelog.contains("non-ASCII terminal input"))
    }

    func testAppMetadataHasNoPhotoLibraryPermission() throws {
        let project = try source(at: "project.yml")
        XCTAssertFalse(
            project.contains("NSPhotoLibraryUsageDescription"),
            "pasteboard image upload does not use the Photos API"
        )
        XCTAssertNil(
            try appInfoDictionary()["NSPhotoLibraryUsageDescription"],
            "the app must not claim photo-library access"
        )
        XCTAssertNil(
            try keyboardInfoDictionary()["NSPhotoLibraryUsageDescription"],
            "the keyboard must not claim photo-library access"
        )
    }

    func testAppMetadataHasNoCustomURLScheme() throws {
        let project = try source(at: "project.yml")
        XCTAssertFalse(
            project.contains("CFBundleURLTypes"),
            "the app has no custom URL handler"
        )
        XCTAssertNil(
            try appInfoDictionary()["CFBundleURLTypes"],
            "the app must not declare an unused URL scheme"
        )
        XCTAssertNil(
            try keyboardInfoDictionary()["CFBundleURLTypes"],
            "the keyboard must not declare an unused URL scheme"
        )
    }

    func testKeyboardMetadataDeclaresASCIICapable() throws {
        let project = try source(at: "project.yml")
        XCTAssertTrue(
            project.contains("IsASCIICapable: true"),
            "the English QWERTY keyboard produces standard ASCII"
        )

        let extensionDictionary = try XCTUnwrap(
            try keyboardInfoDictionary()["NSExtension"] as? [String: Any]
        )
        let attributes = try XCTUnwrap(
            extensionDictionary["NSExtensionAttributes"] as? [String: Any]
        )
        XCTAssertEqual(
            attributes["IsASCIICapable"] as? Bool,
            true,
            "the built keyboard metadata must match its QWERTY output"
        )
    }

    func testShippingSourcesDoNotUseLocalExecutionOrDocumentAccessAPIs() throws {
        let prohibitedPatterns = [
            #"\bProcess\s*\("#,
            #"\bNSTask\b"#,
            #"\bposix_spawn\b"#,
            #"(?<!\.)\bfork\s*\("#,
            #"\bexec[lvpe]*\s*\("#,
            #"(?<!\.)\bsystem\s*\("#,
            #"\bpopen\s*\("#,
            #"\bdlopen\s*\("#,
            #"\bdlsym\s*\("#,
            #"/bin/(?:sh|bash|zsh)\b"#,
            #"\bUIDocument\w*\b"#,
            #"\bFileDocument\b"#,
            #"\bNSFileProvider\b"#,
            #"\bFileHandle\b"#,
            #"\bloadFileRepresentation\b"#,
            #"\bloadInPlaceFileRepresentation\b"#,
            #"\bstartAccessingSecurityScopedResource\b"#,
            #"\bfileImporter\s*\("#,
            #"\bfileExporter\s*\("#,
            #"\bimport\s+JavaScriptCore\b"#,
            #"\bimport\s+WebKit\b"#,
            #"\bWKWebView\b"#,
            #"\bNSClassFromString\b"#,
            #"\bobjc_getClass\b"#,
        ]

        for file in try shippingSwiftFiles() {
            let text = try String(contentsOf: file, encoding: .utf8)
            for pattern in prohibitedPatterns {
                XCTAssertNil(
                    text.range(of: pattern, options: .regularExpression),
                    "\(relativePath(of: file)) matches prohibited pattern \(pattern)"
                )
            }
        }
    }

    func testFilesystemAccessIsLimitedToTheFixedAppGroupMigration() throws {
        let filesystemTokens = [
            "FileManager",
            "FileHandle",
            "Data(contentsOf:",
            "NSData(contentsOf:",
            "String(contentsOf:",
            "String(contentsOfFile:",
            "URL(fileURLWithPath:",
            ".write(to:",
            "contentsOfDirectory(",
            "subpathsOfDirectory(",
            "enumerator(at:",
            "contents(atPath:",
            "createFile(",
            "copyItem(",
            "moveItem(",
            "removeItem(",
            "containerURL(forSecurityApplicationGroupIdentifier:",
        ]
        let filesWithFilesystemAccess = try shippingSwiftFiles().filter { file in
            let text = try String(contentsOf: file, encoding: .utf8)
            return filesystemTokens.contains { text.contains($0) }
        }

        XCTAssertEqual(
            Set(filesWithFilesystemAccess.map(relativePath)),
            ["KeyJawnKit/Sources/KeyJawnKit/Models/SSHIdentityKeyStore.swift"]
        )

        let keyStore = try source(
            at: "KeyJawnKit/Sources/KeyJawnKit/Models/SSHIdentityKeyStore.swift"
        )
        XCTAssertTrue(keyStore.contains(#"legacyFileName = "ssh-identity-ed25519.key""#))
        XCTAssertTrue(
            keyStore.contains(
                "containerURL(forSecurityApplicationGroupIdentifier: AppGroupConfig.suiteName)"
            )
        )
        XCTAssertTrue(keyStore.contains(".appendingPathComponent(legacyFileName, isDirectory: false)"))

        XCTAssertEqual(keyStore.occurrences(of: "FileManager.default"), 2)
        XCTAssertEqual(keyStore.occurrences(of: "Data(contentsOf:"), 1)
        XCTAssertEqual(
            keyStore.occurrences(
                of: "containerURL(forSecurityApplicationGroupIdentifier:"
            ),
            1
        )
        XCTAssertEqual(keyStore.occurrences(of: ".appendingPathComponent("), 1)
        XCTAssertEqual(keyStore.occurrences(of: ".removeItem(at:"), 1)
    }

    func testAppHasNoPreviewOrLocalEchoTerminalPath() throws {
        let contentView = try source(at: "KeyJawn/App/ContentView.swift")
        let terminalController = try source(
            at: "KeyJawn/Terminal/TerminalViewController.swift"
        )

        XCTAssertFalse(contentView.contains("TerminalPreviewView"))
        XCTAssertFalse(contentView.contains("case hosts, preview, settings"))
        XCTAssertFalse(terminalController.lowercased().contains("local echo"))
        XCTAssertFalse(terminalController.contains("SSHSession?"))
        XCTAssertTrue(terminalController.contains("init(session: SSHSession)"))
    }

    func testUITestingUsesAnInMemoryHostStoreOnlyInDebugBuilds() throws {
        let app = try source(at: "KeyJawn/App/KeyJawnApp.swift")
        let hostStore = try source(at: "KeyJawn/Store/HostStore.swift")

        XCTAssertTrue(app.contains("#if DEBUG"))
        XCTAssertTrue(app.contains(".inMemory"))
        XCTAssertTrue(hostStore.contains("case keychain, inMemory"))
        XCTAssertTrue(
            hostStore.contains("guard persistenceMode == .keychain else { return true }")
        )
    }

    func testTerminalEmulatorRepliesUseTheRemoteSession() throws {
        let terminalController = try source(
            at: "KeyJawn/Terminal/TerminalViewController.swift"
        )

        XCTAssertTrue(terminalController.contains("session.send(Array(data))"))
    }

    func testIOSSSHUsesNetworkFrameworkTransport() throws {
        let project = try source(at: "project.yml")
        let transport = try source(at: "Shared/AppleNetworkSSHTransport.swift")
        let session = try source(at: "KeyJawn/Terminal/SSHSession.swift")
        let uploader = try source(at: "Shared/CitadelSCPUploader.swift")

        XCTAssertTrue(
            project.contains("https://github.com/apple/swift-nio-transport-services.git")
        )
        XCTAssertEqual(
            project.occurrences(of: "product: NIOTransportServices"),
            2,
            "the app and keyboard extension must use the Apple-platform transport"
        )
        XCTAssertTrue(transport.contains("import NIOTransportServices"))
        XCTAssertTrue(transport.contains("NIOTSConnectionBootstrap"))
        XCTAssertTrue(
            transport.contains(".channelInitializer { channel in"),
            "cancellation must capture the NIOTS channel before connect finishes"
        )
        XCTAssertTrue(transport.contains("bootstrap.tlsOptions"))
        XCTAssertTrue(session.contains("AppleNetworkSSHTransport"))
        XCTAssertTrue(uploader.contains("AppleNetworkSSHTransport"))
        XCTAssertEqual(
            session.occurrences(of: "useTLS: host.usesTLSTunnel"),
            2,
            "the terminal and first-use probe must both honor the TLS setting"
        )
        XCTAssertTrue(uploader.contains("useTLS: host.usesTLSTunnel"))
        XCTAssertTrue(uploader.contains("if error is InvalidHostKey"))
        XCTAssertFalse(session.contains("ClientBootstrap("))
        XCTAssertFalse(uploader.contains("SSHClient.connect("))
        XCTAssertTrue(transport.contains("AppleNetworkOperationDeadline"))
        XCTAssertTrue(session.contains("AppleNetworkOperationDeadline"))
        XCTAssertTrue(uploader.contains("AppleNetworkOperationDeadline"))
        XCTAssertTrue(transport.contains("throw connectionError(error)"))
        XCTAssertTrue(transport.contains("guard deadline.complete() else"))
        XCTAssertTrue(uploader.contains("guard deadline.complete() else"))
        XCTAssertTrue(session.contains("AppleNetworkChannelCloser"))
        XCTAssertTrue(uploader.contains("AppleNetworkChannelCloser"))
        XCTAssertTrue(session.contains("withTaskCancellationHandler"))
        XCTAssertTrue(uploader.contains("withTaskCancellationHandler"))
        XCTAssertTrue(transport.contains("error is InvalidHostKey"))
        XCTAssertTrue(session.contains("NoCredentialSSHAuthenticationDelegate"))
        XCTAssertFalse(session.contains("userAuthDelegate: authentication.method"))
        XCTAssertTrue(
            session.contains("throw AppleNetworkSSHTransport.connectionError(error)"),
            "the first-use probe must preserve a connection-timeout result"
        )
        XCTAssertTrue(
            session.contains("guard ptyDeadline.complete() else"),
            "a timed-out PTY must not report a connected session"
        )
        XCTAssertFalse(
            transport.contains("error.localizedDescription, privacy:"),
            "transport diagnostics must not store endpoint-bearing error text"
        )
        XCTAssertFalse(
            uploader.contains("error.localizedDescription, privacy:"),
            "upload diagnostics must not store endpoint- or path-bearing error text"
        )

        let liveTest = try source(at: "Tests/SSHLiveIntegrationTests.swift")
        XCTAssertTrue(liveTest.contains("KEYJAWN_LIVE_SSH_TLS"))
        XCTAssertTrue(liveTest.contains("usesTLSTunnel: configuration.usesTLSTunnel"))
        XCTAssertTrue(
            liveTest.contains("&& rm -f --"),
            "the cleanup marker must require a successful delete command"
        )
        XCTAssertTrue(
            liveTest.contains("&& [ ! -e"),
            "the cleanup marker must require proof that the remote file is absent"
        )
        XCTAssertTrue(
            liveTest.contains("wc -c < \\(quotedPath)"),
            "the live test must measure the remote payload byte count"
        )
        XCTAssertTrue(
            liveTest.contains("-eq \\(payload.count)"),
            "the cleanup marker must require the exact uploaded byte count"
        )
        XCTAssertFalse(
            liveTest.contains("then printf '\\(cleanupMarker)"),
            "PTY echo must not contain the complete cleanup success marker"
        )
        XCTAssertFalse(
            liveTest.contains("else printf '\\(failureMarker)"),
            "PTY echo must not contain the complete cleanup failure marker"
        )
        XCTAssertTrue(liveTest.contains("KEYJAWN_LIVE_CLEANED"))

        let hostEditor = try source(at: "KeyJawn/Hosts/HostEditView.swift")
        XCTAssertTrue(hostEditor.contains("Toggle(\"TLS tunnel\""))
        XCTAssertTrue(
            hostEditor.contains(
                "TLS requires a hostname or IP address that matches the tunnel certificate."
            )
        )
        XCTAssertTrue(
            hostEditor.contains(
                "A plain ssh-keyscan command cannot connect through a TLS tunnel."
            )
        )

        let keyboard = try source(
            at: "KeyJawnKeyboard/KeyboardViewController.swift"
        )
        let terminalInput = try source(
            at: "KeyJawn/Terminal/TerminalInputView.swift"
        )
        for uploadSource in [keyboard, terminalInput] {
            XCTAssertTrue(uploadSource.contains("private var uploadTask: Task<Void, Never>?"))
            XCTAssertTrue(uploadSource.contains("uploadTask?.cancel()"))
            XCTAssertTrue(
                uploadSource.contains("guard !Task.isCancelled"),
                "a canceled upload must not insert its remote path"
            )
            XCTAssertTrue(
                uploadSource.contains(".hostKeyVerificationRequired"),
                "an unpinned SSH-key host needs its own repair instruction"
            )
            XCTAssertTrue(
                uploadSource.contains("panel.isDismissEnabled = false"),
                "the visible Cancel button must stop before remote writing starts"
            )
        }
    }

    func testShippingSourcesDoNotExposePrivateHostAliases() throws {
        let privateHostAliases = ["houseofjawn", "officejawn", "landofjawn"]

        for file in try shippingSwiftFiles() {
            let text = try String(contentsOf: file, encoding: .utf8).lowercased()
            for alias in privateHostAliases {
                XCTAssertFalse(
                    text.contains(alias),
                    "\(relativePath(of: file)) exposes private host alias \(alias)"
                )
            }
        }
    }

    private func source(at relativePath: String) throws -> String {
        try String(
            contentsOf: iosRoot.appendingPathComponent(relativePath),
            encoding: .utf8
        )
    }

    private func repositorySource(at relativePath: String) throws -> String {
        try String(
            contentsOf: repositoryRoot.appendingPathComponent(relativePath),
            encoding: .utf8
        )
    }

    private func appInfoDictionary() throws -> [String: Any] {
        try infoDictionary(at: "KeyJawn/Info.plist")
    }

    private func keyboardInfoDictionary() throws -> [String: Any] {
        try infoDictionary(at: "KeyJawnKeyboard/Info.plist")
    }

    private func infoDictionary(at relativePath: String) throws -> [String: Any] {
        let data = try Data(contentsOf: iosRoot.appendingPathComponent(relativePath))
        return try XCTUnwrap(
            PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any]
        )
    }

    private func shippingSwiftFiles() throws -> [URL] {
        let sourceDirectories = ["KeyJawn", "KeyJawnKeyboard", "KeyJawnKit/Sources", "Shared"]
            .map(iosRoot.appendingPathComponent)
        let files = sourceDirectories.flatMap { directory in
            guard
                let enumerator = FileManager.default.enumerator(
                    at: directory,
                    includingPropertiesForKeys: [.isRegularFileKey]
                )
            else {
                XCTFail("cannot enumerate \(directory.path)")
                return [URL]()
            }
            return enumerator.compactMap { $0 as? URL }.filter { $0.pathExtension == "swift" }
        }
        let paths = Set(files.map(relativePath))
        let requiredPaths = Set([
            "KeyJawn/App/ContentView.swift",
            "KeyJawnKeyboard/KeyboardViewController.swift",
            "KeyJawnKit/Sources/KeyJawnKit/Models/SSHIdentityKeyStore.swift",
            "Shared/CitadelSCPUploader.swift",
        ])
        XCTAssertTrue(
            requiredPaths.isSubset(of: paths),
            "shipping source scan is missing a required source root"
        )
        return files
    }

    private func relativePath(of file: URL) -> String {
        let canonicalRoot = iosRoot.resolvingSymlinksInPath().standardizedFileURL.path
        let canonicalFile = file.resolvingSymlinksInPath().standardizedFileURL.path
        return canonicalFile.replacingOccurrences(of: canonicalRoot + "/", with: "")
    }
}

extension String {
    fileprivate func occurrences(of value: String) -> Int {
        components(separatedBy: value).count - 1
    }
}
