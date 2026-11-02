# Katalog laboratoriów

Laboratoria leżą w `src/test/java/com/example/showcaselab` — **poza** skanowaniem komponentów aplikacji
(`com.example.clinic`), więc ich celowo błędne konfiguracje nie psują głównego kontekstu. Każde ma własną bazę
lub izolowany kontekst, asercje wyniku (nie `println`) i komendę uruchomienia.

Wszystkie naraz: `./mvnw "-Dtest=*LabTest" test`

| Laboratorium | Zjawisko | Komenda |
|---|---|---|
| `http.HttpSemanticsLabTest` | Bezpieczeństwo i idempotencja GET/PUT/DELETE/POST na prawdziwym API | `./mvnw -Dtest=HttpSemanticsLabTest test` |
| `formats.FormatsLabTest` | Pacjent w JSON/YAML/properties/XML; błędy składni JSON | `./mvnw -Dtest=FormatsLabTest test` |
| `jackson.JacksonBasicsLabTest` | Jackson 3: odczyt/zapis, daty ISO, 3 rodzaje błędów, adnotacje, polimorfizm, widoki, serializer, BigDecimal | `./mvnw -Dtest=JacksonBasicsLabTest test` |
| `jackson.PatchSemanticsLabTest` | PATCH: brak / null / wartość; round-trip dat, kwot, identyfikatorów | `./mvnw -Dtest=PatchSemanticsLabTest test` |
| `jackson.ObjectMapperBeanLabTest` | `@Configuration` + `@Bean` mappera, ta sama instancja u dwóch usług | `./mvnw -Dtest=ObjectMapperBeanLabTest test` |
| `jackson.JsonTryLabTest` | Vavr `Try` przy odczycie pliku JSON (IOException + błąd parsowania) | `./mvnw -Dtest=JsonTryLabTest test` |
| `jackson.ProblemDetailComparisonLabTest` | ProblemDetail (RFC 9457) kontra własny ApiError | `./mvnw -Dtest=ProblemDetailComparisonLabTest test` |
| `di.InjectionStylesLabTest` | Konstruktor / setter / pole w kontenerze i poza nim | `./mvnw -Dtest=InjectionStylesLabTest test` |
| `di.MiniContainerLabTest` | Własny mini-kontener DI: graf, singleton, brak zależności, cykl | `./mvnw -Dtest=MiniContainerLabTest test` |
| `di.BeanContainerLabTest` | Skanowanie, stereotypy, @Primary/@Qualifier (Strategy), lista, opcjonalna zależność, cykl | `./mvnw -Dtest=BeanContainerLabTest test` |
| `di.BeanScopesLabTest` | singleton, prototype, ObjectProvider, request, session | `./mvnw -Dtest=BeanScopesLabTest test` |
| `patterns.ObserverLabTest` | Observer przez zdarzenia Springa | `./mvnw -Dtest=ObserverLabTest test` |
| `mvc.MvcPipelineLabTest` | DispatcherServlet, @RequestHeader, @Controller+@ResponseBody, @ResponseStatus, ciche korekty Pageable | `./mvnw -Dtest=MvcPipelineLabTest test` |
| `mvc.InMemoryCrudLabTest` | CRUD na mapie w pamięci i utrata stanu | `./mvnw -Dtest=InMemoryCrudLabTest test` |
| `lombok.LombokLabTest` | Konstruktory/akcesory, @Builder.Default, pułapki @Data/@ToString | `./mvnw -Dtest=LombokLabTest test` |
| `mapstruct.MapperComparisonLabTest` | Mapowanie ręczne, MapStruct, refleksja w runtime | `./mvnw -Dtest=MapperComparisonLabTest test` |
| `mapstruct.MapStructCompileLabTest` | Nowe pole DTO bez źródła = błąd kompilacji (ERROR) | `./mvnw -Dtest=MapStructCompileLabTest test` |
| `validation.ValidationLabTest` | Grupy, @Validated na metodach, @Future/@PastOrPresent, @Pesel, @QuarterHour i null | `./mvnw -Dtest=ValidationLabTest test` |
| `testing.TestDoublesLabTest` | Dummy, stub, fake, mock, spy | `./mvnw -Dtest=TestDoublesLabTest test` |
| `async.CompletableFutureLabTest` | Kompozycja, własny executor, błędy, allOf/anyOf, timeouty, MDC między wątkami | `./mvnw -Dtest=CompletableFutureLabTest test` |
| `sql.SqlLabTest` | Pięć zapytań (JOIN, LEFT JOIN, GROUP BY/HAVING), DML, ograniczenia, EXPLAIN | `./mvnw -Dtest=SqlLabTest test` |
| `jdbc.ConnectionPoolLabTest` | Hikari: timeout puli, oddanie połączenia; JdbcTemplate | `./mvnw -Dtest=ConnectionPoolLabTest test` |
| `dbinit.DatabaseInitializationLabTest` | schema.sql/data.sql, ddl-auto (none/validate/update/create-drop), defer | `./mvnw -Dtest=DatabaseInitializationLabTest test` |
| `jpa.EntityLifecycleLabTest` | transient/managed/detached/removed, flush≠commit, merge, IDENTITY/SEQUENCE/UUID | `./mvnw -Dtest=EntityLifecycleLabTest test` |
| `jpa.CascadeLabTest` | PERSIST/MERGE/REMOVE/REFRESH/DETACH, brak kaskady | `./mvnw -Dtest=CascadeLabTest test` |
| `jpa.OrphanRemovalLabTest` | orphanRemoval kontra REMOVE | `./mvnw -Dtest=OrphanRemovalLabTest test` |
| `jpa.OneToOneLabTest` | Właściciel kontra mappedBy, @MapsId, tabela łącząca, LAZY strony odwrotnej, pole proxy | `./mvnw -Dtest=OneToOneLabTest test` |
| `jpa.EntityEqualityLabTest` | equals/hashCode i HashSet: pole mutowalne, id z bazy, strategia stabilna | `./mvnw -Dtest=EntityEqualityLabTest test` |
| `repository.OrmCostLabTest` | N+1, fetch join, @EntityGraph, @BatchSize — policzone statystykami Hibernate | `./mvnw -Dtest=OrmCostLabTest test` |
| `repository.PaginationLabTest` | Page kontra Slice (koszt COUNT), keyset dateTime+id | `./mvnw -Dtest=PaginationLabTest test` |
| `repository.RepositoryMechanicsLabTest` | Proxy repozytorium, persist kontra merge, błędne zapytanie pochodne | `./mvnw -Dtest=RepositoryMechanicsLabTest test` |
| `tx.TransactionPropagationLabTest` | Wszystkie 7 propagacji, NESTED na savepoint JDBC | `./mvnw -Dtest=TransactionPropagationLabTest test` |
| `tx.RollbackRulesLabTest` | runtime/checked, rollbackFor, noRollbackFor, rollback-only, TransactionTemplate | `./mvnw -Dtest=RollbackRulesLabTest test` |
| `tx.IsolationLabTest` | Dirty / non-repeatable / phantom read na dwóch połączeniach | `./mvnw -Dtest=IsolationLabTest test` |
| `tx.ProxyLabTest` | Proxy CGLIB/JDK, self-invocation, metoda final | `./mvnw -Dtest=ProxyLabTest test` |
| `tx.AdviceOrderLabTest` | Kolejność @Around/@Before/@After | `./mvnw -Dtest=AdviceOrderLabTest test` |
| `liquibase.LiquibaseLabTest` | Drugi start tej samej bazy, checksum, rollback, brak migracji blokuje validate | `./mvnw -Dtest=LiquibaseLabTest test` |
| `liquibase.ExpandContractLabTest` | Expand → backfill → okres zgodności → contract z kryterium | `./mvnw -Dtest=ExpandContractLabTest test` |
| `logging.LogbackLabTest` | Appendery, poziomy, wzorzec z MDC, log JSON, rotacja | `./mvnw -Dtest=LogbackLabTest test` |
| Fixture Maven | Nearest wins, exclusions, import BOM, install | [maven.md](maven.md) |

## Pytania „co się zmieni, jeśli…”

**HTTP i formaty**
- …klient wyśle `PUT` bez pola `phoneNumber`? (PUT zastępuje cały stan — telefon zniknie.) A `PATCH` w wersji z `PatchSemanticsLabTest`?
- …usuniemy `FAIL_ON_UNKNOWN_PROPERTIES` z `ClinicConfiguration`? Co stanie się z literówką `"fistName"`?
- …`documentId` w `PatchSemanticsLabTest` będzie liczbą zamiast tekstu?

**DI i beany**
- …dodamy trzecią implementację `ReminderSender` bez `@Primary`? Który klient przestanie działać?
- …`SingletonHolder` dostanie `PrototypeTicket` przez pole zamiast `ObjectProvider`?
- …`MiniContainer` dopuści klasy z dwoma konstruktorami — po czym miałby wybierać?

**Lombok / MapStruct / walidacja**
- …usuniemy `@Builder.Default` z `tags`? Co zwróci `getTags()` i czy kolekcje dwóch obiektów nadal będą niezależne?
- …zmienimy `unmappedTargetPolicy` na `IGNORE`? Jak klient API dowie się o brakującym polu?
- …`ReminderScheduler` zostanie utworzony przez `new` zamiast przez kontener?

**JPA**
- …`Visit.patient` dostanie `fetch = EAGER`? Ile zapytań wykona lista 100 wizyt?
- …usuniemy `@BatchSize` z `Doctor.facilities`? Jaki wynik pokaże `batchSize_collectionsLoadedInOneExtraQuery`?
- …w `OrphanRemovalLabTest` damy `orphanRemoval = true` relacji `User -> patients` w aplikacji? Co stanie się z wizytami?
- …`equals` encji użyje `getClass()` zamiast `EntityIdentity.effectiveClass`? Który test w `JpaMappingTest` się wyłoży?

**Transakcje i współbieżność**
- …`VisitService.createSlot` przeczyta sloty PRZED pobraniem blokady doktora?
- …usuniemy `@Version` z `Visit`? Co pokaże `BookingConcurrencyIT` (utracona aktualizacja)?
- …`Outer.writeAThen` i `Inner.required` będą w jednym beanie?
- …pula połączeń ma `maximumPoolSize=1`, a kod używa `REQUIRES_NEW`?

**Liquibase i logi**
- …poprawimy literówkę w komentarzu zastosowanego changesetu? (Komentarze YAML nie wchodzą do checksumy.) A w długości kolumny?
- …uruchomimy `phase3.yaml` drugi raz po usunięciu kolumny `phone`? (Patrz komentarz w `03-backfill.yaml`.)
- …`RequestIdFilter` nie przywróci MDC w `finally`?

