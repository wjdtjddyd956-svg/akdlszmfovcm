#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
build_dir="$project_dir/build"

mkdir -p "$build_dir/stubs" "$build_dir/main" "$build_dir/test" "$build_dir/libs"
compiler=(java --module jdk.compiler/com.sun.tools.javac.Main)
find "$project_dir/tools/stubs" -name '*.java' -print0 | xargs -0 "${compiler[@]}" -source 21 -target 21 -encoding UTF-8 -d "$build_dir/stubs"
find "$project_dir/src/main/java" -name '*.java' -print0 | xargs -0 "${compiler[@]}" -source 21 -target 21 -encoding UTF-8 -cp "$build_dir/stubs" -d "$build_dir/main"
find "$project_dir/src/test/java" -name '*.java' -print0 | xargs -0 "${compiler[@]}" -source 21 -target 21 -encoding UTF-8 -cp "$build_dir/main" -d "$build_dir/test"
java -cp "$build_dir/main:$build_dir/test" kr.minecraft.rpg.koreancommands.CommandTranslatorTest

cp -R "$project_dir/src/main/resources/." "$build_dir/main/"
(cd "$build_dir/main" && zip -q -r -FS "$build_dir/libs/KoreanCommands-1.0.0.jar" .)
echo "Built $build_dir/libs/KoreanCommands-1.0.0.jar"
