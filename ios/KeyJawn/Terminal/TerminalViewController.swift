import UIKit
import SwiftTerm
import KeyJawnKit

/// Full-screen terminal view controller.
///
/// When `session` is nil, runs in local-echo mode (useful for testing the
/// keyboard extension without an SSH connection). When a session is provided,
/// all input is routed to the SSH channel and all output from the server is
/// fed into SwiftTerm.
@MainActor
final class TerminalViewController: UIViewController {

    private var terminalView: TerminalView!
    private let inputSink = TerminalInputView()
    private let session: SSHSession?

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
                // SSH mode: send bytes to the remote shell
                session.send(bytes)
            } else {
                // Local echo mode: show input directly in the terminal
                self.terminalView.feed(byteArray: ArraySlice(bytes))
            }
        }
    }

    private func wireSession() {
        guard let session else { return }
        session.onData = { [weak self] bytes in
            self?.terminalView.feed(byteArray: ArraySlice(bytes))
        }
    }
}

// MARK: - TerminalViewDelegate

extension TerminalViewController: @preconcurrency TerminalViewDelegate {
    func sizeChanged(source: TerminalView, newCols: Int, newRows: Int) {}
    func setTerminalTitle(source: TerminalView, title: String) {}
    func hostCurrentDirectoryUpdate(source: TerminalView, directory: String?) {}
    func scrolled(source: TerminalView, position: Double) {}
    func requestOpenLink(source: TerminalView, link: String, params: [String: String]) {}
    func bell(source: TerminalView) {}
    func clipboardCopy(source: TerminalView, content: Data) {}
    func rangeChanged(source: TerminalView, startY: Int, endY: Int) {}

    func send(source: TerminalView, data: ArraySlice<UInt8>) {
        // SwiftTerm captured keyboard input directly — route it the same way
        if let session {
            session.send(Array(data))
        } else {
            terminalView.feed(byteArray: data)
        }
    }
}
