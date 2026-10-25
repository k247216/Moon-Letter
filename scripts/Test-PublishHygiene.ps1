param([switch]$Staged)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
    $taskPaths = if ($Staged) { @(& git -c core.quotepath=false diff --cached --name-only --diff-filter=ACMR) }
        else { @(& git -c core.quotepath=false ls-files -c -o --exclude-standard) }
    if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate Git publication candidates.' }
    $taskPrivatePolicy = Join-Path $taskRoot 'infra/local-runtime/publication-private-patterns.txt'
    $taskPrivatePatterns = if (Test-Path -LiteralPath $taskPrivatePolicy) {
        @([IO.File]::ReadAllLines($taskPrivatePolicy) | Where-Object { $_ -and -not $_.StartsWith('#') })
    } else { @() }
    $taskFindings = [Collections.Generic.List[string]]::new()
    $taskChecked = 0
    foreach ($taskPath in ($taskPaths | Sort-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $taskPath -PathType Leaf)) { continue }
        if ($taskPath -match '(^|/)(local-runtime|storage|uploads|\.git|\.gradle-user|\.gradle-tmp)/|(^|/)\.env($|\.(?!example$))|(^|/)private\.properties$|\.(?:apk|aab|tar|zip|dump|db|sqlite\w*|jks|keystore|pem|p12|pfx|key|pub|log)$' -or
            ($taskPath -match '\.jar$' -and $taskPath -ne 'android/gradle/wrapper/gradle-wrapper.jar')) {
            $taskFindings.Add("$taskPath [private/runtime/build path]"); continue
        }
        if ((Get-Item -LiteralPath $taskPath).Length -gt 95MB) { $taskFindings.Add("$taskPath [oversized Git blob]"); continue }
        if ($taskPath -notmatch '\.(?:md|txt|java|kt|kts|json|ya?ml|xml|toml|properties|ps1|sh|sql|html|svg|example)$' -and
            $taskPath -notmatch '(^|/)\.gitignore$') { continue }
        $taskRaw = if ($Staged) { (& git show ":$taskPath") -join "`n" } else {
            [IO.File]::ReadAllText((Join-Path $taskRoot $taskPath))
        }
        if ($Staged -and $LASTEXITCODE -ne 0) { throw "Cannot inspect staged content: $taskPath" }
        $taskChecked++
        foreach ($taskRule in @(
            @('cloud-access-key', '(?:LTAI[A-Za-z0-9]{12,}|AKIA[A-Z0-9]{16})'),
            @('git-access-token', '(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})'),
            @('private-key-material', '(?s)-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----\s+[A-Za-z0-9+/=]{32,}'),
            @('embedded-jwt', '\beyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\b')
        )) {
            if ([regex]::IsMatch($taskRaw, $taskRule[1])) { $taskFindings.Add("$taskPath [$($taskRule[0])]") }
        }
        foreach ($taskPattern in $taskPrivatePatterns) {
            if ([regex]::IsMatch($taskRaw, $taskPattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
                $taskFindings.Add("$taskPath [private deployment/person/device identifier]"); break
            }
        }
        $taskLiteralRule = '(?i)(?:password|secret|token|api[_-]?key|access[_-]?key)\b["'']?\s*(?:=|:)\s*["''](?<value>[^"''\r\n]{12,})["'']'
        foreach ($taskMatch in [regex]::Matches($taskRaw, $taskLiteralRule)) {
            $taskValue = $taskMatch.Groups['value'].Value
            if ($taskValue -match '^\$|^(?:replace|example|<|\[)|synthetic|dummy|fake|test|task\d|invalid|placeholder|development|sample|integration|contract|fixture|harness|scenario') { continue }
            if ($taskPath -match '/src/(?:test|androidTest)/' -and
                ($taskValue -cmatch '^(?:[A-Z]+_)+[A-Z]+$' -or $taskValue -match '^nested-[a-z]+-secret$|^deployment-secret!?$|^isolated-account-[ab]$|^reinstall-slice-secret$')) { continue }
            if ($taskRaw.Substring($taskMatch.Index + $taskMatch.Length).TrimStart().StartsWith('+')) { continue }
            $taskFindings.Add("$taskPath [review literal credential-shaped value]"); break
        }
    }
    if ($taskFindings.Count -gt 0) {
        $taskFindings | Sort-Object -Unique | Write-Output
        throw "Publication blocked: $($taskFindings.Count) findings. Values are intentionally not printed."
    }
    "PASS: $taskChecked candidate text files checked; no matched credential/private-identifier patterns. Review cannot guarantee absence of every kind of private content."
} finally { Pop-Location }
