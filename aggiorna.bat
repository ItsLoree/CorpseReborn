@echo off
title CorpseReborn - Auto Deploy by Griffer
color 0A

set SOURCE=C:\Users\Lorenzo\Downloads\CorpseReborn_1.21_v2_source\CorpseReborn_1.21
set SERVER=C:\Users\Lorenzo\Downloads\ServerMC_Test\ServerMC
set GITHUB_USER=ItsLoree
set REPO_NAME=CorpseReborn
set GITHUB_TOKEN=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

echo.
echo  ================================================
echo   CorpseReborn - Auto Deploy  ^|  by Griffer
echo  ================================================
echo.

cd /d "%SOURCE%"
if errorlevel 1 (
    echo [ERRORE] Cartella sorgente non trovata!
    pause & exit
)

echo [1/4] Aggiungo i file modificati...
git add .

echo [2/4] Creo il commit...
set MSG=update
git commit -m "%MSG%"

echo [3/4] Push su GitHub...
git push -u origin main
if errorlevel 1 (
    echo [ERRORE] Push fallito!
    pause & exit
)

echo.
echo [4/4] Aspetto che GitHub Actions compili...
echo.

set /a COUNT=75
:WAIT
cls
echo  ================================================
echo   CorpseReborn - Auto Deploy  ^|  by Griffer
echo  ================================================
echo.
echo  GitHub sta compilando... attendo %COUNT% secondi
echo  Progresso: https://github.com/%GITHUB_USER%/%REPO_NAME%/actions
echo.
timeout /t 1 /nobreak >nul
set /a COUNT=%COUNT%-1
if %COUNT% GTR 0 goto WAIT

echo.
echo [DOWNLOAD] Scarico il JAR da GitHub...
echo.

powershell -Command ^
  "$token = '%GITHUB_TOKEN%';" ^
  "$headers = @{'Authorization'='Bearer ' + $token; 'Accept'='application/vnd.github+json'};" ^
  "Write-Host 'Cerco ultima build...';" ^
  "$runs = Invoke-RestMethod -Uri 'https://api.github.com/repos/%GITHUB_USER%/%REPO_NAME%/actions/runs?branch=main&per_page=1' -Headers $headers;" ^
  "$run = $runs.workflow_runs[0];" ^
  "Write-Host ('Stato build: ' + $run.status + ' - ' + $run.conclusion);" ^
  "if ($run.conclusion -ne 'success') { Write-Host 'Build non ancora completata o fallita! Vai su Actions per controllare.'; exit 1; }" ^
  "$runId = $run.id;" ^
  "$artifacts = Invoke-RestMethod -Uri \"https://api.github.com/repos/%GITHUB_USER%/%REPO_NAME%/actions/runs/$runId/artifacts\" -Headers $headers;" ^
  "$artifact = $artifacts.artifacts | Where-Object { $_.name -eq 'CorpseReborn-jar' } | Select-Object -First 1;" ^
  "Write-Host ('Download artifact: ' + $artifact.name);" ^
  "$downloadUrl = $artifact.archive_download_url;" ^
  "Invoke-WebRequest -Uri $downloadUrl -Headers $headers -OutFile '%TEMP%\CorpseReborn-jar.zip';" ^
  "Write-Host 'Download completato! Estraggo...';" ^
  "Expand-Archive -Path '%TEMP%\CorpseReborn-jar.zip' -DestinationPath '%TEMP%\CorpseReborn-jar' -Force;" ^
  "$jar = Get-ChildItem '%TEMP%\CorpseReborn-jar\*.jar' | Select-Object -First 1;" ^
  "Copy-Item $jar.FullName -Destination '%SERVER%\plugins\' -Force;" ^
  "Write-Host ('JAR copiato in plugins: ' + $jar.Name);" ^
  "Write-Host 'SUCCESSO!'"

if errorlevel 1 (
    echo.
    echo [ATTENZIONE] Download automatico fallito.
    echo Scarica manualmente da: https://github.com/%GITHUB_USER%/%REPO_NAME%/actions
    pause
) else (
    echo.
    echo [SERVER] Riavvio il server...
    taskkill /FI "WINDOWTITLE eq Server Minecraft*" /F >nul 2>&1
    timeout /t 2 /nobreak >nul
    start "Server Minecraft - CorpseReborn" cmd /k "cd /d \"%SERVER%\" && java -Xmx2G -Xms1G -jar paper.jar nogui"
    echo.
    echo  ================================================
    echo   Deploy completato! Server riavviato!
    echo   by Griffer
    echo  ================================================
)
echo.
pause