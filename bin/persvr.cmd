@echo off
echo Starting Persevere

setlocal
set SHELL=cmd.exe

set PACKAGE_HOME=%~dp0..
set NODULES_PATH=%~dp0..

call %PACKAGE_HOME%\packages\narwhal\bin\activate.cmd

set NARWHAL_PATH=%PACKAGE_HOME%
set NODULES_PATH=%PACKAGE_HOME%
set PERSVR_APP=$1
cd %1
set SEA=%cd%
set PORT=$2
set NARWHAL_OPTIMIZATION=-1

rem throw the first parameter away
shift
set params=%1
:loop
shift
if [%1]==[] goto afterloop
set params=%params% %1
goto loop
:afterloop

%PACKAGE_HOME%\packages\jack\bin\jackup