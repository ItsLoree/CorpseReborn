@echo off
title Setup GitHub - CorpseReborn
color 0B

set SOURCE=C:\Users\Lorenzo\Downloads\CorpseReborn_1.21_v2_source\CorpseReborn_1.21
set GITHUB_USER=ItsLoree
set REPO_NAME=CorpseReborn

echo.
echo  ================================================
echo   Setup GitHub - Prima configurazione
echo   by Griffer
echo  ================================================
echo.
echo  Questo script configura Git e collega il progetto
echo  al tuo repository GitHub.
echo.
echo  PRIMA di continuare assicurati di aver:
echo  1. Creato il repo "CorpseReborn" su github.com
echo     (vai su github.com/new - mettilo PUBBLICO)
echo  2. Installato Git per Windows (git-scm.com)
echo.
pause

:: Installa git se non c'e'
where git >nul 2>&1
if errorlevel 1 (
    echo [INFO] Git non trovato. Installo con Chocolatey...
    choco install git -y
    echo [INFO] Riavvia questo script dopo l'installazione di Git!
    pause & exit
)

echo [OK] Git trovato!

:: Configura nome e email git
echo.
set /p GITNAME="Il tuo nome per Git (es. Griffer): "
set /p GITEMAIL="La tua email GitHub: "
git config --global user.name "%GITNAME%"
git config --global user.email "%GITEMAIL%"

:: Vai nella cartella sorgente
cd /d "%SOURCE%"

:: Copia il workflow di GitHub Actions
if not exist ".github\workflows" mkdir ".github\workflows"
copy /Y "%~dp0.github\workflows\build.yml" ".github\workflows\build.yml" >nul

echo [INFO] Inizializzo repository Git...
git init
git branch -M main
git remote add origin https://github.com/%GITHUB_USER%/%REPO_NAME%.git

echo [INFO] Primo commit...
git add .
git commit -m "Initial commit - CorpseReborn by Griffer"

echo.
echo [INFO] Push su GitHub...
echo        Ti verra' chiesto username e password/token GitHub
echo.
git push -u origin main

echo.
echo  ================================================
echo   Setup completato!
echo.
echo   Repository: https://github.com/%GITHUB_USER%/%REPO_NAME%
echo   Actions:    https://github.com/%GITHUB_USER%/%REPO_NAME%/actions
echo.
echo   Da ora in poi usa "aggiorna.bat" per deployare!
echo  ================================================
echo.
pause
