package handist.glb.examples.pi;

import static apgas.Constructs.places;

import apgas.Configuration;
import handist.glb.examples.util.ExampleHelper;
import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.GLBFactory;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import handist.glb.multiworker.GLBcomputer;
import org.apache.commons.cli.*;

public class StartPi {

  static final long N_DEFAULT = 50000L;

  static long n = N_DEFAULT;

  private static void parseArguments(String[] args, boolean verbose) {
    Options options = new Options();
    options.addOption("n", true, "points to simulate");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;
    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      e.printStackTrace();
    }

    n = Long.parseLong(cmd.getOptionValue("n", String.valueOf(N_DEFAULT)));

    if (verbose) {
      System.out.println("PI config:\n" + "  n=" + n + "\n");
    }
  }

  public static void main(String[] args) {
    ExampleHelper.printStartMessage(StartPi.class.getName());
    ExampleHelper.configureAPGAS(false);
    Configuration.printAllConfigs();
    GLBMultiWorkerConfiguration.printAllConfigs();
    parseArguments(args, true);
    //    ExampleHelper.printAllFJsScheduled(5);

    final int repetitions =
        GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_BENCHMARKREPETITIONS.get();

    for (int i = 0; i < repetitions; i++) {
      GLBcomputer<LongSum, Pi> glb = new GLBFactory<LongSum, Pi>().setupGLB(places());

      //      final long np = Configuration.CONFIG_APGAS_PLACES.get();
      //      final long wpp =
      // GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();
      //      //      final long thrown = np * wpp * n;
      //
      //      final long perWorker = n / (np * wpp);
      //      System.out.println("perWorker = " + perWorker);

      // LongSum result = glb.computeDynamic(pi, () -> new LongSum(0L), () -> new Pi());
      final long _n = n;
      LongSum result = glb.computeStatic(() -> new LongSum(0L), () -> new Pi(0), () -> new Pi(_n));

      System.out.println(
          "Run "
              + (i + 1)
              + "/"
              + repetitions
              + "; "
              + result.sum
              + "; "
              + (4.0 * result.sum / n)
              + "; "
              + glb.getLog().computationTime / 1e9
              + "; ");

      System.out.println("Process time: " + glb.getLog().computationTime / 1e9 + " seconds");

      glb.getLog().printShort(System.out);
      glb.getLog().printAll(System.out);
      System.out.println();
      System.out.println("#############################################################");
      System.out.println("Pi is : " + (4.0 * result.sum / n));
      System.out.println("#############################################################");
      System.out.println();
    }
  }
}
