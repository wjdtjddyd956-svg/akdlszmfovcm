#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
repo_dir="$(cd "$project_dir/.." && pwd)"
java_bin="${TYCOON_JDK:-/workspace/toolchains/jdk-25.0.5.0.3}/bin/java"
javac_bin="${TYCOON_JDK:-/workspace/toolchains/jdk-25.0.5.0.3}/bin/javac"
mkdir -p "$project_dir/build/test-current"
"$javac_bin" --release 21 -encoding UTF-8 -d "$project_dir/build/test-current" "$project_dir/src/main/java/kr/minecraft/rpg/koreancommands/CommandTranslator.java" "$project_dir/src/test/java/kr/minecraft/rpg/koreancommands/CommandTranslatorTest.java"
"$java_bin" -cp "$project_dir/build/test-current" kr.minecraft.rpg.koreancommands.CommandTranslatorTest | tee "$project_dir/build/test-results.txt"
python3 "$repo_dir/tools/build_tycoon.py"
