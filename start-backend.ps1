$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
$envPath = Join-Path $projectDirectory '.env'
$targetDirectory = Join-Path $projectDirectory 'backend\target'
$jarPath = Join-Path $targetDirectory 'mycomap-0.0.1-SNAPSHOT.jar'

if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'Brak .env. Skopiuj .env.example do .env i uzupelnij konfiguracje SMTP.'
}

# Odczytujemy dane jako tekst, bez wykonywania zawartosci pliku i bez wypisywania hasel.
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines($envPath)) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        $settings[$Matches[1]] = $Matches[2].Trim()
    }
}

if ($settings['MAIL_HOST'] -eq 'smtp.gmail.com') {
    foreach ($key in @('MAIL_USERNAME', 'MAIL_PASSWORD', 'MAIL_FROM')) {
        if ([string]::IsNullOrWhiteSpace($settings[$key])) { throw "Uzupelnij $key w lokalnym pliku .env." }
    }
    if ($settings['MAIL_PASSWORD'] -match '\s' -or $settings['MAIL_PASSWORD'].Length -ne 16) {
        throw 'Wpisz 16-znakowe haslo aplikacji Google bez spacji w MAIL_PASSWORD.'
    }
    if ($settings['MAIL_USERNAME'] -ne $settings['MAIL_FROM']) {
        throw 'Ustaw MAIL_FROM na ten sam adres co MAIL_USERNAME.'
    }
    if ($settings['MAIL_PORT'] -ne '587' -or $settings['MAIL_AUTH'] -ne 'true' -or $settings['MAIL_STARTTLS'] -ne 'true' -or $settings['MAIL_SSL'] -ne 'false') {
        throw 'Dla Gmaila ustaw port 587, MAIL_AUTH=true, MAIL_STARTTLS=true i MAIL_SSL=false.'
    }
}
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Brak zbudowanego pliku JAR w backend\target.' }
$databaseName = $settings['POSTGRES_DB']
if ([string]::IsNullOrWhiteSpace($databaseName)) { $databaseName = 'mycomap' }
if ($databaseName -notmatch '^[A-Za-z0-9_]+$') { throw 'Nieprawidlowa nazwa bazy POSTGRES_DB.' }

Push-Location $projectDirectory
try {
    docker compose --env-file $envPath up -d --wait db
    if ($LASTEXITCODE -ne 0) { throw 'Nie udalo sie uruchomic bazy. Sprawdz Docker Desktop.' }
    foreach ($name in @('mycomap-stage1-preview', 'mycomap-backend')) {
        $containerId = docker ps -aq --filter "name=^/$name$"
        if ($containerId) {
            docker stop $name
            if ($LASTEXITCODE -ne 0) { throw "Nie udalo sie zatrzymac kontenera $name." }
        }
    }
    docker run -d --rm --name mycomap-backend --network mycomap_default -p 127.0.0.1:8080:8080 --env-file $envPath -e "DB_URL=jdbc:postgresql://db:5432/$databaseName" --mount "type=bind,source=$targetDirectory,target=/app,readonly" maven:3.9-eclipse-temurin-21 java -jar /app/mycomap-0.0.1-SNAPSHOT.jar
    if ($LASTEXITCODE -ne 0) { throw 'Nie udalo sie uruchomic backendu.' }
    Write-Output 'Backend uruchomiony z konfiguracja z .env. Sprawdz status na http://127.0.0.1:8080/api/health.'
} finally {
    Pop-Location
}
