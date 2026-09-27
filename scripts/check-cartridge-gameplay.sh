#!/usr/bin/env bash
set -euo pipefail

if (($# == 0)); then
  echo "Usage: bash scripts/check-cartridge-gameplay.sh image.crt [more.crt ...]" >&2
  exit 2
fi

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT

g++ -std=c++17 -O2 -w -DANDROID -DFRODO_PC \
  -DPRECISE_CPU_CYCLES=1 -DPRECISE_CIA_CYCLES=1 -DPC_IS_POINTER=0 \
  -I"$repo_dir/app/src/main/cpp/emu" -I"$repo_dir/app/src/main/cpp" \
  "$repo_dir/scripts/cartridge_gameplay_smoke.cpp" \
  "$repo_dir"/app/src/main/cpp/emu/*.cpp \
  "$repo_dir"/app/src/main/cpp/emu/pc/*.cpp \
  "$repo_dir"/app/src/main/cpp/emu/rom/*.cpp \
  -o "$build_dir/cartridge-gameplay"

result=0
for crt in "$@"; do
  if ! "$build_dir/cartridge-gameplay" "$crt" >"$build_dir/output" 2>&1; then
    result=1
  fi
  cat "$build_dir/output"
  if grep -q 'Illegal opcode' "$build_dir/output"; then result=1; fi
done
exit "$result"
