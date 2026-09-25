@echo off
rem Launcher for scripts\sync-oss.ps1 (incremental OSS mirror backup).
rem Parameters pass through, e.g.:  sync-oss.cmd -Verify
rem Scheduled tasks should call:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\sync-oss.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\sync-oss.ps1" %*
exit /b %ERRORLEVEL%
