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

The `--output` option is required. It writes a CSV matrix where the header is the ordered feature list and each generated configuration is encoded as `1` for selected features and `0` for deselected features. New DIMACS files use typed comments such as `c var <id> <name>` for variable names. Variables whose names start with `__` are treated as auxiliary variables and are omitted from the sampled feature coverage and CSV columns. Legacy DIMACS variable comments such as `c 1 Feature` and `c 3$ helper` can be read with `--old-dimacs`.

CNF files can use named variables:

```text
v Root A B
Root
-Root A B
-A -B
```

They can also use DIMACS-style integers:

```text
c var 1 A
c var 2 B
c var 3 __expr_1
p cnf 3 3
1 0
-1 2 3 0
-2 -3 0
```

Use `--old-dimacs` for the previous variable-comment format:

```text
c 1 A
c 2 B
c 3$ helper
p cnf 3 3
1 0
-1 2 3 0
-2 -3 0
```
