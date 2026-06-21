# WA Response Suggester

Aplikacja Android dla Samsung S21 (Android 8.0+), która sugeruje odpowiedzi w WhatsApp w Twoim stylu, korzystając z Claude AI.

## Jak działa

1. **Accessibility Service** odczytuje wiadomości z aktywnej rozmowy WhatsApp
2. Gdy wchodzisz w rozmowę, obok pola tekstowego pojawia się **zielony pływający przycisk AI**
3. Po kliknięciu przycisku aplikacja:
   - Zbiera historię rozmowy z tą osobą (do początku poprzedniego dnia)
   - Analizuje Twój styl pisania (długość wiadomości, ton, emotikony, wyrażenia)
   - Wysyła prompt do Claude AI z prośbą o odpowiedź w Twoim stylu
   - Wpisuje sugestię bezpośrednio do pola tekstowego WhatsApp
4. Możesz edytować sugestię przed wysłaniem lub ją odrzucić

## Wymagania

- Android 8.0+ (minSdk 26)
- Konto Anthropic z kluczem API (console.anthropic.com)
- WhatsApp zainstalowany

## Konfiguracja (po instalacji APK)

### 1. Zainstaluj APK
```
adb install app-release.apk
```
Lub po prostu prześlij plik APK na telefon i otwórz go.

### 2. Przyznaj uprawnienia w aplikacji

Otwórz **WA Suggester** i wykonaj:

**a) Usługa dostępności:**
- Kliknij "Włącz" przy "Usługa dostępności"
- Przejdź do listy, znajdź "WA Response Suggester"
- Włącz przełącznik i zatwierdź

**b) Wyświetlanie nad aplikacjami:**
- Kliknij "Włącz" przy "Wyświetlanie nad aplikacjami"
- Znajdź "WA Suggester" i włącz

### 3. Wpisz klucz API
- Wejdź na console.anthropic.com
- Utwórz klucz API (zaczyna się od `sk-ant-...`)
- Wklej w aplikacji i kliknij "Zapisz klucz API"

### 4. Gotowe!
- Otwórz WhatsApp
- Wejdź w dowolną rozmowę
- Naciśnij zielony przycisk AI w rogu ekranu

## Budowanie APK

### Wymagania
- Android Studio Hedgehog lub nowszy
- JDK 17+

### Kroki
```bash
cd whatsapp-response-suggester
./gradlew assembleRelease
```
APK znajdziesz w: `app/build/outputs/apk/release/app-release-unsigned.apk`

Podpisanie APK:
```bash
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias

./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=my-release-key.jks \
  -Pandroid.injected.signing.store.password=HASLO \
  -Pandroid.injected.signing.key.alias=my-key-alias \
  -Pandroid.injected.signing.key.password=HASLO
```

### Debug build (szybsze, bez podpisywania)
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Architektura

```
com.whatsappsuggester/
├── ui/
│   └── MainActivity.kt          # Ekran konfiguracji
├── service/
│   ├── WhatsAppAccessibilityService.kt  # Odczyt wiadomości + wypełnianie pola
│   ├── OverlayService.kt        # Pływający przycisk AI (foreground service)
│   └── BootReceiver.kt          # Auto-start po restarcie telefonu
├── api/
│   └── ClaudeApiClient.kt       # Wywołania Claude API (claude-haiku-4-5)
└── utils/
    └── Prefs.kt                  # SharedPreferences (klucz API)
```

## Ważne uwagi

- Klucz API jest przechowywany w SharedPreferences urządzenia (nie synchronizowany z chmurą)
- Aplikacja NIE wysyła wiadomości automatycznie — tylko wpisuje sugestię
- Historia rozmowy jest wysyłana do API Anthropic w celu generowania odpowiedzi
- Zużycie tokenów Claude Haiku: ~500-2000 tokenów na zapytanie (ok. $0.001-0.004)

## Prywatność

Treść wiadomości jest przesyłana do API Anthropic wyłącznie w celu generowania odpowiedzi.
Nie jest przechowywana lokalnie ani nigdzie indziej przez tę aplikację.
Zapoznaj się z polityką prywatności Anthropic: https://www.anthropic.com/privacy
