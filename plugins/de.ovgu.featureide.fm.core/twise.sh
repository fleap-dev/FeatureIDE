#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLI_DIR="$SCRIPT_DIR/twise-cli"
PLUGIN_JAR="$SCRIPT_DIR/target/de.ovgu.featureide.fm.core-3.12.0-SNAPSHOT.jar"
RUN_DIR=$(pwd)

cd "$SCRIPT_DIR"
mvn -q package

cd "$CLI_DIR"
mvn -q package

cd "$RUN_DIR"
echo java -cp "$CLI_DIR/target/classes:$PLUGIN_JAR:$SCRIPT_DIR/lib/*" example.TWiseCli "$@"
