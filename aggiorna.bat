@echo off
title CorpseReborn - Auto Deploy by Griffer
color 0A

set SOURCE=C:\Users\Lorenzo\Downloads\CorpseReborn_1.21_v2_source\CorpseReborn_1.21
set SERVER=C:\Users\Lorenzo\Downloads\ServerMC_Test\ServerMC
set GITHUB_USER=ItsLoree
set REPO_NAME=CorpseReborn
set ARTIFACT_NAME=CorpseReborn-jar

echo.
echo  ================================================
echo   CorpseReborn - Auto Deploy  ^|  by Griffer
echo  ================================================
echo.

:: Vai nella cartella sorgente
cd /d "%SOURCE%"
if errorlevel 1 (
    echo [ERRORE] Cartella sorgente non trovata: %SOURCE%
    pause & exit
)

:: Controlla se git e' inizializzato
if not exist ".git" (
    echo [INFO] Inizializzo repository Git...
    git init
    git branch -M main
    git remote add origin https://github.com/%GITHUB_USER%/%REPO_NAME%.git
)

echo [1/4] Aggiungo i file modificati...
git add .

echo [2/4] Creo il commit...
set /p MSG="Messaggio commit (invio per usare 'update'): "
if "%MSG%"=="" set MSG=update
git commit -m "%MSG%"

echo [3/4] Push su GitHub...
git push -u origin main
if errorlevel 1 (
    echo [ERRORE] Push fallito! Controlla le credenziali GitHub.
    pause & exit
)

echo.
echo [4/4] Aspetto che GitHub Actions compili...
echo       (di solito ci vogliono 60-90 secondi)
echo.

:: Aspetta 90 secondi per la compilazione
set /a COUNT=90
:WAIT
cls
echo  ================================================
echo   CorpseReborn - Auto Deploy  ^|  by Griffer
echo  ================================================
echo.
echo  Push completato! GitHub sta compilando...
echo  Attendo: %COUNT% secondi...
echo.
echo  Puoi vedere il progresso su:
echo  https://github.com/%GITHUB_USER%/%REPO_NAME%/actions
echo.
timeout /t 1 /nobreak >nul
set /a COUNT=%COUNT%-1
if %COUNT% GTR 0 goto WAIT

echo.
echo [DOWNLOAD] Scarico il JAR da GitHub...
echo.

:: Usa PowerShell per scaricare l'artifact da GitHub
powershell -Command ^
  "$headers = @{'Accept'='application/vnd.github+json'};" ^
  "$runs = Invoke-RestMethod -Uri 'https://api.github.com/repos/%GITHUB_USER%/%REPO_NAME%/actions/runs?branch=main&status=success&per_page=1' -Headers $headers;" ^
  "$runId = $runs.workflow_runs[0].id;" ^
  "Write-Host 'Run ID:' $runId;" ^
  "$artifacts = Invoke-RestMethod -Uri \"https://api.github.com/repos/%GITHUB_USER%/%REPO_NAME%/actions/runs/$runId/artifacts\" -Headers $headers;" ^
  "$artifact = $artifacts.artifacts | Where-Object { $_.name -eq '%ARTIFACT_NAME%' } | Select-Object -First 1;" ^
  "Write-Host 'Artifact:' $artifact.name;" ^
  "Write-Host 'NOTA: Gli artifact richiedono autenticazione GitHub per il download automatico.'" ^
  "Write-Host 'Scarica manualmente da: https://github.com/%GITHUB_USER%/%REPO_NAME%/actions'"

echo.
echo  ================================================
echo   ISTRUZIONI DOWNLOAD MANUALE:
echo  ================================================
echo.
echo  1. Vai su: https://github.com/%GITHUB_USER%/%REPO_NAME%/actions
echo  2. Clicca sull'ultima build (verde)
echo  3. In basso trovi "Artifacts" - clicca su "CorpseReborn-jar"
echo  4. Estrai il .jar scaricato
echo  5. Copialo in: %SERVER%\plugins\
echo  6. Poi premi un tasto qui per riavviare il server!
echo.
pause

echo.
echo [SERVER] Riavvio il server...

:: Ferma il server se sta girando (cerca la finestra)
taskkill /FI "WINDOWTITLE eq Server Minecraft*" /F >nul 2>&1
timeout /t 3 /nobreak >nul

:: Riavvia il server
start "Server Minecraft - CorpseReborn" cmd /k "cd /d \"%SERVER%\" && java -Xmx2G -Xms1G -jar paper.jar nogui"

echo.
echo  ================================================
echo   Deploy completato! Server riavviato!  
echo   by Griffer
echo  ================================================
echo.
pause
