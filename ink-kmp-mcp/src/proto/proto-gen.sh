#!/usr/bin/env bash
# proto-gen.sh — Generate protobuf bindings from proto source.
#
# Proto source:  src/proto/ink/model/*.proto     (package ink.model)
#
# Kotlin (KMP):  Wire Gradle plugin (./gradlew generateProtos)
#                Wire → build/generated/source/wire/ink/model/  (package ink.model)
#
# This script generates non-Kotlin targets only:
#   cs    → src/csMain/ink/cs/model/             (namespace Ink.Cs.Model — via csharp_namespace)
#   py    → src/pyMain/ink/py/model/             (package ink.py.model — post-processed)
#   ts    → src/tsMain/ink/ts/model/             (module ink/ts/model — post-processed)
#
# Usage:  ./proto-gen.sh [cs] [py] [ts]
#         ./proto-gen.sh           # generates all targets
#         ./proto-gen.sh cs py     # generates only C# and Python

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$(dirname "$SCRIPT_DIR")"                     # ink-kmp-mcp/src/
PROTO_ROOT="$SCRIPT_DIR"                           # proto_path root (src/proto/)
PROTO_MODEL="$SCRIPT_DIR/ink/model"                # .proto source files

# ── Locate protoc ────────────────────────────────────────────────
find_protoc() {
    command -v protoc && return
    local gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
    local cached
    cached=$(find "$gradle_home/caches" -name "protoc-*-$(uname -s | tr '[:upper:]' '[:lower:]')*" -type f 2>/dev/null | sort -V | tail -1)
    if [[ -n "$cached" ]]; then echo "$cached"; return; fi
    cached=$(find "$gradle_home/caches" -name "protoc-*-windows-x86_64.exe" -type f 2>/dev/null | sort -V | tail -1)
    if [[ -n "$cached" ]]; then echo "$cached"; return; fi
    echo "ERROR: protoc not found. Install via: choco install protoc" >&2
    exit 1
}

PROTOC="$(find_protoc)"
echo "protoc: $PROTOC ($("$PROTOC" --version))"

# ── Proto files ──────────────────────────────────────────────────
PROTOS=("$PROTO_MODEL"/*.proto)
echo "Proto files: ${#PROTOS[@]} (package ink.model)"

# ── Target selection ─────────────────────────────────────────────
ALL_TARGETS=(cs py ts)
if [[ $# -eq 0 ]]; then
    TARGETS=("${ALL_TARGETS[@]}")
else
    TARGETS=("$@")
fi

echo "Targets: ${TARGETS[*]}"
echo ""

# ── Helper ───────────────────────────────────────────────────────
count_ext() { find "$1" -name "*.$2" 2>/dev/null | wc -l; }

# ── Generate ─────────────────────────────────────────────────────
for target in "${TARGETS[@]}"; do
    case "$target" in

    cs)
        # csharp_namespace = "Ink.Cs.Model" controls the namespace
        CS_OUT="$SRC/csMain/ink/cs/model"
        rm -rf "$CS_OUT" 2>/dev/null
        mkdir -p "$CS_OUT"
        echo "[$target] → $CS_OUT"
        "$PROTOC" \
            --proto_path="$PROTO_ROOT" \
            --csharp_out="$CS_OUT" \
            "${PROTOS[@]}"
        echo "[$target] ✓ $(count_ext "$CS_OUT" cs) files → Ink.Cs.Model"
        ;;

    py)
        # protoc output follows proto file path → ink/model/
        # Post-process: rename to ink/py/model/ and fix imports
        PY_OUT="$SRC/pyMain/ink/py/model"
        TMP="$SRC/.py-tmp"
        rm -rf "$TMP" "$PY_OUT" 2>/dev/null
        mkdir -p "$TMP" "$PY_OUT"
        echo "[$target] → $PY_OUT"
        "$PROTOC" \
            --proto_path="$PROTO_ROOT" \
            --python_out="$TMP" \
            --pyi_out="$TMP" \
            "${PROTOS[@]}"
        for f in "$TMP"/ink/model/*; do
            sed 's/ink\.model/ink.py.model/g; s/ink_dot_model/ink_dot_py_dot_model/g' "$f" > "$PY_OUT/$(basename "$f")"
        done
        # __init__.py for package imports
        touch "$SRC/pyMain/ink/__init__.py" "$SRC/pyMain/ink/py/__init__.py" "$PY_OUT/__init__.py"
        rm -rf "$TMP"
        echo "[$target] ✓ $(count_ext "$PY_OUT" py) files → ink.py.model"
        ;;

    ts|js)
        # ts-proto output follows proto file path → ink/model/
        # Post-process: move to ink/ts/model/
        TS_OUT="$SRC/tsMain/ink/ts/model"
        TMP="$SRC/.ts-tmp"
        rm -rf "$TMP" "$TS_OUT" 2>/dev/null
        mkdir -p "$TMP" "$TS_OUT"
        echo "[ts] → $TS_OUT"
        TS_PROTO_DIR="/tmp/proto-gen"
        if [[ ! -f "$TS_PROTO_DIR/node_modules/.bin/protoc-gen-ts_proto" ]]; then
            echo "[ts] Installing ts-proto..."
            mkdir -p "$TS_PROTO_DIR"
            (cd "$TS_PROTO_DIR" && npm init -y --silent 2>/dev/null && npm install ts-proto 2>/dev/null)
        fi
        if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$(uname -o 2>/dev/null)" == "Msys" ]]; then
            TS_PLUGIN="$(cygpath -w "$TS_PROTO_DIR/node_modules/.bin/protoc-gen-ts_proto.cmd")"
        else
            TS_PLUGIN="$TS_PROTO_DIR/node_modules/.bin/protoc-gen-ts_proto"
        fi
        "$PROTOC" \
            --proto_path="$PROTO_ROOT" \
            --plugin="protoc-gen-ts_proto=$TS_PLUGIN" \
            --ts_proto_out="$TMP" \
            --ts_proto_opt=esModuleInterop=true \
            --ts_proto_opt=outputJsonMethods=true \
            --ts_proto_opt=outputEncodeMethods=false \
            --ts_proto_opt=outputClientImpl=false \
            "${PROTOS[@]}"
        for f in "$TMP"/ink/model/*.ts; do
            cp "$f" "$TS_OUT/$(basename "$f")"
        done
        rm -rf "$TMP"
        echo "[ts] ✓ $(count_ext "$TS_OUT" ts) files → ink.ts.model"
        ;;

    *)
        echo "Unknown target: $target (supported: ${ALL_TARGETS[*]})"
        exit 1
        ;;
    esac
done

echo ""
echo "Done."
echo "  proto: src/proto/ink/model/ ($(find "$PROTO_MODEL" -name '*.proto' | wc -l) files)"
echo "  kt:    Wire Gradle plugin → build/generated/source/wire/ink/model/"
echo "  cs:    src/csMain/ink/cs/model/ ($(count_ext "$SRC/csMain/ink/cs/model" cs) files)"
echo "  py:    src/pyMain/ink/py/model/ ($(count_ext "$SRC/pyMain/ink/py/model" py) files)"
echo "  ts:    src/tsMain/ink/ts/model/ ($(count_ext "$SRC/tsMain/ink/ts/model" ts) files)"
