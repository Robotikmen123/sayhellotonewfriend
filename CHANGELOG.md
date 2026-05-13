# Changelog

## Unreleased

İlk uçtan uca dijital ikiz pipeline'ı:

- `scripts/` — WhatsApp export parser, Claude Opus ile persona dossier
  builder, ElevenLabs Instant Voice Cloning helper.
- `backend/` — Cloudflare Worker; ElevenLabs Conversational AI'nın çağıracağı
  OpenAI-uyumlu chat completions endpoint'i. Her turn'de persona dossier ile
  RAG ile çekilmiş alakalı anıları Claude Sonnet 4.6'ya inject eder ve
  yanıtı SSE olarak stream eder. Oturum sonu transcript'ini Voyage embeddings
  ile uzun dönem hafızaya yazar.
- `ios/Persona/` — SwiftUI app, iOS 15 deployment target (iPhone 7 uyumlu).
  AVAudioEngine ile 16 kHz PCM mic capture, ElevenLabs Conv-AI WebSocket
  istemcisi, AVAudioPlayerNode ile inbound TTS playback. Interruption/barge-in
  desteği. Tek ekran, tek buton arayüz.
- `build/sign-and-package.sh` — `xcodebuild archive` (unsigned) + `ldid -S` +
  `zip` → TrollStore-installable IPA. Apple Developer hesabı gerekmiyor.
- `.github/workflows/build-ipa.yml` — macOS-14 GitHub Actions runner üzerinde
  otomatik build, IPA artifact upload.
- `docs/install-iphone7.md` — Türkçe TrollStore kurulum rehberi.
- `docs/persona-pipeline.md` — WhatsApp export'tan IPA'ya kadar uçtan uca
  pipeline rehberi.
