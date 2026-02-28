cd "`dirname "$0"`"

cd ink-kmp-mcp/src/jsMain/ink/js
npm install
npm test
npm run build-package -- -codesign -zip mac win32 win64 linux
