# 接口回归测试：覆盖认证、公开查询、用户业务、管理端工作流、角色权限与报表导出。
# 用法：pwsh -File scripts/api-test.ps1 [-Base http://127.0.0.1:8080]
param(
    [string]$Base = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$script:Passed = 0
$script:Failed = 0
$script:Failures = New-Object System.Collections.Generic.List[string]

function Send-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$Token = ''
    )
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $uri = "$Base$Path"
    try {
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 8 -Compress
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
            $response = Invoke-WebRequest -Method $Method -Uri $uri -Headers $headers `
                -ContentType 'application/json; charset=utf-8' -Body $bytes -UseBasicParsing
        }
        else {
            $response = Invoke-WebRequest -Method $Method -Uri $uri -Headers $headers -UseBasicParsing
        }
        $raw = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Raw = $raw; Json = ConvertFrom-JsonSafe $raw }
    }
    catch {
        $webResponse = $_.Exception.Response
        if ($null -ne $webResponse) {
            $reader = New-Object System.IO.StreamReader($webResponse.GetResponseStream(), [System.Text.Encoding]::UTF8)
            $raw = $reader.ReadToEnd()
            $reader.Close()
            return [pscustomobject]@{ Status = [int]$webResponse.StatusCode; Raw = $raw; Json = ConvertFrom-JsonSafe $raw }
        }
        return [pscustomobject]@{ Status = 0; Raw = $_.Exception.Message; Json = $null }
    }
}

function ConvertFrom-JsonSafe {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    try { return $Text | ConvertFrom-Json } catch { return $null }
}

function Assert-True {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if ($Condition) {
        $script:Passed++
        Write-Host ("  [PASS] " + $Name) -ForegroundColor Green
    }
    else {
        $script:Failed++
        $script:Failures.Add("$Name $Detail")
        Write-Host ("  [FAIL] " + $Name + " " + $Detail) -ForegroundColor Red
    }
}

function Login {
    param([string]$Username, [string]$Password = 'password')
    $result = Send-Api -Method Post -Path '/api/auth/login' -Body @{ username = $Username; password = $Password }
    if ($result.Json -and $result.Json.code -eq 200) { return $result.Json.data.token }
    throw "登录失败：$Username -> $($result.Raw)"
}

Write-Host "目标服务：$Base" -ForegroundColor Cyan

# ---------------------------------------------------------------- 认证
Write-Host "`n[1] 认证与登录" -ForegroundColor Cyan
$userToken = Login 'rider_xiaowang'
$stationToken = Login 'station_admin_01'
$adminToken = Login 'platform_admin'
Assert-True '普通用户登录返回 token' ([bool]$userToken)
Assert-True '驿站管理员登录返回 token' ([bool]$stationToken)
Assert-True '平台管理员登录返回 token' ([bool]$adminToken)

$anonymous = Send-Api -Method Get -Path '/api/appointment/my'
Assert-True '未登录访问受保护接口返回 401' ($anonymous.Status -eq 401) "实际 $($anonymous.Status)"

$wrongPassword = Send-Api -Method Post -Path '/api/auth/login' -Body @{ username = 'rider_xiaowang'; password = 'wrong' }
Assert-True '错误密码登录被拒绝' ($wrongPassword.Status -eq 400) "实际 $($wrongPassword.Status)"

# ---------------------------------------------------------------- 公开查询
Write-Host "`n[2] 公开查询接口" -ForegroundColor Cyan
$overview = Send-Api -Method Get -Path '/api/stat/overview'
Assert-True '统计概览返回 200' ($overview.Status -eq 200)
Assert-True '统计概览包含驿站总数' ($null -ne $overview.Json.data.stationCount)
Assert-True '统计概览标注数据口径' ($overview.Json.data.dataScope -like '*数据库*' -or $overview.Json.data.dataScope -like '*内存*')

$stationPage = Send-Api -Method Get -Path '/api/station/page?current=1&size=10'
Assert-True '驿站分页返回记录' ($stationPage.Json.data.records.Count -ge 1)
$stationId = $stationPage.Json.data.records[0].id

$stationDetail = Send-Api -Method Get -Path "/api/station/$stationId"
Assert-True '驿站详情返回名称' ([bool]$stationDetail.Json.data.stationName)

$missingStation = Send-Api -Method Get -Path '/api/station/999999'
Assert-True '不存在的驿站返回业务错误' ($missingStation.Status -eq 400)

$news = Send-Api -Method Get -Path '/api/news/page?current=1&size=5'
Assert-True '资讯分页返回 PageResult' ($null -ne $news.Json.data.total)
$notice = Send-Api -Method Get -Path '/api/notice/page?current=1&size=5'
Assert-True '公告分页返回 PageResult' ($null -ne $notice.Json.data.total)
$activities = Send-Api -Method Get -Path '/api/activity/page?current=1&size=5'
Assert-True '活动分页返回 PageResult' ($null -ne $activities.Json.data.total)
Assert-True '活动返回剩余名额字段' ($null -ne $activities.Json.data.records[0].remaining)

$trend = Send-Api -Method Get -Path '/api/stat/appeal/month'
Assert-True '月度趋势返回 6 个月份' ($trend.Json.data.months.Count -eq 6)
$ranking = Send-Api -Method Get -Path '/api/stat/service/ranking'
Assert-True '服务排行返回数组' ($null -ne $ranking.Json.data)

$evaluationPage = Send-Api -Method Get -Path '/api/evaluation/station/page?current=1&size=5'
Assert-True '评价分页返回 PageResult' ($null -ne $evaluationPage.Json.data.total)
$evaluationSummary = Send-Api -Method Get -Path '/api/evaluation/station/summary'
Assert-True '评价汇总返回平均分' ($null -ne $evaluationSummary.Json.data.averageScore)

# ---------------------------------------------------------------- 用户业务
Write-Host "`n[3] 用户业务闭环" -ForegroundColor Cyan
$appointment = Send-Api -Method Post -Path '/api/appointment' -Token $userToken `
    -Body @{ stationId = $stationId; serviceName = '咨询政策并了解入会流程'; date = '2026-10-20'; slot = '09:00-11:00' }
Assert-True '提交预约成功' ($appointment.Status -eq 200 -and $appointment.Json.data.status -eq 'PENDING_STATION_CONFIRMATION') $appointment.Raw
Assert-True '预约返回驿站名称' ([bool]$appointment.Json.data.stationName)
$appointmentId = $appointment.Json.data.id

$myAppointments = Send-Api -Method Get -Path '/api/appointment/my' -Token $userToken
Assert-True '我的预约包含新记录' ($myAppointments.Json.data.Count -ge 1)

$appeal = Send-Api -Method Post -Path '/api/appeal' -Token $userToken `
    -Body @{ title = '接口回归测试诉求'; type = '驿站服务'; area = '西湖区'; content = '自动化回归测试创建'; anonymous = $false }
Assert-True '提交诉求成功' ($appeal.Status -eq 200 -and $appeal.Json.data.status -eq 'PENDING_ACCEPTANCE') $appeal.Raw
$appealId = $appeal.Json.data.id

$records = Send-Api -Method Get -Path "/api/appeal/$appealId/records" -Token $userToken
Assert-True '诉求处理记录包含提交记录' ($records.Json.data.Count -ge 1 -and $records.Json.data[0].action -eq 'SUBMIT')

# 报名校验使用专用活动，保证脚本重复执行时「首次报名」的语义仍然成立。
$signupActivity = Send-Api -Method Post -Path '/api/activity/admin' -Token $adminToken `
    -Body @{ title = '接口回归测试报名活动'; stationId = $stationId; activityTime = '2026-12-30 09:00:00'; maxCount = 50 }
$signupActivityId = $signupActivity.Json.data.id

    # 报名必须填姓名与电话——承办方要靠这些联系报名人
    $signupMissing = Send-Api -Method Post -Path '/api/activity/signup' -Token $userToken -Body @{ activityId = $signupActivityId }
    Assert-True '报名缺少姓名被拒绝（400）' ($signupMissing.Status -eq 400) $signupMissing.Raw
    $signup = Send-Api -Method Post -Path '/api/activity/signup' -Token $userToken -Body @{ activityId = $signupActivityId; contactName = '王骑手'; phone = '13800002088' }
Assert-True '活动报名成功' ($signup.Status -eq 200) $signup.Raw
$duplicateSignup = Send-Api -Method Post -Path '/api/activity/signup' -Token $userToken -Body @{ activityId = $signupActivityId; contactName = '王骑手'; phone = '13800002088' }
Assert-True '重复报名被拒绝' ($duplicateSignup.Status -eq 400 -and $duplicateSignup.Json.message -like '*已报名*') $duplicateSignup.Raw

# ---------------------------------------------------------------- 承办方与报名审核
# 第三方活动只在站内展示并提供跳转链接，不接受站内报名。
$externalNoUrl = Send-Api -Method Post -Path '/api/activity/admin' -Token $adminToken `
    -Body @{ title = '接口回归第三方活动（无链接）'; organizerType = 'EXTERNAL'; activityTime = '2026-12-31 09:00:00' }
Assert-True '第三方活动缺少链接被拒绝（400）' ($externalNoUrl.Status -eq 400) $externalNoUrl.Raw

$externalActivity = Send-Api -Method Post -Path '/api/activity/admin' -Token $adminToken `
    -Body @{ title = '接口回归第三方活动'; organizerType = 'EXTERNAL'; externalUrl = 'https://example.org/race'; activityTime = '2026-12-31 09:00:00' }
Assert-True '第三方活动创建成功' ($externalActivity.Status -eq 200) $externalActivity.Raw
Assert-True '第三方活动标注承办方为第三方机构' ($externalActivity.Json.data.organizerLabel -eq '第三方机构') $externalActivity.Raw
Assert-True '第三方活动不接受站内报名' ($externalActivity.Json.data.acceptsSignup -eq $false) $externalActivity.Raw

$externalSignup = Send-Api -Method Post -Path '/api/activity/signup' -Token $userToken `
    -Body @{ activityId = $externalActivity.Json.data.id; contactName = '王骑手'; phone = '13800002088' }
Assert-True '向第三方活动报名被拒绝（400）' ($externalSignup.Status -eq 400 -and $externalSignup.Json.message -like '*第三方*') $externalSignup.Raw

# 非第三方活动不接受外部链接，避免两套报名入口
$stationWithUrl = Send-Api -Method Post -Path '/api/activity/admin' -Token $adminToken `
    -Body @{ title = '接口回归驿站活动带链接'; organizerType = 'STATION'; stationId = $stationId; externalUrl = 'https://example.org/x'; activityTime = '2026-12-30 10:00:00' }
Assert-True '驿站活动忽略外部链接' ($stationWithUrl.Status -eq 200 -and [string]::IsNullOrEmpty($stationWithUrl.Json.data.externalUrl)) $stationWithUrl.Raw

# 报名审核：未通过必须说明原因，报名人有权知道为什么
$rejectNoNote = Send-Api -Method Put -Path '/api/activity/signup/review' -Token $adminToken `
    -Body @{ id = $signup.Json.data.id; status = 'REJECTED' }
Assert-True '审核未通过缺少原因被拒绝（400）' ($rejectNoNote.Status -eq 400) $rejectNoNote.Raw

$reviewed = Send-Api -Method Put -Path '/api/activity/signup/review' -Token $adminToken `
    -Body @{ id = $signup.Json.data.id; status = 'APPROVED'; reviewNote = '已电话确认，周六上午 9 点在驿站集合' }
Assert-True '审核通过成功' ($reviewed.Status -eq 200) $reviewed.Raw
Assert-True '报名状态文案为审核通过' ($reviewed.Json.data.statusLabel -eq '审核通过') $reviewed.Raw
Assert-True '审核反馈随报名返回' ($reviewed.Json.data.reviewNote -like '*周六上午*') $reviewed.Raw

# 反馈必须回到报名人自己能看到的地方
$mySignups = Send-Api -Method Get -Path '/api/activity/my' -Token $userToken
$myReviewed = @($mySignups.Json.data | Where-Object { $_.id -eq $signup.Json.data.id })[0]
Assert-True '我的报名能看到审核反馈' ($myReviewed -and $myReviewed.reviewNote -like '*周六上午*') $mySignups.Raw
Assert-True '我的报名带有报名人姓名' ($myReviewed.contactName -eq '王骑手') $mySignups.Raw

# 用户不能处理自己的报名
$userReview = Send-Api -Method Put -Path '/api/activity/signup/review' -Token $userToken `
    -Body @{ id = $signup.Json.data.id; status = 'APPROVED' }
Assert-True '普通用户不能审核报名（403）' ($userReview.Status -eq 403) $userReview.Raw

$evaluation = Send-Api -Method Post -Path '/api/evaluation/station' -Token $userToken `
    -Body @{ stationId = $stationId; score = 5; content = '接口回归测试评价' }
Assert-True '提交驿站评价成功' ($evaluation.Status -eq 200 -and $evaluation.Json.data.status -eq 'SAVED') $evaluation.Raw

$myEvaluations = Send-Api -Method Get -Path '/api/evaluation/my' -Token $userToken
Assert-True '我的评价列表可查询' ($myEvaluations.Json.data.Count -ge 1)

# ---------------------------------------------------------------- 管理端
Write-Host "`n[4] 管理端工作流" -ForegroundColor Cyan
$adminOverview = Send-Api -Method Get -Path '/api/admin/overview' -Token $adminToken
Assert-True '管理端概览返回 200' ($adminOverview.Status -eq 200)
Assert-True '管理端概览包含预约与诉求计数' ($null -ne $adminOverview.Json.data.appointments -and $null -ne $adminOverview.Json.data.appeals)

$adminAppointments = Send-Api -Method Get -Path '/api/appointment/admin/page?current=1&size=10' -Token $adminToken
Assert-True '预约管理分页返回 PageResult' ($null -ne $adminAppointments.Json.data.total)

$audit = Send-Api -Method Put -Path '/api/appointment/audit' -Token $adminToken -Body @{ id = $appointmentId; status = 'CONFIRMED' }
Assert-True '预约审核通过' ($audit.Status -eq 200 -and $audit.Json.data.status -eq 'CONFIRMED') $audit.Raw

$repeatAudit = Send-Api -Method Put -Path '/api/appointment/audit' -Token $adminToken -Body @{ id = $appointmentId; status = 'REJECTED' }
Assert-True '重复审核被拒绝' ($repeatAudit.Status -eq 400) $repeatAudit.Raw

$adminAppeals = Send-Api -Method Get -Path '/api/appeal/admin/page?current=1&size=10' -Token $adminToken
Assert-True '诉求管理分页返回 PageResult' ($null -ne $adminAppeals.Json.data.total)

$assign = Send-Api -Method Put -Path '/api/appeal/assign' -Token $adminToken -Body @{ id = $appealId; content = '分派给驿站处理' }
Assert-True '诉求分派成功' ($assign.Status -eq 200 -and $assign.Json.data.status -eq 'ASSIGNED') $assign.Raw

$handle = Send-Api -Method Put -Path '/api/appeal/handle' -Token $adminToken -Body @{ id = $appealId; status = 'RESOLVED'; content = '已完成处理' }
Assert-True '诉求办结成功' ($handle.Status -eq 200 -and $handle.Json.data.status -eq 'RESOLVED') $handle.Raw

$appealRecords = Send-Api -Method Get -Path "/api/appeal/$appealId/records" -Token $userToken
Assert-True '诉求处理记录已留存分派与办结' ($appealRecords.Json.data.Count -ge 3)

$evaluateAppeal = Send-Api -Method Post -Path '/api/appeal/evaluate' -Token $userToken -Body @{ appealId = $appealId; score = 5; content = '处理及时' }
Assert-True '办结后评价诉求成功' ($evaluateAppeal.Status -eq 200) $evaluateAppeal.Raw

$signups = Send-Api -Method Get -Path '/api/activity/admin/signups' -Token $adminToken
Assert-True '活动报名管理列表可查询' ($signups.Json.data.Count -ge 1)

# ---------------------------------------------------------------- 账号与角色
Write-Host "`n[5] 账号与角色权限管理" -ForegroundColor Cyan
$users = Send-Api -Method Get -Path '/api/admin/users?current=1&size=10' -Token $adminToken
Assert-True '账号分页返回 PageResult' ($users.Json.data.total -ge 3)
Assert-True '账号返回角色名称' ([bool]$users.Json.data.records[0].roleName)

$roles = Send-Api -Method Get -Path '/api/admin/roles' -Token $adminToken
Assert-True '角色列表返回 3 个内置角色' ($roles.Json.data.Count -eq 3)

$targetUserId = $users.Json.data.records[0].id
$setRoles = Send-Api -Method Put -Path "/api/admin/users/$targetUserId/roles" -Token $adminToken -Body @{ roleCodes = @('USER') }
Assert-True '设置用户角色成功' ($setRoles.Status -eq 200) $setRoles.Raw

$badRole = Send-Api -Method Put -Path "/api/admin/users/$targetUserId/roles" -Token $adminToken -Body @{ roleCodes = @('NOT_A_ROLE') }
Assert-True '非法角色编码被拒绝' ($badRole.Status -eq 400) $badRole.Raw

$stationAdminUsers = Send-Api -Method Get -Path '/api/admin/users' -Token $stationToken
Assert-True '驿站管理员不能访问账号管理（403）' ($stationAdminUsers.Status -eq 403) "实际 $($stationAdminUsers.Status)"

$userInfo = Send-Api -Method Get -Path '/api/user/info' -Token $userToken
Assert-True '个人中心返回当前账号' ($userInfo.Status -eq 200 -and $userInfo.Json.data.username -eq 'rider_xiaowang') $userInfo.Raw

$updateInfo = Send-Api -Method Put -Path '/api/user/info' -Token $userToken -Body @{ realName = '骑手小王'; jobType = '外卖骑手'; area = '西湖区' }
Assert-True '更新个人资料成功' ($updateInfo.Status -eq 200) $updateInfo.Raw

# ---------------------------------------------------------------- 驿站与服务项目
Write-Host "`n[6] 驿站与服务项目管理" -ForegroundColor Cyan
$createdStation = Send-Api -Method Post -Path '/api/station' -Token $adminToken `
    -Body @{ stationName = '接口回归测试驿站'; area = '滨江区'; address = '测试路 1 号'; contactPhone = '0571-00000000'; openTime = '09:00-18:00' }
Assert-True '新增驿站成功' ($createdStation.Status -eq 200) $createdStation.Raw
$newStationId = $createdStation.Json.data.id

$updatedStation = Send-Api -Method Put -Path "/api/station/$newStationId" -Token $adminToken `
    -Body @{ stationName = '接口回归测试驿站（已改名）'; area = '滨江区'; address = '测试路 2 号'; openTime = '10:00-19:00' }
Assert-True '修改驿站成功' ($updatedStation.Json.data.stationName -like '*已改名*') $updatedStation.Raw

$stationStatus = Send-Api -Method Put -Path "/api/station/$newStationId/status?status=CLOSED" -Token $adminToken
Assert-True '修改驿站状态成功' ($stationStatus.Json.data.status -eq 'CLOSED') $stationStatus.Raw

$badStatus = Send-Api -Method Put -Path "/api/station/$newStationId/status?status=UNKNOWN" -Token $adminToken
Assert-True '非法驿站状态被拒绝' ($badStatus.Status -eq 400) $badStatus.Raw

$adminStationPage = Send-Api -Method Get -Path '/api/station/admin/page?current=1&size=50' -Token $adminToken
Assert-True '驿站管理分页可查询' ($adminStationPage.Json.data.total -ge 1)

$createdService = Send-Api -Method Post -Path '/api/station/service' -Token $adminToken `
    -Body @{ stationId = $newStationId; serviceName = '接口回归测试服务'; serviceType = 'GENERAL'; needAppoint = $true }
Assert-True '新增服务项目成功' ($createdService.Status -eq 200) $createdService.Raw
$newServiceId = $createdService.Json.data.id

$servicePage = Send-Api -Method Get -Path "/api/station/service/page?current=1&size=10&stationId=$newStationId"
Assert-True '服务项目分页可查询' ($servicePage.Json.data.total -ge 1)

$serviceList = Send-Api -Method Get -Path "/api/station/service/list?stationId=$newStationId"
Assert-True '服务项目列表可查询' ($serviceList.Json.data.Count -ge 1)

$updateService = Send-Api -Method Put -Path "/api/station/service/$newServiceId" -Token $adminToken `
    -Body @{ stationId = $newStationId; serviceName = '接口回归测试服务（已改名）'; serviceType = 'POWER'; needAppoint = $false }
Assert-True '修改服务项目成功' ($updateService.Json.data.serviceName -like '*已改名*') $updateService.Raw

$serviceStatus = Send-Api -Method Put -Path "/api/station/service/$newServiceId/status?status=0" -Token $adminToken
Assert-True '修改服务项目状态成功' ($serviceStatus.Json.data.status -eq 0) $serviceStatus.Raw

# ---------------------------------------------------------------- 资讯公告活动管理
Write-Host "`n[7] 资讯、公告与活动管理" -ForegroundColor Cyan
$createdNews = Send-Api -Method Post -Path '/api/news' -Token $adminToken `
    -Body @{ title = '接口回归测试资讯'; category = '政策'; content = '自动化回归测试创建'; status = 'PUBLISHED' }
Assert-True '新增资讯成功' ($createdNews.Status -eq 200) $createdNews.Raw
$newNewsId = $createdNews.Json.data.id

$newsDetail = Send-Api -Method Get -Path "/api/news/$newNewsId"
Assert-True '资讯详情可查询' ($newsDetail.Json.data.title -like '*回归测试资讯*') $newsDetail.Raw

$adminNews = Send-Api -Method Get -Path '/api/news/admin/page?current=1&size=10' -Token $adminToken
Assert-True '资讯管理分页可查询' ($adminNews.Json.data.total -ge 1)

$createdNotice = Send-Api -Method Post -Path '/api/notice' -Token $adminToken `
    -Body @{ title = '接口回归测试公告'; noticeType = '通知'; content = '自动化回归测试创建' }
Assert-True '新增公告成功' ($createdNotice.Status -eq 200) $createdNotice.Raw
$newNoticeId = $createdNotice.Json.data.id

$createdActivity = Send-Api -Method Post -Path '/api/activity/admin' -Token $adminToken `
    -Body @{ title = '接口回归测试活动'; stationId = $newStationId; activityTime = '2026-11-01 09:00:00'; address = '测试路 1 号'; maxCount = 10; content = '自动化回归测试创建' }
Assert-True '新增活动成功' ($createdActivity.Status -eq 200) $createdActivity.Raw
$newActivityId = $createdActivity.Json.data.id

$activityAdminPage = Send-Api -Method Get -Path '/api/activity/admin/page?current=1&size=10' -Token $adminToken
Assert-True '活动管理分页可查询' ($activityAdminPage.Json.data.total -ge 1)

$deleteNews = Send-Api -Method Delete -Path "/api/news/$newNewsId" -Token $adminToken
Assert-True '删除资讯成功' ($deleteNews.Json.data.deleted -eq $true) $deleteNews.Raw
$deleteNotice = Send-Api -Method Delete -Path "/api/notice/$newNoticeId" -Token $adminToken
Assert-True '删除公告成功' ($deleteNotice.Json.data.deleted -eq $true) $deleteNotice.Raw
$deleteActivity = Send-Api -Method Delete -Path "/api/activity/admin/$newActivityId" -Token $adminToken
Assert-True '删除活动成功' ($deleteActivity.Json.data.deleted -eq $true) $deleteActivity.Raw
$deleteService = Send-Api -Method Delete -Path "/api/station/service/$newServiceId" -Token $adminToken
Assert-True '删除服务项目成功' ($deleteService.Json.data.deleted -eq $true) $deleteService.Raw
$deleteStation = Send-Api -Method Delete -Path "/api/station/$newStationId" -Token $adminToken
Assert-True '删除驿站成功（逻辑删除）' ($deleteStation.Json.data.deleted -eq $true) $deleteStation.Raw

# ---------------------------------------------------------------- 报表导出
Write-Host "`n[8] 报表导出" -ForegroundColor Cyan
foreach ($type in @('appeals', 'appointments', 'stations', 'signups', 'evaluations')) {
    $export = Send-Api -Method Get -Path "/api/stat/export/$type" -Token $adminToken
    Assert-True "导出 $type 返回 CSV" ($export.Status -eq 200 -and $export.Raw.Length -gt 0) "状态 $($export.Status)"
}
$badExport = Send-Api -Method Get -Path '/api/stat/export/unknown' -Token $adminToken
Assert-True '未知导出类型返回业务错误' ($badExport.Status -eq 400) $badExport.Raw
$userExport = Send-Api -Method Get -Path '/api/stat/export/appeals' -Token $userToken
Assert-True '普通用户不能导出报表（403）' ($userExport.Status -eq 403) "实际 $($userExport.Status)"

# ---------------------------------------------------------------- 越权
Write-Host "`n[9] 越权访问控制" -ForegroundColor Cyan
$userAudit = Send-Api -Method Put -Path '/api/appointment/audit' -Token $userToken -Body @{ id = $appointmentId; status = 'CONFIRMED' }
Assert-True '普通用户不能审核预约（403）' ($userAudit.Status -eq 403) "实际 $($userAudit.Status)"
$userCreateStation = Send-Api -Method Post -Path '/api/station' -Token $userToken `
    -Body @{ stationName = '越权驿站'; area = '西湖区'; address = '测试' }
Assert-True '普通用户不能新增驿站（403）' ($userCreateStation.Status -eq 403) "实际 $($userCreateStation.Status)"
$invalidToken = Send-Api -Method Get -Path '/api/appointment/my' -Token 'not-a-real-token'
Assert-True '无效 token 返回 401' ($invalidToken.Status -eq 401) "实际 $($invalidToken.Status)"

# ---------------------------------------------------------------- 数据范围
Write-Host "`n[10] 角色数据范围" -ForegroundColor Cyan
# 演示配置：1~4 号「小哥驿站」由 station_admin_01 负责（见 docs/驿站数据来源.md）。
$managedStationIds = @(1, 2, 3, 4)
$managedAreas = @()
foreach ($sid in $managedStationIds) {
    $stationDetail = Send-Api -Method Get -Path "/api/station/$sid"
    if ($stationDetail.Status -eq 200) { $managedAreas += $stationDetail.Json.data.area }
}
$managedAreas = @($managedAreas | Select-Object -Unique)

$stationAppointments = Send-Api -Method Get -Path '/api/appointment/admin/page?current=1&size=100' -Token $stationToken
$outOfScopeAppointments = @($stationAppointments.Json.data.records | Where-Object { $managedStationIds -notcontains $_.stationId })
Assert-True '驿站管理员只看到自己负责驿站的预约' ($outOfScopeAppointments.Count -eq 0) "越界记录 $($outOfScopeAppointments.Count) 条"

$stationAppeals = Send-Api -Method Get -Path '/api/appeal/admin/page?current=1&size=100' -Token $stationToken
$outOfScopeAppeals = @($stationAppeals.Json.data.records | Where-Object { $managedAreas -notcontains $_.area })
Assert-True '驿站管理员只看到负责区域内的诉求' ($outOfScopeAppeals.Count -eq 0) "越界记录 $($outOfScopeAppeals.Count) 条"

$adminAppointments = Send-Api -Method Get -Path '/api/appointment/admin/page?current=1&size=100' -Token $adminToken
# 概览也必须按范围过滤：否则首页待办数字是全平台，与列表对不上
$stationOverview = Send-Api -Method Get -Path '/api/admin/overview' -Token $stationToken
$platformOverview = Send-Api -Method Get -Path '/api/admin/overview' -Token $adminToken
Assert-True '驿站管理员概览已按范围限定' ($stationOverview.Json.data.scoped -eq $true) $stationOverview.Raw
Assert-True '平台管理员概览为全平台口径' ($platformOverview.Json.data.scoped -eq $false) $platformOverview.Raw
Assert-True '驿站管理员预约数不超过平台总量' `
    ([int]$stationOverview.Json.data.appointments -le [int]$platformOverview.Json.data.appointments) $stationOverview.Raw
Assert-True '驿站管理员诉求数不超过平台总量' `
    ([int]$stationOverview.Json.data.appeals -le [int]$platformOverview.Json.data.appeals) $stationOverview.Raw
Assert-True '平台管理员可见范围覆盖驿站管理员' ($adminAppointments.Json.data.total -ge $stationAppointments.Json.data.total)

$userAppointments = Send-Api -Method Get -Path '/api/appointment/my' -Token $userToken
$foreignAppointments = @($userAppointments.Json.data | Where-Object { $_.userId -ne 1 })
Assert-True '普通用户只能查询本人预约' ($foreignAppointments.Count -eq 0) "越界记录 $($foreignAppointments.Count) 条"

# ---------------------------------------------------------------- 驿站来源
Write-Host "`n[11] 驿站数据来源" -ForegroundColor Cyan
$realStation = Send-Api -Method Get -Path '/api/station/1'
Assert-True '驿站详情返回来源名称' ([bool]$realStation.Json.data.source.sourceName) $realStation.Raw
Assert-True '驿站详情返回来源地址' ($realStation.Json.data.source.sourceUrl -like 'http*')
Assert-True '驿站详情返回核对日期' ($realStation.Json.data.source.verifiedAt -match '^\d{4}-\d{2}-\d{2}$')
Assert-True '驿站详情返回数据状态' (@('VERIFIED', 'PARTIAL', 'MANUAL') -contains $realStation.Json.data.source.dataStatus)
Assert-True '驿站详情返回驿站类型' ([bool]$realStation.Json.data.stationType)
$stationTypePage = Send-Api -Method Get -Path '/api/station/page?current=1&size=10&area=上城区'
Assert-True '按区域筛选返回真实驿站' ($stationTypePage.Json.data.total -ge 1) "实际 $($stationTypePage.Json.data.total)"
$xiaogePage = Send-Api -Method Get -Path '/api/station/page?current=1&size=50&keyword=小哥驿站'
Assert-True '按关键字可检索到小哥驿站' ($xiaogePage.Json.data.total -ge 4) "实际 $($xiaogePage.Json.data.total)"

# ---------------------------------------------------------------- 城市
Write-Host "`n[12] 多城市与定位判断" -ForegroundColor Cyan
$cityList = Send-Api -Method Get -Path '/api/city/list'
Assert-True '城市列表返回 200' ($cityList.Status -eq 200) "实际 $($cityList.Status)"
$cityCount = @($cityList.Json.data).Count
Assert-True '平台覆盖多个城市' ($cityCount -ge 2) "实际 $cityCount 个"
Assert-True '城市列表首项为默认城市' ($cityList.Json.data[0].cityCode -eq '330100') "实际 $($cityList.Json.data[0].cityCode)"
$badCity = @($cityList.Json.data | Where-Object { -not $_.centerLng -or -not $_.centerLat -or $_.stationCount -lt 1 })
Assert-True '每个城市都有中心坐标与驿站数量' ($badCity.Count -eq 0) "异常城市 $($badCity.Count) 个"

$nearestBeijing = Send-Api -Method Get -Path '/api/city/nearest?lng=116.4074&lat=39.9042'
Assert-True '按坐标定位到北京' ($nearestBeijing.Json.data.cityCode -eq '110100') "实际 $($nearestBeijing.Json.data.cityCode)"
$nearestShenzhen = Send-Api -Method Get -Path '/api/city/nearest?lng=114.0579&lat=22.5431'
Assert-True '按坐标定位到深圳' ($nearestShenzhen.Json.data.cityCode -eq '440300') "实际 $($nearestShenzhen.Json.data.cityCode)"

$cityStationPage = Send-Api -Method Get -Path '/api/station/page?current=1&size=50&cityCode=110100'
Assert-True '按城市筛选只返回该城市驿站' (@($cityStationPage.Json.data.records | Where-Object { $_.cityCode -ne '110100' }).Count -eq 0)
Assert-True '城市筛选结果带 cityCode' ([bool]$cityStationPage.Json.data.records[0].cityCode)
$cityAreaSummary = Send-Api -Method Get -Path '/api/station/areas?cityCode=110100'
$cityAreaTotal = 0
foreach ($row in $cityAreaSummary.Json.data) { $cityAreaTotal += $row.count }
Assert-True '城市区域分布与城市驿站总数一致' ($cityAreaTotal -eq $cityStationPage.Json.data.total) "区域合计 $cityAreaTotal / 驿站总数 $($cityStationPage.Json.data.total)"
Assert-True '城市区域分布不含其他城市区县' (@($cityAreaSummary.Json.data | Where-Object { $_.area -eq '上城区' }).Count -eq 0)

$allStationPage = Send-Api -Method Get -Path '/api/station/page?current=1&size=1'
Assert-True '全平台驿站数大于单城市驿站数' ($allStationPage.Json.data.total -gt $cityStationPage.Json.data.total) "全平台 $($allStationPage.Json.data.total) / 北京 $($cityStationPage.Json.data.total)"

# ---------------------------------------------------------------- 坐标与定位
Write-Host "`n[13] 坐标回填与城市定位" -ForegroundColor Cyan
$coordPage = Send-Api -Method Get -Path '/api/station/page?current=1&size=50&cityCode=330100'
$withCoord = @($coordPage.Json.data.records | Where-Object { $_.longitude -and $_.latitude })
Assert-True '驿站接口返回经纬度' ($withCoord.Count -gt 0) "本页 $($withCoord.Count) 条有坐标"
# 每个有坐标的驿站都必须标注来源。AMAP_* 是高德派生；MANUAL_APPROVED 是驿站申请、
# 管理员核准的人工变更（同样要留痕）。断言写成"来源必须可识别"而不是"必须是高德"——
# 后者会在任何一次合法的人工核准之后误报。
$knownSources = @('AMAP_POI', 'AMAP_GEOCODE', 'MANUAL_APPROVED', 'SOURCE')
$unlabeled = @($withCoord | Where-Object { $knownSources -notcontains $_.source.geocodeSource })
Assert-True '坐标来源已标注且可识别' ($unlabeled.Count -eq 0) "未标注或来源不明的记录：$($unlabeled.Count) 条"
Assert-True '多数坐标来源为高德派生' (@($withCoord | Where-Object { $_.source.geocodeSource -like 'AMAP_*' }).Count -ge ($withCoord.Count * 0.8)) "高德派生占比不足 80%"
Assert-True '坐标在中国境内合理范围' (@($withCoord | Where-Object { $_.longitude -lt 70 -or $_.longitude -gt 140 -or $_.latitude -lt 15 -or $_.latitude -gt 55 }).Count -eq 0)
$coordStation = Send-Api -Method Get -Path '/api/station/1'
Assert-True '驿站详情带坐标与坐标来源' ([bool]$coordStation.Json.data.source.geocodeSource -and [bool]$coordStation.Json.data.longitude)
Assert-True 'dataNote 说明坐标非来源给出' ($coordStation.Json.data.source.dataNote -like '*非来源给出*') $coordStation.Json.data.source.dataNote

$locateHangzhou = Send-Api -Method Get -Path '/api/city/locate?lng=120.1551&lat=30.2741'
Assert-True '定位到已覆盖城市（杭州）' ($locateHangzhou.Json.data.covered -eq $true -and $locateHangzhou.Json.data.city.cityCode -eq '330100') "实际 $($locateHangzhou.Json.data.cityName)"
$locateSuzhou = Send-Api -Method Get -Path '/api/city/locate?lng=120.5853&lat=31.2989'
Assert-True '未覆盖城市被正确识别（苏州）' ($locateSuzhou.Json.data.covered -eq $false) "实际 $($locateSuzhou.Json.data.cityName) covered=$($locateSuzhou.Json.data.covered)"
Assert-True '未覆盖时返回真实城市名' ($locateSuzhou.Json.data.cityName -eq '苏州市') "实际 $($locateSuzhou.Json.data.cityName)"
Assert-True '未覆盖时给出最近已覆盖城市' ($locateSuzhou.Json.data.nearest.cityCode -eq '310100') "实际 $($locateSuzhou.Json.data.nearest.cityName)"
$locateFar = Send-Api -Method Get -Path '/api/city/locate?lng=87.6168&lat=43.8256'
Assert-True '远离覆盖范围的定位也能兜底' ($locateFar.Json.data.covered -eq $false -and [bool]$locateFar.Json.data.nearest.cityCode)

# ---------------------------------------------------------------- 营业信息与预约时段
Write-Host "`n[14] 营业信息同步与预约时段校验" -ForegroundColor Cyan
$hourStation = Send-Api -Method Get -Path '/api/station/2'
Assert-True '驿站详情返回营业信息来源' ([bool]$hourStation.Json.data.source.businessSource) "实际 $($hourStation.Json.data.source.businessSource)"
# 口径：营业时间**以高德为准**，来源原值只作为留痕写进 dataNote
Assert-True '营业时间以高德为准' ($hourStation.Json.data.source.businessSource -eq 'AMAP_POI') "实际 $($hourStation.Json.data.source.businessSource)"
Assert-True '高德覆盖来源时保留来源原值留痕' ($hourStation.Json.data.source.dataNote -like '*来源原文的开放时间*') $hourStation.Json.data.source.dataNote

$hourPage = Send-Api -Method Get -Path '/api/station/page?current=1&size=50&cityCode=330100'
$timed = @($hourPage.Json.data.records | Where-Object { $_.openTime -and $_.openTime -notmatch '24\s*小时' -and $_.openTime -match '\d{1,2}[:：]\d{2}\s*[-—~至]\s*\d{1,2}[:：]\d{2}' })
Assert-True '存在营业时间明确的驿站' ($timed.Count -gt 0) "本页 $($timed.Count) 条"
if ($timed.Count -gt 0) {
    $target = $timed[0]
    # 按驿站实际营业时间推导时段：营业时间口径变化后，硬编码时段会失效
    $m = [regex]::Match($target.openTime, '(\d{1,2})[:：](\d{2})\s*[-—~至]\s*(\d{1,2})[:：](\d{2})')
    $sh = [int]$m.Groups[1].Value; $sm = [int]$m.Groups[2].Value
    $eh = [int]$m.Groups[3].Value; $em = [int]$m.Groups[4].Value
    $inSlot = '{0:d2}:{1:d2}-{2:d2}:{3:d2}' -f $sh, $sm, ([Math]::Min(23, $sh + 1)), $sm
    $outSlot = '{0:d2}:{1:d2}-{2:d2}:{3:d2}' -f $eh, $em, ([Math]::Min(23, $eh + 1)), $em

    $inside = Send-Api -Method Post -Path '/api/appointment' -Token $userToken `
        -Body @{ stationId = $target.id; serviceName = '想确认能否借用场地开培训'; date = '2026-12-20'; slot = $inSlot }
    Assert-True '营业时间内的时段可以预约' ($inside.Status -eq 200) "$($target.openTime) → $inSlot → $($inside.Status)"
    $outside = Send-Api -Method Post -Path '/api/appointment' -Token $userToken `
        -Body @{ stationId = $target.id; serviceName = '想确认能否借用场地开培训'; date = '2026-12-21'; slot = $outSlot }
    Assert-True '营业时间外的时段被拒绝（400）' ($outside.Status -eq 400) "$($target.openTime) → $outSlot → $($outside.Status)"
    Assert-True '拒绝提示包含营业时间说明' ($outside.Json.message -like '*营业时间*') $outside.Json.message
}
$allDayPage = Send-Api -Method Get -Path '/api/station/page?current=1&size=20&cityCode=330100&keyword=钱江苑'
if ($allDayPage.Json.data.records.Count -gt 0) {
    $allDayStation = $allDayPage.Json.data.records[0]
    $allDayBooking = Send-Api -Method Post -Path '/api/appointment' -Token $userToken `
        -Body @{ stationId = $allDayStation.id; serviceName = '夜间到达想提前确认门禁'; date = '2026-12-22'; slot = '23:00-23:30' }
    Assert-True '24 小时开放的驿站不受时段限制' ($allDayBooking.Status -eq 200) "实际 $($allDayBooking.Status)"
}

# ---------------------------------------------------------------- 结果
Write-Host "`n==================== 回归测试结果 ====================" -ForegroundColor Cyan
Write-Host ("通过：" + $script:Passed + "  失败：" + $script:Failed)
if ($script:Failed -gt 0) {
    Write-Host "`n失败明细：" -ForegroundColor Red
    $script:Failures | ForEach-Object { Write-Host ("  - " + $_) -ForegroundColor Red }
    exit 1
}
Write-Host '全部接口回归测试通过。' -ForegroundColor Green
exit 0
