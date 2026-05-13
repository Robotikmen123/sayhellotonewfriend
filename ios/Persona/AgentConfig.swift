import Foundation

/// Compile-time configuration baked into the bundle. This is a single-user
/// app — there is no login, no settings screen, no remote config. Override
/// at build time via xcconfig or CI env vars (see build/sign-and-package.sh).
enum AgentConfig {
    /// ElevenLabs agent id (configure the agent to use our Cloudflare Worker
    /// as its custom LLM endpoint).
    static let agentId: String = readEnv("AGENT_ID") ?? "REPLACE_AGENT_ID"

    /// ElevenLabs public API key — only used to fetch a signed URL for the
    /// websocket. Scope it to the minimum needed permissions.
    static let elevenLabsApiKey: String = readEnv("ELEVENLABS_API_KEY") ?? "REPLACE_EL_KEY"

    /// Cloudflare Worker base URL.
    static let backendBaseURL: URL =
        URL(string: readEnv("BACKEND_URL") ?? "https://persona.example.workers.dev")!

    /// Shared bearer token used to call /memory/append.
    static let backendToken: String = readEnv("BACKEND_TOKEN") ?? "REPLACE_BACKEND_TOKEN"

    private static func readEnv(_ key: String) -> String? {
        if let v = Bundle.main.object(forInfoDictionaryKey: key) as? String,
           !v.isEmpty, !v.hasPrefix("REPLACE_") {
            return v
        }
        return nil
    }
}
