#!/usr/bin/env sh
DIRNAME=$(dirname "$0")
APP_HOME=$(cd "$DIRNAME/.." && pwd -P)
exec gradle --project-dir "$APP_HOME" "$@"
