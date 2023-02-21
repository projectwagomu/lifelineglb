package handist.glb.examples.syntheticBenchmark;

import static apgas.Constructs.places;

import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Random;

public class Synthetic implements Serializable {

  public boolean isStatic;
  protected final SyntheticTaskDeque tasks = new SyntheticTaskDeque(64);
  public long durationVariance;
  protected long count = 0;
  protected long result = 0;
  public long maxChildren = 0;
  public float variance = 0;

  protected transient Random randGen = new Random();
  protected long taskBallast;
  protected long tasksPerWorker;
  protected long totalDuration;
  public long diff = 0;
  transient ThreadMXBean bean = ManagementFactory.getThreadMXBean();

  /*
   * static initialization
   * All tasks are evenly distributed on initialization.
   * No tasks are generated from tasks.
   */
  public void initStatic(
      long taskBallast, long tasksPerWorker, long totalDuration, long durationVariance) {
    this.maxChildren = 0;
    this.taskBallast = taskBallast;
    this.tasksPerWorker = tasksPerWorker;
    this.totalDuration = totalDuration;
    this.durationVariance = durationVariance;
  }

  /*
   *  dynamic initialization
   *  On initialization one task is generated on place 0.
   *  Processing tasks can generate new tasks.
   *
   */
  public Synthetic(final long durationVariance, final long maxChildren, boolean isStatic) {
    this.durationVariance = durationVariance;
    this.variance = durationVariance / 100.0f;
    this.maxChildren = maxChildren;
    this.isStatic = isStatic;
  }

  long calculateTreeSize(long d, long c) {
    long taskCount = 1;
    for (long i = 1; i < d; ++i) {
      taskCount += Math.pow(c, i);
    }
    return taskCount;
  }

  long findTreeSize(long tasksPerWorker) {
    final int totalWorkers =
        GLBMultiWorkerConfiguration.GLB_MULTIWORKER_WORKERPERPLACE.get() * places().size();
    final long targetTotalTasks = tasksPerWorker * totalWorkers;
    long lastDiff = Long.MAX_VALUE;
    long lastDepth = 1;
    long lastChildren = 1;

    //    MaxTiefe=24, MaxBreite=8
    for (int d = 2; d < 24; ++d) {
      for (int c = 2; c < 8; ++c) {
        long currentTasks = calculateTreeSize(d, c);
        long currentDiff = Math.abs(currentTasks - targetTotalTasks);
        if (currentDiff < lastDiff) {
          lastDiff = currentDiff;
          lastDepth = d;
          lastChildren = c;
        }
      }
    }

    System.out.println(
        "Found parameter: d=" + lastDepth + " children=" + lastChildren + " lastDiff=" + lastDiff);
    this.maxChildren = lastChildren;
    return lastDepth;
  }

  public long initDynamic(
      final long tasksPerWorker, final long totalDuration, final long taskBallast) {
    /*
    final boolean addPlaces = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_MALLEABILITY_ADD.get();
    final int mallPlaces =
        GLBMultiWorkerConfiguration.GLB_MULTIWORKER_MALLEABILITY_MALLPLACES.get();
    final boolean mallEnabled = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_MALLEABILITY.get();
    */
    final int workerPerPlace = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_WORKERPERPLACE.get();

    final int totalWorkers;
    final long localTasksPerWorker;
    //    if (addPlaces && mallEnabled && mallPlaces > 0) {
    //      // We increase the amount of work to InitPlaces+MallPlaces
    //      totalWorkers = workerPerPlace * (places().size() + mallPlaces);
    //      localTasksPerWorker = (totalWorkers * tasksPerWorker) / (places().size() *
    // workerPerPlace);
    //    } else {
    totalWorkers = workerPerPlace * places().size();
    localTasksPerWorker = tasksPerWorker;
    //    }

    final long depth = findTreeSize(localTasksPerWorker);
    final long taskCount = calculateTreeSize(depth, maxChildren);
    final long taskDuration = (totalWorkers * totalDuration * 1000L * 1000L) / taskCount;
    System.out.println(
        "taskDuration(in nanoseconds)="
            + taskDuration
            + ", totalDuration(in milliseconds)="
            + totalDuration
            + ", taskCount="
            + taskCount
            + ", tasksPerWorker="
            + tasksPerWorker
            + ", localTasksPerWorker="
            + localTasksPerWorker);
    tasks.addLast(new SyntheticTask(taskBallast, 0, depth - 1, taskDuration));
    return taskCount;
  }

  public void calculate() {
    long before = bean.getCurrentThreadCpuTime();
    long after = bean.getCurrentThreadCpuTime();
    long beanDuration = after - before;

    SyntheticTask task = this.tasks.pollLast();
    randGen.setSeed(task.seed);
    if (task.depth > 0) {
      SyntheticTask[] newTasks = new SyntheticTask[(int) maxChildren];
      for (int i = 0; i < maxChildren; ++i) {
        newTasks[i] =
            new SyntheticTask(
                task.ballast.length, task.seed + randGen.nextInt(), task.depth - 1, task.duration);
      }
      tasks.pushArrayLast(newTasks);
    }
    long taskDuration;
    if (maxChildren > 0) {
      long x1 = (long) ((1 - variance) * task.duration);
      float randomFloat = randGen.nextFloat();
      long x2 = (long) (variance * task.duration * 2f * randomFloat);
      taskDuration = x1 + x2;
    } else {
      taskDuration = task.duration;
    }
    taskDuration -= diff;
    taskDuration -= beanDuration;

    long current2;
    long current = bean.getCurrentThreadCpuTime();
    long processedTime = current - before;
    while (processedTime < taskDuration) {
      fibonacci(5);
      current2 = bean.getCurrentThreadCpuTime();
      processedTime += current2 - current;
      current = current2;
    }
    diff = processedTime - taskDuration;
  }

  private static int fibonacci(int a) {
    if (a == 1 || a == 2) return 1;
    else return fibonacci(a - 1) + fibonacci(a - 2);
  }
}
