import AVFoundation
import Combine
import Foundation

enum CallState: Equatable {
    case idle
    case connecting
    case listening
    case speaking
    case error(String)
}

/// Speaks to ElevenLabs Conversational AI over a single WebSocket:
///   - Mic capture at 16 kHz mono PCM16 → base64 → `user_audio_chunk` events
///   - Inbound `audio` events (base64 PCM16 @ ElevenLabs default sample rate)
///     are decoded and scheduled on an AVAudioPlayerNode
///   - `interruption` events stop playback immediately
///   - When the call ends, the accumulated transcript is POSTed to the
///     backend so long-term memory gets one more chunk.
@MainActor
final class VoiceAgentClient: NSObject, ObservableObject {
    @Published private(set) var state: CallState = .idle
    @Published private(set) var level: Float = 0  // 0…1, mic RMS for UI

    private let inputSampleRate: Double = 16_000
    private let outputSampleRate: Double = 16_000  // ElevenLabs default; agent can configure

    private let engine = AVAudioEngine()
    private let playerNode = AVAudioPlayerNode()
    private var playerFormat: AVAudioFormat!
    private var inputConverter: AVAudioConverter?

    private var ws: URLSessionWebSocketTask?
    private var urlSession: URLSession!
    private var lastEventId: Int = 0
    private var transcript: [TranscriptTurn] = []
    private var stopOnce: Bool = false

    override init() {
        super.init()
        urlSession = URLSession(
            configuration: .default,
            delegate: self,
            delegateQueue: nil,
        )
        playerFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: outputSampleRate,
            channels: 1,
            interleaved: true,
        )
    }

    // MARK: - Public

    func start() {
        guard state == .idle || isErrorState else { return }
        stopOnce = false
        transcript.removeAll()
        state = .connecting
        Task { await connect() }
    }

    func stop() {
        guard !stopOnce else { return }
        stopOnce = true
        cleanupAudio()
        ws?.cancel(with: .goingAway, reason: nil)
        ws = nil
        let toUpload = transcript
        transcript.removeAll()
        state = .idle
        if !toUpload.isEmpty {
            Task.detached { await postTranscript(toUpload) }
        }
    }

    // MARK: - Connection

    private func connect() async {
        do {
            let url = try await fetchSignedURL()
            try configureAudioSession()

            var req = URLRequest(url: url)
            req.timeoutInterval = 30
            let task = urlSession.webSocketTask(with: req)
            self.ws = task
            task.resume()

            try startCapture()
            state = .listening
            await receiveLoop(task: task)
        } catch {
            state = .error("Bağlanamadı: \(error.localizedDescription)")
            cleanupAudio()
        }
    }

    private func fetchSignedURL() async throws -> URL {
        // ElevenLabs gives a short-lived signed URL we use to connect.
        let api = "https://api.elevenlabs.io/v1/convai/conversation/get-signed-url?agent_id=\(AgentConfig.agentId)"
        var req = URLRequest(url: URL(string: api)!)
        req.setValue(AgentConfig.elevenLabsApiKey, forHTTPHeaderField: "xi-api-key")
        let (data, resp) = try await urlSession.data(for: req)
        guard (resp as? HTTPURLResponse)?.statusCode == 200 else {
            throw NSError(
                domain: "Persona",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "signed url failed"],
            )
        }
        struct SignedURL: Decodable { let signed_url: String }
        let parsed = try JSONDecoder().decode(SignedURL.self, from: data)
        guard let url = URL(string: parsed.signed_url) else {
            throw NSError(domain: "Persona", code: 2)
        }
        return url
    }

    // MARK: - Audio

    private func configureAudioSession() throws {
        let s = AVAudioSession.sharedInstance()
        try s.setCategory(
            .playAndRecord,
            mode: .voiceChat,
            options: [.allowBluetooth, .defaultToSpeaker],
        )
        try s.setPreferredSampleRate(inputSampleRate)
        try s.setPreferredIOBufferDuration(0.02)
        try s.setActive(true, options: [])
    }

    private func startCapture() throws {
        engine.attach(playerNode)
        engine.connect(playerNode, to: engine.outputNode, format: playerFormat)

        let input = engine.inputNode
        let hwFormat = input.outputFormat(forBus: 0)
        let targetFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: inputSampleRate,
            channels: 1,
            interleaved: true,
        )!
        inputConverter = AVAudioConverter(from: hwFormat, to: targetFormat)

        input.installTap(onBus: 0, bufferSize: 1024, format: hwFormat) { [weak self] buf, _ in
            guard let self = self else { return }
            self.handleMic(buffer: buf, target: targetFormat)
        }

        engine.prepare()
        try engine.start()
        playerNode.play()
    }

    private nonisolated func handleMic(
        buffer hwBuffer: AVAudioPCMBuffer,
        target: AVAudioFormat,
    ) {
        // Compute RMS for the UI level meter — cheap, on the audio thread.
        let rms = Self.computeRMS(hwBuffer)
        Task { @MainActor in self.level = min(1, rms * 4) }

        // Convert to PCM16 @ 16 kHz mono.
        let ratio = target.sampleRate / hwBuffer.format.sampleRate
        let outCapacity = AVAudioFrameCount(Double(hwBuffer.frameLength) * ratio) + 16
        guard
            let outBuf = AVAudioPCMBuffer(
                pcmFormat: target,
                frameCapacity: outCapacity,
            )
        else { return }

        Task { @MainActor in
            guard let converter = self.inputConverter else { return }
            var supplied = false
            var error: NSError?
            let status = converter.convert(to: outBuf, error: &error) { _, outStatus in
                if supplied {
                    outStatus.pointee = .endOfStream
                    return nil
                }
                supplied = true
                outStatus.pointee = .haveData
                return hwBuffer
            }
            if status == .error || error != nil { return }
            self.sendUserAudio(outBuf)
        }
    }

    private static func computeRMS(_ buf: AVAudioPCMBuffer) -> Float {
        guard let ch = buf.floatChannelData?[0] else {
            // Fall back to int16 if needed.
            if let i16 = buf.int16ChannelData?[0] {
                var sum: Float = 0
                let n = Int(buf.frameLength)
                for i in 0..<n {
                    let s = Float(i16[i]) / 32768.0
                    sum += s * s
                }
                return sqrt(sum / Float(max(1, n)))
            }
            return 0
        }
        var sum: Float = 0
        let n = Int(buf.frameLength)
        for i in 0..<n { sum += ch[i] * ch[i] }
        return sqrt(sum / Float(max(1, n)))
    }

    private func sendUserAudio(_ buf: AVAudioPCMBuffer) {
        guard
            let ws = ws,
            let i16 = buf.int16ChannelData?[0]
        else { return }
        let n = Int(buf.frameLength)
        let data = Data(
            bytes: i16,
            count: n * MemoryLayout<Int16>.size,
        )
        let b64 = data.base64EncodedString()
        let payload: [String: Any] = ["user_audio_chunk": b64]
        guard
            let json = try? JSONSerialization.data(withJSONObject: payload),
            let str = String(data: json, encoding: .utf8)
        else { return }
        ws.send(.string(str)) { _ in }
    }

    private func cleanupAudio() {
        if engine.isRunning {
            engine.inputNode.removeTap(onBus: 0)
            playerNode.stop()
            engine.stop()
        }
        try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
        level = 0
    }

    // MARK: - WebSocket receive

    private func receiveLoop(task: URLSessionWebSocketTask) async {
        while !stopOnce {
            do {
                let msg = try await task.receive()
                switch msg {
                case .string(let s):
                    handleEvent(s)
                case .data(let d):
                    if let s = String(data: d, encoding: .utf8) { handleEvent(s) }
                @unknown default: break
                }
            } catch {
                if !stopOnce {
                    state = .error("Bağlantı koptu")
                }
                return
            }
        }
    }

    private func handleEvent(_ raw: String) {
        guard
            let data = raw.data(using: .utf8),
            let any = try? JSONSerialization.jsonObject(with: data),
            let obj = any as? [String: Any]
        else { return }

        let type = obj["type"] as? String

        if let audioEvent = obj["audio_event"] as? [String: Any],
           let b64 = audioEvent["audio_base_64"] as? String {
            scheduleAudio(b64: b64)
            state = .speaking
            return
        }

        if let interruption = obj["interruption_event"] as? [String: Any] {
            _ = interruption
            interruptPlayback()
            state = .listening
            return
        }

        if let userTranscript = obj["user_transcription_event"] as? [String: Any],
           let text = userTranscript["user_transcript"] as? String {
            transcript.append(TranscriptTurn(role: .user, text: text))
            return
        }

        if let agentResponse = obj["agent_response_event"] as? [String: Any],
           let text = agentResponse["agent_response"] as? String {
            transcript.append(TranscriptTurn(role: .agent, text: text))
            return
        }

        if let ping = obj["ping_event"] as? [String: Any],
           let id = ping["event_id"] as? Int {
            let pong: [String: Any] = ["type": "pong", "event_id": id]
            if
                let json = try? JSONSerialization.data(withJSONObject: pong),
                let str = String(data: json, encoding: .utf8)
            {
                ws?.send(.string(str)) { _ in }
            }
            return
        }

        if type == "conversation_initiation_metadata" {
            // Server told us the chosen audio format etc. We assume PCM 16k.
            return
        }
    }

    private func scheduleAudio(b64: String) {
        guard
            let data = Data(base64Encoded: b64),
            let buf = pcm16Buffer(from: data)
        else { return }
        playerNode.scheduleBuffer(buf, at: nil, options: [])
        if !playerNode.isPlaying { playerNode.play() }
    }

    private func pcm16Buffer(from raw: Data) -> AVAudioPCMBuffer? {
        let frameCount = AVAudioFrameCount(raw.count / MemoryLayout<Int16>.size)
        guard
            let buf = AVAudioPCMBuffer(pcmFormat: playerFormat, frameCapacity: frameCount),
            let dst = buf.int16ChannelData?[0]
        else { return nil }
        buf.frameLength = frameCount
        raw.withUnsafeBytes { (ptr: UnsafeRawBufferPointer) in
            guard let src = ptr.bindMemory(to: Int16.self).baseAddress else { return }
            dst.update(from: src, count: Int(frameCount))
        }
        return buf
    }

    private func interruptPlayback() {
        playerNode.stop()
        playerNode.play()
    }

    private var isErrorState: Bool {
        if case .error = state { return true }
        return false
    }
}

extension VoiceAgentClient: URLSessionDelegate, URLSessionWebSocketDelegate {}

private struct TranscriptTurn: Codable {
    enum Role: String, Codable { case user, agent }
    let role: Role
    let text: String
}

private func postTranscript(_ turns: [TranscriptTurn]) async {
    let body = turns
        .map { ($0.role == .user ? "ben: " : "twin: ") + $0.text }
        .joined(separator: "\n")
    var req = URLRequest(
        url: AgentConfig.backendBaseURL.appendingPathComponent("memory/append"),
    )
    req.httpMethod = "POST"
    req.setValue("Bearer \(AgentConfig.backendToken)", forHTTPHeaderField: "authorization")
    req.setValue("application/json", forHTTPHeaderField: "content-type")
    let payload: [String: Any] = ["transcript": body]
    req.httpBody = try? JSONSerialization.data(withJSONObject: payload)
    _ = try? await URLSession.shared.data(for: req)
}
