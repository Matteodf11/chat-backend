param([string]$Destination = (Join-Path $PSScriptRoot '../.env'))

$ErrorActionPreference = 'Stop'
$targetPath = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $targetPath) { throw 'El archivo de configuración ya existe. No se ha sobrescrito.' }

function New-LocalSecret {
    $bytes = [byte[]]::new(32)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes); [Convert]::ToBase64String($bytes) }
    finally { $generator.Dispose() }
}

$jwtSecret = New-LocalSecret
$encryptionSecret = New-LocalSecret
$content = @"
# Desarrollo local. No subir este archivo a GitHub.
SERVER_PORT=8080
POSTGRES_DB=chat_mattu
POSTGRES_USER=chat_mattu
POSTGRES_PASSWORD=chat_mattu
POSTGRES_PORT=5432
DB_URL=jdbc:postgresql://localhost:5432/chat_mattu
DB_USERNAME=chat_mattu
DB_PASSWORD=chat_mattu
JWT_SECRET=$jwtSecret
MESSAGE_ENCRYPTION_KEY=$encryptionSecret
REMEMBERED_SESSION_DAYS=90
CORS_ALLOWED_ORIGINS=http://localhost:*,http://127.0.0.1:*
# Configura un proyecto de almacenamiento propio para avatares y adjuntos.
SUPABASE_URL=
SUPABASE_SERVICE_ROLE_KEY=
SUPABASE_AVATAR_BUCKET=avatars
SUPABASE_ATTACHMENT_BUCKET=attachments
"@
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($targetPath)) | Out-Null
# CreateNew also prevents overwriting a file created between the check and write.
$stream = [IO.File]::Open($targetPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
try {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($content)
    $stream.Write($bytes, 0, $bytes.Length)
} finally { $stream.Dispose() }
Write-Output 'Configuración local creada con claves nuevas. Su contenido no se imprime.'
