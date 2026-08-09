#!/bin/bash
# Gradle wrapper script

# Get the directory of this script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change to the project directory
cd "$DIR"

GRADLE_VERSION="8.11.1"
GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_DIR="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin"

# Download and extract if not exists
if [ ! -d "$GRADLE_DIR" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    mkdir -p "$GRADLE_DIR"
    cd "$GRADLE_DIR"
    wget -q "$GRADLE_URL" -O gradle.zip
    unzip -q gradle.zip
    rm gradle.zip
fi

# Run gradle in the project directory
cd "$DIR"
"$GRADLE_DIR/gradle-$GRADLE_VERSION/bin/gradle" "$@"
