@echo off
setlocal
set VERSION=8.13
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (gradle %* & exit /b %ERRORLEVEL%)
set BASE=%USERPROFILE%\.gradle\lumenwave-wrapper
set HOME_DIR=%BASE%\gradle-%VERSION%
if not exist "%HOME_DIR%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  echo Downloading Gradle %VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%BASE%\gradle.zip'; Expand-Archive -Force '%BASE%\gradle.zip' '%BASE%'"
  if errorlevel 1 exit /b 1
)
call "%HOME_DIR%\bin\gradle.bat" %*
