# Jedno żądanie od klienta do bazy: `POST /api/patients`

Front-end (przeglądarka, aplikacja mobilna) i back-end komunikują się przez **API** — umówiony kontrakt HTTP.
Klientem może być równie dobrze Bruno, curl czy inny serwer. Backend nie ufa walidacji po stronie UI:
każda reguła jest sprawdzana tutaj, bo żądanie można wysłać z pominięciem formularza.

## 1. Żądanie na „drucie” (HTTP/1.1, tekst)

```http
POST /api/patients HTTP/1.1                         ← metoda, ścieżka (path), wersja protokołu
Host: localhost:8080                                ← nagłówki
Content-Type: application/json                      ← format BODY, które wysyłamy
Accept: application/json                            ← format, który chcemy dostać
X-Request-Id: demo-walkthrough-01                   ← identyfikator korelacji (opcjonalny)

{"firstName":"Anna","lastName":"Nowak","idCardNo":"ABC123456",
 "birthday":"1990-05-17","phoneNumber":"+48600700800","userId":1}
```

Query string (`?email=...&page=0`) pojawia się w GET `/api/patients` i trafia do `@RequestParam`;
fragment ścieżki (`/api/patients/{id}`) — do `@PathVariable`; body — do `@RequestBody`; nagłówek — do `@RequestHeader`
lub, jak tu, do filtra servletowego.

## 2. Droga przez serwer

| Krok | Klasa | Co się dzieje |
|---|---|---|
| 1 | Tomcat | Parsuje bajty HTTP, wybiera wątek z puli, tworzy `HttpServletRequest` |
| 2 | `RequestIdFilter` | Przyjmuje poprawny `X-Request-Id` (albo generuje UUID), wpisuje do MDC i nagłówka odpowiedzi |
| 3 | `DispatcherServlet` | HandlerMapping wybiera `PatientController.create` po metodzie POST i ścieżce |
| 4 | `HttpMessageConverter` (Jackson 3) | JSON → rekord `PatientCreateCommand`; nieznane pole lub zły format daty → 400 bez wywołania kontrolera |
| 5 | Bean Validation (`@Valid`) | `@NotBlank`, `@Pattern`, `@Past`… błąd → `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` |
| 6 | `PatientController` | Wywołuje serwis; buduje `201 Created` + `Location` |
| 7 | Proxy `@Transactional` | Otwiera transakcję przed wejściem do `PatientService.create` |
| 8 | `PatientService` | Pobiera konto (`findById` → 404), sprawdza unikalność dokumentu (409), mapuje Command → encja (MapStruct), przypina do konta |
| 9 | `PatientRepository.save` | Hibernate: `INSERT INTO patients (...) VALUES (?, ...)` — IDENTITY, więc od razu, aby poznać id |
| 10 | Commit | Proxy zatwierdza transakcję; gdyby baza odrzuciła zapis (UNIQUE), advice zamieni to na 409 |
| 11 | `PatientMapper.toDto` | Encja → `PatientDto` (bez `idCardNo`) |
| 12 | Jackson | DTO → JSON w body odpowiedzi |

## 3. Odpowiedź

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/patients/7
X-Request-Id: demo-walkthrough-01
Content-Type: application/json

{"id":7,"firstName":"Anna","lastName":"Nowak","fullName":"Anna Nowak",
 "birthday":"1990-05-17","phoneNumber":"+48600700800","userId":1}
```

`Location` wskazuje nowy zasób — `GET` pod ten adres zwraca 200 (sprawdza to `ClinicFlowIntegrationTest`
i krok `03 Read patient from Location` w Bruno).

## 4. Gdzie to zobaczyć samodzielnie

- logi SQL: uruchom z profilem `local` (`spring.jpa.show-sql=true`);
- requestId w logach: każda linia ma `[requestId=...]`;
- dowody: `PatientControllerTest` (warstwa HTTP), `ClinicFlowIntegrationTest` (całość z bazą), `HttpSmokeIT` (prawdziwy port).

Nazwa endpointu (`POST /api/patients`) to kontrakt HTTP: zasób w liczbie mnogiej i metoda. Nie jest to nazwa
metody Javy — `PatientController.create` można przemianować bez zmiany API.

