# Przykłady curl

Uruchom aplikację (`./mvnw spring-boot:run`) i wykonaj w PowerShell (curl.exe, nie alias Invoke-WebRequest).
Identyfikatory podstawiaj z odpowiedzi — nie zakładaj, że rekord ma id=1.

## Utworzenie konta i pacjenta (dwie równoważne formy)

```powershell
# 1. Body w linii poleceń (cudzysłowy w JSON trzeba escapować dla PowerShell)
curl.exe -i -X POST http://localhost:8080/api/users `
  -H "Content-Type: application/json" `
  -d '{\"email\":\"anna@example.test\",\"password\":\"Secret-123\",\"role\":\"USER\"}'

# 2. Body z pliku - wygodniejsze i bez problemów z escapowaniem
Set-Content patient.json '{"firstName":"Anna","lastName":"Nowak","idCardNo":"ABC123456","birthday":"1990-05-17","userId":1}'
curl.exe -i -X POST http://localhost:8080/api/patients -H "Content-Type: application/json" --data-binary "@patient.json"
```

Oczekiwane: `HTTP/1.1 201`, nagłówki `Location` i `X-Request-Id`, body bez `idCardNo` i hasła.

## Obsługa błędu

```powershell
curl.exe -i -X POST http://localhost:8080/api/patients -H "Content-Type: application/json" `
  -H "X-Request-Id: curl-demo-0001" -d '{\"firstName\":\"\",\"lastName\":\"Nowak\"}'
```

Oczekiwane: `HTTP/1.1 400`, `"code":"VALIDATION_FAILED"`, lista `errors` z polami, `"requestId":"curl-demo-0001"`.
Ten sam requestId znajdziesz w logu aplikacji.

## Query string i stronicowanie

```powershell
curl.exe "http://localhost:8080/api/visits?doctorId=1&available=true&page=0&size=5&sort=dateTime,desc"
curl.exe -i "http://localhost:8080/api/visits?size=500"   # 400 - zamiast cichego obcięcia do 100
```

