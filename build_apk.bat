@echo off
cd /d "%~dp0"
echo ========================================================
echo   DANG TIEN HANH BIEN DICH CUSTOM TTS SANG APK...
echo ========================================================
echo.

python build_apk.py
if errorlevel 1 (
    echo.
    echo Thu lai bang lenh py...
    py -3 build_apk.py
)

if errorlevel 1 (
    echo.
    echo ========================================================
    echo [LOI] Khong the bien dich APK! Vui long kiem tra loi o tren.
    echo ========================================================
) else (
    echo.
    echo ========================================================
    echo   THANH CONG! TEP APK DA DUOC TAO THANH CONG!
    echo ========================================================
)

echo.
pause
