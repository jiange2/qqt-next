#Requires -Version 5.1
<#
.SYNOPSIS
  Mirror the OSS bucket qqt7 (app media) to a local folder for disaster backup.

.DESCRIPTION
  Phase A: enumerate every object via V1-signed ListObjects (paginated) and
           write _manifest.csv (original key -> local path; source of truth
           for restoring the bucket).
  Phase B: download missing / short / corrupt files through the CDN with the
           system curl.exe (--parallel batches), then verify each downloaded
           file against the remote size and MD5 (OSS ETag).
  While downloading it keeps one live progress line on screen (bar, percent,
  GB, speed, ETA, batch and todo counters) and lists every finished file.
  When the output is redirected (pipes, scheduled-task logs) the line is
  printed once every 15 seconds instead of being re-rendered in place.
  Safe to re-run: complete files are skipped by size (-Verify re-checks them
  by MD5 first), partial files resume. It never deletes local files that no
  longer exist on the remote side.

  Filename mapping: name characters that NTFS forbids are percent-encoded per
  path segment (':' -> '%3A' and so on). A raw ':' is parsed by the Windows
  filesystem as Alternate Data Stream syntax: the payload silently lands in a
  hidden stream and the visible file stays 0 bytes. Keys like
  uploads/123_enc1:abc.mp3 become 123_enc1%3Aabc.mp3 locally; the manifest
  always keeps the original key, so the mapping is reversible.

.PARAMETER Dest
  Destination root. Default: <repo>\backup\oss
.PARAMETER EnvFile
  File to read OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET from.
  Default: <repo>\backend-next\server\.env
.PARAMETER Prefix
  Only download keys under this literal prefix (keep the trailing slash),
  e.g. 'images/' or 'uploads/'. The manifest always covers the whole bucket.
  Default: download everything.
.PARAMETER Parallel
  Max simultaneous file transfers (1-64). Default: 32
.PARAMETER MaxFiles
  Download at most N files this run (0 = unlimited). For smoke tests.
.PARAMETER Verify
  Re-check every already-complete local file by MD5 (against the OSS ETag)
  instead of trusting the size match. Corrupt files are deleted and
  re-downloaded; with -ListOnly they are only reported and never touched.
  Costs one full read of the local mirror (a few minutes for 32 GB).
.PARAMETER ListOnly
  Enumerate and write the manifest, but download nothing.

.EXAMPLE
  .\scripts\sync-oss.ps1
  .\scripts\sync-oss.ps1 -Prefix images/ -ListOnly
  .\scripts\sync-oss.ps1 -Prefix uploads/ -MaxFiles 3
#>
[CmdletBinding()]
param(
  [string]$Dest,
  [string]$EnvFile,
  [string]$Prefix = '',
  [string]$CdnBase = 'http://cdn.qqt.yunshangzhiai7.top/',
  [string]$Bucket = 'qqt7',
  [string]$Endpoint = 'https://qqt7.oss-cn-shanghai.aliyuncs.com/',
  [int]$Parallel = 32,
  [int]$MaxFiles = 0,
  [switch]$Verify,
  [switch]$ListOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $Dest)    { $Dest = Join-Path $repoRoot 'backup\oss' }
if (-not $EnvFile) { $EnvFile = Join-Path $repoRoot 'backend-next\server\.env' }
$Dest = [IO.Path]::GetFullPath($Dest)
$CdnBase = $CdnBase.TrimEnd('/')
$Endpoint = $Endpoint.TrimEnd('/') + '/'
if ($Parallel -lt 1 -or $Parallel -gt 64) { throw 'Parallel must be 1..64' }

$curl = Join-Path $env:SystemRoot 'System32\curl.exe'
if (-not (Test-Path -LiteralPath $curl)) {
  $c = Get-Command curl.exe -ErrorAction SilentlyContinue
  if (-not $c) { throw 'curl.exe not found (Windows 10 1803+ ships one in System32)' }
  $curl = $c.Source
}

if (-not (Test-Path -LiteralPath $EnvFile)) { throw "Env file not found: $EnvFile" }
$envMap = @{}
foreach ($line in [IO.File]::ReadAllLines($EnvFile)) {
  if ($line -match '^\s*#' -or -not $line.Contains('=')) { continue }
  $i = $line.IndexOf('=')
  $envMap[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim().Trim('"').Trim("'")
}
$ak = $envMap['OSS_ACCESS_KEY_ID']
$sk = $envMap['OSS_ACCESS_KEY_SECRET']
if (-not $ak -or -not $sk) { throw "OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET missing in $EnvFile" }

function Get-OssAuthHeaders([string]$Ak, [string]$Sk, [string]$BucketName) {
  # Invariant culture is required: a localized OS would render weekday/month
  # names in the local language and OSS would reject the signature.
  $date = [DateTime]::UtcNow.ToString("ddd, dd MMM yyyy HH:mm:ss 'GMT'", [Globalization.CultureInfo]::InvariantCulture)
  $toSign = "GET`n`n`n$date`n/$BucketName/"
  $hmac = New-Object System.Security.Cryptography.HMACSHA1
  $hmac.Key = [Text.Encoding]::UTF8.GetBytes($Sk)
  $sig = [Convert]::ToBase64String($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($toSign)))
  $hmac.Dispose()
  return @{ Date = $date; Authorization = "OSS ${Ak}:$sig" }
}

function Get-OssObjectList([string]$Pfx, [string]$Ak, [string]$Sk, [string]$BucketName, [string]$Ep, [string]$CurlPath) {
  $all = New-Object System.Collections.ArrayList
  $marker = $null
  $page = 0
  while ($true) {
    $page++
    $auth = Get-OssAuthHeaders $Ak $Sk $BucketName
    $tmp = [IO.Path]::GetTempFileName()
    $cargs = @('-sS', '--fail', '--max-time', '60', '-G',
      '--connect-timeout', '20', '--retry', '3', '--retry-delay', '2', '--retry-connrefused',
      '-H', "Date: $($auth.Date)", '-H', "Authorization: $($auth.Authorization)",
      '--data-urlencode', "prefix=$Pfx", '--data-urlencode', 'max-keys=1000')
    if ($marker) { $cargs += @('--data-urlencode', "marker=$marker") }
    $cargs += @('-o', $tmp, $Ep)
    try {
      & $CurlPath @cargs
      if ($LASTEXITCODE -ne 0) { throw "ListObjects request failed (curl exit $LASTEXITCODE)" }
      [xml]$xml = [IO.File]::ReadAllText($tmp, [Text.Encoding]::UTF8)
    }
    finally {
      Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }
    $contents = @($xml.ListBucketResult.Contents)
    foreach ($c in $contents) {
      if ($null -eq $c -or $null -eq $c.Key) { continue }
      [void]$all.Add([pscustomobject]@{
          Key          = [string]$c.Key
          Bytes        = [long]$c.Size
          ETag         = ([string]$c.ETag).Trim([char]'"')
          LastModified = [string]$c.LastModified
        })
    }
    Write-Host ("  listed page {0}: {1} objects (total {2})" -f $page, $contents.Count, $all.Count)
    if (([string]$xml.ListBucketResult.IsTruncated) -ne 'true') { break }
    $next = [string]$xml.ListBucketResult.NextMarker
    if (-not $next -and $all.Count -gt 0) { $next = $all[$all.Count - 1].Key }
    if (-not $next) { break }
    $marker = $next
  }
  return , $all
}

function Convert-SegmentToSafeName([string]$s) {
  $r = $s.Replace('%', '%25').Replace(':', '%3A').Replace('\', '%5C').Replace('*', '%2A')
  $r = $r.Replace('?', '%3F').Replace('"', '%22').Replace('<', '%3C').Replace('>', '%3E').Replace('|', '%7C')
  while ($r.Length -gt 0 -and ($r.EndsWith('.') -or $r.EndsWith(' '))) {
    if ($r.EndsWith('.')) { $r = $r.Substring(0, $r.Length - 1) + '%2E' }
    else { $r = $r.Substring(0, $r.Length - 1) + '%20' }
  }
  return $r
}

function Convert-KeyToLocalRelPath([string]$key) {
  return (($key -split '/') | ForEach-Object { Convert-SegmentToSafeName $_ }) -join '\'
}

function Convert-KeyToUrlPath([string]$key) {
  return (($key -split '/') | ForEach-Object { [uri]::EscapeDataString($_) }) -join '/'
}

function ConvertTo-Win32Arg([string]$a) {
  # Quote one argument per MSVCRT rules, so curl.exe's argv parser sees the
  # exact string (used when the batch is launched via Start-Process, which
  # takes a single command line instead of an argument array).
  if ($a.Length -eq 0) { return '""' }
  if ($a -notmatch '[\s"]') { return $a }
  $bs = [char]92
  $sb = New-Object Text.StringBuilder
  [void]$sb.Append('"')
  $n = 0
  foreach ($ch in $a.ToCharArray()) {
    if ($ch -eq $bs) { $n++ }
    elseif ($ch -eq [char]34) {
      [void]$sb.Append((New-Object string ($bs, ($n * 2 + 1))))
      $n = 0
      [void]$sb.Append('"')
    }
    else {
      if ($n -gt 0) { [void]$sb.Append((New-Object string ($bs, $n))); $n = 0 }
      [void]$sb.Append($ch)
    }
  }
  if ($n -gt 0) { [void]$sb.Append((New-Object string ($bs, ($n * 2)))) }
  [void]$sb.Append('"')
  return $sb.ToString()
}

function Format-Eta([double]$Seconds) {
  if ([double]::IsNaN($Seconds) -or [double]::IsInfinity($Seconds) -or $Seconds -lt 0) { return '--:--' }
  $s = [long][Math]::Round($Seconds)
  if ($s -ge 3600) { return ('{0}:{1:00}:{2:00}' -f [long]($s / 3600), [long](($s % 3600) / 60), ($s % 60)) }
  return ('{0:00}:{1:00}' -f [long]($s / 60), ($s % 60))
}

$script:LiveConsole = $true
try { if ([Console]::IsOutputRedirected) { $script:LiveConsole = $false } } catch { $script:LiveConsole = $false }
$script:LiveBarLen = 0
$script:LivePlainAt = [DateTime]::MinValue

function Show-ProgressLine {
  param([long]$Done, [long]$Total, [double]$Speed, [int]$BatchNo, [int]$BatchCount, [int]$FilesDone, [int]$FilesTotal)
  $pct = 0.0
  if ($Total -gt 0) { $pct = (100.0 * $Done) / $Total }
  if ($pct -lt 0) { $pct = 0.0 }
  if ($pct -gt 100) { $pct = 100.0 }
  $eta = '--:--'
  if ($Speed -gt 0.05 -and $Done -lt $Total) { $eta = Format-Eta ((($Total - $Done) / 1MB) / $Speed) }
  $head = ('  b{0}/{1} ' -f $BatchNo, $BatchCount)
  $tail = (' {0,5:N1}%  {1,5:N1}/{2,5:N1} GB  {3,5:N1} MB/s  ETA {4}  todo {5}/{6}' -f $pct, ($Done / 1GB), ($Total / 1GB), $Speed, $eta, $FilesDone, $FilesTotal)
  $width = 100
  try { $w = [Console]::WindowWidth; if ($w -gt 20) { $width = $w } } catch { }
  $barW = $width - $head.Length - $tail.Length - 2
  if ($barW -lt 8) { $barW = 8 }
  if ($barW -gt 34) { $barW = 34 }
  $fill = [int][Math]::Floor(($pct / 100.0) * $barW)
  if ($fill -lt 0) { $fill = 0 }
  if ($fill -gt $barW) { $fill = $barW }
  $bar = ('#' * $fill) + ('-' * ($barW - $fill))
  $line = $head + '[' + $bar + ']' + $tail
  if ($script:LiveConsole) {
    $pad = ''
    if ($script:LiveBarLen -gt $line.Length) { $pad = ' ' * ($script:LiveBarLen - $line.Length) }
    Write-Host -NoNewline ("`r" + $line + $pad)
    $script:LiveBarLen = $line.Length
  }
  else {
    $now = [DateTime]::UtcNow
    if (($now - $script:LivePlainAt).TotalSeconds -ge 15) {
      $script:LivePlainAt = $now
      Write-Host $line
    }
  }
}

function Clear-ProgressLine {
  if ($script:LiveBarLen -gt 0) {
    Write-Host -NoNewline ("`r" + (' ' * $script:LiveBarLen) + "`r")
    $script:LiveBarLen = 0
  }
}

Write-Host "== Phase A: listing bucket '$Bucket' (whole bucket) =="
$objects = Get-OssObjectList '' $ak $sk $Bucket $Endpoint $curl
if ($objects.Count -eq 0) { throw 'No objects returned (check credentials and endpoint)' }
$totalBytes = 0L
foreach ($o in $objects) { $totalBytes += $o.Bytes }
Write-Host ("  {0} objects, {1:N2} GB" -f $objects.Count, ($totalBytes / 1GB))

[void](New-Item -ItemType Directory -Force -Path $Dest)
$manifestPath = Join-Path $Dest '_manifest.csv'
$sb = New-Object Text.StringBuilder
[void]$sb.AppendLine('Key,Bytes,ETag,LastModified,LocalPath')
$dirMarkers = 0
foreach ($o in $objects) {
  if ($o.Key.EndsWith('/')) { $dirMarkers++; $rel = '' }
  else { $rel = Convert-KeyToLocalRelPath $o.Key }
  [void]$sb.AppendLine(('"{0}",{1},"{2}","{3}","{4}"' -f $o.Key.Replace('"', '""'), $o.Bytes, $o.ETag, $o.LastModified, $rel))
}
[IO.File]::WriteAllText($manifestPath, $sb.ToString(), (New-Object Text.UTF8Encoding($true)))
Write-Host "  manifest: $manifestPath"
if ($dirMarkers -gt 0) { Write-Host "  skipped $dirMarkers directory-marker object(s) (key ends with '/')" }

Write-Host ''
Write-Host '== Phase B: download =='
$todo = New-Object System.Collections.ArrayList
$skipFiles = 0
$skipBytes = 0L
$scopeFiles = 0
$scopeBytes = 0L
$md5Skipped = 0
$verifyChecked = 0
$corruptFiles = 0
foreach ($o in $objects) {
  if ($o.Key.EndsWith('/')) { continue }
  if ($Prefix -and -not $o.Key.StartsWith($Prefix)) { continue }
  $scopeFiles++
  $scopeBytes += $o.Bytes
  $rel = Convert-KeyToLocalRelPath $o.Key
  $local = Join-Path $Dest $rel
  $skip = $false
  if ((Test-Path -LiteralPath $local -PathType Leaf) -and ((Get-Item -LiteralPath $local).Length -eq $o.Bytes)) {
    $skip = $true
    if ($Verify) {
      if ($o.Bytes -gt 0 -and $o.ETag -match '^[0-9A-Fa-f]{32}$') {
        $verifyChecked++
        $md5 = (Get-FileHash -LiteralPath $local -Algorithm MD5).Hash
        if ($md5 -ne $o.ETag.ToUpperInvariant()) {
          Write-Host ("  CORRUPT: {0} (MD5 mismatch)" -f $rel)
          if (-not $ListOnly) { Remove-Item -LiteralPath $local -Force -ErrorAction SilentlyContinue }
          $corruptFiles++
          $skip = $false
        }
        elseif (($verifyChecked % 200) -eq 0) {
          Write-Host ("  verified {0} files (MD5 OK)..." -f $verifyChecked)
        }
      }
      else { $md5Skipped++ }
    }
  }
  if ($skip) {
    $skipFiles++
    $skipBytes += $o.Bytes
    continue
  }
  [void]$todo.Add([pscustomobject]@{
      Key        = $o.Key
      Local      = $local
      Url        = "$CdnBase/" + (Convert-KeyToUrlPath $o.Key)
      Bytes      = $o.Bytes
      ETag       = $o.ETag
      DoneLogged = $false
    })
}
if ($Prefix) { Write-Host ("  in scope ('{0}'): {1} files ({2:N2} GB)" -f $Prefix, $scopeFiles, ($scopeBytes / 1GB)) }
Write-Host ("  already complete: {0} files ({1:N2} GB) skipped" -f $skipFiles, ($skipBytes / 1GB))

$missingTotal = 0L
foreach ($t in $todo) { $missingTotal += $t.Bytes }
Write-Host ("  missing/changed:  {0} files ({1:N2} GB)" -f $todo.Count, ($missingTotal / 1GB))
if ($Verify) { Write-Host ("  MD5 verify:       {0} files re-checked, {1} corrupt" -f $verifyChecked, $corruptFiles) }

if ($ListOnly) {
  Write-Host '  -ListOnly: nothing downloaded.'
  exit 0
}

if ($MaxFiles -gt 0 -and $todo.Count -gt $MaxFiles) {
  $kept = New-Object System.Collections.ArrayList
  for ($i = 0; $i -lt $MaxFiles; $i++) { [void]$kept.Add($todo[$i]) }
  $todo = $kept
  Write-Host ("  -MaxFiles: this run downloads {0} of them" -f $todo.Count)
}
if ($todo.Count -eq 0) {
  Write-Host '  Nothing to do - local mirror is up to date.'
  exit 0
}

$todoBytes = 0L
foreach ($t in $todo) { $todoBytes += $t.Bytes }

$batches = New-Object System.Collections.ArrayList
$cur = New-Object System.Collections.ArrayList
$curLen = 220
foreach ($t in $todo) {
  $cost = $t.Url.Length + $t.Local.Length + 12
  if ($cur.Count -gt 0 -and ($cur.Count -ge 256 -or ($curLen + $cost) -gt 28000)) {
    [void]$batches.Add($cur)
    $cur = New-Object System.Collections.ArrayList
    $curLen = 220
  }
  [void]$cur.Add($t)
  $curLen += $cost
}
if ($cur.Count -gt 0) { [void]$batches.Add($cur) }

$failed = New-Object System.Collections.ArrayList
$okCount = 0
$okBytes = 0L
$processed = 0
$todoDoneCount = 0
$unstartedBaseBytes = 0L
foreach ($t in $todo) {
  if (Test-Path -LiteralPath $t.Local -PathType Leaf) {
    $s = (Get-Item -LiteralPath $t.Local).Length
    if ($s -gt $t.Bytes) { $s = $t.Bytes }
    $unstartedBaseBytes += $s
  }
}
$emaSpeed = 0.0
$sw = [Diagnostics.Stopwatch]::StartNew()
$batchNo = 0
foreach ($b in $batches) {
  $batchNo++
  $batchBytes = 0L
  foreach ($t in $b) { $batchBytes += $t.Bytes }
  Clear-ProgressLine
  Write-Host ("  [batch {0}/{1}] {2} files, {3:N1} MB" -f $batchNo, $batches.Count, $b.Count, ($batchBytes / 1MB))

  # resume bytes already on the disk are re-counted live below, not in the total
  $batchBase = 0L
  foreach ($t in $b) {
    if (Test-Path -LiteralPath $t.Local -PathType Leaf) {
      $s = (Get-Item -LiteralPath $t.Local).Length
      if ($s -gt $t.Bytes) { $s = $t.Bytes }
      $batchBase += $s
    }
  }
  $unstartedBaseBytes -= $batchBase

  $cargs = @('-sS', '--fail', '--create-dirs', '-C', '-',
    '--connect-timeout', '15', '--speed-limit', '1024', '--speed-time', '60',
    '--retry', '3', '--retry-delay', '2',
    '-H', 'Referer: http://com.qqt.music/',
    '--parallel', '--parallel-max', [string]$Parallel)
  foreach ($t in $b) { $cargs += @('-o', $t.Local, $t.Url) }
  $argStr = ($cargs | ForEach-Object { ConvertTo-Win32Arg $_ }) -join ' '
  # no stream redirection needed: with -sS curl only writes real errors to stderr
  $psi = New-Object Diagnostics.ProcessStartInfo
  $psi.FileName = $curl
  $psi.Arguments = $argStr
  $psi.UseShellExecute = $false
  $psi.CreateNoWindow = $true
  $proc = New-Object Diagnostics.Process
  $proc.StartInfo = $psi
  [void]$proc.Start()

  Show-ProgressLine -Done ($okBytes + $unstartedBaseBytes + $batchBase) -Total $todoBytes -Speed 0 -BatchNo $batchNo -BatchCount $batches.Count -FilesDone $todoDoneCount -FilesTotal $todo.Count
  $lastDone = -1L
  $lastTick = [DateTime]::UtcNow
  while (-not $proc.HasExited) {
    Start-Sleep -Milliseconds 600
    $live = 0L
    $justDone = New-Object System.Collections.ArrayList
    foreach ($t in $b) {
      $s = 0L
      if (Test-Path -LiteralPath $t.Local -PathType Leaf) {
        $s = (Get-Item -LiteralPath $t.Local).Length
        if ($s -gt $t.Bytes) { $s = $t.Bytes }
      }
      $live += $s
      if ($s -gt 0 -and $s -ge $t.Bytes -and -not $t.DoneLogged) {
        $t.DoneLogged = $true
        $todoDoneCount++
        [void]$justDone.Add(('  + {0} ({1:N1} MB)' -f $t.Key, ($t.Bytes / 1MB)))
      }
    }
    $done = $okBytes + $unstartedBaseBytes + $live
    $now = [DateTime]::UtcNow
    $dt = ($now - $lastTick).TotalSeconds
    if ($dt -gt 0.2 -and $lastDone -ge 0) {
      $inst = (($done - $lastDone) / 1MB) / $dt
      if ($inst -lt 0) { $inst = 0 }
      if ($emaSpeed -le 0) { $emaSpeed = $inst } else { $emaSpeed = (0.35 * $inst) + (0.65 * $emaSpeed) }
    }
    $lastDone = $done
    $lastTick = $now
    if ($justDone.Count -gt 0) {
      Clear-ProgressLine
      foreach ($l in $justDone) { Write-Host $l }
    }
    Show-ProgressLine -Done $done -Total $todoBytes -Speed $emaSpeed -BatchNo $batchNo -BatchCount $batches.Count -FilesDone $todoDoneCount -FilesTotal $todo.Count
  }
  $proc.WaitForExit()
  Clear-ProgressLine
  $code = $null
  try { $code = $proc.ExitCode } catch { $code = $null }
  if ($null -ne $code -and $code -ne 0) { Write-Host ("  (curl exit code {0})" -f $code) }
  foreach ($t in $b) {
    if ($t.Bytes -gt 0 -and -not $t.DoneLogged -and (Test-Path -LiteralPath $t.Local -PathType Leaf) -and ((Get-Item -LiteralPath $t.Local).Length -ge $t.Bytes)) {
      $t.DoneLogged = $true
      $todoDoneCount++
      Write-Host ('  + {0} ({1:N1} MB)' -f $t.Key, ($t.Bytes / 1MB))
    }
  }

  foreach ($t in $b) {
    $processed++
    $actual = -1L
    if (Test-Path -LiteralPath $t.Local -PathType Leaf) { $actual = (Get-Item -LiteralPath $t.Local).Length }
    $ok = $false
    if ($actual -eq $t.Bytes) {
      $ok = $true
      if ($t.ETag -match '^[0-9A-Fa-f]{32}$') {
        $md5 = (Get-FileHash -LiteralPath $t.Local -Algorithm MD5).Hash
        if ($md5 -ne $t.ETag.ToUpperInvariant()) {
          [void]$failed.Add(("{0}`tMD5 mismatch (local {1}, remote {2})`t{3}" -f $t.Key, $md5, $t.ETag, $t.Local))
          Remove-Item -LiteralPath $t.Local -Force -ErrorAction SilentlyContinue
          $ok = $false
        }
      }
      else { $md5Skipped++ }
    }
    if ($ok) { $okCount++; $okBytes += $actual; continue }
    $reason = ''
    if ($actual -lt 0) { $reason = 'not created (HTTP error or connection failure)' }
    elseif ($actual -eq 0) { $reason = 'empty file (HTTP error suppressed by --fail)' }
    elseif ($actual -lt $t.Bytes) { $reason = "incomplete: $actual of $($t.Bytes) bytes (will resume next run)" }
    else { $reason = "size mismatch: $actual bytes, expected $($t.Bytes)" }
    [void]$failed.Add(("{0}`t{1}`t{2}" -f $t.Key, $reason, $t.Local))
    if ($actual -eq 0 -or $actual -gt $t.Bytes) { Remove-Item -LiteralPath $t.Local -Force -ErrorAction SilentlyContinue }
    # a kept partial still counts as progress on the bar / in the ETA
    if ($actual -gt 0 -and $actual -lt $t.Bytes) { $unstartedBaseBytes += $actual }
  }

  $elapsed = $sw.Elapsed
  $rate = 0.0
  if ($elapsed.TotalSeconds -gt 0) { $rate = ($okBytes / 1MB) / $elapsed.TotalSeconds }
  Write-Host ("    {0}/{1} files | {2:N2}/{3:N2} GB | {4:N1} MB/s | elapsed {5:hh\:mm\:ss}" -f $processed, $todo.Count, ($okBytes / 1GB), ($todoBytes / 1GB), $rate, $elapsed)
}

Write-Host ''
Write-Host '== Summary =='
Write-Host ("  manifest:         {0}" -f $manifestPath)
Write-Host ("  bucket total:     {0} objects, {1:N2} GB" -f $objects.Count, ($totalBytes / 1GB))
Write-Host ("  skipped complete: {0} files" -f $skipFiles)
Write-Host ("  downloaded OK:    {0} files ({1:N2} GB)" -f $okCount, ($okBytes / 1GB))
if ($corruptFiles -gt 0) {
  if ($ListOnly) { Write-Host ("  MD5 corruption:   {0} files (reported only, nothing touched)" -f $corruptFiles) }
  else { Write-Host ("  MD5 corruption:   {0} files (re-downloaded this run)" -f $corruptFiles) }
}
if ($md5Skipped -gt 0) { Write-Host ("  MD5 skipped:      {0} files (ETag was not a plain MD5)" -f $md5Skipped) }
if ($failed.Count -gt 0) {
  $failFile = Join-Path $Dest '_failed.txt'
  [IO.File]::WriteAllLines($failFile, $failed)
  Write-Host ("  FAILED:           {0} files -> {1}" -f $failed.Count, $failFile)
  Write-Host '  Re-run the same command to retry (partial files resume).'
  exit 2
}
Write-Host '  All good.'
exit 0
