@echo off
REM FreeXmlToolkit Updater Script for Windows
REM This script is launched by the application after downloading an update.
REM It waits for the application to exit, then copies the new files and restarts.

setlocal enabledelayedexpansion

set "APP_DIR=%~1"
set "UPDATE_DIR=%~2"
set "LAUNCHER=%~3"

echo ========================================
echo FreeXmlToolkit Updater
echo ========================================
echo.
echo Application directory: %APP_DIR%
echo Update directory: %UPDATE_DIR%
echo Launcher: %LAUNCHER%
echo.

REM Validate parameters
if "%APP_DIR%"=="" (
    echo Error: Application directory not specified
    pause
    exit /b 1
)

if "%UPDATE_DIR%"=="" (
    echo Error: Update directory not specified
    pause
    exit /b 1
)

if "%LAUNCHER%"=="" (
    echo Error: Launcher path not specified
    pause
    exit /b 1
)

echo Waiting for application to exit...
echo.

:wait_loop
REM Check if the application is still running
tasklist /FI "IMAGENAME eq FreeXmlToolkit.exe" 2>NUL | find /I "FreeXmlToolkit.exe" >NUL
if %ERRORLEVEL%==0 (
    echo Application is still running, waiting...
    timeout /t 1 /nobreak >NUL
    goto wait_loop
)

REM Additional wait to ensure all file handles are released
timeout /t 2 /nobreak >NUL

echo Application has exited. Starting update installation...
echo.

REM Find the extracted application folder
set "UPDATE_APP_DIR="
for /d %%d in ("%UPDATE_DIR%\*") do (
    if exist "%%d\FreeXmlToolkit.exe" (
        set "UPDATE_APP_DIR=%%d"
        echo Found update in: %%d
    )
)

REM If not found at first level, check second level (for nested zip structure)
if "%UPDATE_APP_DIR%"=="" (
    for /d %%d in ("%UPDATE_DIR%\*") do (
        for /d %%e in ("%%d\*") do (
            if exist "%%e\FreeXmlToolkit.exe" (
                set "UPDATE_APP_DIR=%%e"
                echo Found update in: %%e
            )
        )
    )
)

if "%UPDATE_APP_DIR%"=="" (
    echo Error: Could not find update files with FreeXmlToolkit.exe
    echo Contents of update directory:
    dir /b "%UPDATE_DIR%"
    pause
    exit /b 1
)

echo.
echo Copying new files...

REM Use xcopy to copy all files, overwriting existing
xcopy /E /Y /I "%UPDATE_APP_DIR%\*" "%APP_DIR%\" >NUL 2>&1

if %ERRORLEVEL% neq 0 (
    echo.
    echo Error: Failed to copy files!
    echo Please try running the updater as Administrator.
    pause
    exit /b 1
)

echo Files copied successfully.
echo.

REM Clean up the update directory
echo Cleaning up temporary files...
rmdir /S /Q "%UPDATE_DIR%" 2>NUL

echo.
echo Update completed successfully!
echo Starting the updated application...
echo.

REM Small delay before starting
timeout /t 1 /nobreak >NUL

REM Start the updated application
start "" "%LAUNCHER%"

exit /b 0
