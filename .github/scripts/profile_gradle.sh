#!/usr/bin/env bash
# Record coarse-grained wall time and produce Gradle task-level HTML profiles.
# Does not log command environment or signing inputs.
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo 'Usage: profile_gradle.sh <verify|manifest|release> <gradle-task> [other-tasks...]' >&2
  exit 2
fi
phase="$1"
shift
case "$phase" in
  verify|manifest|release) ;;
  *) echo "Unknown build phase: $phase" >&2; exit 2 ;;
esac

metrics='build/ci-metrics/gradle-steps.tsv'
mkdir -p "$(dirname "$metrics")"
if [[ ! -e "$metrics" ]]; then
  printf 'phase\twall_seconds\texit_code\n' > "$metrics"
fi

started="$(date +%s)"
set +e
gradle --no-daemon --profile --console=plain "$@"
result=$?
set -e
seconds=$(( $(date +%s) - started ))
printf '%s\t%s\t%s\n' "$phase" "$seconds" "$result" >> "$metrics"
echo "Gradle phase ${phase}: ${seconds}s (exit ${result})"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    printf '### Gradle %s\n\n' "$phase"
    printf 'Wall time: **%s seconds**; exit code: **%s**.\n\n' "$seconds" "$result"
    printf 'Task timings: download the `MN-StreamX-ci-diagnostics` artifact and open the Gradle HTML profile.\n\n'
  } >> "$GITHUB_STEP_SUMMARY"
fi
exit "$result"