#!/usr/bin/env bash
# Removes local build outputs, caches and test debris for this project.
# Never touches tracked files, dist/, local.properties or other projects.
#
#   scripts/clean.sh               remove debris
#   scripts/clean.sh --dry-run     only list what would be removed
#   scripts/clean.sh --stop-daemons  also stop Gradle daemons (affects every
#                                    project on this machine using Gradle 9.6)
set -euo pipefail
cd "$(dirname "$0")/.."

dry_run=false
stop_daemons=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) dry_run=true ;;
    --stop-daemons) stop_daemons=true ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

remove() {
  echo "remove $1"
  $dry_run || rm -rf -- "$1"
}

if $stop_daemons && ! $dry_run; then
  ./gradlew --stop >/dev/null 2>&1 || true
fi

tmp="${TMPDIR:-/tmp}"
tmp="${tmp%/}"
# The build directory is redirected to the system temp dir (see build.gradle.kts).
for target in \
  "$tmp/glass-orb-android-build" \
  "/tmp/glass-orb-gradle-project-cache" \
  ".gradle" ".kotlin" "build" "app/build" ".idea/caches" "captures" ".omx"; do
  if [[ -e "$target" ]]; then remove "$target"; fi
done

while IFS= read -r -d '' file; do
  remove "$file"
done < <(find . -path ./.git -prune -o -type f \
  \( -name '.DS_Store' -o -name '*.hprof' -o -name '*.apk.zip' -o -name '*.log' \) -print0)

echo "clean: done"
