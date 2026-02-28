cd "`dirname "$0"`"

cd ink-electron
npm install
npm run build-package -- -codesign -zip mac win32 win64 linux
