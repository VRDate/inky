cd "`dirname "$0"`"

cd ink-kmp-mcp/src/jsMain/ink/js/electron
npm install
npm test
npm run build-package -- -codesign -zip mac