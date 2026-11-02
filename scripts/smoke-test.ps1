# Smoke test gotowego JAR-a: scenariusz z kolekcji Bruno wykonany przez Invoke-WebRequest,
# drugi start na tej samej bazie plikowej (Liquibase nic nie wykonuje) i profil demo.
#
# Uruchomienie (po ./mvnw package):  powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
# Wynik: linie "OK ..." albo wyjątek z opisem niespełnionego oczekiwania.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$jar = Join-Path $root "target\clinic-showcase-0.0.1-SNAPSHOT.jar"
$dataDir = Join-Path $root "target\smoke-data"
$port = 18080
$base = "http://localhost:$port"

function Start-App([string[]] $appArgs, [string] $logName) {
    $log = Join-Path $root "target\$logName"
    $process = Start-Process java -ArgumentList (@("-jar", $jar, "--server.port=$port") + $appArgs) `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err" -PassThru -WindowStyle Hidden
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        try { Invoke-WebRequest "$base/v3/api-docs" -UseBasicParsing -TimeoutSec 2 | Out-Null; return @{ Process = $process; Log = $log } }
        catch { Start-Sleep -Milliseconds 500 }   # oczekiwanie na start serwera, nie synchronizacja logiki
    }
    Stop-Process $process -Force
    throw "Application did not start, see $log"
}

function Call([string] $method, [string] $path, $body = $null) {
    $params = @{ Uri = "$base$path"; Method = $method; UseBasicParsing = $true; ContentType = "application/json" }
    if ($null -ne $body) { $params.Body = ($body | ConvertTo-Json -Compress) }
    try { $response = Invoke-WebRequest @params; $status = [int]$response.StatusCode; $content = $response.Content }
    catch { $status = [int]$_.Exception.Response.StatusCode; $content = $_.ErrorDetails.Message }
    $json = if ($content) { $content | ConvertFrom-Json } else { $null }
    return @{ Status = $status; Body = $json }
}

function Expect($result, [int] $status, [string] $label) {
    if ($result.Status -ne $status) { throw "$label expected $status but was $($result.Status): $($result.Body | ConvertTo-Json -Compress)" }
    Write-Host "OK $label -> $status"
}

Remove-Item -Recurse -Force $dataDir -ErrorAction SilentlyContinue
$dbUrl = "--spring.datasource.url=jdbc:h2:file:$($dataDir -replace '\\','/')/clinic"

# ---- 1. scenariusz na profilu local-file ----
$app = Start-App @("--spring.profiles.active=local-file", $dbUrl) "smoke-run1.log"
try {
    $run = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    $user = Call POST "/api/users" @{ email = "owner.$run@example.test"; password = "Secret-123"; role = "USER" }; Expect $user 201 "create account"
    $patient = Call POST "/api/patients" @{ firstName = "Anna"; lastName = "Nowak"; idCardNo = "SMK" + ($run % 1000000).ToString("000000"); birthday = "1990-05-17"; userId = $user.Body.id }
    Expect $patient 201 "create patient"
    if ($null -ne $patient.Body.idCardNo) { throw "idCardNo leaked in response" }
    $f1 = Call POST "/api/facilities" @{ name = "North"; address = "Street 1" }; Expect $f1 201 "create facility"
    $docUser = Call POST "/api/users" @{ email = "doctor.$run@example.test"; password = "Secret-123"; role = "USER" }; Expect $docUser 201 "create doctor account"
    $doctor = Call POST "/api/doctors" @{ firstName = "Jan"; lastName = "Kowalski"; specialization = "Cardiology"; userId = $docUser.Body.id; facilityIds = @($f1.Body.id) }
    Expect $doctor 201 "create doctor"
    $day = (Get-Date).AddDays(2).ToString("yyyy-MM-dd")
    $slot = Call POST "/api/visits" @{ doctorId = $doctor.Body.id; dateTime = "${day}T10:00:00"; durationInMinutes = 30 }; Expect $slot 201 "create slot"
    Expect (Call POST "/api/visits" @{ doctorId = $doctor.Body.id; dateTime = "${day}T10:15:00"; durationInMinutes = 30 }) 409 "overlapping slot"
    $booking = "/api/users/$($user.Body.id)/visits/$($slot.Body.id)/booking"
    Expect (Call POST $booking @{ patientId = $patient.Body.id }) 201 "book slot"
    Expect (Call POST $booking @{ patientId = $patient.Body.id }) 409 "book again"
    Expect (Call GET $booking) 200 "read booking"
    Expect (Call DELETE $booking) 204 "cancel booking"
    Expect (Call DELETE $booking) 204 "cancel again (idempotent)"
    Expect (Call GET "/api/visits?size=500") 400 "page size above limit"
    Expect (Call POST "/api/visits" @{ doctorId = $doctor.Body.id; dateTime = "2020-01-08T10:00:00"; durationInMinutes = 30 }) 400 "visit in past"
    $firstRunUserId = $user.Body.id
} finally { Stop-Process $app.Process -Force; Start-Sleep -Seconds 1 }

# ---- 2. drugi start na tej samej bazie plikowej ----
$app = Start-App @("--spring.profiles.active=local-file", $dbUrl) "smoke-run2.log"
try {
    Expect (Call GET "/api/users/$firstRunUserId") 200 "data survived restart"
} finally { Stop-Process $app.Process -Force; Start-Sleep -Seconds 1 }
$summary1 = Select-String -Path (Join-Path $root "target\smoke-run1.log") -Pattern "Run:\s+\d+" | Select-Object -First 1
$summary2 = Select-String -Path (Join-Path $root "target\smoke-run2.log") -Pattern "Run:\s+\d+" | Select-Object -First 1
Write-Host "Liquibase first start : $($summary1.Line.Trim())"
Write-Host "Liquibase second start: $($summary2.Line.Trim())"

# ---- 3. profil demo ----
$app = Start-App @("--spring.profiles.active=demo") "smoke-demo.log"
try {
    $visits = Call GET "/api/visits"
    Expect $visits 200 "demo visits"
    if ($visits.Body.totalElements -ne 4) { throw "demo seeder should create 4 slots, found $($visits.Body.totalElements)" }
    Write-Host "OK demo profile seeded $($visits.Body.totalElements) slots"
} finally { Stop-Process $app.Process -Force }
Write-Host "SMOKE TEST PASSED"

