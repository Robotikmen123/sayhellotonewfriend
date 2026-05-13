# Persona pipeline (veri → digital twin)

İki ayrı ürün çıkıyor bu pipeline'dan:

1. **`persona_doc.md`** — sen nasıl konuşuyorsan onu tarif eden bir markdown
   dosyası. Backend her turn'de bunu Claude'a system prompt olarak veriyor.
2. **`voice_id`** — ElevenLabs Instant Voice Cloning ile yapılmış, senin
   kendi sesinden klonlanmış bir ses kimliği.

## Hazırlık

```bash
# Python script'leri için
pip install -r scripts/requirements.txt
# ffmpeg, jq, curl, unzip kurulu olmalı (macOS: brew install ffmpeg jq)
```

## Adım 1: WhatsApp sohbetlerini export et (iPhone'dan)

iPhone'da WhatsApp'ı aç, en yoğun yazıştığın **5-10 sohbet** için:

1. Sohbeti aç
2. Üstteki kişi/grup ismine bas → en alta in
3. **Sohbeti Dışa Aktar** → **Medyayı Ekle** seç
4. **Dosyalar**'a kaydet (iCloud Drive klasörü uygun)

Çıktılar: `WhatsApp Chat - <isim>.zip`

Bu zip'lerin hepsini bir klasöre topla, örneğin `~/Desktop/exports/`.

## Adım 2: Mesajları parse et

```bash
python scripts/extract_whatsapp.py \
    --me "Ali Yilmaz" \
    --out data/messages.jsonl \
    ~/Desktop/exports/*.zip
```

`--me`'yi WhatsApp'ta gözüktüğü ŞEKİLDE yaz (boşluk, büyük/küçük harf aynı
olsun). Eğer "no messages matched --me" hatası gelirse, bir `.txt`'yi açıp
ismini kopyala.

## Adım 3: Persona dossier'ı üret

```bash
export ANTHROPIC_API_KEY=sk-ant-...
python scripts/build_persona.py \
    --in data/messages.jsonl \
    --name "Ali" \
    --out persona_doc.md
```

Bu adım Claude Opus'u çağırıyor, ~30 saniye sürüyor, ~$0.30 maliyet.

Çıktıyı **mutlaka oku** ve gerekirse elle düzelt. Bu dosya digital twin'in
beynidir. Yanlış bir şey varsa, twin yanlış olur.

## Adım 4: Sesini klonla

```bash
export ELEVENLABS_API_KEY=...
./scripts/clone_from_whatsapp.sh \
    --exports ~/Desktop/exports \
    --name "Ali (twin)"
```

Bu script:
- Tüm zip'lerden `.m4a` ses notlarını çıkarır
- En uzun 20 tanesini birleştirir
- Sessizlikleri kırpar, normalize eder
- ElevenLabs Voice Isolator'dan geçirir (arka plan gürültüsü temizler)
- Instant Voice Cloning'e yükler
- Sonunda `voice_id`'yi stdout'a basar

`voice_id`'yi bir kenara not et. Hiç ses notun yoksa, telefonla 1-2 dakika
konuş, sesi `voicenote.m4a` olarak kaydet, scripti `--exports` yerine
elle bir zip'leyip ver veya direkt elevenlabs.io üzerinden upload et.

## Adım 5: Backend'e yükle

```bash
curl -X POST "$BACKEND_URL/persona/upload" \
    -H "authorization: Bearer $BACKEND_TOKEN" \
    -H "content-type: application/json" \
    -d "$(jq -n \
        --arg d "$(cat persona_doc.md)" \
        --arg v "$VOICE_ID" \
        '{user: "Ali", dossier: $d, voiceId: $v}')"
```

## Adım 6: ElevenLabs agent'ı yapılandır

[elevenlabs.io](https://elevenlabs.io) → Conversational AI → Create Agent:

| Alan | Değer |
|---|---|
| Agent name | Ali (twin) |
| Voice | Adım 4'teki klon |
| LLM | "Custom LLM" |
| Server URL | `https://persona.<account>.workers.dev/v1/chat/completions` |
| Model id | `claude-sonnet-4-6` |
| API key | `$BACKEND_TOKEN` |
| System prompt | **BOŞ BIRAK** (backend kendi prompt'unu inject ediyor) |
| First message | "alo" (veya boş bırak) |
| Turn detection | varsayılan (server VAD) |
| Language | Turkish |
| TTS model | `eleven_flash_v2_5` |

"Save" → Agent'ı yayınla. Agent settings'in en üstündeki **agent id**'yi
not et. Bunu GitHub repo secrets'a `AGENT_ID` olarak ekle.

## Adım 7: iOS app'i build et

GitHub repo settings → Secrets → Actions → şunları ekle:

- `AGENT_ID` — adım 6'daki
- `ELEVENLABS_API_KEY` — get-signed-url çağrısı için
- `BACKEND_URL` — Worker URL'i
- `BACKEND_TOKEN` — shared bearer token

Sonra Actions → "build-ipa" workflow'unu çalıştır. `Persona.ipa` artifact
olarak gelir. Telefona indirip TrollStore ile kur (bkz.
[install-iphone7.md](install-iphone7.md)).

## Persona tuning loop

İlk hafta her gün:
1. 10 dakika konuş
2. `persona_doc.md`'yi aç, "burada ben böyle demezdim" yerleri işaretle
3. Aşağıdaki bölümleri güncelle:
   - "Sık kullanılan kalıplar" — eksik buldukların ekle
   - "ASLA şöyle konuşma" — twin'in yapay-zekâ klişesine kaçtığı momentler
   - "Birebir örnek mesajlar" — yenilerini ekle
4. Adım 5'i tekrar çalıştır (yeniden upload)
5. Yeniden konuş

3-4 iterasyondan sonra ayırt edemez hale gelir.
