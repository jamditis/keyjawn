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
    private var generation = 0
    private var awaitingFinal = false

    var isListening: Bool { session.isListening }

    func toggle() {
        if session.isListening {
            stopListening(commit: true)
        } else {
            start()
        }
    }

    func cancel() {
        stopListening(commit: false)
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
        stopListening(commit: false)
        recognizer = SFSpeechRecognizer()
        guard let recognizer, recognizer.isAvailable else {
            onError?("Speech recognition is not available")
            return
        }

        do {
            let audio = AVAudioSession.sharedInstance()
            try audio.setCategory(.record, mode: .measurement, options: .duckOthers)
            try audio.setActive(true)
        } catch {
            onError?("Could not start the microphone")
            return
        }

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.request = request

        generation += 1
        let started = generation
        session.start()
        awaitingFinal = false
        onListeningChange?(true)

        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self, self.generation == started else { return }
                if let result {
                    self.session.updatePartial(result.bestTranscription.formattedString)
                    if result.isFinal {
                        self.emitCommit()
                    }
                } else if error != nil, self.awaitingFinal {
                    self.emitCommit()
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
            stopListening(commit: false)
        }
    }

    private func stopListening(commit: Bool) {
        if engine.isRunning {
            engine.stop()
        }
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        if commit, session.isListening {
            awaitingFinal = true
            onListeningChange?(false)
            return
        }

        generation += 1
        awaitingFinal = false
        task?.cancel()
        task = nil
        request = nil
        session.cancel()
        onListeningChange?(false)
    }

    private func emitCommit() {
        awaitingFinal = false
        task = nil
        request = nil
        if let text = session.commit() {
            onCommit?(text)
        }
        onListeningChange?(false)
    }
}
