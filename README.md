# Spring Clinic Showcase

Edukacyjny serwis małej przychodni: od żądania HTTP przez walidację, logikę biznesową i JPA do bazy danych.
Każdy mechanizm ma zastosowanie w kodzie, komentarz „co / jak / dlaczego” oraz test potwierdzający działanie.

**Stos:** Java 21 · Spring Boot 4.1.2 (Spring Framework 7, Hibernate 7, Jackson 3) · H2 · Liquibase (YAML) ·
MapStruct + Lombok · springdoc OpenAPI · JUnit Jupiter + Mockito + AssertJ. Dokładne wersje: [docs/versions.md](docs/versions.md).

## Szybki start

Wymagany tylko JDK 21 (Maven Wrapper pobierze Mavena i zależności przy pierwszym uruchomieniu — wymaga sieci).

```powershell
./mvnw clean verify                                        # wszystkie testy: jednostkowe, slice, integracyjne, laboratoria
./mvnw spring-boot:run                                     # pusta baza H2 w pamięci, port 8080
./mvnw spring-boot:run "-Dspring-boot.run.profiles=demo"   # dane demonstracyjne + aspekt pomiaru czasu + log do pliku
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local-file,local"  # baza w pliku target/data + konsola H2
java -jar target/clinic-showcase-0.0.1-SNAPSHOT.jar        # ten sam serwis z gotowego JAR-a
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1   # scenariusz HTTP na JAR-ze + restart bazy plikowej + profil demo
```

- Swagger UI: <http://localhost:8080/swagger-ui.html>, specyfikacja: <http://localhost:8080/v3/api-docs>
- Konsola H2 (profil `local`): <http://localhost:8080/h2-console>
- Kolekcja Bruno: `bruno/medical-clinic` (otwórz w Bruno → wybierz środowisko `local` → uruchom folder `01-scenario`)
- Przykłady curl: [docs/http-examples.md](docs/http-examples.md)

## Domena w jednym akapicie

Konto (`User`) ma pacjentów (`Patient`, 1:N). Doktor (`Doctor`) jest powiązany 1:1 z kontem i M:N z placówkami
(`Facility`). Wizyta (`Visit`) to slot w grafiku doktora; brak pacjenta = wolny slot, przypisanie pacjenta = rezerwacja.
Sloty zaczynają się w pełnym kwadransie, trwają 15–240 minut, kończą się tego samego dnia i nie mogą się nakładać
u jednego doktora. Czas to lokalny czas przychodni (Europe/Warsaw) bez offsetu.

## Ścieżka nauki

| Krok | Co czytać | Co uruchomić |
|---|---|---|
| 1. Żądanie od HTTP do bazy | [docs/request-walkthrough.md](docs/request-walkthrough.md), `PatientController` → `PatientService` → `PatientRepository` | Bruno `01-scenario/02 Create patient` |
| 2. Kontrakt HTTP i błędy | [docs/http-contract.md](docs/http-contract.md), `GlobalExceptionHandler`, `ApiError` | `PatientControllerTest`, Bruno `02-errors` |
| 3. Model i relacje JPA | `domain/*`, migracje `db/changelog/changes/*` | `JpaMappingTest`, `MigrationConstraintsTest` |
| 4. Reguły slotów i czas | `VisitRules`, `TimeRange`, `@QuarterHour` | `VisitRulesTest`, `TimeRangeTest` |
| 5. Transakcje i współbieżność | `VisitService.createSlot`, `BookingFacade`, `Visit.version` | `SlotCreationConcurrencyIT`, `BookingConcurrencyIT`, `BookingRollbackTest` |
| 6. Listy i koszt ORM | `PageQuery`, `VisitSpecifications`, `DoctorRepository` | `RepositoryQueriesTest`, `OrmCostLabTest`, `PaginationLabTest` |
| 7. Logi i zdarzenia aplikacji | `RequestIdFilter`, `TimingAspect`, `BookingConfirmationListener` | `LoggingAndRequestIdTest`, `BookingEventsIntegrationTest` |
| 8. Laboratoria | [docs/labs/README.md](docs/labs/README.md) — każde z komendą i pytaniami | `./mvnw "-Dtest=*LabTest" test` |

Trzy miejsca szczególnie warte przeczytania:

1. **`VisitService.createSlot` + `DoctorRepository.findByIdForUpdate`** — dlaczego `@Version` nie chroni przed dwoma
   nakładającymi się INSERT-ami i jak blokada wiersza-rodzica to rozwiązuje (dowód: `SlotCreationConcurrencyIT`).
2. **`GlobalExceptionHandler`** — jeden kontrakt błędu dla wyjątków frameworka, walidacji, domeny i bazy, bez wycieku danych.
3. **Encje w `domain/`** — równość zgodna z proxy Hibernate, właściciel relacji, brak kaskad na współdzielonych encjach.

## Dokumentacja projektu

| Plik | Zawartość |
|---|---|
| [docs/implementation-plan.md](docs/implementation-plan.md) | Plan i kryteria odbioru |
| [docs/coverage.md](docs/coverage.md) | Zagadnienie → implementacja → dowód → status |
| [docs/decisions.md](docs/decisions.md) | Decyzje techniczne i odstępstwa |
| [docs/progress.md](docs/progress.md) | Stan realizacji i ostatnia weryfikacja |
| [docs/testing.md](docs/testing.md) | Rodzaje testów i ryzyka, które pokrywają |
| [docs/persistence-alternatives.md](docs/persistence-alternatives.md) | JDBC, JdbcTemplate, Spring Data JDBC, JPA; Flyway kontra Liquibase |
| [docs/labs/](docs/labs/README.md) | Katalog laboratoriów, Maven, transakcje i izolacja |

## Ograniczenia (świadome)

Jedna strefa czasowa, sloty jednodniowe, limit 240 minut, brak logowania i autoryzacji (userId w ścieżce **nie**
uwierzytelnia klienta), brak zewnętrznych powiadomień, pięć encji. Gwarancja „brak nakładających się slotów” obowiązuje
dla ścieżek aplikacji (blokada doktora), nie dla ręcznych INSERT-ów w SQL.


