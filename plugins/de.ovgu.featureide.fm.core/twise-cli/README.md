# FeatureIDE TWise CLI Example

This is a small standalone Java project that depends on the local
`de.ovgu.featureide.fm.core` plugin jar and runs
`TWiseConfigurationGenerator`.

Build the plugin first if the jar is missing or stale:

```sh
cd ..
mvn package
```

Build and run this CLI:

```sh
mvn package
java -cp "target/classes:../target/de.ovgu.featureide.fm.core-3.12.0-SNAPSHOT.jar:../lib/*" example.TWiseCli --t 2 --iterations 1 --output target/sample.csv
```

Run with the sample CNF file:

```sh
java -cp "target/classes:../target/de.ovgu.featureide.fm.core-3.12.0-SNAPSHOT.jar:../lib/*" example.TWiseCli --cnf examples/simple.cnf --t 2 --output target/simple-sample.csv
```

The `--output` option is required. It writes a CSV matrix where the header is the ordered feature list and each generated configuration is encoded as `1` for selected features and `0` for deselected features. DIMACS variables marked as auxiliary with a `$` suffix in the variable comment are omitted from the CSV columns.

CNF files can use named variables:

```text
v Root A B
Root
-Root A B
-A -B
```

They can also use DIMACS-style integers:

```text
p cnf 3 3
1 0
-1 2 3 0
-2 -3 0
```
