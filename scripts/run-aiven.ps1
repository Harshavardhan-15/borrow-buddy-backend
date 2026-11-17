# ---------------------------------------------------------------------------
# Start BorrowBuddy against the Aiven MySQL database.
#
#   powershell -ExecutionPolicy Bypass -File scripts\run-aiven.ps1
#
# Prerequisites:
#   * Java 21 on PATH or JAVA_HOME set
#   * Maven on PATH, or set MAVEN_CMD to the full path of mvn.cmd
#   * .env.aiven in the repository root with the Aiven connection values
#     (git-ignored; copy aiven.env.example)
# ---------------------------------------------------------------------------
param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env.aiven"

if (-not (Test-Path $envFile)) {
    throw "Missing .env.aiven. Copy aiven.env.example or follow docs\AIVEN_SETUP.md."
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $separator = $line.IndexOf("=")
        if ($separator -gt 0) {
            $name = $line.Substring(0, $separator).Trim()
            $value = $line.Substring($separator + 1).Trim().Trim('"').Trim("'")
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

$env:PORT = "$Port"

$maven = $env:MAVEN_CMD
if (-not $maven) {
    $command = Get-Command mvn -ErrorAction SilentlyContinue
    if ($command) { $maven = $command.Source }
}
if (-not $maven) {
    throw "Maven not found. Install Maven, add it to PATH, or set MAVEN_CMD to mvn.cmd."
}

Write-Host "Starting the BorrowBuddy API on http://localhost:$Port using Aiven MySQL..." -ForegroundColor Cyan
Push-Location $root
try {
    & $maven spring-boot:run
} finally {
    Pop-Location
}
