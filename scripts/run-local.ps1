param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '../.env'),
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $EnvFile -PathType Leaf)) { throw 'Falta el archivo .env. Ejecuta scripts/create-local-env.ps1.' }
$allowedKeys = @('SERVER_PORT', 'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'POSTGRES_PORT',
    'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'MESSAGE_ENCRYPTION_KEY', 'REMEMBERED_SESSION_DAYS',
    'CORS_ALLOWED_ORIGINS', 'SUPABASE_URL', 'SUPABASE_SERVICE_ROLE_KEY', 'SUPABASE_AVATAR_BUCKET', 'SUPABASE_ATTACHMENT_BUCKET')
$values = @{}
$lineNumber = 0
foreach ($line in [IO.File]::ReadAllLines([IO.Path]::GetFullPath($EnvFile))) {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
    if ($line -notmatch '^\s*([A-Z][A-Z0-9_]*)=(.*)$') { throw "Formato no válido en la línea $lineNumber del archivo de configuración." }
    $key = $Matches[1]
    if ($key -notin $allowedKeys -or $values.ContainsKey($key)) { throw "Variable no admitida o repetida: $key" }
    $value = $Matches[2].Trim()
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'")))) { $value = $value.Substring(1, $value.Length - 2) }
    $values[$key] = $value # Literal text: no interpolation or execution of .env contents.
}
foreach ($key in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'MESSAGE_ENCRYPTION_KEY')) {
    if ([string]::IsNullOrWhiteSpace($values[$key])) { throw "Falta la variable $key" }
}
if ($values.DB_URL -notmatch '^jdbc:postgresql://(localhost|127\.0\.0\.1)(:\d+)?/') {
    throw 'El arranque local requiere una base PostgreSQL de localhost o 127.0.0.1.'
}
if ($values.JWT_SECRET.Length -lt 32) { throw 'JWT_SECRET necesita al menos 32 caracteres.' }
try { $encryptionBytes = [Convert]::FromBase64String($values.MESSAGE_ENCRYPTION_KEY) }
catch { throw 'MESSAGE_ENCRYPTION_KEY debe ser Base64 válido.' }
if ($encryptionBytes.Length -ne 32) { throw 'MESSAGE_ENCRYPTION_KEY debe contener 32 bytes.' }
if ($CheckOnly) { Write-Output 'Configuración local válida. No se ha iniciado el servidor ni modificado el entorno.'; return }

# Support both the checked-in YAML and older ignored local .properties files.
$values.SPRING_DATASOURCE_URL = $values.DB_URL
$values.SPRING_DATASOURCE_USERNAME = $values.DB_USERNAME
$values.SPRING_DATASOURCE_PASSWORD = $values.DB_PASSWORD
$values.PORT = if ($values.SERVER_PORT) { $values.SERVER_PORT } else { '8080' }
$previous = @{}
try {
    foreach ($key in $values.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
    }
    Push-Location (Join-Path $PSScriptRoot '..')
    try { & './mvnw.cmd' spring-boot:run '-Dspring-boot.run.profiles=local'; $serverExitCode = $LASTEXITCODE }
    finally { Pop-Location }
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
if ($serverExitCode -ne 0) { throw "El backend terminó con código $serverExitCode." }
