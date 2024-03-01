/*
 * Copyright (c) 2023 Wagomu project.
 *
 * This program and the accompanying materials are made available to you under
 * the terms of the Eclipse Public License 1.0 which accompanies this
 * distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */
package handist.glb.examples.syntheticBenchmark;

import static apgas.Constructs.here;
import static apgas.Constructs.places;

import apgas.impl.GlobalRuntimeImpl;
import apgas.util.ConsolePrinter;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Random;

public class Synthetic implements Serializable {

  /** Serial Version UID */
  private static final long serialVersionUID = -3390480610966580801L;

  protected final SyntheticTaskDeque tasks = new SyntheticTaskDeque(64);
  public long diff = 0;
  public long durationVariance;
  public boolean isStatic;
  public long maxChildren = 0;
  public float variance = 0;
  protected long count = 0;
  protected transient Random randGen = new Random();

  protected long result = 0;
  protected long taskBallast;
  protected long tasksPerWorker;
  protected long totalDuration;
  transient ThreadMXBean bean = ManagementFactory.getThreadMXBean();

  /*
   * Dynamic initialization:
   * At initialization one task is generated on place 0.
   * Processing tasks can generate new tasks.
   */
  public Synthetic(
      final long durationVariance,
      final long maxChildren,
      boolean isStatic,
      final long totalDuration) {
    this.durationVariance = durationVariance;
    variance = durationVariance / 100.0f;
    this.maxChildren = maxChildren;
    this.isStatic = isStatic;
    this.totalDuration = totalDuration;
  }

  private static int fibonacci(int a) {
    if (a == 1 || a == 2) {
      return 1;
    }
    return fibonacci(a - 1) + fibonacci(a - 2);
  }

  public void calculate() {
    final long before = bean.getCurrentThreadCpuTime();
    final long after = bean.getCurrentThreadCpuTime();
    final long beanDuration = after - before;
    final SyntheticTask task = tasks.pollLast();
    randGen.setSeed(task.seed);

    long taskDuration;
    if (maxChildren > 0) {
      final long x1 = (long) ((1 - variance) * task.duration);
      final float randomFloat = randGen.nextFloat();
      final long x2 = (long) (variance * task.duration * 2f * randomFloat);
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

    // Generate an unbalanced tree with values from findTreeSize
    if (task.depth > 0) {
      final SyntheticTask[] newTasks = new SyntheticTask[(int) maxChildren];
      for (int i = 1; i <= maxChildren; ++i) {
        long newTaskID = task.taskID * maxChildren + i;
        int myI = i - 1;
        newTasks[myI] =
            new SyntheticTask(
                task.ballast.length,
                task.seed + randGen.nextInt(),
                task.depth - 1,
                task.duration,
                newTaskID,
                task.totalNumberOfTasks,
                task.realDepth,
                task.branch,
                task.durationTree);
      }
      tasks.pushArrayLast(newTasks);
    } else if (task.depth == 0) { // The Last level of the tree reached
      // Used to connect a long branch with only one child per parent
      long idLastNodeRight = (long) Math.pow(maxChildren, task.realDepth - 1);
      if (task.taskID == idLastNodeRight) { // unten rechts
        if (task.branch) {
          ConsolePrinter.getInstance()
              .printlnAlways(
                  "[SyntheticTree] "
                      + here()
                      + " : Starts tree generation "
                      + (System.nanoTime() - GlobalRuntimeImpl.getRuntime().startupTime) / 1e9
                      + " Seconds after Program start.");
          ConsolePrinter.getInstance()
              .printlnAlways("[SyntheticTree] " + here() + " : idLastNodeRight:" + idLastNodeRight);
          ConsolePrinter.getInstance()
              .printlnAlways(
                  "[SyntheticTree] " + here() + " : Branch starts here with following parameters:");
          ConsolePrinter.getInstance()
              .printlnAlways("[SyntheticTree] " + here() + " : totalDuration: " + totalDuration);
          ConsolePrinter.getInstance()
              .printlnAlways(
                  "[SyntheticTree] "
                      + here()
                      + " : numTasks: "
                      + GLBMultiWorkerConfiguration.GLBOPTION_SYNTH_BRANCH.get());
          // Branch duration should be double the duration of the part of the tree before the
          // branch.
          long taskDurationBranch =
              (1000L * 1000L * totalDuration * 2)
                  / GLBMultiWorkerConfiguration.GLBOPTION_SYNTH_BRANCH.get();
          ConsolePrinter.getInstance()
              .printlnAlways(
                  "[SyntheticTree] " + here() + " : taskDurationBranch: " + taskDurationBranch);
          ConsolePrinter.getInstance()
              .printlnAlways("[SyntheticTree] " + here() + " : diff: " + diff);
          SyntheticTask newTask =
              new SyntheticTask(
                  task.ballast.length,
                  task.seed + randGen.nextInt(),
                  -1,
                  taskDurationBranch,
                  0,
                  task.totalNumberOfTasks,
                  task.realDepth,
                  false,
                  task.durationTree);
          tasks.addLast(newTask);
        }
      }
    } else if (task.depth == -1) { // Branch starts here
      if (task.taskID
          < GLBMultiWorkerConfiguration.GLBOPTION_SYNTH_BRANCH.get() - 1) { // Creating Branch
        long newTaskId = task.taskID + 1;
        SyntheticTask newTask =
            new SyntheticTask(
                task.ballast.length,
                task.seed + randGen.nextInt(),
                -1,
                task.duration,
                newTaskId,
                task.totalNumberOfTasks,
                task.realDepth,
                false,
                task.durationTree);
        tasks.addLast(newTask);

      } else { // Branch is complete - generate root for a new tree from the last node of branch.
        ConsolePrinter.getInstance()
            .printlnAlways(
                "[SyntheticTree] Second Tree started, Branch generation finished "
                    + (System.nanoTime() - GlobalRuntimeImpl.getRuntime().startupTime) / 1e9
                    + " Seconds after Program start.");
        tasks.addLast(
            new SyntheticTask(
                task.ballast.length,
                0,
                task.realDepth - 1,
                task.durationTree,
                0,
                task.totalNumberOfTasks,
                task.realDepth,
                task.branch,
                task.durationTree));
      }
    }
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
        GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get() * places().size();
    final long targetTotalTasks = tasksPerWorker * totalWorkers;
    long lastDiff = Long.MAX_VALUE;
    long lastDepth = 1;
    long lastChildren = 1;

    // MaxDepth=24, MaxWidth=8
    for (int d = 2; d < 24; ++d) {
      for (int c = 2; c < 8; ++c) {
        final long currentTasks = calculateTreeSize(d, c);
        final long currentDiff = Math.abs(currentTasks - targetTotalTasks);
        if (currentDiff < lastDiff) {
          lastDiff = currentDiff;
          lastDepth = d;
          lastChildren = c;
        }
      }
    }

    System.out.println(
        "Found parameter: d=" + lastDepth + " children=" + lastChildren + " lastDiff=" + lastDiff);
    maxChildren = lastChildren;
    return lastDepth;
  }

  public long initDynamic(
      final long tasksPerWorker, final long totalDuration, final long taskBallast) {
    this.totalDuration = totalDuration;
    final int workerPerPlace =
        GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();
    final int totalWorkers;
    final long localTasksPerWorker;
    totalWorkers = workerPerPlace * places().size();
    localTasksPerWorker = tasksPerWorker;

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
    /*
     * If GLBOPTION_SYNTH_TREE is set to evotree a bigger tree will be created.
     * Each node is a task; the total nodes will be double the amount of the
     * original tree plus the size of the added branch.
     * Return taskCount for expected value to compare calculated result to.
     */
    if (GLBMultiWorkerConfiguration.GLBOPTION_SYNTH_TREE.get().equals("evotree")) {
      tasks.addLast(
          new SyntheticTask(
              taskBallast, 0, depth - 1, taskDuration, 0, taskCount, depth, true, taskDuration));
      return taskCount * 2 + GLBMultiWorkerConfiguration.GLBOPTION_SYNTH_BRANCH.get();
    } else { // Original synthetic dynamic benchmark
      tasks.addLast(
          new SyntheticTask(
              taskBallast, 0, depth - 1, taskDuration, 0, taskCount, depth, false, taskDuration));
      return taskCount;
    }
  }

  /*
   * Static initialization:
   * All tasks are evenly distributed on initialization.
   * No tasks are generated from tasks.
   */
  public void initStatic(
      long taskBallast, long tasksPerWorker, long totalDuration, long durationVariance) {
    maxChildren = 0;
    this.taskBallast = taskBallast;
    this.tasksPerWorker = tasksPerWorker;
    this.totalDuration = totalDuration;
    this.durationVariance = durationVariance;
  }
}
