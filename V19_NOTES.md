# V19 — Erken uyarı ve kapanış teyidi

- Erken uyarı: en az 3 gözlem, stabilite >= 60 ve yön uyumu >= %65; mevcut yapı/tetik kapıları ve V18 hotfix puan eşikleri korunur. İşlem izni vermez.
- Temiz işlem: en az 5 gözlem, stabilite >= 68, yön uyumu >= %65 ve son iki kapanış aynı yönde. Önceki daha seçici stabilite ceza basamakları kullanılır.
- Stabilite eksik, yönü uyumsuz veya sayısal olarak geçersizse temiz işlem verilmez. 3–4 gözlem yalnız erken uyarı üretebilir.
- Güncel günlük mum, veri sağlayıcının o güne ait seans sonundan 15 dakika sonra kullanılır. Seans bilgisi yoksa yalnız önceki günler kullanılır. Gelecek günlerin mumları dışlanır.
- Tarama kartları analiz edilen kapanış tarihini ve ERKEN UYARI / VERİ YETERSİZ durumlarını gösterir. Erken uyarıda varant seçimi açılmaz.
- Tarihsel teyit zinciri erken uyarıyı aday sayar; yalnız gerçek temiz teyitten sonraki açılışta işlem başlatır.
- Uygulama kimliği ve mevcut imza düzeni korunmuştur; sürüm 19.0 / kod 19.

## Doğrulama

Kotlin 2.0.21 ve JUnit 4.13.2 ile 41 çekirdek testi geçti. Sınır değerler, eksik veri, kesintili yön, seans kapanışı ve erken adayın gerçek teyidi kapsanır. Arayüz JavaScript sözdizimi kontrol edildi. APK derlemesi mevcut GitHub Actions akışındaki Android test kapısından geçer.

Gerçek piyasa verisinde eski/yeni/önerilen eşiklerin karşılaştırmalı getirisi henüz ölçülmedi. Bu değişiklik daha seçici davranış uygular; daha yüksek kârlılık iddiası içermez.
