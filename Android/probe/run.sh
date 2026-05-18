#!/usr/bin/env bash
# Convenience wrapper that points to Android Studio's bundled JDK and runs the probe.
#
# Usage (recommended — keeps the password OUT of argv and shell history):
#   ./run.sh
#   → prompts for username and password interactively
#
# Or pre-populate via env vars (note: visible in `ps` and ~/.zsh_history if set inline):
#   export MAITRI_USERNAME=you@bmu.edu.in
#   read -rs MAITRI_PASSWORD && export MAITRI_PASSWORD
#   ./run.sh

set -euo pipefail

# Prompt interactively if either credential is missing. -s hides the password.
if [[ -z "${MAITRI_USERNAME:-}" ]]; then
  read -rp "Maitri username: " MAITRI_USERNAME
  export MAITRI_USERNAME
fi
if [[ -z "${MAITRI_PASSWORD:-}" ]]; then
  read -rsp "Maitri password: " MAITRI_PASSWORD
  echo
  export MAITRI_PASSWORD
fi
: "${MAITRI_USERNAME:?username is required}"
: "${MAITRI_PASSWORD:?password is required}"

DEFAULT_JAVA_HOME="/Volumes/APPS/Applications/Android Studio.app/Contents/jbr/Contents/Home"
FALLBACK_JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "$DEFAULT_JAVA_HOME" ]]; then
    export JAVA_HOME="$DEFAULT_JAVA_HOME"
  elif [[ -d "$FALLBACK_JAVA_HOME" ]]; then
    export JAVA_HOME="$FALLBACK_JAVA_HOME"
  else
    echo "Could not auto-detect Android Studio's JDK. Set JAVA_HOME explicitly." >&2
    exit 1
  fi
fi

export PATH="$JAVA_HOME/bin:$PATH"

cd "$(dirname "$0")"
exec ./gradlew run -q --console=plain
