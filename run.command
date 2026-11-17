#!/bin/sh
cd "$(dirname "$0")" || exit 1
JAVA_BIN=java
if [ -x /opt/homebrew/opt/openjdk/bin/java ]; then
    JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java
fi
exec "$JAVA_BIN" -jar dist/mac-aarch64/phonebook.jar "$@"
