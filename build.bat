@echo off
rem PortLookup build script: compile and package.
rem Source files are UTF-8. -encoding UTF-8 is required, otherwise javac
rem reads them as GBK on a Chinese Windows and fails with unmappable characters.
setlocal

echo [1/3] Cleaning old output
del /q *.class 2>nul
del /q PortLookupUtil.jar 2>nul

echo [2/3] Compiling
javac -encoding UTF-8 PortLookupUtil.java
if errorlevel 1 (
    echo Compile failed. Aborted.
    exit /b 1
)

echo [3/3] Packaging jar
jar cfm PortLookupUtil.jar MANIFEST.MF PortLookupUtil*.class
if errorlevel 1 (
    echo Packaging failed. Aborted.
    exit /b 1
)

echo Build finished: PortLookupUtil.jar
endlocal
