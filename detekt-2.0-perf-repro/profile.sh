#!/usr/bin/env bash
# ABOUTME: Reproduce the detekt 2.0.0-alpha.3 ktlint-wrapper perf regression vs 1.23.8.
# ABOUTME: Downloads CLI jars on first run, runs timed cold-JVM passes, optionally captures flame graphs.

set -euo pipefail

# ── locations ─────────────────────────────────────────────────────────────────
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/.." && pwd)"
JARS="$HERE/jars"
P_OLD="$HERE/profiles-1.23.8"
P_NEW="$HERE/profiles-2.0.0-alpha.3"
mkdir -p "$JARS" "$P_OLD" "$P_NEW"

# ── flags ─────────────────────────────────────────────────────────────────────
RUNS=3
DO_FLAMES=0
DO_NEGATIVE_CONTROL=0
DO_ALL_RULES=0
usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Reproduces detekt 1.23.8 vs 2.0.0-alpha.3 perf comparison on this repo's
Kotlin sources, with the formatting/ktlint plugin loaded (the regressing
workload).

Options:
  --runs N            cold-JVM runs per version (default: 3)
  --flames            also capture CPU + alloc flame graphs via async-profiler
  --negative-control  also run the default-rules-only comparison (no plugins)
  --all-rules         also run --all-rules comparison
  -h, --help          show this help

Outputs land in detekt-2.0-perf-repro/profiles-{1.23.8,2.0.0-alpha.3}/.
EOF
}
while [[ $# -gt 0 ]]; do
  case "$1" in
    --runs)              RUNS="$2"; shift 2;;
    --flames)            DO_FLAMES=1; shift;;
    --negative-control)  DO_NEGATIVE_CONTROL=1; shift;;
    --all-rules)         DO_ALL_RULES=1; shift;;
    -h|--help)           usage; exit 0;;
    *) echo "unknown arg: $1" >&2; usage; exit 2;;
  esac
done

# ── platform detection ────────────────────────────────────────────────────────
case "$(uname -s)" in
  Darwin) TIME_BIN=/usr/bin/time; TIME_FLAG=-l; ASPROF_LIBNAME=libasyncProfiler.dylib;;
  Linux)  TIME_BIN=/usr/bin/time; TIME_FLAG=-v; ASPROF_LIBNAME=libasyncProfiler.so;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 2;;
esac

ASPROF_LIB=""
if [[ $DO_FLAMES -eq 1 ]]; then
  for cand in /opt/async-profiler/lib /usr/local/lib /opt/homebrew/lib; do
    if [[ -f "$cand/$ASPROF_LIBNAME" ]]; then ASPROF_LIB="$cand/$ASPROF_LIBNAME"; break; fi
  done
  if [[ -z "$ASPROF_LIB" ]]; then
    echo "asprof agent not found ($ASPROF_LIBNAME). Install async-profiler or drop --flames." >&2
    exit 2
  fi
fi

# ── download jars (skip if cached) ────────────────────────────────────────────
fetch() {  # fetch <url> <dest>
  local url="$1" dest="$2"
  if [[ -s "$dest" ]]; then return; fi
  echo "  ↓ $(basename "$dest")"
  curl -fsSL -o "$dest" "$url"
}
echo "==> jars"
fetch "https://github.com/detekt/detekt/releases/download/v1.23.8/detekt-cli-1.23.8-all.jar"             "$JARS/detekt-cli-1.23.8-all.jar"
fetch "https://github.com/detekt/detekt/releases/download/v1.23.8/detekt-formatting-1.23.8.jar"          "$JARS/detekt-formatting-1.23.8.jar"
fetch "https://github.com/detekt/detekt/releases/download/v2.0.0-alpha.3/detekt-cli-2.0.0-alpha.3-all.jar" "$JARS/detekt-cli-2.0.0-alpha.3-all.jar"
fetch "https://github.com/detekt/detekt/releases/download/v2.0.0-alpha.3/detekt-rules-ktlint-wrapper-2.0.0-alpha.3.jar" "$JARS/detekt-rules-ktlint-wrapper-2.0.0-alpha.3.jar"
# Manually fetch the shadow jar that the alpha cli-all should bundle but doesn't
fetch "https://repo.maven.apache.org/maven2/dev/detekt/ktlint-repackage/2.0.0-alpha.3/ktlint-repackage-2.0.0-alpha.3-all.jar" "$JARS/ktlint-repackage-2.0.0-alpha.3-all.jar"

# ── build the input set ───────────────────────────────────────────────────────
echo "==> input set"
INPUTS=()
for d in "$REPO_ROOT"/*/src/main/kotlin "$REPO_ROOT"/*/src/test/kotlin; do
  [[ -d "$d" ]] && INPUTS+=("$d")
done
[[ -d "$REPO_ROOT/documentation-website/Writerside/snippets" ]] && INPUTS+=("$REPO_ROOT/documentation-website/Writerside/snippets")
[[ -d "$REPO_ROOT/samples" ]] && INPUTS+=("$REPO_ROOT/samples")
INPUTS_COMMA="$(IFS=, ; echo "${INPUTS[*]}")"
INPUTS_COLON="$(IFS=: ; echo "${INPUTS[*]}")"
echo "$INPUTS_COMMA" > "$HERE/inputs-comma.txt"
echo "$INPUTS_COLON" > "$HERE/inputs-colon.txt"
echo "  ${#INPUTS[@]} source roots, $(echo "${INPUTS[@]}" | xargs -n1 -I{} find {} -name '*.kt' 2>/dev/null | wc -l | tr -d ' ') .kt files"

# ── run a single cold-JVM measurement and append to summary file ──────────────
# usage: measure <label> <out_dir> <run_idx> <time_log_name> <java_args...>
# detekt cli exits 2 when findings are present; treat that as success.
measure() {
  local label="$1" out_dir="$2" idx="$3" log="$4"; shift 4
  set +e
  "$TIME_BIN" "$TIME_FLAG" java "$@" > "$out_dir/$log.out" 2> "$out_dir/$log.log"
  local rc=$?
  set -e
  if [[ $rc -ne 0 && $rc -ne 2 ]]; then
    echo "  run $idx FAILED with exit $rc — see $out_dir/$log.log"
    tail -5 "$out_dir/$log.log" | sed 's/^/    /'
    return 0
  fi
  if [[ "$(uname -s)" == "Darwin" ]]; then
    awk -v idx="$idx" '/real/ {wall=$1} / user/ {cpu=$3} /maximum resident set size/ {rss=$1}
         END {printf "  run %s: wall=%ss cpu=%ss rss=%.2fGB\n", idx, wall, cpu, rss/1024/1024/1024}' \
         "$out_dir/$log.log"
  else
    awk -v idx="$idx" '/Elapsed/ {wall=$NF} /User time/ {cpu=$NF} /Maximum resident/ {rss=$NF}
         END {printf "  run %s: wall=%s cpu=%s rss=%.2fMB\n", idx, wall, cpu, rss/1024}' \
         "$out_dir/$log.log"
  fi
}

# ── workload: 1.23.8 + detekt-formatting (the baseline) ───────────────────────
echo "==> 1.23.8 + detekt-formatting ($RUNS runs)"
for i in $(seq 1 "$RUNS"); do
  measure "1.23.8/fmt" "$P_OLD" "$i" "fmt-run-$i" \
    -jar "$JARS/detekt-cli-1.23.8-all.jar" \
    --parallel \
    --input "$INPUTS_COMMA" \
    --plugins "$JARS/detekt-formatting-1.23.8.jar"
done

# ── workload: 2.0.0-alpha.3 + ktlint-wrapper (the regressing one) ─────────────
echo "==> 2.0.0-alpha.3 + ktlint-wrapper + ktlint-repackage ($RUNS runs)"
ALPHA_PLUGINS="$JARS/detekt-rules-ktlint-wrapper-2.0.0-alpha.3.jar:$JARS/ktlint-repackage-2.0.0-alpha.3-all.jar"
for i in $(seq 1 "$RUNS"); do
  measure "alpha/fmt" "$P_NEW" "$i" "fmt-run-$i" \
    -jar "$JARS/detekt-cli-2.0.0-alpha.3-all.jar" \
    --parallel \
    --input "$INPUTS_COLON" \
    --plugins "$ALPHA_PLUGINS"
done

# ── optional: default-rules negative control ──────────────────────────────────
if [[ $DO_NEGATIVE_CONTROL -eq 1 ]]; then
  echo "==> default rules only — negative control ($RUNS runs each)"
  for i in $(seq 1 "$RUNS"); do
    measure "1.23.8/default" "$P_OLD" "$i" "run-$i" \
      -jar "$JARS/detekt-cli-1.23.8-all.jar" --parallel --input "$INPUTS_COMMA"
  done
  for i in $(seq 1 "$RUNS"); do
    measure "alpha/default"  "$P_NEW" "$i" "run-$i" \
      -jar "$JARS/detekt-cli-2.0.0-alpha.3-all.jar" --parallel --input "$INPUTS_COLON"
  done
fi

# ── optional: --all-rules ─────────────────────────────────────────────────────
if [[ $DO_ALL_RULES -eq 1 ]]; then
  echo "==> --all-rules ($RUNS runs each)"
  for i in $(seq 1 "$RUNS"); do
    measure "1.23.8/all-rules" "$P_OLD" "$i" "allrules-run-$i" \
      -jar "$JARS/detekt-cli-1.23.8-all.jar" --all-rules --parallel --input "$INPUTS_COMMA"
  done
  for i in $(seq 1 "$RUNS"); do
    measure "alpha/all-rules"  "$P_NEW" "$i" "allrules-run-$i" \
      -jar "$JARS/detekt-cli-2.0.0-alpha.3-all.jar" --all-rules --parallel --input "$INPUTS_COLON"
  done
fi

# ── optional: capture flame graphs ────────────────────────────────────────────
if [[ $DO_FLAMES -eq 1 ]]; then
  echo "==> async-profiler flame graphs (CPU + alloc, both versions, formatting workload)"
  for event in cpu alloc; do
    measure "1.23.8/fmt-$event" "$P_OLD" "1" "fmt-$event" \
      "-agentpath:$ASPROF_LIB=start,event=$event,file=$P_OLD/$event-fmt.html" \
      "-Xlog:gc*:file=$P_OLD/gc-fmt-$event.log" \
      -jar "$JARS/detekt-cli-1.23.8-all.jar" --parallel \
      --input "$INPUTS_COMMA" --plugins "$JARS/detekt-formatting-1.23.8.jar"
    measure "alpha/fmt-$event" "$P_NEW" "1" "fmt-$event" \
      "-agentpath:$ASPROF_LIB=start,event=$event,file=$P_NEW/$event-fmt.html" \
      "-Xlog:gc*:file=$P_NEW/gc-fmt-$event.log" \
      -jar "$JARS/detekt-cli-2.0.0-alpha.3-all.jar" --parallel \
      --input "$INPUTS_COLON" --plugins "$ALPHA_PLUGINS"
  done
  echo "  flame graphs:"
  ls "$P_OLD"/*-fmt.html "$P_NEW"/*-fmt.html
fi

# ── summary ───────────────────────────────────────────────────────────────────
echo ""
echo "==> done."
echo "Per-run log files in:"
echo "  $P_OLD/"
echo "  $P_NEW/"
echo "Inputs at:    $HERE/inputs-{comma,colon}.txt"
[[ $DO_FLAMES -eq 1 ]] && echo "Flame graphs: $P_OLD/*.html, $P_NEW/*.html"
echo ""
echo "Tip: 'grep -E \"real|maximum resident\" $P_OLD/fmt-run-*.log' for per-run timings."
