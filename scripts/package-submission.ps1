# Run from any folder: powershell -File scripts/package-submission.ps1 -SignedManualPath C:\signed\group-5.pdf
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$SignedManualPath)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path -Parent $PSScriptRoot
$manual = (Resolve-Path -LiteralPath $SignedManualPath).Path
$manualBytes = [IO.File]::ReadAllBytes($manual)
if ($manualBytes.Length -lt 5 -or [Text.Encoding]::ASCII.GetString($manualBytes, 0, 5) -ne '%PDF-') {
    throw 'SignedManualPath must be a PDF, not a renamed text/document file.'
}
$draft = Join-Path $repo 'docs/task12/group-5-signing-copy.pdf'
if ((Test-Path -LiteralPath $draft) -and ((Get-FileHash -LiteralPath $manual).Hash -eq (Get-FileHash -LiteralPath $draft).Hash)) {
    throw 'This is the untouched signing copy. Complete member details and BOTH personally signed declarations first.'
}
# A PDF hash/header cannot establish signature validity. The speaker must review all eight signatures.
foreach ($tool in @('java', 'mvn', 'g++')) { Get-Command $tool -ErrorAction Stop | Out-Null }
Push-Location $repo
try {
    & java -version
    if ($LASTEXITCODE -ne 0) { throw 'Java is not runnable.' }
    & g++ --version
    if ($LASTEXITCODE -ne 0) { throw 'g++ is not runnable.' }
    $reviewedJar = Join-Path $repo 'target/spl-parser.jar'
    if (-not (Test-Path -LiteralPath $reviewedJar -PathType Leaf)) {
        throw 'First run mvn clean verify, then have everyone review target/spl-parser.jar and sign the manual.'
    }
    $reviewedHash = (Get-FileHash -LiteralPath $reviewedJar -Algorithm SHA256).Hash
    # Require fresh test results while preserving the exact JAR reviewed before signing.
    if (Test-Path -LiteralPath 'target/surefire-reports') {
        Remove-Item -LiteralPath 'target/surefire-reports' -Recurse -Force
    }
    & mvn verify
    if ($LASTEXITCODE -ne 0) {
        throw 'Build/tests failed. Restore missing original frontend fixtures if reported; do not skip failing tests.'
    }
    if ((Get-FileHash -LiteralPath $reviewedJar -Algorithm SHA256).Hash -ne $reviewedHash) {
        throw 'Verification changed the reviewed JAR. Review the new JAR and renew member agreement before packaging again.'
    }
    $reports = @(Get-ChildItem -LiteralPath 'target/surefire-reports' -Filter 'TEST-*.xml')
    if ($reports.Count -eq 0) { throw 'No Surefire test reports found.' }
    $total = 0
    foreach ($report in $reports) {
        [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
        $suite = $xml.testsuite
        $total += [int]$suite.tests
        if ([int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0) {
            throw "Failed/errored/skipped tests in $($report.Name). g++ execution tests must actually run."
        }
    }
    if ($total -eq 0) { throw 'No tests ran.' }
    $stage = Join-Path $repo 'target/submission/group-5'
    New-Item -ItemType Directory -Path $stage -Force | Out-Null
    Copy-Item -LiteralPath 'target/spl-parser.jar' -Destination (Join-Path $stage 'group-5.jar')
    [IO.File]::WriteAllBytes((Join-Path $stage 'group-5.pdf'), $manualBytes)
    $zip = Join-Path $repo 'target/submission/group-5.pending.zip'
    Compress-Archive -LiteralPath (Join-Path $stage 'group-5.jar'), (Join-Path $stage 'group-5.pdf') -DestinationPath $zip -Force
    $smoke = Join-Path $repo 'target/submission/extracted smoke test'
    Expand-Archive -LiteralPath $zip -DestinationPath $smoke -Force
    $names = @(Get-ChildItem -LiteralPath $smoke -File | Select-Object -ExpandProperty Name | Sort-Object)
    if (($names -join ',') -ne 'group-5.jar,group-5.pdf') { throw 'ZIP must contain exactly the JAR and signed manual at its root.' }
    foreach ($name in $names) {
        if ((Get-FileHash -LiteralPath (Join-Path $stage $name)).Hash -ne (Get-FileHash -LiteralPath (Join-Path $smoke $name)).Hash) {
            throw "ZIP extraction changed $name."
        }
    }
    Push-Location $smoke
    try {
        & java -jar .\group-5.jar --help
        if ($LASTEXITCODE -ne 0) { throw 'Extracted JAR help failed.' }
        [IO.File]::WriteAllText((Join-Path $smoke 'SPL.txt'), '#x : : #x = add ( 40 2 ) ; print ( #x ) ;', [Text.Encoding]::ASCII)
        & java -jar .\group-5.jar
        if ($LASTEXITCODE -ne 0) { throw 'Extracted JAR default compilation failed.' }
        & g++ -std=c++17 -pedantic-errors -Wall .\target.cpp -o .\target.exe
        if ($LASTEXITCODE -ne 0) { throw 'Extracted target compilation failed.' }
        $actual = & .\target.exe
        if ($LASTEXITCODE -ne 0 -or ($actual -join "`n") -cne '42') { throw 'Extracted target should print 42 and exit 0.' }
        [IO.File]::WriteAllText((Join-Path $smoke 'SPL.txt'), ': : print ( #missing ) ;', [Text.Encoding]::ASCII)
        & java -jar .\group-5.jar .\SPL.txt
        if ($LASTEXITCODE -ne 4 -or (Test-Path -LiteralPath 'target.cpp')) { throw 'Scope error must exit 4 and remove stale target.' }
    } finally { Pop-Location }
    $finalZip = Join-Path $repo 'target/submission/group-5.zip'
    Move-Item -LiteralPath $zip -Destination $finalZip -Force
    $sha = (Get-FileHash -LiteralPath $finalZip -Algorithm SHA256).Hash
    @("ZIP: group-5.zip", "SHA256: $sha", "Tests passed with no skips: $total", "Created UTC: $([DateTime]::UtcNow.ToString('o'))") |
        Set-Content -LiteralPath 'target/submission/verification.txt' -Encoding UTF8
    Write-Host "Verified ZIP: $finalZip"
    Write-Host "SHA256: $sha"
    Write-Host 'Final human check: correct group, four member details, BOTH declarations signed personally by all four members.'
    Write-Host 'Speaker uploads ONLY to the 1st Class slot before the published cutoff. Do not upload to both categories.'
} finally { Pop-Location }
