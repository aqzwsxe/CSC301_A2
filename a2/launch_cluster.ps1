# Get the project root directory
$projectRoot = Get-Location
$binDir = "$projectRoot\out\production\CSC301_A2" # Path to your .class files
$configPath = "$projectRoot\a2\config.json"

# Function to start a service
function Start-ServiceInstance($className, $port) {
    Start-Process java -ArgumentList "-cp `"$binDir`"", $className, $port, "`"$configPath`"" -WindowStyle Normal
}

Write-Host "--- Launching CSC301 A2 Cluster ---" -ForegroundColor Cyan

# 1. Start UserServices (Ports 14001-14007)
for ($i=1; $i -le 7; $i++) {
    $port = 14000 + $i
    Write-Host "Starting UserService on $port..."
    Start-ServiceInstance "UserService.UserServiceMain" $port
}

# 2. Start ProductServices (Ports 15001-15007)
for ($i=1; $i -le 7; $i++) {
    $port = 15000 + $i
    Write-Host "Starting ProductService on $port..."
    Start-ServiceInstance "ProductService.ProductServiceMain" $port
}

# 3. Start OrderServices (Ports 16001-16007)
for ($i=1; $i -le 7; $i++) {
    $port = 16000 + $i
    Write-Host "Starting OrderService on $port..."
    Start-ServiceInstance "OrderService.OrderServiceMain" $port
}

# 4. Start ISCS (Load Balancer)
Write-Host "Starting ISCS Load Balancer on 14000..." -ForegroundColor Green
Start-ServiceInstance "ISCS.ISCS" 14000

Write-Host "Cluster is up! Press any key to close all windows (not implemented, use task manager if needed)."