#!/usr/bin/env bash
# Usage: bash qa/run-jvm-check.sh /path/to/6.1-checkout /path/to/HTQ-5.3-runtime-official.zip /path/to/HTQ-5.3-native-specs-official.zip
set -euo pipefail
cd "$(dirname "$0")/.."
base=$(realpath "${1:?6.1 checkout required}")
runtime=$(realpath "${2:?runtime archive required}")
spec=$(realpath "${3:?native specification archive required}")
parser="$base/core/src/main/kotlin/vn/htq/core/Json.kt"
[[ $(git hash-object "$parser") == ca2b054b583fa0c43d9743be2a445887c3245fdb ]]
projection="app/src/main/assets/htq_ui/content.json"
before=$(sha256sum "$projection")
python3 scripts/build_ui_projection.py "$runtime" "$spec"
[[ $(sha256sum "$projection") == "$before" ]]
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
kotlinc "$parser" core/src/main/kotlin/vn/htq/core/ui/*.kt qa/UiContentCheck.kt -include-runtime -d "$work/ui-check.jar"
java -jar "$work/ui-check.jar" "$projection" "$runtime"
