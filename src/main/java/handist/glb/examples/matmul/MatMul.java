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
package handist.glb.examples.matmul;

import static apgas.Constructs.async;
import static apgas.Constructs.finish;
import static apgas.Constructs.here;
import static apgas.Constructs.places;

import java.io.Serializable;
import java.util.Random;

import apgas.Configuration;
import apgas.util.ConsolePrinter;
import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.Bag;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;

public class MatMul implements Bag<MatMul, LongSum>, Serializable {

	private static transient double[][][] A;
	private static transient double[][][] B;
	private static transient double[][][] C;
	static transient Object initLock = new Object();
	private static final long serialVersionUID = 2103676329015152355L;

	public static void main(String[] args) {

		final MatMul matMul = new MatMul(4, 2);
		System.out.println(matMul.isSplittable());
	}

	private final transient int bsize;
	private int currentPos = 0;
	private long currentResult = 0;
	private transient long lastPrint;
	private final transient int msize;
	// Task Queue:
	private int[] x;

	private int[] y;

	public MatMul(int msize, int bsize) {
		this.msize = msize;
		this.bsize = bsize;
		x = new int[msize * msize];
		y = new int[msize * msize];
		lastPrint = System.nanoTime();
	}

	private void addTask(int first, int second) {
		if ((currentPos + 1) >= x.length) {
			grow();
		}
		x[currentPos] = first;
		y[currentPos] = second;
		++currentPos;
	}

	@Override
	public long getCurrentTaskCount() {
		return currentPos;
	}

	@Override
	public LongSum getResult() {
		return new LongSum(currentResult);
	}

	private void grow() {
		final int n = x.length * 2;
		final int[] newX = new int[n];
		final int[] newY = new int[n];
		System.arraycopy(x, 0, newX, 0, currentPos);
		System.arraycopy(y, 0, newY, 0, currentPos);
		x = newX;
		y = newY;
	}

	public void init() {
		if (A == null) {
			synchronized (initLock) {
				if (A == null) {
					ConsolePrinter.getInstance().printlnAlways("Start init matrix");
					A = initializeMatrix();
					B = initializeMatrix();
					C = new double[msize][msize][bsize * bsize];
					ConsolePrinter.getInstance().printlnAlways("Finished init matrix");
				}
			}
		}
	}

	public double[] initializeBlock(int size, int seed) {
		final Random random = new Random(seed);
		final double[] block = new double[size * size];
		for (int i = 0; i < size * size; ++i) {
			block[i] = random.nextDouble() * 10.0;
		}
		return block;
	}

	private double[][][] initializeMatrix() {
		final double[][][] matrix = new double[msize][msize][bsize * bsize];
		finish(() -> {
			for (int i = 0; i < msize; ++i) {
				final int _i = i;
				async(() -> {
					for (int j = 0; j < msize; ++j) {
						final int seed = Integer.parseInt(_i + "" + j);
						matrix[_i][j] = initializeBlock(bsize, seed);
					}
				});
			}
		});
		return matrix;
	}

	@Override
	public void initStaticTasks(int localWorkerID) {
		final int workerPerPlace = GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();
		final int globalWorkerID = (here().id * workerPerPlace) + localWorkerID;
		final int max = places().size() * workerPerPlace;
		final int numberTasks = msize * msize;
		final int taskPerWorker = numberTasks / max;
		ConsolePrinter.getInstance()
				.println("localWorkerID=" + localWorkerID + ", globalWorkerID= " + globalWorkerID + ", workerPerPlace="
						+ workerPerPlace + ", max=" + max + ", numberTasks=" + numberTasks + ", taskPerWorker= "
						+ taskPerWorker);

		int taskCounter = 0;
		int currentWorker = 0;
		for (int i = 0; i < msize; i++) {
			for (int j = 0; j < msize; j++) {
				final int ii = i;
				final int jj = j;

				if (currentWorker == globalWorkerID) {
					addTask(ii, jj);
				}

				taskCounter++;
				if (taskCounter % taskPerWorker == 0) {
					if (currentPos > 0) { // mindestens 1 task wurde schon geaddet
						if (globalWorkerID == (max - 1) && (numberTasks > (((max - 1) * taskPerWorker) + currentPos))) {
							continue;
						}
					}

					currentWorker++;

					if (currentWorker > globalWorkerID) {
						break;
					}
				}
			}
		}
		ConsolePrinter.getInstance().println("localWorkerID=" + localWorkerID + ", globalWorkerID=" + globalWorkerID
				+ ", count of generated tasks=" + currentPos);
	}

	@Override
	public boolean isEmpty() {
		return currentPos <= 0;
	}

	@Override
	public boolean isSplittable() {
		return currentPos > 1;
	}

	@Override
	public void merge(MatMul matmul) {
		while (x.length < (matmul.currentPos + currentPos)) {
			grow();
		}

		System.arraycopy(matmul.x, 0, x, currentPos, matmul.currentPos);
		System.arraycopy(matmul.y, 0, y, currentPos, matmul.currentPos);
		currentPos += matmul.currentPos;

		currentResult += matmul.currentResult;
	}

	public void multiplyAccumulative(double[] a, double[] b, double[] c) {
		final int M = (int) Math.sqrt(a.length);
		for (int i = 0; i < M; i++) {
			for (int j = 0; j < M; j++) {
				for (int k = 0; k < M; k++) {
					c[i * M + j] += a[i * M + k] * b[k * M + j];
				}
			}
		}
	}

	/**
	 * Used for debugging purposes
	 */
	@SuppressWarnings("unused")
	private void printTaskQueues() {
		final StringBuilder stringBuilder = new StringBuilder();
		for (int i = 0; i < currentPos; i++) {
			stringBuilder.append(x[i] + "," + y[i] + "; ");
		}
		System.out.println(here() + " : " + stringBuilder.toString());
	}

	@Override
	public int process(int workAmount, LongSum sharedObject) {
		final ConsolePrinter consolePrinter = ConsolePrinter.getInstance();

		int i = 0;
		while (!isEmpty() && workAmount > 0) {
			final int ii = x[currentPos - 1];
			final int jj = y[currentPos - 1];
			--currentPos;

			for (int k = 0; k < msize; k++) {
				multiplyAccumulative(A[ii][k], B[k][jj], C[ii][jj]);
			}

			if (Configuration.CONFIG_APGAS_CONSOLEPRINTER.get()) {
				// print only every XX seconds
				final long now = System.nanoTime();
				if (((now - lastPrint) / 1e9) > 5) {
					lastPrint = now;
					consolePrinter.println("Processing...i=" + i);
				}
			}

			currentResult++;
			workAmount--;
			i++;
		}
		return i;
	}

	@Override
	public MatMul split(boolean takeAll) {
		if (currentPos == 0) {
			return new MatMul(msize, bsize);
		}

		final MatMul split = new MatMul(msize, bsize);

		/*
		 * Stealing 1/10 appeared to be faster than 1/2
		 */
		final int splitSize = currentPos * 1 / 10;
		while (split.x.length < splitSize) {
			split.grow();
		}
		if (takeAll && splitSize == 0) {
			// Special case where the bag cannot be split. The whole content of this
			// bag is given away as a result.
			for (int i = 0; i < currentPos; i++) {
				split.x[i] = x[i];
				split.y[i] = y[i];
			}
			split.currentPos = currentPos;
			currentPos = 0; // This bag is now empty
		} else {
			// Split the bag as per usual
			int currentSplitPos = 0;
			for (int i = (currentPos - splitSize); i < currentPos; ++i) {
				split.x[currentSplitPos] = x[i];
				split.y[currentSplitPos] = y[i];
				currentSplitPos++;
			}
			split.currentPos = splitSize;
			currentPos -= splitSize;
		}
		return split;
	}

	@Override
	public void submit(LongSum longSum) {
		longSum.sum += currentResult;
	}
}
