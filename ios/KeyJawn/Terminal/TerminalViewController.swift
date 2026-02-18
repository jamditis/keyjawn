import UIKit
import SwiftTerm
import KeyJawnKit

/// Full-screen terminal view controller.
/// SwiftTerm's TerminalView renders VT100/ANSI output; a transparent
/// TerminalInputView overlay captures keyboard + extra row input.
/// Currently runs in local-echo mode — SSH via Citadel is the next step.
@MainActor
final class TerminalViewController: UIViewController {

    private var terminalView: TerminalView!
    private let inputSink = TerminalInputView()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1)
        setupTerminal()
        setupInputSink()
    }

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

        terminalView.feed(text: "KeyJawn — tap to open keyboard\r\n")
    }

    private func setupInputSink() {
        // Transparent overlay on top of TerminalView. Receives all taps and
        // keyboard events; routes raw bytes to SwiftTerm.
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
            // Local echo: feed bytes back to the terminal renderer.
            // Replace this block with SSH channel write when wiring Citadel.
            self.terminalView.feed(byteArray: ArraySlice(bytes))
        }
    }
}

// MARK: - TerminalViewDelegate

extension TerminalViewController: @preconcurrency TerminalViewDelegate {
    func sizeChanged(source: TerminalView, newCols: Int, newRows: Int) {
        // TODO: notify SSH channel of new window size
    }
    func setTerminalTitle(source: TerminalView, title: String) {}
    func hostCurrentDirectoryUpdate(source: TerminalView, directory: String?) {}
    func scrolled(source: TerminalView, position: Double) {}
    func requestOpenLink(source: TerminalView, link: String, params: [String: String]) {}
    func bell(source: TerminalView) {}
    func clipboardCopy(source: TerminalView, content: Data) {}
    func rangeChanged(source: TerminalView, startY: Int, endY: Int) {}

    func send(source: TerminalView, data: ArraySlice<UInt8>) {
        // Called if SwiftTerm somehow captures keyboard input directly.
        // Route through local echo / SSH the same way.
        terminalView.feed(byteArray: data)
    }
}
