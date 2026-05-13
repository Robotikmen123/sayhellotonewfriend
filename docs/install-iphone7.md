# iPhone 7'ye kurulum (Mac yok, Bilgisayar yok)

Hedef: `Persona.ipa`'yı iPhone 7'ne kalıcı olarak yüklemek. 7 günde bir
yenileme yok, App Store yok, Mac yok. **TrollStore** kullanıyoruz.

## Önkoşullar

- **iPhone 7** ya da iPhone 7 Plus
- **iOS 15.0 – 15.8.x** (Ayarlar → Genel → Hakkında'dan kontrol et)
  - iOS 15.8.6'ya kadar destekleniyor
  - Daha yeni bir iOS varsa (16+) bu cihazda mümkün değil — ama iPhone 7
    zaten iOS 15'in üstüne çıkmaz, yani büyük ihtimalle sorun yok
- İnternet bağlantısı (WiFi yeterli)
- 10-15 dakika

## Adım 1: TrollStore'u kur

1. iPhone 7'de **Safari**'yi aç.
2. Adres çubuğuna **jailbreaks.app** yaz, git.
3. Listeden **TrollInstallerX**'i seç → **Install** butonuna bas.
4. Safari "Bu site bir yapılandırma profili indirmek istiyor" diye soracak.
   **İzin Ver**'e bas.
5. **Ayarlar → Genel → VPN ve Cihaz Yönetimi**'ne git.
6. İndirilen profili gör, üzerine bas, sağ üstte **Yükle** → şifreni gir →
   tekrar **Yükle** → **Bitti**.
7. Ana ekrana dön — **TrollInstallerX** uygulaması belirmiş olmalı.
8. TrollInstallerX'i aç → **Install TrollStore** butonuna bas.
9. Sallama olmadan bekle (1-3 dakika). Ekran bir kez beyazlayıp/respring
   atabilir, normal. Sonunda "Installed successfully" yazar.
10. Ana ekranda **TrollStore** uygulaması var artık.
11. TrollStore'u aç → ilk açılışta **persistence helper** seçmeni isteyecek.
    **Tips** (Türkçe: "İpuçları") gibi nadiren açtığın bir Apple uygulaması
    seç. Bu, TrollStore'un kalıcı olarak imzalı kalmasını sağlar.

Tebrikler — bu telefonda artık ömür boyu istediğin imzasız `.ipa`'yı
yükleyebilirsin.

## Adım 2: Persona.ipa'yı indir

`.ipa` dosyası, GitHub Actions çıktısı olarak veya repo'nun "Releases"
sayfasından geliyor.

1. iPhone'da Safari'yi aç, repo'nun GitHub sayfasına git:
   `https://github.com/robotikmen123/sayhellotonewfriend/releases`
2. En son release'i seç → **Persona.ipa**'ya dokun → "İndir"'e bas.
3. **Dosyalar** uygulamasını aç → **İndirilenler** klasörüne git.
4. `Persona.ipa`'yı orada gör.

(Alternatif: GitHub Actions sekmesinden artifact olarak da indirebilirsin
ama Releases daha rahat.)

## Adım 3: TrollStore ile kur

1. **Dosyalar** uygulamasında `Persona.ipa`'ya basılı tut.
2. Açılan menüden **Paylaş** → liste içinden **TrollStore**'u bul ve seç.
3. TrollStore açılır, "Install" butonuna bas.
4. 5-10 saniye sonra ana ekrana **Persona** uygulaması düşer.
5. Bitti.

## Adım 4: İlk açılış

1. Persona'yı aç.
2. Mikrofon izni iste — **İzin Ver**'e bas.
3. Yeşil telefon butonuna dokun.
4. Konuşmaya başla.

## Güncelleme yapmak istediğinde

Repo'da yeni release çıkınca:
1. Yeni `Persona.ipa`'yı yine Safari'den indir.
2. Dosyalar → ipa'ya basılı tut → Paylaş → TrollStore → Install.
3. TrollStore eski sürümün üzerine yazar. Veriler kaybolmaz.

## Sorun giderme

**"Bu app açılamaz"** — TrollStore'da persistence helper kurulmamış olabilir.
TrollStore'u aç → Settings → "Install Persistence Helper" → Tips seç.

**TrollInstallerX çakışıyor / başlamıyor** — jailbreaks.app yerine
**trollstore.app** sitesinden de aynı kurucu mevcut. Profil kurulumundan
önce eski profilleri sil (Ayarlar → Genel → VPN ve Cihaz Yönetimi).

**"Mikrofon çalışmıyor"** — Ayarlar → Persona → Mikrofon erişimini aç.

**"Persona açılır açılmaz hata veriyor"** — `AGENT_ID`, `BACKEND_URL`,
`ELEVENLABS_API_KEY`, `BACKEND_TOKEN` build sırasında bundle'a gömülmemiş
demektir. GitHub repo secrets'ı kontrol et, sonra Actions'tan yeniden
build et.

## Kalıcılık testi

Telefonu tamamen kapat, 8 gün bekle, aç, Persona'yı çalıştır — açılıyorsa
TrollStore doğru kurulmuş. (Normal sideload ile 7 günde ölürdü.)
