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
package handist.glb.examples.bc;

import static apgas.Constructs.here;
import static apgas.Constructs.places;

import handist.glb.examples.util.*;
import handist.glb.multiworker.Bag;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import java.io.Serializable;
import java.util.Arrays;

public class BC implements Bag<BC, DoubleArraySum>, Serializable {

  static Graph graph;
  static Object graphLock = new Object();
  static int N;
  //  static transient int M;
  static int[] verticesToWorkOn;
  private final MyIntegerDeque deque;
  int workerId;
  int state = 0;
  int s;
  // stores the local result
  double[] realBetweennessMap;
  transient long refTime = 0;
  transient double accTime = 0;
  transient FixedRailQueueInt regularQueue;
  // These are the per-vertex data structures.
  transient int[] predecessorMap;

  transient int[] predecessorCount;

  transient long[] distanceMap;

  transient long[] sigmaMap;

  transient double[] deltaMap;

  public BC(final int qSize) {
    this.realBetweennessMap = new double[N];
    this.deque = new MyIntegerDeque(qSize);
  }

  public int init(long seed, int n, double a, double b, double c, double d, int permute) {
    if (graph == null) {
      synchronized (graphLock) {
        if (graph == null) {
          Rmat rmat = new Rmat(seed, n, a, b, c, d);
          graph = rmat.generate();
          graph.compress();
          N = graph.numVertices();
          //                    this.M = graph.numEdges();
          verticesToWorkOn = new int[N];
          Arrays.setAll(verticesToWorkOn, i -> i); // i is the array index
          if (permute > 0) {
            this.permuteVertices();
          }
        }
      }
    }

    this.realBetweennessMap = new double[N];
    Arrays.fill(this.realBetweennessMap, 0.0d);
    this.predecessorMap = new int[graph.numEdges()];
    this.predecessorCount = new int[N];
    this.distanceMap = new long[N];
    Arrays.setAll(this.distanceMap, i -> Long.MAX_VALUE); // i is the array index
    this.sigmaMap = new long[N];
    this.regularQueue = new FixedRailQueueInt(N);
    this.deltaMap = new double[N];
    return N;
  }

  @Override
  public void initStaticTasks(int workerId) {
    int workerPerPlace = GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();
    final int h = (here().id * workerPerPlace) + workerId;
    final int max = places().size() * workerPerPlace;
    int lower = (int) ((long) N * h / max);
    int upper = (int) ((long) N * (h + 1) / max);
    int size = upper - lower;

    for (int i = 0; i < size; i++) {
      deque.offerLast(i + lower);
    }
  }

  /** A function to shuffle the vertices randomly to give better work dist. */
  private void permuteVertices() {
    X10Random prng = new X10Random(1);
    for (int i = 0; i < N; i++) {
      int indexToPick = prng.nextInt(N - i);
      int v = verticesToWorkOn[i];
      verticesToWorkOn[i] = verticesToWorkOn[i + indexToPick];
      verticesToWorkOn[i + indexToPick] = v;
    }
  }

  private final void bfsShortestPath1(int s) {
    // Put the values for source vertex
    distanceMap[s] = 0L;
    sigmaMap[s] = 1L;
    regularQueue.push(s);
  }

  private final void bfsShortestPath2() {
    // Pop the node with the least distance
    final int v = regularQueue.pop();
    // Get the start and the end points for the edge list for "v"
    final int edgeStart = graph.begin(v);
    final int edgeEnd = graph.end(v);

    // Iterate over all its neighbors
    for (int wIndex = edgeStart; wIndex < edgeEnd; ++wIndex) {

      // Get the target of the current edge.
      final int w = graph.getAdjacentVertexFromIndex(wIndex);
      final long distanceThroughV = distanceMap[v] + 1L;

      // In BFS, the minimum distance will only be found once --- the
      // first time that a node is discovered. So, add it to the queue.
      if (distanceMap[w] == Long.MAX_VALUE) {
        regularQueue.push(w);
        distanceMap[w] = distanceThroughV;
      }

      // If the distance through "v" for "w" from "timestamps" was the same as its
      // current distance, we found another shortest path. So, add
      // "v" to predecessorMap of "w" and update other maps.
      if (distanceThroughV == distanceMap[w]) {
        sigmaMap[w] = sigmaMap[w] + sigmaMap[v]; // XTENLANG-2027
        predecessorMap[graph.rev(w) + predecessorCount[w]++] = v;
      }
    }
  }

  protected final void bfsShortestPath3() {
    regularQueue.rewind();
  }

  protected final void bfsShortestPath4(int s) {
    final int w = regularQueue.top();
    final int rev = graph.rev(w);
    while (predecessorCount[w] > 0) {
      final int v = predecessorMap[rev + (--predecessorCount[w])];
      deltaMap[v] += (((double) sigmaMap[v]) / sigmaMap[w]) * (1.0 + deltaMap[w]);
    }

    // Accumulate updates locally
    if (w != s) {
      this.realBetweennessMap[w] += this.deltaMap[w];
    }
    distanceMap[w] = Long.MAX_VALUE;
    sigmaMap[w] = 0L;
    deltaMap[w] = 0.0;
  }

  protected final void bfsShortestPath(final int vertexIndex) {
    refTime = System.nanoTime();
    final int s = verticesToWorkOn[vertexIndex];
    bfsShortestPath1(s);
    while (!regularQueue.isEmpty()) {
      bfsShortestPath2();
    }
    bfsShortestPath3();
    while (!regularQueue.isEmpty()) {
      bfsShortestPath4(s);
    }
    accTime += (System.nanoTime() - refTime) / 1e9;
  }

  @Override
  public boolean isEmpty() {
    return this.deque.size() <= 0;
  }

  @Override
  public boolean isSplittable() {
    return this.deque.size() > 1;
  }

  @Override
  public void merge(BC other) {
    this.deque.pushArrayFirst(other.deque.toArray());

    for (int i = 0; i < other.realBetweennessMap.length; i++) {
      this.realBetweennessMap[i] += other.realBetweennessMap[i];
    }
  }

  @Override
  public int process(int workAmount, DoubleArraySum sharedObject) {
    int processedTasks = 0;
    for (int i = 0; i < workAmount && this.deque.size() > 0; ++i) {
      switch (state) {
        case 0:
          int u = deque.removeLast();
          processedTasks++;
          refTime = System.nanoTime();
          s = verticesToWorkOn[u];
          this.state = 1;

        case 1:
          this.bfsShortestPath1(s);
          this.state = 2;

        case 2:
          while (!regularQueue.isEmpty()) {
            this.bfsShortestPath2();
          }
          this.state = 3;

        case 3:
          this.bfsShortestPath3();
          this.state = 4;

        case 4:
          while (!regularQueue.isEmpty()) {
            this.bfsShortestPath4(s);
          }
          this.accTime += ((System.nanoTime() - refTime) / 1E9);
          this.state = 0;
      }
    }
    return processedTasks;
  }

  @Override
  public BC split(boolean takeAll) {
    int otherHalf = (int) (this.deque.size() * 0.5);
    if (takeAll) {
      otherHalf = this.deque.size();
    }

    if (0 == otherHalf) {
      return null;
    }

    BC bag = new BC(otherHalf);
    bag.deque.pushArrayFirst(deque.getFromFirst(otherHalf));
    return bag;
  }

  @Override
  public void submit(DoubleArraySum sum) {
    System.out.println(
        here()
            + " sum.length="
            + sum.sum.length
            + ", this.realBetweennessMap.length="
            + realBetweennessMap.length);
    for (int i = 0; i < sum.sum.length; i++) {
      sum.sum[i] += this.realBetweennessMap[i];
    }
  }

  @Override
  public DoubleArraySum getResult() {
    DoubleArraySum doubleArraySum = new DoubleArraySum(realBetweennessMap.length);
    System.arraycopy(realBetweennessMap, 0, doubleArraySum.sum, 0, realBetweennessMap.length);
    return doubleArraySum;
  }

  @Override
  public long getCurrentTaskCount() {
    return this.deque.size();
  }
}
