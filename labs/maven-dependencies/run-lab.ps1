# Fixture Maven: wybór wersji zależności przechodnich
#
# Uruchomienie (PowerShell, z katalogu labs/maven-dependencies):
#   ./run-lab.ps1
#
# Skrypt używa IZOLOWANEGO lokalnego repozytorium (../../target/m2-lab), więc nie dotyka ~/.m2.
# Sprzątanie: usuń katalog target/m2-lab (albo uruchom ./mvnw clean w projekcie głównym).
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$mvnw = Join-Path $here "..\..\mvnw.cmd"
$repo = Join-Path $here "..\..\target\m2-lab"
$common = @("-B", "-q", "-Dmaven.repo.local=$repo")

# install = package + skopiowanie artefaktu do lokalnego repozytorium (tu: izolowanego).
foreach ($module in "common-v1", "common-v2", "lib-a", "lib-c", "lib-b", "bom") {
    & $mvnw @common -f (Join-Path $here "$module\pom.xml") install
}

$app = Join-Path $here "app\pom.xml"
foreach ($variant in @("", "-Pdirect", "-Pexclude", "-Pbom")) {
    Write-Host "=== dependency:tree $variant ==="
    $mvnArgs = @("-B", "-Dmaven.repo.local=$repo", "-f", $app, "dependency:tree", "-Dincludes=lab.maven")
    if ($variant) { $mvnArgs += $variant }
    & $mvnw @mvnArgs | Select-String "lab.maven:" | ForEach-Object { $_.Line }
}


