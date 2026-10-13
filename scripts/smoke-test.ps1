$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body '{"username":"platform_admin","password":"password"}'
if ($login.code -ne 200) { throw 'login failed' }
$headers = @{ Authorization = "Bearer $($login.data.token)" }
$checks = @(
  (Invoke-RestMethod -Headers $headers "$base/api/stat/overview"),
  (Invoke-RestMethod -Headers $headers "$base/api/admin/overview"),
  (Invoke-RestMethod -Headers $headers "$base/api/user/page"),
  (Invoke-RestMethod -Headers $headers "$base/api/appointment/admin/page"),
  (Invoke-RestMethod -Headers $headers "$base/api/appeal/admin/page"),
  (Invoke-RestMethod -Headers $headers "$base/api/activity/admin/signups")
)
if (($checks | Where-Object { $_.code -ne 200 }).Count -gt 0) { throw 'smoke check failed' }
Write-Output 'smoke test passed: auth, overview, user, appointment, appeal, activity APIs'
