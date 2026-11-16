#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.13
DIST="$HOME/.gradle/bootstrap/gradle-$GRADLE_VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$HOME/.gradle/bootstrap"
  ARCHIVE="$HOME/.gradle/bootstrap/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ARCHIVE"
  else
    wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$ARCHIVE"
  fi
  unzip -q -o "$ARCHIVE" -d "$HOME/.gradle/bootstrap"
fi
exec "$DIST/bin/gradle" "$@"
