#!/usr/bin/env bash
# Small wrapper bootstrap. Uses an installed Gradle or downloads Gradle 8.13.
set -euo pipefail
VERSION="8.13"
if [[ -n "${GRADLE_HOME:-}" && -x "${GRADLE_HOME}/bin/gradle" ]]; then
  exec "${GRADLE_HOME}/bin/gradle" "$@"
fi
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/lumenwave-wrapper"
HOME_DIR="$BASE/gradle-$VERSION"
if [[ ! -x "$HOME_DIR/bin/gradle" ]]; then
  mkdir -p "$BASE"
  ZIP="$BASE/gradle-$VERSION-bin.zip"
  echo "Downloading Gradle $VERSION..."
  if command -v curl >/dev/null 2>&1; then curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  else echo "Install Gradle $VERSION or curl/wget first." >&2; exit 1; fi
  if command -v unzip >/dev/null 2>&1; then unzip -q -o "$ZIP" -d "$BASE"
  else echo "unzip is required to unpack Gradle." >&2; exit 1; fi
fi
exec "$HOME_DIR/bin/gradle" "$@"
