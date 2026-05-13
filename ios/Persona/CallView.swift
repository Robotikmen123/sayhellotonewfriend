import SwiftUI

struct CallView: View {
    @StateObject private var client = VoiceAgentClient()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 32) {
                Spacer()

                WaveformView(level: client.level, color: stateColor)
                    .frame(height: 220)
                    .padding(.horizontal, 40)

                Text(stateLabel)
                    .font(.system(size: 18, weight: .medium, design: .rounded))
                    .foregroundColor(.gray)
                    .animation(.easeInOut, value: client.state)

                Spacer()

                Button(action: toggle) {
                    Image(systemName: client.state == .idle ? "phone.fill" : "phone.down.fill")
                        .font(.system(size: 36, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 96, height: 96)
                        .background(buttonColor)
                        .clipShape(Circle())
                        .shadow(color: buttonColor.opacity(0.5), radius: 20)
                }
                .padding(.bottom, 60)
            }
        }
    }

    private func toggle() {
        switch client.state {
        case .idle, .error:
            client.start()
        case .connecting, .listening, .speaking:
            client.stop()
        }
    }

    private var stateLabel: String {
        switch client.state {
        case .idle: return "Aramak için dokun"
        case .connecting: return "Bağlanıyor…"
        case .listening: return "Dinliyor"
        case .speaking: return "Konuşuyor"
        case .error(let msg): return msg
        }
    }

    private var stateColor: Color {
        switch client.state {
        case .listening: return .green
        case .speaking: return .blue
        case .connecting: return .yellow
        case .error: return .red
        case .idle: return .gray
        }
    }

    private var buttonColor: Color {
        client.state == .idle ? Color.green : Color.red
    }
}

/// Minimal audio waveform driven by a 0…1 level value the client publishes.
struct WaveformView: View {
    var level: Float
    var color: Color

    var body: some View {
        GeometryReader { geo in
            HStack(spacing: 4) {
                ForEach(0..<28, id: \.self) { i in
                    Capsule()
                        .fill(color)
                        .frame(width: 6, height: barHeight(for: i, in: geo.size.height))
                        .animation(.easeOut(duration: 0.12), value: level)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func barHeight(for i: Int, in maxH: CGFloat) -> CGFloat {
        // Center-weighted curve so the middle bars dance more than the edges.
        let dist = abs(Double(i) - 13.5) / 13.5
        let envelope = 1.0 - pow(dist, 1.4)
        let baseline: CGFloat = 8
        return max(baseline, CGFloat(envelope) * CGFloat(level) * maxH)
    }
}
