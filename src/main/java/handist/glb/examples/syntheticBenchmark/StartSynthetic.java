package handist.glb.examples.syntheticBenchmark;

import static apgas.Constructs.places;

import apgas.Configuration;
import handist.glb.examples.util.ExampleHelper;
import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.GLBFactory;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import handist.glb.multiworker.GLBcomputer;
import handist.glb.multiworker.SerializableSupplier;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class StartSynthetic {

  static final boolean STATICMODE_DEFAULT = false;
  static final long BALLAST_DEFAULT = 0;
  static final long TASKSPERWORKER_DEFAULT = 1_000_000;
  static final long TOTALDURATION_DEFAULT = 50_000;
  static final long TASK_DURATION_VARIANCE_DEFAULT = 20;

  static boolean staticMode = STATICMODE_DEFAULT;
  static long ballast = BALLAST_DEFAULT;
  static long tasksPerWorker = TASKSPERWORKER_DEFAULT;
  static long totalDuration = TOTALDURATION_DEFAULT;
  static long taskDurationVariance = TASK_DURATION_VARIANCE_DEFAULT;

  private static void parseArguments(String[] args, boolean verbose) {
    Options options = new Options();

    options.addOption("static", false, "Static initialization (Default dynamic)");
    options.addOption("dynamic", false, "Dynamic initialization (Default)");
    options.addOption("b", true, "Task ballast in bytes (Default 0 KiB)");
    options.addOption(
        "t", true, "Task count per worker (only used in static version) (Default 1_000_000)");
    options.addOption("g", true, "Total duration in milliseconds (Default 10_000)");
    options.addOption("u", true, "Task duration variance percentage (Default 0");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;
    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      e.printStackTrace();
    }

    staticMode = cmd.hasOption("static");
    ballast = Long.parseLong(cmd.getOptionValue("b", String.valueOf(BALLAST_DEFAULT)));
    tasksPerWorker =
        Long.parseLong(cmd.getOptionValue("t", String.valueOf(TASKSPERWORKER_DEFAULT)));
    totalDuration = Long.parseLong(cmd.getOptionValue("g", String.valueOf(TOTALDURATION_DEFAULT)));
    taskDurationVariance =
        Long.parseLong(cmd.getOptionValue("u", String.valueOf(TASK_DURATION_VARIANCE_DEFAULT)));

    if (verbose) {
      System.out.println(
          "Synthetic config:\n"
              + (staticMode ? "  static" : "  dynamic")
              + "\n"
              + "  ballast="
              + ballast
              + "\n"
              + "  tasksPerWorker="
              + tasksPerWorker
              + "\n"
              + "  totalDuration(in milliseconds)="
              + totalDuration
              + "\n"
              + "  totalDuration(in seconds)="
              + totalDuration / 1e3
              + "\n"
              + "  taskDurationVariance="
              + taskDurationVariance
              + "\n");
    }
  }

  public static void main(String[] args) {
    ExampleHelper.printStartMessage(StartSynthetic.class.getName());
    ExampleHelper.configureAPGAS(false);
    Configuration.printAllConfigs();
    GLBMultiWorkerConfiguration.printAllConfigs();
    parseArguments(args, true);
    //    ExampleHelper.printAllFJsScheduled(5);

    final int repetitions = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_BENCHMARKREPETITIONS.get();

    final long _ballast = ballast;
    final long _tasksPerWorker = tasksPerWorker;
    final long _totalDuration = totalDuration;
    final long _taskDurationVariance = taskDurationVariance;
    /*
    final boolean addPlaces = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_MALLEABILITY_ADD.get();
    final int mallPlaces =
        GLBMultiWorkerConfiguration.GLB_MULTIWORKER_MALLEABILITY_MALLPLACES.get();
    final boolean mallEnabled = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_MALLEABILITY.get();
    */
    final int workerPerPlace = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_WORKERPERPLACE.get();
    final int wholeNumPlaces;
    //    if (addPlaces && mallEnabled && mallPlaces > 0) {
    // We increase the amount of work to InitPlaces+MallPlaces
    //      wholeNumPlaces = places().size() + mallPlaces;
    //    } else {
    wholeNumPlaces = places().size();
    //    }

    long expectedResult = 0;

    for (int i = 0; i < repetitions; i++) {

      final LongSum sum;
      final GLBcomputer<LongSum, SyntheticQueue> glb;

      if (staticMode) {

        expectedResult = wholeNumPlaces * workerPerPlace * _tasksPerWorker;

        final SerializableSupplier<SyntheticQueue> workerInitializer =
            () -> {
              SyntheticQueue sq = new SyntheticQueue(_taskDurationVariance, 0, true);
              sq.initStatic(_ballast, _tasksPerWorker, _totalDuration, _taskDurationVariance);
              return sq;
            };

        final SerializableSupplier<SyntheticQueue> queueInitializer =
            () -> {
              SyntheticQueue s = new SyntheticQueue(_taskDurationVariance, 0, true);
              return s;
            };

        glb = new GLBFactory<LongSum, SyntheticQueue>().setupGLB(places());

        sum = glb.computeStatic(() -> new LongSum(0), queueInitializer, workerInitializer);

      } else { // dynamic

        // maxChildren=0, wird gleich durch initDynamic gesetzt
        final SyntheticQueue syntheticQueue = new SyntheticQueue(_taskDurationVariance, 0, false);
        expectedResult = syntheticQueue.initDynamic(_tasksPerWorker, _totalDuration, _ballast);
        final long _maxChildren = syntheticQueue.maxChildren;
        //        final float _variance = syntheticQueue.variance;
        //        final long _depth = syntheticQueue.depth;

        glb = new GLBFactory<LongSum, SyntheticQueue>().setupGLB(places());

        sum =
            glb.computeDynamic(
                syntheticQueue,
                () -> new LongSum(0),
                () -> new SyntheticQueue(_taskDurationVariance, _maxChildren, false));
      }

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
      System.out.println("Result is " + (expectedResult != sum.sum ? "NOT " : "") + "correct");
      System.out.println("#############################################################");
      System.out.println();
    }
  }
}
