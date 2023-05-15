package handist.glb.examples.bc;

import handist.glb.examples.util.*;
import handist.glb.multiworker.Bag;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;

import java.io.Serializable;
import java.util.Arrays;

import static apgas.Constructs.here;
import static apgas.Constructs.places;

public class BC implements Bag<BC, DoubleArraySum>, Serializable {

  static transient Graph graph;
  static transient Object graphLock = new Object();
  static transient int N;
  //  static transient int M;
  static transient int[] verticesToWorkOn;

  int workerId;
  private final int qSize;
  int[] lower;
  int[] upper;
  int state = 0;
  int s;
  int size;
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
    this.lower = new int[qSize];
    this.upper = new int[qSize];
    this.size = 0;
    this.qSize = qSize;
    this.realBetweennessMap = new double[N];
  }

  public int init(long seed, int n, double a, double b, double c, double d, int permute) {
    if (graph == null) {
      synchronized (graphLock) {
        if (graph == null) {
          Rmat rmat = new Rmat(seed, n, a, b, c, d);
          graph = rmat.generate();
          graph.compress();
          this.N = graph.numVertices();
          //          this.M = graph.numEdges();
          this.verticesToWorkOn = new int[N];
          Arrays.setAll(this.verticesToWorkOn, i -> i); // i is the array index
          if (permute > 0) {
            this.permuteVertices();
          }
        }
      }
    }

    this.realBetweennessMap = new double[this.N];
    Arrays.fill(this.realBetweennessMap, 0.0d);
    this.predecessorMap = new int[graph.numEdges()];
    this.predecessorCount = new int[this.N];
    this.distanceMap = new long[N];
    Arrays.setAll(this.distanceMap, i -> Long.MAX_VALUE); // i is the array index
    this.sigmaMap = new long[this.N];
    this.regularQueue = new FixedRailQueueInt(this.N);
    this.deltaMap = new double[this.N];
    return this.N;
  }

  @Override
  public void initStaticTasks(int workerId) {
    int workerPerPlace = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_WORKERPERPLACE.get();
    final int h = (here().id * workerPerPlace) + workerId;
    final int max = places().size() * workerPerPlace;
    this.lower[0] = (int) ((long) this.N * h / max);
    this.upper[0] = (int) ((long) this.N * (h + 1) / max);
    this.size = 1;
  }

  /** A function to shuffle the vertices randomly to give better work dist. */
  private void permuteVertices() {
    X10Random prng = new X10Random(1);
    for (int i = 0; i < this.N; i++) {
      int indexToPick = prng.nextInt(this.N - i);
      int v = this.verticesToWorkOn[i];
      this.verticesToWorkOn[i] = this.verticesToWorkOn[i + indexToPick];
      this.verticesToWorkOn[i + indexToPick] = v;
    }
  }

  private void grow() {
    int capacity = this.lower.length * 2;
    int[] l = new int[capacity];
    System.arraycopy(this.lower, 0, l, 0, this.size);
    this.lower = l;
    int[] u = new int[capacity];
    System.arraycopy(this.upper, 0, u, 0, this.size);
    this.upper = u;
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
    if (this.size <= 0) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean isSplittable() {
    for (int i = 0; i < this.size; ++i) {
      if (2 <= (this.upper[i] - this.lower[i])) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void merge(BC other) {
    int bagSize = other.size;
    int thisSize = this.size;
    while (this.upper.length < bagSize + thisSize) {
      grow();
    }
    System.arraycopy(this.lower, 0, this.lower, bagSize, thisSize);
    System.arraycopy(this.upper, 0, this.upper, bagSize, thisSize);

    System.arraycopy(other.lower, 0, this.lower, 0, bagSize);
    System.arraycopy(other.upper, 0, this.upper, 0, bagSize);
    this.size += bagSize;

    for (int i = 0; i < other.realBetweennessMap.length; i++) {
      this.realBetweennessMap[i] += other.realBetweennessMap[i];
    }
  }

  @Override
  public int process(int workAmount, DoubleArraySum sharedObject) {
    int i = 0;
    if (this.size <= 0) {
      return 0;
    }

    switch (state) {
      case 0:
        int top = this.size - 1;
        final int l = this.lower[top];
        final int u = this.upper[top] - 1;
        if (u == l) {
          this.size--;
        } else {
          this.upper[top] = u;
        }
        refTime = System.nanoTime();
        try {
          s = this.verticesToWorkOn[u];
        } catch (Exception e) {
          System.out.println(
              here()
                  + " exception, lower.length "
                  + this.lower.length
                  + ", upper.length "
                  + this.upper.length
                  + ", verticesToWorkOn.length "
                  + this.verticesToWorkOn.length
                  + ", workAmount "
                  + workAmount
                  + ", s "
                  + s);
          e.printStackTrace(System.out);
        }
        this.state = 1;

      case 1:
        this.bfsShortestPath1(s);
        this.state = 2;

      case 2:
        while (!regularQueue.isEmpty()) {
          if (i++ > workAmount) {
            return i;
          }
          this.bfsShortestPath2();
        }
        this.state = 3;

      case 3:
        this.bfsShortestPath3();
        this.state = 4;

      case 4:
        while (!regularQueue.isEmpty()) {
          if (i++ > workAmount) {
            return i;
          }
          this.bfsShortestPath4(s);
        }
        this.accTime += ((System.nanoTime() - refTime) / 1e9);
        this.state = 0;
    }
    return i;
  }

  @Override
  public BC split(boolean takeAll) {
    int s = 0;
    for (int i = 0; i < this.size; ++i) {
      if (2 <= (this.upper[i] - this.lower[i])) {
        ++s;
      }
    }

    if (s == 0) {
      if (takeAll == false) {
        return null;
      } else {
        BC bag = new BC(this.size);
        bag.merge(this);
        this.size = 0;
        return bag;
      }
    }

    BC bag = new BC(s);
    bag.size = s;

    s = 0;
    for (int i = 0; i < this.size; i++) {
      int p = this.upper[i] - this.lower[i];
      if (2 <= p) {
        bag.lower[s] = this.lower[i];
        bag.upper[s] = this.upper[i] - ((p + 1) / 2);
        this.lower[i] = bag.upper[s++];
      }
    }
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
    for (int i = 0; i < realBetweennessMap.length; i++) {
      doubleArraySum.sum[i] = realBetweennessMap[i];
    }
    return doubleArraySum;
  }

  @Override
  public long getCurrentTaskCount() {
    return this.size;
  }
}
