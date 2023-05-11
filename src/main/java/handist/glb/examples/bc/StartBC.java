package handist.glb.examples.bc;

import apgas.Configuration;
import handist.glb.examples.util.DoubleArraySum;
import handist.glb.examples.util.ExampleHelper;
import handist.glb.multiworker.GLBFactory;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import handist.glb.multiworker.GLBcomputer;
import handist.glb.multiworker.SerializableSupplier;
import org.apache.commons.cli.*;

import static apgas.Constructs.places;

public class StartBC {

  static final int SEED_DEFAULT = 2;
  static final int N_DEFAULT = 14;
  static final int QSIZE_DEFAULT = 16;
  static final double A_DEFAULT = 0.55d;
  static final double B_DEFAULT = 0.1d;
  static final double C_DEFAULT = 0.1d;
  static final double D_DEFAULT = 0.25d;
  static final int PERMUTE_DEFAULT = 1;

  static int seed = SEED_DEFAULT;
  static int n = N_DEFAULT;
  static int qSize = QSIZE_DEFAULT;

  private static void parseArguments(String[] args, boolean verbose) {
    Options options = new Options();

    options.addOption("seed", true, "Seed for the random number");
    options.addOption("n", true, "Number of vertices = 2^n");
    options.addOption("q", true, "Queue Size");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;
    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      e.printStackTrace();
    }

    seed = Integer.parseInt(cmd.getOptionValue("seed", String.valueOf(SEED_DEFAULT)));
    n = Integer.parseInt(cmd.getOptionValue("n", String.valueOf(N_DEFAULT)));
    qSize = Integer.parseInt(cmd.getOptionValue("q", String.valueOf(QSIZE_DEFAULT)));

    if (verbose) {
      System.out.println(
          "BC config:\n" + "  n=" + n + "\n" + "  seed=" + seed + "\n" + "  qSize=" + qSize + "\n");
    }
  }

  public static void main(String[] args) {
    ExampleHelper.printStartMessage(StartBC.class.getName());
    ExampleHelper.configureAPGAS(false);
    Configuration.printAllConfigs();
    GLBMultiWorkerConfiguration.printAllConfigs();
    parseArguments(args, true);
    //    ExampleHelper.printAllFJsScheduled(5);

    final int repetitions = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_BENCHMARKREPETITIONS.get();

    for (int i = 0; i < repetitions; i++) {

      final int _resultSize =
          new BC(0).init(seed, n, A_DEFAULT, B_DEFAULT, C_DEFAULT, D_DEFAULT, PERMUTE_DEFAULT);

      final int _n = n;
      final int _seed = seed;
      final int _qSize = qSize;
      final SerializableSupplier<BC> workerInitializer =
          () -> {
            BC b = new BC(_qSize);
            b.init(_seed, _n, A_DEFAULT, B_DEFAULT, C_DEFAULT, D_DEFAULT, PERMUTE_DEFAULT);
            return b;
          };

      final SerializableSupplier<BC> queueInitializer =
          () -> {
            BC b = new BC(_qSize);
            return b;
          };

      GLBcomputer<DoubleArraySum, BC> glb = new GLBFactory<DoubleArraySum, BC>().setupGLB(places());

      final DoubleArraySum sum =
          glb.computeStatic(
              () -> new DoubleArraySum(_resultSize), queueInitializer, workerInitializer);

      System.out.println(
          "Run "
              + (i + 1)
              + "/"
              + repetitions
              + "; "
              + sum
              + "; "
              + glb.getLog().computationTime / 1e9
              + "; ");

      System.out.println("Process time: " + glb.getLog().computationTime / 1e9 + " seconds");

      glb.getLog().printShort(System.out);
      glb.getLog().printAll(System.out);
      System.out.println();
      System.out.println("#############################################################");
      //      sum.printSumArray();
      System.out.println("#############################################################");
      System.out.println();
    }
  }
}
