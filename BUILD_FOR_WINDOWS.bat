@echo off
REM Uses: https://github.com/electron-userland/electron-packager
REM To install it globally:
REM
REM     npm install electron-packager -g
REM

cd ink-kmp-mcp\src\jsMain\ink\js\electron
call npm install
call npm test
call npm run build-package -- win64