@echo off
rem Execucao manual (diagnostico). Em producao o BFF roda como servico NSSM "TecalAlmoxarifadoBFF".
cd /d "%~dp0bff-node"
node dist\server.js
