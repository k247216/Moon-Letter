# Read-only checks. TLS uses the operating system trust store; redirects are NOT followed.
param([Parameter(Mandatory=$true)][string]$Origin)
$ErrorActionPreference='Stop'
$taskOrigin = [Uri]$Origin
if(-not $taskOrigin.IsAbsoluteUri -or $taskOrigin.Scheme -ne 'https' -or $taskOrigin.UserInfo -or $taskOrigin.Query -or $taskOrigin.Fragment -or $taskOrigin.AbsolutePath -ne '/') {throw 'Supply an HTTPS origin only; no credentials, path or query'}
$Origin = $Origin.TrimEnd('/')
$taskHandler=[System.Net.Http.HttpClientHandler]::new()
$taskHandler.AllowAutoRedirect=$false
$taskClient=[System.Net.Http.HttpClient]::new($taskHandler)
$taskClient.Timeout=[TimeSpan]::FromSeconds(15)
try {
    $taskManagement=$taskClient.GetAsync("$Origin/actuator/health").GetAwaiter().GetResult()
    if([int]$taskManagement.StatusCode -ne 404){throw 'Public management endpoint must return 404'}
    $taskManagement.Dispose()
    $taskApi=$taskClient.GetAsync("$Origin/api/v1/couple/00000000-0000-0000-0000-000000000001/albums").GetAwaiter().GetResult()
    if([int]$taskApi.StatusCode -notin @(401,403)){throw 'Protected API must reject a request without credentials'}
    $taskApi.Dispose()
    'PASS: HTTPS connection trusted; management hidden; unauthenticated API rejected. This does not prove paired media or cross-province delivery.'
} finally { $taskClient.Dispose(); $taskHandler.Dispose() }
