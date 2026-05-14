# Dijital Sirk — Android APK kurulum & kullanım

`android/` modülü, telefonda yaşayan bir karakter kadrosunu çalıştırır.
Caine ilk haftadan itibaren açık; her 7 günde bir yeni oyuncu sahneye
çıkar (Kinger → Ragatha → Gangle → Jax → Zooble → Pomni).

## Güvenlik modeli — en önce bu

- Tek host izni: `INTERNET` (Anthropic API'ya konuşmak için).
- Karakter "sahne dışı" bir şey yapmak istediğinde gösterilen onay
  ekranı **tamamen rol gereği**. Hiçbir `Intent` gönderilmez, hiçbir
  dosya okunup yazılmaz, ses kaydı alınmaz.
- Onaylar `data/RoleplayConsentLedger.kt` içine yazılır; bu kayıt
  sadece "hangi rol-play sahnesini açtın?" amaçlıdır.
- API anahtarı yalnızca DataStore'da saklanır, yedeklere dahil
  edilmez (`data_extraction_rules.xml`).

## Derleme

```bash
cd android
./gradlew :app:assembleDebug
# çıktı: app/build/outputs/apk/debug/app-debug.apk
```

Cihaza yüklemek için `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Release imzalama için `app/build.gradle.kts`'e bir `signingConfigs`
bloğu ekleyip `release` build type'a bağla; sonra `./gradlew :app:assembleRelease`.

## İlk açılış

1. **Ayarlar** sekmesi → Anthropic API anahtarını yapıştır. Model
   varsayılan olarak `claude-opus-4-7`; istersen daha hafif bir model
   yaz.
2. **Sohbet** sekmesi → Caine açılışı söyler. Türkçe konuş.
3. **Kadro** sekmesi → kilitli karakterleri ve sahneye çıkış tarihlerini
   gör. Bir karaktere "Konuş" dersen aktif karakter o olur.
4. **Dünya** sekmesi → kadronun arka planda kendi başına ürettiği küçük
   sahnelerin akışı. "Caine'a sahne aç" butonu, anlık bir replik ister.
5. **Sahne** sekmesi → aktif karakterin 3D modelini .zip olarak yükle.

## 3D modelleri yerleştirme

İki yol var:

**A) Sahne ekranından yükle (kolay)**

Sahne sekmesinde "${karakter} için .zip seç" butonu. Seçtiğin zip:
- Sadece `filesDir/models/<karakter-id>/` altına çıkarılır
- Zip-slip korumalı, max 64 MB, max 200 dosya
- İçinde `.glb` veya `.gltf` yoksa reddedilir

**B) Assets'e gömerek** (paketle dağıtacaksan)

```
android/app/src/main/assets/models/
  caine.zip
  kinger.zip
  ragatha.zip
  gangle.zip
  jax.zip
  zooble.zip
  pomni.zip
```

`CircusCharacter.modelAssetCandidates` zaten bu yolları kontrol
edecek şekilde tanımlı; `StageScreen` için tek yapman gereken aynı isimde
ek bir helper yazıp `assets`'ten `filesDir`'e kopyalamak.

## Haftalık kilit nasıl çalışıyor

- Uygulamanın ilk açılışı `firstLaunchAt` olarak DataStore'a yazılır.
- `UnlockManager.currentWeek()` = `(şimdi − firstLaunchAt) / 7gün`.
- `CharacterRegistry.all` listesindeki her karakterin `unlockWeek`'i
  bu sayıdan küçükse açıktır.
- Sıfırlamak için: Ayarlar → "Haftalık kilitleri sıfırla". İlk açılış
  zamanı silinir, bir sonraki açılışta tekrar Caine'ten başlar.

## Kişilikleri özelleştirmek

`character/Personalities.kt` içindeki yedi prompt birer **vibe sheet**.
Yapı şöyle:

- `PROMPT_CONTRACT` → tüm karakterler için ortak güvenlik kuralları
  (host'a dokunma, sahne içi kal, Türkçe konuş, vs.).
- `brief(role, voice, quirks, autonomousMood)` → bir karakterin
  arketipini özetleyen kısa parçalar; üretilen system prompt bunları
  birleştirir.

Hangisini istersen baştan yaz, eski wording'lere bağlı değilsin.

## Dünya simülatörü

`ai/WorldSimulator.kt` `run()` fonksiyonu sonsuz döngüdür; her 6-14
dakikada bir rastgele açık karakterden 1-3 cümlelik "kendi başına
yaptığı şey" üretir. Şu an `WorldScreen`'deki "Caine’a sahne aç" ve
"Rastgele oyuncu" butonları manuel `nudge()` çağırıyor. Otomatik
çalıştırma istersen:

- `WorkManager` ile `PeriodicWorkRequest(repeatInterval = 30min)`
  içine `simulator.nudge(random())` koy
- ya da bir `Service` içinde `simulator.run()`'ı bir coroutine
  scope'unda başlat.

İkisi de host'a dokunmaz — sadece DataStore'a yazar.

## Sınırlar

- Sesli konuşma yok (henüz). iOS Persona modülündeki ElevenLabs
  pipeline'ı burada kullanılmıyor; isterse aynı Cloudflare Worker'a
  bir `/circus/chat` endpoint'i eklenebilir.
- SceneView Compose entegrasyonu placeholder; model dosyası diske
  iniyor ama henüz render edilmiyor. Bir `Scene { Model(filePath) }`
  Compose çağrısı eklenince çalışır.
- Karakterlerin kendi aralarındaki çapraz konuşmaları (Caine + Jax
  birlikte) bu sürümde yok; tek aktif karakterle konuşuyorsun.
