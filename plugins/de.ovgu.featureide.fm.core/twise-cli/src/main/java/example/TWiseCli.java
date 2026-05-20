package example;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.Variables;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.job.monitor.ConsoleMonitor;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;

public final class TWiseCli {

	private static final String MAIN_CLASS = "example.TWiseCli";
	private static final Pattern OLD_DIMACS_VARIABLE_COMMENT = Pattern.compile("^c\\s+(\\d+)(\\$?)\\s+(.+)$");
	private static final Pattern NEW_DIMACS_VARIABLE_COMMENT = Pattern.compile("^c\\s+var\\s+(\\d+)\\s+(.+)$");

	private TWiseCli() {
	}

	public static void main(String[] args) throws Exception {
		final Options options = Options.parse(args);
		if (options.help) {
			printUsage();
			return;
		}
		if (options.outputFile == null) {
			throw new IllegalArgumentException("Missing required option: --output <file>");
		}

		final ParsedCnf input = options.cnfFile == null ? new ParsedCnf(createExampleCnf(), null) : readCnf(options.cnfFile, options.oldDimacs);
		final CNF cnf = input.cnf;
		final TWiseConfigurationGenerator generator = input.hasFeatureFilter()
			? new TWiseConfigurationGenerator(cnf, TWiseConfigurationGenerator.convertLiterals(input.createCoverageLiterals()), options.t, options.limit)
			: new TWiseConfigurationGenerator(cnf, options.t, options.limit);
		generator.setIterations(options.iterations);
		generator.setRandom(new Random(options.seed));

		final IMonitor<List<LiteralSet>> monitor = options.isProgressEnabled()
			? new ProgressBarMonitor<List<LiteralSet>>()
			: new ConsoleMonitor<List<LiteralSet>>(false);
		final List<LiteralSet> sample = generator.execute(monitor);
		writeSampleCsv(cnf.getVariables(), input.realFeatureVariables, sample, options.outputFile);
		System.out.println("Generated configurations: " + sample.size());
		System.out.println("CSV output: " + options.outputFile);
	}

	private static CNF createExampleCnf() {
		final Variables variables = new Variables(Arrays.asList("Root", "Engine", "UI", "Basic", "Advanced"));
		final List<LiteralSet> clauses = new ArrayList<>();

		addClause(clauses, variables, "Root");
		addClause(clauses, variables, "-Engine", "Root");
		addClause(clauses, variables, "-UI", "Root");
		addClause(clauses, variables, "-Basic", "Root");
		addClause(clauses, variables, "-Advanced", "Root");
		addClause(clauses, variables, "-Root", "Basic", "Advanced");
		addClause(clauses, variables, "-Basic", "-Advanced");

		return new CNF(variables, clauses);
	}

	private static ParsedCnf readCnf(Path path, boolean oldDimacs) throws IOException {
		return oldDimacs ? readOldDimacsCnf(path) : readCnf(path);
	}

	private static ParsedCnf readCnf(Path path) throws IOException {
		return readCnf(path, new DimacsVariableDirectory(DimacsVariableDirectory.Syntax.NEW));
	}

	private static ParsedCnf readOldDimacsCnf(Path path) throws IOException {
		return readCnf(path, new DimacsVariableDirectory(DimacsVariableDirectory.Syntax.OLD));
	}

	private static ParsedCnf readCnf(Path path, DimacsVariableDirectory dimacsVariables) throws IOException {
		final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		Variables variables = null;
		final List<LiteralSet> clauses = new ArrayList<>();
		boolean dimacsInput = false;

		for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
			final String line = stripComment(lines.get(lineNumber)).trim();
			if (line.isEmpty()) {
				continue;
			}
			final String[] tokens = line.split("\\s+");
			if ("c".equals(tokens[0])) {
				dimacsVariables.parse(line);
				continue;
			}
			if ("p".equals(tokens[0])) {
				variables = parseDimacsProblemLine(tokens, dimacsVariables, lineNumber + 1);
				dimacsInput = true;
			} else if ("v".equals(tokens[0])) {
				variables = new Variables(Arrays.asList(Arrays.copyOfRange(tokens, 1, tokens.length)));
				dimacsInput = false;
			} else {
				if (variables == null) {
					throw new IllegalArgumentException("Line " + (lineNumber + 1) + ": add a variable line first, for example: v Root A B");
				}
				clauses.add(parseClause(variables, tokens, lineNumber + 1));
			}
		}

		if (variables == null) {
			throw new IllegalArgumentException("CNF file must contain either 'v <names...>' or 'p cnf <variables> <clauses>'.");
		}
		return new ParsedCnf(new CNF(variables, clauses), dimacsInput ? dimacsVariables.createRealFeatureVariables(variables.size()) : null);
	}

	private static Variables parseDimacsProblemLine(String[] tokens, DimacsVariableDirectory dimacsVariables, int lineNumber) {
		if ((tokens.length < 4) || !"cnf".equalsIgnoreCase(tokens[1])) {
			throw new IllegalArgumentException("Line " + lineNumber + ": expected 'p cnf <variables> <clauses>'.");
		}
		final int variableCount = Integer.parseInt(tokens[2]);
		final List<String> names = new ArrayList<>(variableCount);
		for (int i = 1; i <= variableCount; i++) {
			names.add(dimacsVariables.getName(i));
		}
		return new Variables(names);
	}

	private static LiteralSet parseClause(Variables variables, String[] tokens, int lineNumber) {
		final List<Integer> literals = new ArrayList<>();
		for (String token : tokens) {
			if (token.endsWith(",")) {
				token = token.substring(0, token.length() - 1);
			}
			if (token.isEmpty() || "0".equals(token)) {
				continue;
			}
			literals.add(parseLiteral(variables, token, lineNumber));
		}
		final int[] literalArray = new int[literals.size()];
		for (int i = 0; i < literals.size(); i++) {
			literalArray[i] = literals.get(i);
		}
		return new LiteralSet(literalArray);
	}

	private static int parseLiteral(Variables variables, String token, int lineNumber) {
		try {
			return Integer.parseInt(token);
		} catch (NumberFormatException ignored) {
			boolean positive = true;
			String name = token;
			while (name.startsWith("-") || name.startsWith("!") || name.startsWith("~")) {
				positive = !positive;
				name = name.substring(1);
			}
			final int variable = variables.getVariable(name);
			if (variable == 0) {
				throw new IllegalArgumentException("Line " + lineNumber + ": unknown variable '" + name + "'.");
			}
			return positive ? variable : -variable;
		}
	}

	private static void addClause(List<LiteralSet> clauses, Variables variables, String... literals) {
		clauses.add(parseClause(variables, literals, 0));
	}

	private static String stripComment(String line) {
		final int hash = line.indexOf('#');
		return hash >= 0 ? line.substring(0, hash) : line;
	}

	private static void writeSampleCsv(Variables variables, Set<Integer> realFeatureVariables, List<LiteralSet> sample, Path outputFile) throws IOException {
		final List<Integer> outputVariables = getOutputVariables(variables, realFeatureVariables);
		final Path parent = outputFile.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
			writeHeader(variables, outputVariables, writer);
			for (final LiteralSet solution : sample) {
				writeConfigurationRow(variables, outputVariables, solution, writer);
			}
		}
	}

	private static List<Integer> getOutputVariables(Variables variables, Set<Integer> realFeatureVariables) {
		if (realFeatureVariables != null) {
			return new ArrayList<>(realFeatureVariables);
		}
		final List<Integer> outputVariables = new ArrayList<>(variables.size());
		for (int variable = 1; variable <= variables.size(); variable++) {
			outputVariables.add(variable);
		}
		return outputVariables;
	}

	private static void writeHeader(Variables variables, List<Integer> outputVariables, BufferedWriter writer) throws IOException {
		boolean first = true;
		for (final int variable : outputVariables) {
			if (first) {
				first = false;
			} else {
				writer.write(',');
			}
			writeCsvField(writer, variables.getName(variable));
		}
		writer.newLine();
	}

	private static void writeConfigurationRow(Variables variables, List<Integer> outputVariables, LiteralSet solution, BufferedWriter writer) throws IOException {
		final boolean[] selected = new boolean[variables.size() + 1];
		for (final int literal : solution.getLiterals()) {
			if (literal > 0) {
				selected[literal] = true;
			}
		}
		boolean first = true;
		for (final int variable : outputVariables) {
			if (first) {
				first = false;
			} else {
				writer.write(',');
			}
			writer.write(selected[variable] ? '1' : '0');
		}
		writer.newLine();
	}

	private static void writeCsvField(BufferedWriter writer, String value) throws IOException {
		boolean quote = false;
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if ((c == ',') || (c == '"') || (c == '\n') || (c == '\r')) {
				quote = true;
				break;
			}
		}
		if (!quote) {
			writer.write(value);
			return;
		}
		writer.write('"');
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (c == '"') {
				writer.write('"');
			}
			writer.write(c);
		}
		writer.write('"');
	}

	private static void printUsage() {
		System.out.println("Usage: java -cp <classpath> " + MAIN_CLASS + " [options]");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  --cnf <file>            Read a simple CNF file. If omitted, a built-in example is used.");
		System.out.println("  --output <file>         Write generated configurations as a CSV matrix. Required.");
		System.out.println("  --t <n>                 Interaction strength. Default: 2.");
		System.out.println("  --limit <n>             Maximum generated sample size. Default: unlimited.");
		System.out.println("  --iterations <n>        YASA refinement iterations. Default: 1.");
		System.out.println("  --seed <n>              Random seed. Default: 112358.");
		System.out.println("  --progress              Force the progress bar, even when stderr is redirected.");
		System.out.println("  --no-progress           Disable the progress bar. By default, it is shown on interactive consoles.");
		System.out.println("  --old-dimacs            Read old DIMACS variable comments such as 'c 1 Feature'.");
		System.out.println("  --help                  Show this help.");
		System.out.println();
		System.out.println("CNF input:");
		System.out.println("  v Root A B");
		System.out.println("  Root");
		System.out.println("  -Root A B");
		System.out.println("  -A -B");
		System.out.println();
		System.out.println("DIMACS-style input is also accepted:");
		System.out.println("  p cnf 3 3");
		System.out.println("  1 0");
		System.out.println("  -1 2 3 0");
		System.out.println("  -2 -3 0");
	}

	private static final class Options {
		private Path cnfFile;
		private Path outputFile;
		private int t = 2;
		private int limit = Integer.MAX_VALUE;
		private int iterations = 1;
		private long seed = 112358L;
		private Boolean progress;
		private boolean help;
		private boolean oldDimacs;

		private static Options parse(String[] args) {
			final Options options = new Options();
			final Set<String> noValueOptions = new LinkedHashSet<>(Arrays.asList("--progress", "--no-progress", "--old-dimacs", "--help"));
			for (int i = 0; i < args.length; i++) {
				final String arg = args[i];
				if (noValueOptions.contains(arg)) {
					if ("--progress".equals(arg)) {
						options.progress = Boolean.TRUE;
					} else if ("--no-progress".equals(arg)) {
						options.progress = Boolean.FALSE;
					} else if ("--old-dimacs".equals(arg)) {
						options.oldDimacs = true;
					} else if ("--help".equals(arg)) {
						options.help = true;
					}
					continue;
				}
				if ((i + 1) >= args.length) {
					throw new IllegalArgumentException("Missing value for " + arg);
				}
				final String value = args[++i];
				if ("--cnf".equals(arg)) {
					options.cnfFile = Paths.get(value);
				} else if ("--output".equals(arg)) {
					options.outputFile = Paths.get(value);
				} else if ("--t".equals(arg)) {
					options.t = Integer.parseInt(value);
				} else if ("--limit".equals(arg)) {
					options.limit = Integer.parseInt(value);
				} else if ("--iterations".equals(arg)) {
					options.iterations = Integer.parseInt(value);
				} else if ("--seed".equals(arg)) {
					options.seed = Long.parseLong(value);
				} else {
					throw new IllegalArgumentException("Unknown option: " + arg);
				}
			}
			return options;
		}

		private boolean isProgressEnabled() {
			return progress == null ? System.console() != null : progress.booleanValue();
		}
	}

	private static final class ProgressBarMonitor<T> implements IMonitor<T> {
		private static final int BAR_WIDTH = 30;
		private static final long MIN_RENDER_INTERVAL_MS = 100;

		private int totalWork;
		private int remainingWork;
		private String taskName = "...";
		private boolean canceled;
		private boolean active;
		private long lastRenderTime;
		private Consumer<T> intermediateFunction;

		@Override
		public synchronized void setRemainingWork(int work) {
			totalWork = Math.max(0, work);
			remainingWork = totalWork;
			active = totalWork > 0;
			render(true);
		}

		@Override
		public synchronized int getRemainingWork() {
			return remainingWork;
		}

		@Override
		public void step() throws MethodCancelException {
			step(1, null);
		}

		@Override
		public void step(int work) throws MethodCancelException {
			step(work, null);
		}

		@Override
		public void step(T t) throws MethodCancelException {
			step(1, t);
		}

		@Override
		public void step(int work, T t) throws MethodCancelException {
			worked(work);
			invoke(t);
			checkCancel();
		}

		@Override
		public <R> IMonitor<R> subTask(int size) {
			worked(size);
			return new ProgressBarMonitor<R>();
		}

		@Override
		public synchronized void setTaskName(String name) {
			taskName = name == null ? "..." : name;
			render(true);
		}

		@Override
		public synchronized String getTaskName() {
			return taskName;
		}

		@Override
		public void worked() {
			worked(1);
		}

		@Override
		public synchronized void worked(int work) {
			if (work <= 0) {
				return;
			}
			remainingWork = Math.max(0, remainingWork - work);
			render(false);
		}

		@Override
		public synchronized void checkCancel() throws MethodCancelException {
			if (canceled) {
				throw new MethodCancelException();
			}
		}

		@Override
		public void invoke(T t) {
			if (intermediateFunction != null) {
				intermediateFunction.accept(t);
			}
		}

		@Override
		public void setIntermediateFunction(Consumer<T> intermediateFunction) {
			this.intermediateFunction = intermediateFunction;
		}

		@Override
		public synchronized void cancel() {
			canceled = true;
		}

		@Override
		public synchronized void done() {
			remainingWork = 0;
			render(true);
		}

		private void render(boolean force) {
			if (!active || (totalWork == 0)) {
				return;
			}
			final long now = System.currentTimeMillis();
			if (!force && (remainingWork > 0) && ((now - lastRenderTime) < MIN_RENDER_INTERVAL_MS)) {
				return;
			}
			lastRenderTime = now;
			final int completed = totalWork - remainingWork;
			final int percent = (int) Math.min(100, Math.round((completed * 100.0) / totalWork));
			final int filled = (int) Math.round((percent / 100.0) * BAR_WIDTH);
			final StringBuilder line = new StringBuilder();
			line.append('\r');
			line.append(taskName);
			line.append(" [");
			for (int i = 0; i < BAR_WIDTH; i++) {
				line.append(i < filled ? '=' : ' ');
			}
			line.append("] ");
			if (percent < 100) {
				line.append(' ');
			}
			if (percent < 10) {
				line.append(' ');
			}
			line.append(percent);
			line.append('%');
			if (remainingWork == 0) {
				line.append(System.lineSeparator());
				active = false;
			}
			System.err.print(line.toString());
			System.err.flush();
		}
	}

	private static final class ParsedCnf {
		private final CNF cnf;
		private final Set<Integer> realFeatureVariables;

		private ParsedCnf(CNF cnf, Set<Integer> realFeatureVariables) {
			this.cnf = cnf;
			this.realFeatureVariables = realFeatureVariables;
		}

		private boolean hasFeatureFilter() {
			return realFeatureVariables != null;
		}

		private LiteralSet createCoverageLiterals() {
			final int[] literals = new int[realFeatureVariables.size() * 2];
			int index = 0;
			for (final int variable : realFeatureVariables) {
				literals[index++] = -variable;
			}
			for (final int variable : realFeatureVariables) {
				literals[index++] = variable;
			}
			return new LiteralSet(literals);
		}
	}

	private static final class DimacsVariableDirectory {
		private enum Syntax {
			OLD,
			NEW
		}

		private final Syntax syntax;
		private final List<String> names = new ArrayList<>();
		private final Set<Integer> realFeatureVariables = new LinkedHashSet<>();
		private boolean hasVariableMapping;

		private DimacsVariableDirectory(Syntax syntax) {
			this.syntax = syntax;
		}

		private void parse(String line) {
			if (syntax == Syntax.OLD) {
				parseOldDimacsVariableComment(line);
			} else {
				parseNewDimacsVariableComment(line);
			}
		}

		private void parseOldDimacsVariableComment(String line) {
			final Matcher matcher = OLD_DIMACS_VARIABLE_COMMENT.matcher(line);
			if (!matcher.matches()) {
				return;
			}
			final int variable = Integer.parseInt(matcher.group(1));
			final boolean auxiliary = !matcher.group(2).isEmpty();
			final String name = matcher.group(3).trim();
			addVariable(variable, name, auxiliary);
		}

		private void parseNewDimacsVariableComment(String line) {
			final Matcher matcher = NEW_DIMACS_VARIABLE_COMMENT.matcher(line);
			if (!matcher.matches()) {
				return;
			}
			final int variable = Integer.parseInt(matcher.group(1));
			final String name = matcher.group(2).trim();
			addVariable(variable, name, isAuxiliaryVariableName(name));
		}

		private void addVariable(int variable, String name, boolean auxiliary) {
			if (name.isEmpty()) {
				return;
			}
			ensureCapacity(variable);
			names.set(variable, name);
			hasVariableMapping = true;
			if (!auxiliary) {
				realFeatureVariables.add(variable);
			}
		}

		private boolean isAuxiliaryVariableName(String name) {
			return name.startsWith("__");
		}

		private String getName(int variable) {
			if ((variable < names.size()) && (names.get(variable) != null)) {
				return names.get(variable);
			}
			return "F" + variable;
		}

		private Set<Integer> createRealFeatureVariables(int variableCount) {
			if (!hasVariableMapping) {
				return null;
			}
			final Set<Integer> variables = new LinkedHashSet<>();
			for (final int variable : realFeatureVariables) {
				if (variable <= variableCount) {
					variables.add(variable);
				}
			}
			return variables;
		}

		private void ensureCapacity(int variable) {
			while (names.size() <= variable) {
				names.add(null);
			}
		}
	}
}
