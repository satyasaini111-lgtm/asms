$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "=== ASMS Local Dev Startup ===" -ForegroundColor Cyan

# Step 1: Start infrastructure
Write-Host "`n[1/3] Starting infrastructure (MongoDB, Redis, Kafka)..." -ForegroundColor Yellow
docker compose -f docker-compose.infra.yml up -d
Start-Sleep -Seconds 10

# Step 2: Build
Write-Host "`n[2/3] Building all services..." -ForegroundColor Yellow
mvn package -DskipTests -q
if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FAILED" -ForegroundColor Red; exit 1 }

# Step 3: Start services
Write-Host "`n[3/3] Starting microservices..." -ForegroundColor Yellow
if (-not (Test-Path "logs")) { New-Item -ItemType Directory -Path "logs" -Force | Out-Null }

$services = @(
    @{name="user-service";         port=8081; jar="user-service\target\user-service-1.0.0-SNAPSHOT.jar"},
    @{name="amenity-service";      port=8083; jar="amenity-service\target\amenity-service-1.0.0-SNAPSHOT.jar"},
    @{name="support-service";      port=8084; jar="support-service\target\support-service-1.0.0-SNAPSHOT.jar"},
    @{name="visitor-service";      port=8085; jar="visitor-service\target\visitor-service-1.0.0-SNAPSHOT.jar"},
    @{name="payment-service";      port=8086; jar="payment-service\target\payment-service-1.0.0-SNAPSHOT.jar"},
    @{name="billing-service";      port=8087; jar="billing-service\target\billing-service-1.0.0-SNAPSHOT.jar"},
    @{name="workflow-service";     port=8088; jar="workflow-service\target\workflow-service-1.0.0-SNAPSHOT.jar"},
    @{name="notification-service"; port=8089; jar="notification-service\target\notification-service-1.0.0-SNAPSHOT.jar"},
    @{name="helpbot-service";      port=8090; jar="helpbot-service\target\helpbot-service-1.0.0-SNAPSHOT.jar"}
)

foreach ($svc in $services) {
    Start-Process -FilePath "java" -ArgumentList "--enable-preview", "-jar", $svc.jar `
        -WindowStyle Hidden `
        -RedirectStandardOutput "logs\$($svc.name).log" `
        -RedirectStandardError "logs\$($svc.name).err.log"
    Write-Host "  Started $($svc.name) on port $($svc.port)"
}

# Wait and health check
Write-Host "`nWaiting 30s for startup..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

Write-Host "`n=== Health Check ===" -ForegroundColor Cyan
foreach ($svc in $services) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:$($svc.port)/actuator/health" -TimeoutSec 5 -ErrorAction Stop
        Write-Host "  $($svc.name):$($svc.port)  $($r.status)" -ForegroundColor Green
    } catch {
        Write-Host "  $($svc.name):$($svc.port)  DOWN" -ForegroundColor Red
    }
}

Write-Host "`n=== Access Points ===" -ForegroundColor Cyan
Write-Host "  Swagger UI:  http://localhost:8081/swagger-ui.html (user-service)"
Write-Host "  API Base:    http://localhost:{port}/api/v1/{resource}"
Write-Host "  Auth Header: X-User-Id: admin-001, X-User-Role: ADMIN"
Write-Host "  Logs:        ./logs/{service-name}.log"
