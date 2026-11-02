# Kontrakt HTTP przychodni

## Słownik

| Pojęcie | Znaczenie w tym projekcie |
|---|---|
| HTTP | Protokół żądanie–odpowiedź; klient wysyła metodę, ścieżkę, nagłówki i opcjonalne body |
| API | Zbiór endpointów i ich kontraktów (dane wejściowe, statusy, kształt JSON, kody błędów) |
| REST | Styl: zasoby jako rzeczowniki w liczbie mnogiej (`/api/patients`), operacje jako metody HTTP |
| JSON | Format body; daty jako tekst ISO-8601 |
| Klient / serwer | Klientem jest Bruno, curl, przeglądarka albo inny serwer — przeglądarka nie jest jedynym klientem |
| FE / BE | Front-end prezentuje dane; back-end (ten projekt) pilnuje reguł i danych niezależnie od UI |

## Metody: bezpieczeństwo i idempotencja

| Metoda | Bezpieczna (nie zmienia stanu) | Idempotentna (N powtórzeń = ten sam stan) | Użycie |
|---|---|---|---|
| GET | tak | tak | odczyt zasobu / listy |
| POST | nie | **nie** | tworzenie (każde wywołanie tworzy nowy zasób) |
| PUT | nie | tak | pełna zamiana edytowalnych pól; przypięcie placówki |
| PATCH | nie | zależy od operacji | tu: zmiana hasła (ta sama wartość = ten sam stan) |
| DELETE | nie | tak (co do stanu) | drugi DELETE tego samego zasobu daje 404 — stan ten sam, status inny |

Dowód: `HttpSemanticsLabTest`.

**Utrata odpowiedzi po POST.** Serwer zapisał pacjenta i zatwierdził transakcję, ale odpowiedź nie dotarła
(zerwane połączenie). Klient nie wie, czy ponowić — ponowienie utworzy drugi zasób (lub, jak tu, dostanie 409
dzięki UNIQUE na dokumencie). Typowe rozwiązanie: nagłówek `Idempotency-Key`; serwer zapamiętuje klucz z wynikiem
i na powtórkę zwraca ten sam wynik. W projekcie **nie jest wdrożone** — to omówienie, nie funkcja.

## Nagłówki

| Nagłówek | Zachowanie |
|---|---|
| `Content-Type` | Body musi być `application/json`; inny typ → 415 `UNSUPPORTED_MEDIA_TYPE` |
| `Accept` | Gdy klient akceptuje tylko np. XML → 406 (puste body, bo ApiError i tak nie dałby się zapisać) |
| `X-Request-Id` | Przyjmowany, jeśli ma 8–64 znaki `[A-Za-z0-9-]`; inaczej generowany UUID. Zawsze wraca w odpowiedzi, w `ApiError.requestId` i w logach |
| `Location` | W odpowiedzi 201 — adres utworzonego zasobu |
| `Allow` | W odpowiedzi 405 — lista dozwolonych metod |
| `Authorization` | **Nieobsługiwany.** Sam nagłówek niczego nie uwierzytelnia; podpisany JWT nie jest zaszyfrowany (każdy odczyta jego treść). Projekt nie ma modułu logowania |

## Statusy — tabela decyzji

| Rodzina | Znaczenie | W projekcie |
|---|---|---|
| 1xx | informacyjne (np. 100 Continue) | obsługuje serwer, nie aplikacja |
| 2xx | sukces | 200 odczyt/aktualizacja, 201 + Location utworzenie, 204 bez body (DELETE, PATCH hasła, PUT pary) |
| 3xx | przekierowanie | nieużywane |
| 4xx | błąd po stronie klienta — ponowienie bez zmiany żądania nie pomoże | 400, 404, 405, 406, 409, 415 |
| 5xx | błąd serwera — klient nie zawinił | 500 `INTERNAL_ERROR` z requestId |

| Kod | Kiedy | Uwaga |
|---|---|---|
| 400 | Zły format/brak pola/reguła formalna; termin w przeszłości | `VALIDATION_FAILED`, `INVALID_REQUEST`, `VISIT_IN_PAST`, `VISIT_INVALID_TIME` |
| 401 | Brak/niepoprawna tożsamość | nieużywany — brak uwierzytelniania |
| 403 | Tożsamość znana, brak uprawnień | nieużywany; konflikt właściciela pacjenta to 409 `OWNERSHIP_MISMATCH`, bo to spójność danych, a nie autoryzacja |
| 404 | Zasób nie istnieje | `PATIENT_NOT_FOUND`, `VISIT_NOT_FOUND`, … |
| 409 | Stan zasobu nie pozwala na operację | `VISIT_OVERLAP`, `VISIT_ALREADY_BOOKED`, `CONCURRENT_MODIFICATION`, `RESOURCE_IN_USE`, duplikaty |
| 422 | Poprawna składnia, błędna semantyka | alternatywa dla części 400; projekt świadomie używa jednego kodu 400 |
| 202 | Przyjęto do przetwarzania asynchronicznego | nieużywany — wszystkie operacje są synchroniczne |
| 503 | Usługa chwilowo niedostępna | nieużywany; zwracałby go np. load balancer |

Klient rozgałęzia logikę po `status` i `code`, **nie po treści `message`**.

## Kształt błędu

```json
{
  "timestamp": "2030-01-07T07:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/patients",
  "requestId": "3f2c...",
  "errors": [{"field": "birthday", "code": "Past", "message": "must be a date in the past"}]
}
```

Zakres: advice działa wewnątrz DispatcherServlet. Błąd rzucony w filtrze servletowym lub przez sam kontener
trafia do mechanizmu `/error` Boota i ma jego domyślny kształt.

## HTTP/1.1, HTTP/2, HTTP/3

| | HTTP/1.1 | HTTP/2 | HTTP/3 |
|---|---|---|---|
| Format na drucie | tekst (jak w `request-walkthrough.md`) | binarne ramki, kompresja nagłówków HPACK | binarne ramki, QPACK |
| Wiele żądań na połączenie | kolejno (pipelining w praktyce nieużywany) | multipleksowanie strumieni w jednym TCP | multipleksowanie bez blokowania całego połączenia przez zgubiony pakiet |
| Transport | TCP | TCP (zwykle TLS) | QUIC na UDP |
| Semantyka | metody, statusy, nagłówki | **te same** | **te same** |

Tekstowy przykład HTTP/1.1 nie pokazuje ramek HTTP/2/3, ale kontrakt (metody, statusy, nagłówki, JSON) jest wspólny.
Projekt nie konfiguruje HTTP/2 ani HTTP/3 — testy używają HTTP/1.1 (`HttpSmokeIT`).

## Formaty danych

| Format | Gdzie w projekcie |
|---|---|
| JSON | body API |
| XML | `pom.xml` |
| YAML | migracje Liquibase |
| properties | `application*.properties` |

Ta sama struktura pacjenta w czterech formatach: `src/test/resources/labs/formats/`, `FormatsLabTest`.

**Protobuf / Avro** — binarne formaty ze schematem: mniejsze i szybsze, z kontrolą zgodności wersji schematu,
ale nieczytelne bez narzędzi i wymagające generowania kodu. Dobre między serwisami o dużym ruchu; dla
edukacyjnego API, które oglądamy w Bruno, czytelny JSON jest ważniejszy — dlatego nie dodajemy binarnego API.

## Alternatywni klienci

Bruno (kolekcja w repozytorium, pliki tekstowe `.bru` w Git), curl (`docs/http-examples.md`), Postman (podobny
do Bruno; kolekcja w chmurze/JSON — nie utrzymujemy drugiej kolekcji). Specyfikację `/v3/api-docs` można
zaimportować do każdego z nich jako punkt startowy.

