package example;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TWiseCliTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void newDimacsCommentsDefineRealFeatureNamesAndHideAuxiliaryVariables() throws Exception {
		final Path cnf = temporaryFolder.newFile("new.dimacs").toPath();
		final Path output = temporaryFolder.newFile("sample.csv").toPath();
		Files.write(cnf, (
			"c mode coerced\n" +
			"c arch x86\n" +
			"c var 1 FEATURE_A\n" +
			"c var 2 FEATURE_B\n" +
			"c var 3 __expr_2264\n" +
			"p cnf 3 3\n" +
			"1 0\n" +
			"-1 2 3 0\n" +
			"-2 -3 0\n").getBytes(StandardCharsets.UTF_8));

		TWiseCli.main(new String[] {
			"--cnf", cnf.toString(),
			"--output", output.toString(),
			"--t", "1",
			"--limit", "4",
			"--iterations", "1",
			"--no-progress"
		});

		final List<String> rows = Files.readAllLines(output, StandardCharsets.UTF_8);
		assertEquals("FEATURE_A,FEATURE_B", rows.get(0));
	}

	@Test
	public void oldDimacsFlagUsesLegacyVariableCommentsAndDollarAuxiliaryMarker() throws Exception {
		final Path cnf = temporaryFolder.newFile("old.dimacs").toPath();
		final Path output = temporaryFolder.newFile("old-sample.csv").toPath();
		Files.write(cnf, (
			"c 1 FEATURE_A\n" +
			"c 2 FEATURE_B\n" +
			"c 3$ helper\n" +
			"p cnf 3 3\n" +
			"1 0\n" +
			"-1 2 3 0\n" +
			"-2 -3 0\n").getBytes(StandardCharsets.UTF_8));

		TWiseCli.main(new String[] {
			"--cnf", cnf.toString(),
			"--output", output.toString(),
			"--old-dimacs",
			"--t", "1",
			"--limit", "4",
			"--iterations", "1",
			"--no-progress"
		});

		final List<String> rows = Files.readAllLines(output, StandardCharsets.UTF_8);
		assertEquals("FEATURE_A,FEATURE_B", rows.get(0));
	}
}
