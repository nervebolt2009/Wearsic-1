#!/data/data/com.termux/files/usr/bin/bash
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
APP="$SCRIPT_DIR/build/install/wearsic-server/bin/wearsic-server"

if [ ! -x "$APP" ]; then
  echo "Missing $APP; run ./gradlew installDist first." >&2
  exit 1
fi

export JAVA_OPTS="${JAVA_OPTS:--Xms32m -Xmx192m -XX:+UseSerialGC -XX:TieredStopAtLevel=1}"
export PORT="${PORT:-8080}"
export WEARSIC_DB_PATH="${WEARSIC_DB_PATH:-$SCRIPT_DIR/wearsic.db}"

exec "$APP"
