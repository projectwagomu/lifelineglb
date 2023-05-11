/*
 *
 * Initiale Version von COMPSs, Barcelona Supercomputing Center (www.bsc.es)
 * https://github.com/bsc-wdc/tutorial_apps/tree/stable/java/matmul
 *
 */
package handist.glb.examples.matmul;

import apgas.Configuration;
import handist.glb.examples.util.ExampleHelper;
import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.GLBFactory;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import handist.glb.multiworker.GLBcomputer;
import handist.glb.multiworker.SerializableSupplier;
import org.apache.commons.cli.*;

import static apgas.Constructs.places;

public class StartMatMul {

  static final int MSIZE_DEFAULT = 256;
  static final int BSIZE_DEFAULT = 4;

  static int msize = MSIZE_DEFAULT;
  static int bsize = BSIZE_DEFAULT;

  private static void parseArguments(String[] args, boolean verbose) {
    Options options = new Options();

    options.addOption("m", true, "msize, default=" + MSIZE_DEFAULT);
    options.addOption("b", true, "bsize, default=" + BSIZE_DEFAULT);

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;
    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      e.printStackTrace();
    }

    msize = Integer.parseInt(cmd.getOptionValue("m", String.valueOf(MSIZE_DEFAULT)));
    bsize = Integer.parseInt(cmd.getOptionValue("b", String.valueOf(BSIZE_DEFAULT)));

    if (verbose) {
      System.out.println(
          "MatMul config:\n" + "  msize=" + msize + "\n" + "  bsize=" + bsize + "\n");
    }
  }

  public static void main(String[] args) {
    ExampleHelper.printStartMessage(StartMatMul.class.getName());
    ExampleHelper.configureAPGAS(false);
    Configuration.printAllConfigs();
    GLBMultiWorkerConfiguration.printAllConfigs();
    parseArguments(args, true);
    //    ExampleHelper.printAllFJsScheduled(5);

    int workerPerPlace = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_WORKERPERPLACE.get();
    final int max = places().size() * workerPerPlace;
    final int numberTasks = msize * msize;
    final int taskPerWorker = numberTasks / max;

    // Condition kommt aus MatMul.initStaticTasks()
    //    if ((taskPerWorker * max) != numberTasks) {
    //      System.out.println("Error in Parameters!!!!");
    //      System.exit(42);
    //    }

    final int repetitions = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_BENCHMARKREPETITIONS.get();

    for (int i = 0; i < repetitions; i++) {

      final int _msize = msize;
      final int _bsize = bsize;
      final SerializableSupplier<MatMul> workerInitializer =
          () -> {
            MatMul matMul = new MatMul(_msize, _bsize);
            matMul.init();
            return matMul;
          };

      final SerializableSupplier<MatMul> queueInitializer =
          () -> {
            MatMul matMul = new MatMul(_msize, _bsize);
            return matMul;
          };

      GLBcomputer<LongSum, MatMul> glb = new GLBFactory<LongSum, MatMul>().setupGLB(places());

      final LongSum sum =
          glb.computeStatic(() -> new LongSum(0l), queueInitializer, workerInitializer);

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
      proveCorrectness(_msize, sum.sum);
      System.out.println("#############################################################");
      System.out.println();
    }
  }

  private static void proveCorrectness(int msize, long sum) {
    if ((msize * msize) != sum) {
      System.out.println("Result is NOT correct!!!!");
      return;
    }
    System.out.println("Result is correct");
  }
}
