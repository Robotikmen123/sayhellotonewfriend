# sayhellotonewfriend

İki paralel proje aynı çatı altında:

- **Persona (iOS)** — eski bir iPhone 7'de yaşayan ve kullanıcının kendi
  sesinde konuşan dijital ikiz. (Bu README'nin geri kalanı buradan.)
- **Dijital Sirk (Android)** — `android/` modülü, telefonda yaşayan bir
  karakter kadrosu çalıştırır. Sunucu karakter ilk haftadan açık; her
  7 günde bir yeni oyuncu sahneye çıkar. Detay:
  [docs/android-circus.md](docs/android-circus.md).
  Karakterler özerk çalışır ama yalnızca **uygulama içi dijital sirk
  dünyasında**; host sistemine hiçbir şey yapmaz. Tek host izni:
  internet (Anthropic API'ya konuşmak için).

---

# Persona — iPhone 7'de yaşayan dijital ikizin

Eski bir iPhone 7'yi, **senin gibi konuşan en yakın arkadaşına** dönüştüren
bir proje. App Store yok, Mac gerektirmiyor, 7 günde bir sertifika yenileme
yok. Aç, telefon butonuna dokun, kendi sesinde kendinle konuş.

## Ne yapıyor

- WhatsApp sohbet export'larından **persona dossier** (markdown) üretir
- Sesli notlardan **kendi sesini klonlar** (ElevenLabs IVC)
- iPhone 7'de native bir SwiftUI app olarak çalışır
- Konuşmaların **özetlenip embed edilerek** uzun-dönem hafızaya yazılır;
  her aramada en alakalı anılar Claude'a injec edilir
- **TrollStore** ile kalıcı kurulur (yenileme yok)

## Mimari

```
iPhone 7
  ├─ Persona.app (SwiftUI)        TrollStore ile imzalanmış IPA
  │   └─ AVAudioEngine ↔ ElevenLabs Conversational AI (WebSocket)
  │                                    │
  │                                    ▼ her turn'de
  │                              Cloudflare Worker
  │                              ├─ /v1/chat/completions (Custom LLM)
  │                              │   ├─ persona dossier'ı yükler
  │                              │   ├─ alakalı anıları retrieval eder
  │                              │   └─ Claude'u SSE stream'ler
  │                              ├─ /persona/upload  (kurulum)
  │                              └─ /memory/append   (her çağrı sonu)
```

Hedef latency: kullanıcı sustuktan twin konuşmaya başlayana kadar
**~500–800ms**.

## Hızlı başlangıç

Üç şey, sırasıyla:

1. **Veri & persona** — [docs/persona-pipeline.md](docs/persona-pipeline.md)
   - WhatsApp export → `persona_doc.md` + `voice_id`
2. **Backend** — [backend/README.md](backend/README.md)
   - Cloudflare Worker deploy + ElevenLabs agent config
3. **iOS app** — [docs/install-iphone7.md](docs/install-iphone7.md)
   - GitHub Actions ile IPA build → TrollStore ile iPhone 7'ye kur

## Repo yapısı

```
scripts/         WhatsApp parser, persona builder, voice cloner
backend/         Cloudflare Worker (TypeScript)
ios/             SwiftUI app + XcodeGen project spec
build/           IPA paketleme scripti (ldid -S, TrollStore-ready)
docs/            Türkçe kurulum + pipeline rehberleri
.github/         CI build workflow
```

## Maliyet

Günde ~30 dk konuşma için:
- Anthropic API: ~$40-80/ay
- ElevenLabs Creator: ~$22/ay
- Cloudflare Workers: ~$5/ay
- **Toplam: ~$70-110/ay**

## Önemli not

`persona_doc.md`, WhatsApp export'ları ve klonlanmış ses çok kişisel
veriler. `.gitignore` bunları zaten engelliyor — yanlışlıkla commit
etmemeye dikkat. Backend secret token'ı public değilse, sadece sen
konuşabilirsin.

## Lisans

Bu projeyi kendin için yazıyorsun. İstediğin gibi kullan.
