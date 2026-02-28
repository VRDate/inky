#!/usr/bin/env bash
# proto-gen.sh — Generate protobuf bindings for all ink.[ext].model targets.
#
# Usage:  ./proto-gen.sh [cs] [kt] [java] [js] [ts] [py]
#         ./proto-gen.sh           # generates all targets
#         ./proto-gen.sh cs kt     # generates only C# and Kotlin
#
# Requires: protoc 4.28.x (auto-detected from Gradle cache)
# For ts:   npx ts-proto (auto-downloaded via npx)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROTO_DIR="$SCRIPT_DIR/src/main/proto"
GEN_DIR="$SCRIPT_DIR/src/main/gen"

# ── Locate protoc ────────────────────────────────────────────────
find_protoc() {
    # 1. PATH
    if command -v protoc &>/dev/null; then
        echo "$(command -v protoc)"
        return
    fi
    # 2. Gradle cache (Windows)
    local gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
    local cached
    cached=$(find "$gradle_home/caches" -name "protoc-*-$(uname -s | tr '[:upper:]' '[:lower:]')*" -type f 2>/dev/null | sort -V | tail -1)
    if [[ -n "$cached" ]]; then
        echo "$cached"
        return
    fi
    # 3. Gradle cache (Windows-specific exe pattern)
    cached=$(find "$gradle_home/caches" -name "protoc-*-windows-x86_64.exe" -type f 2>/dev/null | sort -V | tail -1)
    if [[ -n "$cached" ]]; then
        echo "$cached"
        return
    fi
    echo "ERROR: protoc not found. Install via: choco install protoc" >&2
    exit 1
}

PROTOC="$(find_protoc)"
echo "protoc: $PROTOC ($("$PROTOC" --version))"

# ── Proto files (ink/[ext]/*.proto layout) ────────────────────────
PROTOS=()
while IFS= read -r f; do
    PROTOS+=("$f")
done < <(find "$PROTO_DIR/ink" -name '*.proto' -type f | sort)
echo "Proto files: ${#PROTOS[@]} (ink.[ext].model packages)"

# ── Target selection ─────────────────────────────────────────────
ALL_TARGETS=(java kt cs py ts)
if [[ $# -eq 0 ]]; then
    TARGETS=("${ALL_TARGETS[@]}")
else
    TARGETS=("$@")
fi

echo "Targets: ${TARGETS[*]}"
echo ""

# ── Count generated files across all ink subdirs ──────────────────
count_gen() {
    local dir="$1" ext="$2"
    find "$dir" -path "*/ink/*" -name "*.$ext" 2>/dev/null | wc -l
}

# ── Generate ─────────────────────────────────────────────────────
for target in "${TARGETS[@]}"; do
    case "$target" in

    java)
        OUT="$SCRIPT_DIR/src/jvmMain/java"
        mkdir -p "$OUT"
        echo "[$target] → $OUT"
        "$PROTOC" \
            --proto_path="$PROTO_DIR" \
            --java_out="$OUT" \
            "${PROTOS[@]}"
        echo "[$target] ✓ $(count_gen "$OUT" java) files"
        ;;

    kt)
        OUT="$SCRIPT_DIR/src/jvmMain/kotlin"
        mkdir -p "$OUT"
        echo "[$target] → $OUT"
        "$PROTOC" \
            --proto_path="$PROTO_DIR" \
            --kotlin_out="$OUT" \
            "${PROTOS[@]}"
        echo "[$target] ✓ $(count_gen "$OUT" kt) files"
        ;;

    cs)
        OUT="$GEN_DIR/csharp"
        mkdir -p "$OUT"
        echo "[$target] → $OUT"
        "$PROTOC" \
            --proto_path="$PROTO_DIR" \
            --csharp_out="$OUT" \
            "${PROTOS[@]}"
        echo "[$target] ✓ $(count_gen "$OUT" cs) files"
        ;;

    py)
        OUT="$GEN_DIR/python"
        mkdir -p "$OUT"
        echo "[$target] → $OUT"
        "$PROTOC" \
            --proto_path="$PROTO_DIR" \
            --python_out="$OUT" \
            --pyi_out="$OUT" \
            "${PROTOS[@]}"
        # Add __init__.py for each package directory
        find "$OUT/ink" -type d -exec touch "{}/__init__.py" \;
        echo "[$target] ✓ $(count_gen "$OUT" py) files (+ .pyi stubs)"
        ;;

    ts|js)
        OUT="$GEN_DIR/ts"
        mkdir -p "$OUT"
        echo "[ts] → $OUT"
        # ts-proto generates TypeScript interfaces (compile to JS with tsc)
        # Install ts-proto locally if not present
        TS_PROTO_DIR="/tmp/proto-gen"
        if [[ ! -f "$TS_PROTO_DIR/node_modules/.bin/protoc-gen-ts_proto" ]]; then
            echo "[ts] Installing ts-proto..."
            mkdir -p "$TS_PROTO_DIR"
            (cd "$TS_PROTO_DIR" && npm init -y --silent 2>/dev/null && npm install ts-proto 2>/dev/null)
        fi
        # On Windows use .cmd shim; on Unix use the bare script
        if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$(uname -o 2>/dev/null)" == "Msys" ]]; then
            TS_PLUGIN="$(cygpath -w "$TS_PROTO_DIR/node_modules/.bin/protoc-gen-ts_proto.cmd")"
        else
            TS_PLUGIN="$TS_PROTO_DIR/node_modules/.bin/protoc-gen-ts_proto"
        fi
        "$PROTOC" \
            --proto_path="$PROTO_DIR" \
            --plugin="protoc-gen-ts_proto=$TS_PLUGIN" \
            --ts_proto_out="$OUT" \
            --ts_proto_opt=esModuleInterop=true \
            --ts_proto_opt=outputJsonMethods=true \
            --ts_proto_opt=outputEncodeMethods=false \
            --ts_proto_opt=outputClientImpl=false \
            "${PROTOS[@]}"
        echo "[ts] ✓ $(count_gen "$OUT" ts) files"
        ;;

    *)
        echo "Unknown target: $target (supported: ${ALL_TARGETS[*]})"
        exit 1
        ;;
    esac
done

echo ""
echo "Done. Generated files:"
find "$GEN_DIR" -type f 2>/dev/null | head -80
echo ""
echo "  java: $(count_gen "$SCRIPT_DIR/src/jvmMain/java" java) files"
echo "  kotlin: $(count_gen "$SCRIPT_DIR/src/jvmMain/kotlin" kt) files"
