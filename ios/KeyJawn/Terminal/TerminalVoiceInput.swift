import AVFoundation
import Foundation
import Speech
import KeyJawnKit

/// Main-app speech recognizer. The keyboard extension must not instantiate this.
@MainActor
final class TerminalVoiceInput {
    var onCommit: ((String) -> Void)?
    var onError: ((String) -> Void)?
    var onListeningChange: ((Bool) -> Void)?

    private var session = VoiceSession()
    private var recognizer: SFSpeechRecognizer?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let engine = AVAudioEngine()

    var isListening: Bool { session.isListening }

    func toggle() {
        if session.isListening {
            finish(commit: true)
        } else {
            start()
        }
    }

    func cancel() {
        finish(commit: false)
    }

    private func start() {
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            Task { @MainActor in
                guard let self else { return }
                guard status == .authorized else {
                    self.onError?("Speech recognition is not allowed")
                    return
                }
                AVAudioApplication.requestRecordPermission { granted in
                    Task { @MainActor in
                        guard granted else {
                            self.onError?("Microphone is not allowed")
                            return
                        }
                        self.beginRecording()
                    }
                }
            }
        }
    }

    private func beginRecording() {
        finish(commit: false)
        recognizer = SFSpeechRecognizer()
        guard let recognizer, recognizer.isAvailable else {
            onError?("Speech recognition is not available")
            return
        }

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.request = request

        session.start()
        onListeningChange?(true)

        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self else { return }
                if let result {
                    self.session.updatePartial(result.bestTranscription.formattedString)
                    if result.isFinal {
                        self.finish(commit: true)
                    }
                } else if error != nil, self.session.isListening {
                    self.finish(commit: true)
                }
            }
        }

        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            self?.request?.append(buffer)
        }
        engine.prepare()
        do {
            try engine.start()
        } catch {
            onError?("Could not start the microphone")
            finish(commit: false)
        }
    }

    private func finish(commit: Bool) {
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.cancel()
        task = nil
        request = nil

        if commit, let text = session.commit() {
            onCommit?(text)
        } else {
            session.cancel()
        }
        onListeningChange?(false)
    }
}
