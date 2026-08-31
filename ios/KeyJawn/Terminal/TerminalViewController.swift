import UIKit
import SwiftTerm
import KeyJawnKit
import Combine

/// Full-screen terminal view controller.
///
/// Terminal resize is handled automatically: SwiftTerm fires `sizeChanged`
/// whenever it recomputes the grid, and we immediately forward that to the
/// PTY via SSHSession.resize(cols:rows:). A Combine sink sends the initial
/// resize once the SSH connection is established, correcting the hardcoded
/// 80×24 PTY request.
@MainActor
final class TerminalViewController: UIViewController {

    private var terminalView: TerminalView!
    private let inputSink = TerminalInputView()
    private let session: SSHSession

    // Latest terminal dimensions from SwiftTerm's sizeChanged callback.
    private var terminalCols = 80
    private var terminalRows = 24

    private var cancellables = Set<AnyCancellable>()

    // MARK: - Init

    init(session: SSHSession) {
        self.session = session
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("use init(session:)")
    }

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1)
        setupTerminal()
        setupInputSink()
        wireSession()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        inputSink.applyExtraRowPreset()
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        inputSink.cancelVoice()
        // Tear the SSH session down only when the terminal is actually going away
        // (a navigation pop or a modal dismissal), not when it is merely hidden.
        // HostTerminalView is a pushed destination inside the Hosts tab of a
        // TabView, so a plain tab switch also hides it; on a tab switch nothing is
        // removed from its parent, so isBeingRemoved stays false and the live
        // session survives. Without this the detached SSH pump task, the SwiftNIO
        // channel, and the remote shell would leak on every back-navigation (#43).
        // disconnect() is idempotent, so the toolbar Disconnect button having
        // fired first is fine.
        if isBeingRemoved {
            session.disconnect()
        }
    }

    /// True when this controller or any ancestor is leaving for good (a
    /// navigation pop or a modal dismissal), and false on a plain tab switch
    /// where the containers are only hidden.
    ///
    /// This controller is embedded in SwiftUI via UIViewControllerRepresentable,
    /// so it is a child of the destination's hosting controller rather than a
    /// direct child of the navigation controller. On a pop it is that
    /// hosting-controller ancestor that reports isMovingFromParent, not this
    /// represented child, whose own flag is unreliable across iOS versions.
    /// Walking the parent chain catches the removal wherever UIKit reports it,
    /// while a tab switch removes nothing and leaves every level false.
    private var isBeingRemoved: Bool {
        var controller: UIViewController? = self
        while let current = controller {
            if current.isMovingFromParent || current.isBeingDismissed {
                return true
            }
            controller = current.parent
        }
        return false
    }

    // MARK: - Setup

    private func setupTerminal() {
        terminalView = TerminalView(frame: .zero)
        terminalView.translatesAutoresizingMaskIntoConstraints = false
        terminalView.terminalDelegate = self
        view.addSubview(terminalView)

        NSLayoutConstraint.activate([
            terminalView.topAnchor.constraint(equalTo: view.topAnchor),
            terminalView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            terminalView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            terminalView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
    }

    private func setupInputSink() {
        inputSink.translatesAutoresizingMaskIntoConstraints = false
        inputSink.backgroundColor = .clear
        view.addSubview(inputSink)

        NSLayoutConstraint.activate([
            inputSink.topAnchor.constraint(equalTo: view.topAnchor),
            inputSink.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            inputSink.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            inputSink.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])

        inputSink.onRawInput = { [weak self] bytes in
            self?.session.send(bytes)
        }
    }

    private func wireSession() {
        session.onData = { [weak self] bytes in
            self?.terminalView.feed(byteArray: ArraySlice(bytes))
        }

        // When SSH connects, send the actual terminal dimensions to fix the
        // hardcoded 80×24 PTY. SwiftTerm's sizeChanged fires before the user
        // enters the password, so terminalCols/Rows are already correct here.
        session.$connectionState
            .receive(on: RunLoop.main)
            .sink { [weak self] state in
                guard let self, case .connected = state else { return }
                self.session.resize(cols: self.terminalCols, rows: self.terminalRows)
            }
            .store(in: &cancellables)
    }
}

// MARK: - TerminalViewDelegate

extension TerminalViewController: @preconcurrency TerminalViewDelegate {

    func sizeChanged(source: TerminalView, newCols: Int, newRows: Int) {
        terminalCols = newCols
        terminalRows = newRows
        // Forward live resize events (device rotation, split-view) to the PTY.
        session.resize(cols: newCols, rows: newRows)
    }

    func setTerminalTitle(source: TerminalView, title: String) {}
    func hostCurrentDirectoryUpdate(source: TerminalView, directory: String?) {}
    func scrolled(source: TerminalView, position: Double) {}
    func requestOpenLink(source: TerminalView, link: String, params: [String: String]) {}
    func bell(source: TerminalView) {}
    func clipboardCopy(source: TerminalView, content: Data) {}
    func rangeChanged(source: TerminalView, startY: Int, endY: Int) {}

    // User input enters through TerminalInputView. This callback carries replies
    // generated by the emulator for the remote terminal, such as status reports.
    func send(source: TerminalView, data: ArraySlice<UInt8>) {
        session.send(Array(data))
    }
}
