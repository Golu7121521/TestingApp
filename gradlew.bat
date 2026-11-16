@echo off
setlocal
set GRADLE_VERSION=8.13
set DIST=%USERPROFILE%\.gradle\bootstrap\gradle-%GRADLE_VERSION%
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%USERPROFILE%\.gradle\bootstrap" mkdir "%USERPROFILE%\.gradle\bootstrap"
  powershell -NoProfile -Command "Invoke-WebRequest https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip -OutFile '%USERPROFILE%\.gradle\bootstrap\gradle.zip'; Expand-Archive -Force '%USERPROFILE%\.gradle\bootstrap\gradle.zip' '%USERPROFILE%\.gradle\bootstrap'"
)
call "%DIST%\bin\gradle.bat" %*
