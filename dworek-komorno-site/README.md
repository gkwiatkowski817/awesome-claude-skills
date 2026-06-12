# Cukiernia Dworek Komorno — WordPress (sklep z tortami + CRM)

Kompletny pakiet WordPress dla cukierni internetowej: zamawianie tortów online z
kreatorem „Stwórz własny tort", CRM oraz zarządzanie sprzedażą. Funkcjonalnie
wzorowany na serwisach typu cukiernia internetowa (struktura stron, kreator
tortu, dostawa 24h, punkty odbioru), z brandingiem w stylu Dworku Komorno
(elegancka, dworska stylistyka: krem, ciemny brąz, złoto, bordo, typografia
szeryfowa Playfair Display + Lato).

> **Uwaga prawna:** pakiet odtwarza *funkcjonalność i strukturę* serwisu
> wzorcowego, a nie jego treści. Teksty, zdjęcia, logotypy i cennik należy
> uzupełnić własnymi materiałami. Nie kopiuj treści ani grafik z cudzych stron.

## Zawartość

```
wp-content/
├── themes/
│   └── dworek-komorno/          # motyw z brandingiem Dworku Komorno
└── plugins/
    ├── dworek-cake-builder/     # kreator tortów [dworek_tort_konfigurator]
    └── dworek-crm/              # CRM + zarządzanie sprzedażą
```

## Instalacja

1. Zainstaluj WordPress 6.x (PHP ≥ 7.4).
2. Skopiuj zawartość katalogu `wp-content/` do `wp-content/` instalacji.
3. W panelu: **Wygląd → Motywy** — aktywuj „Dworek Komorno Cukiernia".
4. **Wtyczki** — aktywuj „Dworek CRM", a następnie „Dworek Cake Builder".
5. Ustaw **Ustawienia → Bezpośrednie odnośniki** na „Nazwa wpisu".

Przy aktywacji motyw automatycznie tworzy strukturę stron i menu główne:

| Strona | Slug | Odpowiednik funkcjonalny |
|---|---|---|
| Strona główna | `strona-glowna` | hero, kroki zamówienia, oferta, dostawa |
| Oferta | `oferta` | torty okolicznościowe / weselne / z grafiką |
| Stwórz własny tort | `stworz-wlasny-tort` | kreator tortu (shortcode) |
| Dostawa prosto do domu | `dostawa` | dostawa + dostawa ekspresowa 24h |
| Punkty odbioru zamówień | `punkty-odbioru` | odbiór osobisty |
| Galeria | `galeria` | realizacje |
| O nas | `o-nas` | o cukierni |
| Kontakt | `kontakt` | dane kontaktowe |
| Regulamin, Polityka prywatności | `regulamin`, `polityka-prywatnosci` | dokumenty (do uzupełnienia) |

## Kreator tortów — `[dworek_tort_konfigurator]`

Dziewięć kroków, wszystkie pola znanych konfiguratorów tortów:

1. **Kształt** — okrągły, prostokątny, kwadratowy, serce.
2. **Rozmiar** — filtrowany według kształtu; pokazuje wagę, orientacyjną
   liczbę porcji i cenę bazową.
3. **Smak** — śmietankowy, śmietankowo-malinowy / -truskawkowy / -porzeczkowy /
   -czekoladowy, śmietankowo-czekoladowo-wiśniowy, czekoladowy, orzechowy.
4. **Wykończenie** — krem, polewa czekoladowa, tynk maślany, masa cukrowa.
5. **Dekoracje** (wielokrotny wybór) — owoce, kwiaty cukrowe, makaroniki,
   figurka, świeczki, jadalne złoto.
6. **Własna grafika** — upload JPG/PNG/WEBP (maks. 8 MB) z edytorem kadru:
   przesuwanie (drag), skalowanie i obracanie (suwaki); kadr zapisywany do
   zamówienia.
7. **Napis na torcie + okazja + uwagi**.
8. **Termin i sposób odbioru** — odbiór osobisty / dostawa do domu / dostawa
   ekspresowa 24h; walidacja minimalnego wyprzedzenia (3 dni, express: 1 dzień);
   pole adresu pojawia się tylko dla dostawy.
9. **Dane zamawiającego** + zgoda na regulamin.

Cena liczona jest na żywo w podsumowaniu i **ponownie po stronie serwera**
przy składaniu zamówienia (klient nie może zmanipulować kwoty). Zamówienie
trafia do Dworek CRM, a klient i administrator dostają e-mail z podsumowaniem.

Cennik i listy opcji: `dworek-cake-builder/includes/config.php` — całość można
nadpisać filtrem `dwk_cake_config` bez modyfikowania wtyczki.

## Dworek CRM — zarządzanie sprzedażą

Menu **Dworek CRM** w panelu administracyjnym:

- **Pulpit sprzedaży** — przychód w bieżącym miesiącu i łącznie, liczba
  zamówień, liczba klientów, pipeline statusów z licznikami, lista
  najbliższych realizacji, eksport CSV.
- **Zamówienia** — lista z kolumnami: termin, klient, konfiguracja tortu,
  kwota, status (kolorowe odznaki); sortowanie po terminie i kwocie. Karta
  zamówienia pokazuje pełną specyfikację tortu wraz z wgraną grafiką i
  parametrami kadru oraz wybór statusu.
- **Pipeline statusów:** Nowe → Potwierdzone → W realizacji → Gotowe do
  odbioru → Zrealizowane (oraz Anulowane). Zmiana statusu wysyła klientowi
  powiadomienie e-mail.
- **Klienci** — kartoteka tworzona automatycznie (dopasowanie po e-mailu),
  z telefonem, adresem, datą pierwszego/ostatniego zamówienia, historią
  zamówień i łączną wartością zakupów.
- **Eksport CSV** — wszystkie zamówienia (średnik jako separator, BOM dla
  Excela).

API dla integracji: `dwk_crm_create_order( array $data )`,
`dwk_crm_upsert_customer()`, `dwk_crm_get_customer_orders()`, hook
`dwk_crm_order_created`, filtr `dwk_crm_order_statuses`.

## Branding i konfiguracja wyglądu

**Wygląd → Dostosuj**:

- *Dworek — kolory marki*: krem (tło), ciemny brąz (tekst), złoto (akcent),
  bordo (akcent dodatkowy) — domyślnie paleta dworska; podmień na dokładne
  kolory z księgi znaku Dworku Komorno.
- *Dworek — dane kontaktowe*: telefon, e-mail, adres, godziny otwarcia
  (pasek górny i stopka).
- *Dworek — sekcja powitalna*: tytuł, podtytuł i zdjęcie hero (np. fotografia
  dworku lub tortów z własnej sesji).
- Logo: **Wygląd → Dostosuj → Tożsamość witryny**.

## Katalog produktów (opcjonalnie WooCommerce)

Motyw deklaruje wsparcie WooCommerce. Jeśli oprócz kreatora potrzebny jest
klasyczny sklep z gotowymi tortami (strona `/sklep/`, karty produktów,
koszyk, płatności online), zainstaluj WooCommerce — zamówienia z kreatora
nadal trafiają do Dworek CRM niezależnie od WooCommerce.

## Co trzeba uzupełnić przed startem

- [ ] Własne zdjęcia (hero, galeria, oferta) i logo.
- [ ] Treść regulaminu i polityki prywatności (konsultacja prawna).
- [ ] Rzeczywisty cennik w `includes/config.php` (lub przez filtr).
- [ ] Strefy/koszty dostawy odpowiednie dla lokalizacji.
- [ ] Konfiguracja wysyłki e-mail (np. wtyczka SMTP), aby powiadomienia
      nie trafiały do spamu.
- [ ] Płatności online (opcjonalnie, przez WooCommerce lub bramkę płatności).
