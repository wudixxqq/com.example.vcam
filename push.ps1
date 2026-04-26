$ErrorActionPreference = "Continue"
Write-Host "Starting push..."
Set-Location "e:\com.example.vcam"
Write-Host "Current directory: $(Get-Location)"
Write-Host "Git status:"
& git status
Write-Host ""
Write-Host "Git remote:"
& git remote -v
Write-Host ""
Write-Host "Pushing to origin main..."
& git push origin main 2>&1
Write-Host "Push completed with exit code: $LASTEXITCODE"
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
