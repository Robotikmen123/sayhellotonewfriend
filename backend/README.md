# Persona Backend (Cloudflare Worker)

Tek bir Worker üç işi yapar:

| Endpoint | Kim çağırır | Ne yapar |
|---|---|---|
| `POST /persona/upload` | sen (curl, build sonrası) | Persona dossier + voice_id yükler |
| `POST /memory/append` | iOS app, çağrı sonunda | Transcript özetler, embed eder, KV'ye yazar |
| `POST /llm/webhook` (alias: `/v1/chat/completions`) | ElevenLabs Conversational AI | Her turn'de Claude'a yönlendirir |

## Setup

```bash
cd backend
npm install
npx wrangler kv:namespace create PERSONA_KV
# çıktıdaki id'yi wrangler.toml'a yapıştır

npx wrangler secret put ANTHROPIC_API_KEY
npx wrangler secret put VOYAGE_API_KEY
npx wrangler secret put ELEVENLABS_WEBHOOK_TOKEN   # rastgele uzun string

npx wrangler deploy
```

## Persona yükleme

```bash
curl -X POST https://persona.<your-account>.workers.dev/persona/upload \
  -H "authorization: Bearer $ELEVENLABS_WEBHOOK_TOKEN" \
  -H "content-type: application/json" \
  -d "$(jq -n --arg d "$(cat ../persona_doc.md)" --arg v "$VOICE_ID" \
        '{user:"Ali", dossier:$d, voiceId:$v}')"
```

## ElevenLabs agent config

Conversational AI dashboard → agent → LLM → "Custom LLM":
- **Server URL:** `https://persona.<your-account>.workers.dev/v1/chat/completions`
- **API key:** `$ELEVENLABS_WEBHOOK_TOKEN`
- **Model id:** `claude-sonnet-4-6` (veya `claude-opus-4-7`)

Voice → `voiceId`'yi seç. System prompt'u BOŞ bırak (biz inject ediyoruz).
