param(
    [string]$DatabaseUrl = $env:NEON_DATABASE_URL,
    [string]$DatabaseUsername = $env:NEON_DATABASE_USERNAME,
    [Security.SecureString]$DatabasePassword
)

$ErrorActionPreference = "Stop"

function Convert-ToJdbcUrl([string]$Url) {
    if ([string]::IsNullOrWhiteSpace($Url)) {
        throw "Set NEON_DATABASE_URL to the direct Neon connection URL."
    }
    $jdbcUrl = $Url.Trim()
    if ($jdbcUrl.StartsWith("postgresql://")) {
        $jdbcUrl = "jdbc:$jdbcUrl"
    } elseif ($jdbcUrl.StartsWith("postgres://")) {
        $jdbcUrl = "jdbc:postgresql://" + $jdbcUrl.Substring("postgres://".Length)
    }
    if (-not $jdbcUrl.StartsWith("jdbc:postgresql://")) {
        throw "NEON_DATABASE_URL must be a PostgreSQL or JDBC PostgreSQL URL."
    }
    if ($jdbcUrl -notmatch "[?&]sslmode=") {
        $separator = if ($jdbcUrl.Contains("?")) { "&" } else { "?" }
        $jdbcUrl = "$jdbcUrl${separator}sslmode=require"
    }
    return $jdbcUrl
}

if ([string]::IsNullOrWhiteSpace($DatabaseUrl)) {
    $DatabaseUrl = Read-Host "Paste the complete DIRECT Neon connection string"
}

# Neon normally supplies postgresql://user:password@host/database. Accept that
# single value so users do not have to split credentials into three variables.
if ($DatabaseUrl -match '^(?<jdbc>jdbc:)?(?<scheme>postgres(?:ql)?://)(?<username>[^:/@]+):(?<password>[^@]+)@(?<rest>.+)$') {
    if ([string]::IsNullOrWhiteSpace($DatabaseUsername)) {
        $DatabaseUsername = [Uri]::UnescapeDataString($Matches['username'])
    }
    if ($null -eq $DatabasePassword) {
        $decodedPassword = [Uri]::UnescapeDataString($Matches['password'])
        $DatabasePassword = ConvertTo-SecureString $decodedPassword -AsPlainText -Force
        $decodedPassword = $null
    }
    $jdbcPrefix = if ([string]::IsNullOrWhiteSpace($Matches['jdbc'])) { '' } else { 'jdbc:' }
    $normalizedScheme = if ($Matches['scheme'] -eq 'postgres://') { 'postgresql://' } else { $Matches['scheme'] }
    $DatabaseUrl = $jdbcPrefix + $normalizedScheme + $Matches['rest']
}

if ([string]::IsNullOrWhiteSpace($DatabaseUsername)) {
    $DatabaseUsername = Read-Host "Neon database role/username (shown before ':' in the connection string)"
    if ([string]::IsNullOrWhiteSpace($DatabaseUsername)) {
        throw "A Neon database role/username is required."
    }
}
if ($null -eq $DatabasePassword) {
    if (-not [string]::IsNullOrWhiteSpace($env:NEON_DATABASE_PASSWORD)) {
        $DatabasePassword = ConvertTo-SecureString $env:NEON_DATABASE_PASSWORD -AsPlainText -Force
    } else {
        $DatabasePassword = Read-Host "Neon database password" -AsSecureString
    }
}

$javaOutput = (& java -version 2>&1) -join "`n"
if ($javaOutput -notmatch 'version "21[\.]') {
    throw "Java 21 is required. Current Java output:`n$javaOutput"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$jarPath = Join-Path $backendDir "target\stockpilot-ai-backend-0.1.0.jar"
$jdbcUrl = Convert-ToJdbcUrl $DatabaseUrl
$plainPassword = [Net.NetworkCredential]::new("", $DatabasePassword).Password

if (-not (Test-Path $jarPath)) {
    Push-Location $backendDir
    try {
        & .\mvnw.cmd package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            throw "Backend package failed; demo data was not seeded."
        }
    } finally {
        Pop-Location
    }
}

$managedVariables = @(
    "DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD",
    "FLYWAY_URL", "FLYWAY_USERNAME", "FLYWAY_PASSWORD",
    "APP_DEMO_SEED_ENABLED", "CACHE_TYPE", "SWAGGER_PUBLIC_ENABLED",
    "APP_DEV_TOOLS_ENABLED", "MANAGEMENT_HEALTH_RABBIT_ENABLED",
    "MANAGEMENT_HEALTH_REDIS_ENABLED"
)
$previousValues = @{}
foreach ($name in $managedVariables) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

$logPath = Join-Path $backendDir "target\neon-demo-seed.log"
$errorLogPath = Join-Path $backendDir "target\neon-demo-seed-error.log"
$process = $null

try {
    $env:DATABASE_URL = $jdbcUrl
    $env:DATABASE_USERNAME = $DatabaseUsername
    $env:DATABASE_PASSWORD = $plainPassword
    $env:FLYWAY_URL = $jdbcUrl
    $env:FLYWAY_USERNAME = $DatabaseUsername
    $env:FLYWAY_PASSWORD = $plainPassword
    $env:APP_DEMO_SEED_ENABLED = "true"
    $env:CACHE_TYPE = "simple"
    $env:SWAGGER_PUBLIC_ENABLED = "false"
    $env:APP_DEV_TOOLS_ENABLED = "false"
    $env:MANAGEMENT_HEALTH_RABBIT_ENABLED = "false"
    $env:MANAGEMENT_HEALTH_REDIS_ENABLED = "false"

    $process = Start-Process -FilePath "java" `
        -ArgumentList @("-jar", $jarPath, "--spring.main.web-application-type=none", "--spring.main.banner-mode=off") `
        -WorkingDirectory $backendDir `
        -RedirectStandardOutput $logPath `
        -RedirectStandardError $errorLogPath `
        -WindowStyle Hidden `
        -PassThru

    $deadline = (Get-Date).AddMinutes(5)
    $seeded = $false
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 1
        if (Test-Path $logPath) {
            $log = Get-Content $logPath -Raw
            if ($log -match "Started StockPilotApplication") {
                $seeded = $true
                break
            }
            if ($log -match "APPLICATION FAILED TO START") {
                break
            }
        }
        if ($process.HasExited) {
            break
        }
    }

    if (-not $seeded) {
        $details = if (Test-Path $errorLogPath) { Get-Content $errorLogPath -Raw } else { "No error log was created." }
        throw "Neon demo seed did not complete. Review $logPath and $errorLogPath.`n$details"
    }

    Write-Host "Demo data seeded successfully in Neon." -ForegroundColor Green
    Write-Host "Owner login: owner@demo.com / password123"
    Write-Host "Dealer login: ravi@demo.com / password123"
    Write-Host "Keep APP_DEMO_SEED_ENABLED=false in the deployed backend."
} finally {
    if ($null -ne $process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
    foreach ($name in $managedVariables) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], "Process")
    }
    $plainPassword = $null
}
