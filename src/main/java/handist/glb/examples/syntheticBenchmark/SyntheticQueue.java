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

import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.Bag;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;

public class SyntheticQueue extends Synthetic implements Bag<SyntheticQueue, LongSum> {

  /** Serial Version UID */
  private static final long serialVersionUID = -7797377631042311713L;

  public SyntheticQueue(
      final long durationVariance,
      final long maxChildren,
      boolean isStatic,
      final long totalDuration) {
    super(durationVariance, maxChildren, isStatic, totalDuration);
  }

  @Override
  public long getCurrentTaskCount() {
    return tasks.size();
  }

  @Override
  public LongSum getResult() {
    return new LongSum(result);
  }

  @Override
  public void initStaticTasks(int workerId) {
    final int workerPerPlace =
        GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();
    final int totalWorkers;
    totalWorkers = workerPerPlace * places().size();

    final long taskCount = tasksPerWorker * totalWorkers;
    long taskDuration = (1000L * 1000L * totalDuration * totalWorkers) / taskCount;
    randGen.setSeed(42);
    final double[] w = new double[totalWorkers];
    double s = 0.0;
    variance = durationVariance / 100.0f;
    for (int i = 0; i < w.length; ++i) {
      w[i] = (1 - variance) + randGen.nextFloat() * 2 * variance;
      s += w[i];
    }

    final int myWorkerID = (here().id * workerPerPlace) + workerId;
    taskDuration = (long) (totalWorkers * taskDuration * w[myWorkerID] / s);

    final long localTasksPerWorker;
    localTasksPerWorker = tasksPerWorker;

    for (long i = 0; i < localTasksPerWorker; ++i) {
      // The variables taskID, totalNumberOfTasks,
      // realDepth and branch are only needed for dynamic variant of synth evotree
      // so they are 0/false if generated static
      tasks.addLast(new SyntheticTask(taskBallast, taskDuration, 0, 0, 0, false));
    }
    System.out.println(
        here()
            + " worker="
            + workerId
            + " created static tasks: taskDuration="
            + taskDuration
            + ", taskCount="
            + taskCount
            + ", tasksPerWorker="
            + tasksPerWorker
            + ", localTasksPerWorker="
            + localTasksPerWorker);
  }

  @Override
  public boolean isEmpty() {
    return tasks.isEmpty();
  }

  @Override
  public boolean isSplittable() {
    return tasks.size() >= 2;
  }

  @Override
  public void merge(SyntheticQueue syntheticQueue) {
    tasks.pushArrayFirst(syntheticQueue.tasks.toArray());
    diff += syntheticQueue.diff;
    result += syntheticQueue.result;
  }

  @Override
  public int process(int workAmount, LongSum sharedObject) {
    int i = 0;
    for (; i < workAmount && tasks.size() > 0; ++i) {
      calculate();
    }
    count += i;
    result += i;
    return i;
  }

  @Override
  public SyntheticQueue split(boolean takeAll) {
    int nStolen = Math.max(tasks.size() / 2, 1);
    if (tasks.size() < 2 && !takeAll) {
      return new SyntheticQueue(durationVariance, maxChildren, isStatic, totalDuration);
    }

    // StaticSyn performs better if all tasks are taken out
    // DynamicSyn performs better if it follows the steal half scheme
    if (isStatic) {
      if (takeAll) {
        nStolen = tasks.size();
      }
    }

    final SyntheticQueue syntheticQueue =
        new SyntheticQueue(durationVariance, maxChildren, isStatic, totalDuration);
    final SyntheticTask[] fromFirst = tasks.getFromFirst(nStolen);
    for (final SyntheticTask t : fromFirst) {
      syntheticQueue.tasks.addFirst(t);
    }
    if (takeAll) {
      syntheticQueue.diff = diff;
      diff = 0;
    }

    return syntheticQueue;
  }

  @Override
  public void submit(LongSum longSum) {
    longSum.sum += result;
  }
}
