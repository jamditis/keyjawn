import UIKit
import SwiftTerm
import KeyJawnKit
import Combine

/// Full-screen terminal view controller.
///
/// When `session` is nil, runs in local-echo mode (for testing without SSH).
/// When a session is provided, all input routes to the SSH channel and all
/// server output is fed into SwiftTerm.
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
    private let session: SSHSession?

    // Latest terminal dimensions from SwiftTerm's sizeChanged callback.
    private var terminalCols = 80
    private var terminalRows = 24

    private var cancellables = Set<AnyCancellable>()

    // MARK: - Init

    init(session: SSHSession? = nil) {
        self.session = session
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        self.session = nil
        super.init(coder: coder)
    }

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1)
        setupTerminal()
        setupInputSink()
        wireSession()
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

        if session == nil {
            terminalView.feed(text: "KeyJawn — tap to open keyboard\r\n")
        }
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
            guard let self else { return }
            if let session = self.session {
                session.send(bytes)
            } else {
                self.terminalView.feed(byteArray: ArraySlice(bytes))
            }
        }
    }

    private func wireSession() {
        guard let session else { return }

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
                self.session?.resize(cols: self.terminalCols, rows: self.terminalRows)
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
        session?.resize(cols: newCols, rows: newRows)
    }

    func setTerminalTitle(source: TerminalView, title: String) {}
    func hostCurrentDirectoryUpdate(source: TerminalView, directory: String?) {}
    func scrolled(source: TerminalView, position: Double) {}
    func requestOpenLink(source: TerminalView, link: String, params: [String: String]) {}
    func bell(source: TerminalView) {}
    func clipboardCopy(source: TerminalView, content: Data) {}
    func rangeChanged(source: TerminalView, startY: Int, endY: Int) {}

    func send(source: TerminalView, data: ArraySlice<UInt8>) {
        // In SSH mode all input flows through TerminalInputView, so we ignore
        // this delegate callback to avoid double-sending to the SSH channel.
        guard session == nil else { return }
        terminalView.feed(byteArray: data)
    }
}
