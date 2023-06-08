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

import java.io.Serializable;
import java.util.Arrays;

import handist.glb.examples.util.DoubleArraySum;
import handist.glb.examples.util.FixedRailQueueInt;
import handist.glb.examples.util.Graph;
import handist.glb.examples.util.Rmat;
import handist.glb.examples.util.X10Random;
import handist.glb.multiworker.Bag;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;

public class BC implements Bag<BC, DoubleArraySum>, Serializable {

	static transient Graph graph;
	static transient Object graphLock = new Object();
	static transient int N;
	private static final long serialVersionUID = 2215032896035106554L;
	// static transient int M;
	static transient int[] verticesToWorkOn;

	transient double accTime = 0;
	transient double[] deltaMap;
	transient long[] distanceMap;
	int[] lower;
	transient int[] predecessorCount;
	// These are the per-vertex data structures.
	transient int[] predecessorMap;
	// stores the local result
	double[] realBetweennessMap;

	transient long refTime = 0;
	transient FixedRailQueueInt regularQueue;
	int s;
	transient long[] sigmaMap;

	int size;

	int state = 0;

	int[] upper;

	public BC(final int qSize) {
		lower = new int[qSize];
		upper = new int[qSize];
		size = 0;
		realBetweennessMap = new double[N];
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
			realBetweennessMap[w] += deltaMap[w];
		}
		distanceMap[w] = Long.MAX_VALUE;
		sigmaMap[w] = 0L;
		deltaMap[w] = 0.0;
	}

	@Override
	public long getCurrentTaskCount() {
		return size;
	}

	@Override
	public DoubleArraySum getResult() {
		final DoubleArraySum doubleArraySum = new DoubleArraySum(realBetweennessMap.length);
		for (int i = 0; i < realBetweennessMap.length; i++) {
			doubleArraySum.sum[i] = realBetweennessMap[i];
		}
		return doubleArraySum;
	}

	private void grow() {
		final int capacity = lower.length * 2;
		final int[] l = new int[capacity];
		System.arraycopy(lower, 0, l, 0, size);
		lower = l;
		final int[] u = new int[capacity];
		System.arraycopy(upper, 0, u, 0, size);
		upper = u;
	}

	public int init(long seed, int n, double a, double b, double c, double d, int permute) {
		if (graph == null) {
			synchronized (graphLock) {
				if (graph == null) {
					final Rmat rmat = new Rmat(seed, n, a, b, c, d);
					graph = rmat.generate();
					graph.compress();
					N = graph.numVertices();
					// this.M = graph.numEdges();
					verticesToWorkOn = new int[N];
					Arrays.setAll(verticesToWorkOn, i -> i); // i is the array index
					if (permute > 0) {
						permuteVertices();
					}
				}
			}
		}

		realBetweennessMap = new double[N];
		Arrays.fill(realBetweennessMap, 0.0d);
		predecessorMap = new int[graph.numEdges()];
		predecessorCount = new int[N];
		distanceMap = new long[N];
		Arrays.setAll(distanceMap, i -> Long.MAX_VALUE); // i is the array index
		sigmaMap = new long[N];
		regularQueue = new FixedRailQueueInt(N);
		deltaMap = new double[N];
		return N;
	}

	@Override
	public void initStaticTasks(int workerId) {
		final int workerPerPlace = GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();
		final int h = (here().id * workerPerPlace) + workerId;
		final int max = places().size() * workerPerPlace;
		lower[0] = (int) ((long) N * h / max);
		upper[0] = (int) ((long) N * (h + 1) / max);
		size = 1;
	}

	@Override
	public boolean isEmpty() {
		if (size <= 0) {
			return true;
		}
		return false;
	}

	@Override
	public boolean isSplittable() {
		for (int i = 0; i < size; ++i) {
			if (2 <= (upper[i] - lower[i])) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void merge(BC other) {
		final int bagSize = other.size;
		final int thisSize = size;
		while (upper.length < bagSize + thisSize) {
			grow();
		}
		System.arraycopy(lower, 0, lower, bagSize, thisSize);
		System.arraycopy(upper, 0, upper, bagSize, thisSize);

		System.arraycopy(other.lower, 0, lower, 0, bagSize);
		System.arraycopy(other.upper, 0, upper, 0, bagSize);
		size += bagSize;

		for (int i = 0; i < other.realBetweennessMap.length; i++) {
			realBetweennessMap[i] += other.realBetweennessMap[i];
		}
	}

	/** A function to shuffle the vertices randomly to give better work dist. */
	private void permuteVertices() {
		final X10Random prng = new X10Random(1);
		for (int i = 0; i < N; i++) {
			final int indexToPick = prng.nextInt(N - i);
			final int v = verticesToWorkOn[i];
			verticesToWorkOn[i] = verticesToWorkOn[i + indexToPick];
			verticesToWorkOn[i + indexToPick] = v;
		}
	}

	@Override
	public int process(int workAmount, DoubleArraySum sharedObject) {
		int i = 0;
		if (size <= 0) {
			return 0;
		}

		switch (state) {
		case 0:
			final int top = size - 1;
			final int l = lower[top];
			final int u = upper[top] - 1;
			if (u == l) {
				size--;
			} else {
				upper[top] = u;
			}
			refTime = System.nanoTime();
			try {
				s = verticesToWorkOn[u];
			} catch (final Exception e) {
				System.out.println(here() + " exception, lower.length " + lower.length + ", upper.length "
						+ upper.length + ", verticesToWorkOn.length " + verticesToWorkOn.length + ", workAmount "
						+ workAmount + ", s " + s);
				e.printStackTrace(System.out);
			}
			state = 1;

		case 1:
			bfsShortestPath1(s);
			state = 2;

		case 2:
			while (!regularQueue.isEmpty()) {
				if (i++ > workAmount) {
					return i;
				}
				bfsShortestPath2();
			}
			state = 3;

		case 3:
			bfsShortestPath3();
			state = 4;

		case 4:
			while (!regularQueue.isEmpty()) {
				if (i++ > workAmount) {
					return i;
				}
				bfsShortestPath4(s);
			}
			accTime += ((System.nanoTime() - refTime) / 1e9);
			state = 0;
		}
		return i;
	}

	@Override
	public BC split(boolean takeAll) {
		int s = 0;
		for (int i = 0; i < size; ++i) {
			if (2 <= (upper[i] - lower[i])) {
				++s;
			}
		}

		if (s == 0) {
			if (!takeAll) {
				return null;
			}
			final BC bag = new BC(size);
			bag.merge(this);
			size = 0;
			return bag;
		}

		final BC bag = new BC(s);
		bag.size = s;

		s = 0;
		for (int i = 0; i < size; i++) {
			final int p = upper[i] - lower[i];
			if (2 <= p) {
				bag.lower[s] = lower[i];
				bag.upper[s] = upper[i] - ((p + 1) / 2);
				lower[i] = bag.upper[s];
				s++;
			}
		}
		return bag;
	}

	@Override
	public void submit(DoubleArraySum sum) {
		System.out.println(here() + " sum.length=" + sum.sum.length + ", this.realBetweennessMap.length="
				+ realBetweennessMap.length);
		for (int i = 0; i < sum.sum.length; i++) {
			sum.sum[i] += realBetweennessMap[i];
		}
	}
}
