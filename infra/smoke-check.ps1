param(
  [string]$ApiBase = "http://localhost:8000/api",
  [string]$Username = "sysadmin01",
  [string]$Password = "sysadmin01"
)

$ErrorActionPreference = "Stop"
$results = New-Object System.Collections.Generic.List[object]

function Add-Result {
  param(
    [string]$Name,
    [bool]$Passed,
    [string]$Detail
  )

  $results.Add([pscustomobject]@{
    Check = $Name
    Status = if ($Passed) { "OK" } else { "FAIL" }
    Detail = $Detail
  })
}

function Invoke-JsonRequest {
  param(
    [string]$Method,
    [string]$Url,
    [object]$Body = $null,
    [hashtable]$Headers = @{}
  )

  $params = @{
    Method = $Method
    Uri = $Url
    Headers = $Headers
    ContentType = "application/json"
  }

  if ($null -ne $Body) {
    $params.Body = ($Body | ConvertTo-Json -Depth 6)
  }

  return Invoke-RestMethod @params
}

function Invoke-WithRetry {
  param(
    [scriptblock]$Action,
    [int]$Attempts = 5,
    [int]$DelayMs = 1500
  )

  for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    try {
      return & $Action
    } catch {
      if ($attempt -eq $Attempts) {
        throw
      }
      Start-Sleep -Milliseconds $DelayMs
    }
  }
}

try {
  $health = Invoke-WithRetry -Action { Invoke-RestMethod -Method Get -Uri "$ApiBase/health" }
  Add-Result -Name "health" -Passed ($health.status -eq "OK") -Detail ("status=" + $health.status)
} catch {
  Add-Result -Name "health" -Passed $false -Detail $_.Exception.Message
}

$loginResponse = $null
try {
  $loginResponse = Invoke-JsonRequest -Method Post -Url "$ApiBase/login" -Body @{
    username = $Username
    password = $Password
  }
  Add-Result -Name "login" -Passed ([string]::IsNullOrWhiteSpace($loginResponse.access_token) -eq $false) -Detail "token primit"
} catch {
  Add-Result -Name "login" -Passed $false -Detail $_.Exception.Message
}

if ($loginResponse -and $loginResponse.refresh_token) {
  try {
    $refreshResponse = Invoke-JsonRequest -Method Post -Url "$ApiBase/refresh" -Body @{
      refreshToken = $loginResponse.refresh_token
    }
    Add-Result -Name "refresh token" -Passed ([string]::IsNullOrWhiteSpace($refreshResponse.access_token) -eq $false) -Detail "token reimprospatat"
  } catch {
    Add-Result -Name "refresh token" -Passed $false -Detail $_.Exception.Message
  }
}

if ($loginResponse -and $loginResponse.access_token) {
  $authHeaders = @{ Authorization = "Bearer $($loginResponse.access_token)" }

  try {
    $me = Invoke-JsonRequest -Method Get -Url "$ApiBase/me" -Headers $authHeaders
    Add-Result -Name "load me" -Passed ([string]::IsNullOrWhiteSpace($me.username) -eq $false) -Detail ("username=" + $me.username)
  } catch {
    Add-Result -Name "load me" -Passed $false -Detail $_.Exception.Message
  }

  try {
    $dashboard = Invoke-JsonRequest -Method Get -Url "$ApiBase/dashboard/summary" -Headers $authHeaders
    $dashboardTitle = if ($null -ne $dashboard.title -and $dashboard.title -ne "") { $dashboard.title } else { "-" }
    Add-Result -Name "dashboard summary" -Passed ($dashboardTitle -ne "-") -Detail ("titlu=" + $dashboardTitle)
  } catch {
    Add-Result -Name "dashboard summary" -Passed $false -Detail $_.Exception.Message
  }

  try {
    $notifications = Invoke-JsonRequest -Method Get -Url "$ApiBase/notifications/me?limit=3" -Headers $authHeaders
    $count = if ($notifications -is [System.Array]) { $notifications.Count } elseif ($null -eq $notifications) { 0 } else { 1 }
    Add-Result -Name "notifications" -Passed $true -Detail ("intrari=" + $count)
  } catch {
    Add-Result -Name "notifications" -Passed $false -Detail $_.Exception.Message
  }

  try {
    $announcements = Invoke-JsonRequest -Method Get -Url "$ApiBase/announcements?limit=3" -Headers $authHeaders
    $count = if ($announcements -is [System.Array]) { $announcements.Count } elseif ($null -eq $announcements) { 0 } else { 1 }
    Add-Result -Name "announcements" -Passed $true -Detail ("intrari=" + $count)
  } catch {
    Add-Result -Name "announcements" -Passed $false -Detail $_.Exception.Message
  }

  try {
    $createdAnnouncement = Invoke-JsonRequest -Method Post -Url "$ApiBase/announcements" -Headers $authHeaders -Body @{
      title = "Smoke check"
      message = "Anunt temporar pentru verificarea fluxului de creare si stergere."
    }
    $deleteResult = Invoke-JsonRequest -Method Delete -Url "$ApiBase/announcements/$($createdAnnouncement.id)" -Headers $authHeaders
    Add-Result -Name "announcement delete" -Passed ($deleteResult.id -eq $createdAnnouncement.id) -Detail ("id=" + $createdAnnouncement.id)
  } catch {
    Add-Result -Name "announcement delete" -Passed $false -Detail $_.Exception.Message
  }

  try {
    $classes = Invoke-JsonRequest -Method Get -Url "$ApiBase/classes" -Headers $authHeaders
    $firstClass = if ($classes -is [System.Array]) { $classes | Select-Object -First 1 } else { $classes }
    if ($null -eq $firstClass -or $null -eq $firstClass.id) {
      throw "Nu exista clase disponibile pentru testul PDF."
    }

    $tempPdf = Join-Path $env:TEMP "orar-smoke-check.pdf"
    $pdfResponse = Invoke-WebRequest -Method Get -Uri "$ApiBase/timetables/classes/$($firstClass.id)/download" -Headers $authHeaders -OutFile $tempPdf
    $pdfLength = (Get-Item $tempPdf).Length
    Add-Result -Name "download timetable PDF" -Passed ($pdfLength -gt 0) -Detail ("bytes=" + $pdfLength)
    Remove-Item -LiteralPath $tempPdf -Force -ErrorAction SilentlyContinue
  } catch {
    Add-Result -Name "download timetable PDF" -Passed $false -Detail $_.Exception.Message
  }
}

$results | Format-Table -AutoSize

if ($results.Status -contains "FAIL") {
  exit 1
}

exit 0
