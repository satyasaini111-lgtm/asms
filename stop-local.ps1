Write-Host "=== Stopping ASMS ===" -ForegroundColor Cyan

Write-Host "Stopping Java services..."
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
Write-Host "  Done"

Write-Host "Stopping Docker infrastructure..."
docker compose -f docker-compose.infra.yml down
Write-Host "  Done"

Write-Host "`nAll services stopped." -ForegroundColor Green
