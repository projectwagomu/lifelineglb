package handist.glb.examples.nqueens;

import apgas.Configuration;
import handist.glb.examples.util.ExampleHelper;
import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.GLBFactory;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import handist.glb.multiworker.GLBcomputer;
import org.apache.commons.cli.*;

import static apgas.Constructs.places;

public class StartNQueens {

  static final int QUEENS_DEFAULT = 15;
  static final int THRESHOLD_DEFAULT = 7;
  static final int QSIZE_DEFAULT = 4096;

  static int queens = QUEENS_DEFAULT;
  static int threshold = THRESHOLD_DEFAULT;
  static int qSize = QSIZE_DEFAULT;

  private static void parseArguments(String[] args, boolean verbose) {
    Options options = new Options();

    options.addOption("n", true, "Number of queens");
    options.addOption("t", true, "Threshold");
    options.addOption("q", true, "Queue Size");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;
    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      e.printStackTrace();
    }

    queens = Integer.parseInt(cmd.getOptionValue("n", String.valueOf(QUEENS_DEFAULT)));
    threshold = Integer.parseInt(cmd.getOptionValue("t", String.valueOf(THRESHOLD_DEFAULT)));
    qSize = Integer.parseInt(cmd.getOptionValue("q", String.valueOf(QSIZE_DEFAULT)));

    if (verbose) {
      System.out.println(
          "NQueens config:\n"
              + "  queens="
              + queens
              + "\n"
              + "  threshold="
              + threshold
              + "\n"
              + "  qSize="
              + qSize
              + "\n");
    }
  }

  public static void main(String[] args) {
    ExampleHelper.printStartMessage(StartNQueens.class.getName());
    ExampleHelper.configureAPGAS(false);
    Configuration.printAllConfigs();
    GLBMultiWorkerConfiguration.printAllConfigs();
    parseArguments(args, true);
    //    ExampleHelper.printAllFJsScheduled(5);

    final int repetitions = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_BENCHMARKREPETITIONS.get();

    for (int i = 0; i < repetitions; i++) {

      final NQueens nQueens = new NQueens(queens, threshold, qSize);
      nQueens.init();

      GLBcomputer<LongSum, NQueens> glb = new GLBFactory<LongSum, NQueens>().setupGLB(places());

      final int _queens = queens;
      final int _threshold = threshold;
      final int _qSize = qSize;
      final LongSum sum =
          glb.computeDynamic(
              nQueens, () -> new LongSum(0), () -> new NQueens(_queens, _threshold, _qSize));

      System.out.println(
          "Run "
              + (i + 1)
              + "/"
              + repetitions
              + "; "
              + sum.sum
              + "; "
              + glb.getLog().computationTime / 1e9
              + "; ");

      System.out.println("Process time: " + glb.getLog().computationTime / 1e9 + " seconds");

      glb.getLog().printShort(System.out);
      glb.getLog().printAll(System.out);
      System.out.println();
      System.out.println("#############################################################");
      NQueensResults.proveCorrectness(queens, sum.sum);
      System.out.println("#############################################################");
      System.out.println();
    }
  }
}
