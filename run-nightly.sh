#!/bin/sh
set -eu
mkdir -p target/classes
javac -d target/classes src/main/java/*.java
exec java -cp target/classes EdtechSnapshotMain --once
