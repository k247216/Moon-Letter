param([string]$BaseUrl = 'http://127.0.0.1:18088', [switch]$UseLocalTestSecret, [switch]$AllowDeviceRebinding)
$ErrorActionPreference = 'Stop'
$uri = [Uri]$BaseUrl
if ($uri.Scheme -ne 'https' -and -not ($uri.Scheme -eq 'http' -and $uri.IsLoopback)) {
    throw 'Use HTTPS or a localhost SSH tunnel, not a public plaintext API.'
}
if (-not $AllowDeviceRebinding) {
    throw 'This probe bootstraps/rejoins BOTH members and revokes older device sessions. Use an isolated test deployment, or explicitly pass -AllowDeviceRebinding and rebind both phones afterward. No data is erased.'
}
$origin = $BaseUrl.TrimEnd('/')
if ($UseLocalTestSecret) {
    if (-not $uri.IsLoopback) { throw 'Local setup key may only be used against loopback.' }
    $secure = Import-Clixml -LiteralPath (Join-Path (Split-Path $PSScriptRoot -Parent) 'infra/local-runtime/bootstrap-secret.clixml')
} else { $secure = Read-Host 'Enter ECS BOOTSTRAP_SECRET locally (hidden; never paste it into chat)' -AsSecureString }
$secret = [Net.NetworkCredential]::new('', $secure).Password
$taskProbeStage = 'bootstrap'
function Api($method, $path, $token, $body) {
    $script:taskProbeStage = "$method $path"
    $headers = @{}
    if ($token) { $headers.Authorization = "Bearer $token" }
    $options = @{ Uri = "$origin$path"; Method = $method; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $body) {
        $options.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8 -Compress))
        $options.ContentType = 'application/json; charset=utf-8'
    }
    Invoke-RestMethod @options
}
$client = [Net.Http.HttpClient]::new()
try {
    $first = Invoke-RestMethod -Uri "$origin/api/v1/bootstrap" -Method Post -Headers @{'X-Bootstrap-Secret'=$secret} -ContentType 'application/json' -Body '{"displayName":"云端验收甲"}' -TimeoutSec 30
    $secret = $null; $secure = $null
    $invite = Api 'POST' "/api/v1/couple/$($first.coupleId)/pairing-token" $first.token $null
    $second = Api 'POST' '/api/v1/couple/pair' $null @{token=$invite.pairingToken;displayName='云端验收乙'}
    $album = [Guid]::NewGuid().ToString(); $photo = [Guid]::NewGuid().ToString()
    $root = "/api/v1/couple/$($first.coupleId)/albums"
    $null = Api 'PUT' "$root/$album" $first.token @{name='真实云端相册链路验收';category='测试'}
    $bytes = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ioAAAAASUVORK5CYII=')
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
    $ticket = Api 'POST' '/api/v1/media/uploads' $first.token @{
        ownerId=$first.userId;coupleId=$first.coupleId;operationId=$photo;kind='IMAGE';
        mimeType='image/png';byteSize=$bytes.Length;width=1;height=1;sha256=$hash
    }
    if ($ticket.uploadUrl.StartsWith('local://')) {
        $taskProbeStage = 'local media upload'
        $null = Invoke-RestMethod -Uri "$origin/api/v1/media/$($ticket.assetId)/data" -Method Post -Headers @{Authorization="Bearer $($first.token)"} -ContentType 'image/png' -Body $bytes -TimeoutSec 30
    } else {
        $taskProbeStage = 'signed OSS upload'
        if (([Uri]$ticket.uploadUrl).Scheme -ne 'https') { throw 'Cloud upload ticket is not HTTPS.' }
        # No app bearer is sent to the signed object-storage URL.
        $null = Invoke-WebRequest -Uri $ticket.uploadUrl -Method Put -ContentType 'image/png' -Body $bytes -TimeoutSec 30
        $null = Api 'POST' "/api/v1/media/$($ticket.assetId)/complete" $first.token @{sha256=$hash}
    }
    $payload = @{assetId=$ticket.assetId;takenAtEpochMillis=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()}
    $null = Api 'PUT' "$root/$album/photos/$photo" $first.token $payload
    $null = Api 'PUT' "$root/$album/photos/$photo" $first.token $payload
    $taskSnapshot = Api 'GET' $root $second.deviceToken $null
    $remote = @($taskSnapshot | ForEach-Object { $_ }) | Where-Object { [string]$_.id -eq [string]$album }
    if (@($remote.photos).Count -ne 1 -or $remote.category -ne '测试') {
        $taskProbeStage = "partner snapshot (albums=$(@($remote).Count), photos=$(@($remote.photos).Count), expectedCategory=$($remote.category -eq '测试'))"
        throw 'Partner snapshot or idempotency failed.'
    }
    $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $second.deviceToken)
    $taskProbeStage = 'partner original download'
    $download = $client.GetByteArrayAsync("$origin/api/v1/media/$($ticket.assetId)/data").GetAwaiter().GetResult()
    $downloadHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($download)).ToLowerInvariant()
    if ($downloadHash -ne $hash) { throw 'Partner original bytes differ.' }
    $null = Api 'DELETE' "$root/$album/photos/$photo" $first.token $null
    $taskSnapshot = Api 'GET' $root $second.deviceToken $null
    $after = @($taskSnapshot | ForEach-Object { $_ }) | Where-Object { [string]$_.id -eq [string]$album }
    if (-not $after.photos[0].deleted) { throw 'Photo deletion tombstone missing.' }
    $null = Api 'DELETE' "$root/$album" $first.token $null
    $taskSnapshot = Api 'GET' $root $second.deviceToken $null
    $after = @($taskSnapshot | ForEach-Object { $_ }) | Where-Object { [string]$_.id -eq [string]$album }
    if (-not $after.deleted) { throw 'Album deletion tombstone missing.' }
    Write-Output 'PASS: two members, categorized album, verified media upload, byte-identical partner download, retry deduplication, photo/album tombstones.'
    Write-Output 'The probe used founder/partner recovery: older phone sessions need rebinding. One tiny original remains; no existing data was reset. This API probe is not a mobile-network UI acceptance.'
} catch {
    # Do not print full HTTP exceptions: a failing signed PUT may include its credentialed URL.
    $taskStatus = $_.Exception.Response.StatusCode.value__
    Write-Error "Private cloud chain FAILED at $taskProbeStage (HTTP $taskStatus, $($_.Exception.GetType().Name)). Review diagnostics locally; do not share secrets or signed URLs."
} finally { $client.Dispose(); $secret=$null; $first=$null; $second=$null; $ticket=$null }
