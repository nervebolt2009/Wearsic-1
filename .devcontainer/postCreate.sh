#!/usr/bin/env bash
set -uo pipefail

echo "--- Post-create setup ---"

sudo chown -R vscode:vscode /home/vscode/.gradle 2>/dev/null || true
mkdir -p /home/vscode/.gradle

if [ ! -f /home/vscode/.gradle/gradle.properties ]; then
  cat > /home/vscode/.gradle/gradle.properties << 'PROPS'
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.daemon=true
org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC -Dfile.encoding=UTF-8
PROPS
  echo "gradle.properties: created"
else
  echo "gradle.properties: already exists, left untouched"
fi

if [ -f gradlew ]; then
  chmod +x gradlew
  echo "gradlew: OK"
else
  echo "gradlew: not found (normal if no repo cloned yet)"
fi

if freebuff --version > /dev/null 2>&1; then
  echo "freebuff: OK ($(freebuff --version))"
else
  echo "freebuff: FAILED"
fi

java -version
