cd "`dirname "$0"`"

cd ink-electron
npm install
npm run build-package -- mac
