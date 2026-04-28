@echo off
setlocal enabledelayedexpansion

echo [EthersMirrors] Building...
call gradlew.bat build
if errorlevel 1 (
    echo [EthersMirrors] BUILD FAILED - aborting deploy
    exit /b 1
)

echo [EthersMirrors] Deploying to modpack...
copy /Y "build\libs\Ethers-Mirrors-1.0.0.jar" "%APPDATA%\ModrinthApp\profiles\OTP\mods\Ethers-Mirrors-1.0.0.jar"
if errorlevel 1 (
    echo [EthersMirrors] Deploy to modpack failed
    exit /b 1
)
echo [EthersMirrors] Modpack updated.

echo [EthersMirrors] Pushing to GitHub...
git add -A
git diff --cached --quiet
if errorlevel 1 (
    git commit -m "Update"
    git push origin HEAD
) else (
    echo [EthersMirrors] Nothing new to commit.
    git push origin HEAD 2>nul
)

echo [EthersMirrors] Done.
endlocal
