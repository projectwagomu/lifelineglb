package handist.glb.examples.bc;

import static apgas.Constructs.places;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import apgas.Configuration;
import handist.glb.examples.util.DoubleArraySum;
import handist.glb.examples.util.ExampleHelper;
import handist.glb.multiworker.GLBFactory;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import handist.glb.multiworker.GLBcomputer;
import handist.glb.multiworker.SerializableSupplier;

public class StartBC {

	static final double A_DEFAULT = 0.55d;
	static final double B_DEFAULT = 0.1d;
	static final double C_DEFAULT = 0.1d;
	static final double D_DEFAULT = 0.25d;

	static final int N_DEFAULT = 14;
	static final int PERMUTE_DEFAULT = 1;
	static final int QSIZE_DEFAULT = 16;
	static final int SEED_DEFAULT = 2;

	public static void main(String[] args) {
		ExampleHelper.printStartMessage(StartBC.class.getName());
		ExampleHelper.configureAPGAS(false);
		Configuration.printAllConfigs();
		GLBMultiWorkerConfiguration.printAllConfigs();
		final CommandLine cmd = parseArguments(args);
		// ExampleHelper.printAllFJsScheduled(5);

		final int seed = Integer.parseInt(cmd.getOptionValue("seed", String.valueOf(SEED_DEFAULT)));
		final int n = Integer.parseInt(cmd.getOptionValue("n", String.valueOf(N_DEFAULT)));
		final int qSize = Integer.parseInt(cmd.getOptionValue("q", String.valueOf(QSIZE_DEFAULT)));
		final boolean verbose = Configuration.CONFIG_APGAS_VERBOSE_LAUNCHER.get();

		if (verbose) {
			System.out
					.println("BC config:\n" + "  n=" + n + "\n" + "  seed=" + seed + "\n" + "  qSize=" + qSize + "\n");
		}

		final int repetitions = GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_BENCHMARKREPETITIONS.get();

		for (int i = 0; i < repetitions; i++) {

			final int _resultSize = new BC(0).init(seed, n, A_DEFAULT, B_DEFAULT, C_DEFAULT, D_DEFAULT,
					PERMUTE_DEFAULT);

			final int _n = n;
			final int _seed = seed;
			final int _qSize = qSize;
			final SerializableSupplier<BC> workerInitializer = () -> {
				final BC b = new BC(_qSize);
				b.init(_seed, _n, A_DEFAULT, B_DEFAULT, C_DEFAULT, D_DEFAULT, PERMUTE_DEFAULT);
				return b;
			};

			final SerializableSupplier<BC> queueInitializer = () -> {
				final BC b = new BC(_qSize);
				return b;
			};

			final GLBcomputer<DoubleArraySum, BC> glb = new GLBFactory<DoubleArraySum, BC>().setupGLB(places());

			final DoubleArraySum sum = glb.computeStatic(() -> new DoubleArraySum(_resultSize), queueInitializer,
					workerInitializer);

			System.out.println("Run " + (i + 1) + "/" + repetitions + "; " + sum + "; "
					+ glb.getLog().computationTime / 1e9 + "; ");

			System.out.println("Process time: " + glb.getLog().computationTime / 1e9 + " seconds");

			glb.getLog().printShort(System.out);
			glb.getLog().printAll(System.out);
			System.out.println();
			System.out.println("#############################################################");
			// sum.printSumArray();
			System.out.println("#############################################################");
			System.out.println();
		}
	}

	private static CommandLine parseArguments(String[] args) {
		final Options options = new Options();

		options.addOption("seed", true, "Seed for the random number");
		options.addOption("n", true, "Number of vertices = 2^n");
		options.addOption("q", true, "Queue Size");

		final CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (final ParseException e) {
			e.printStackTrace();
		}
		return cmd;
	}
}
