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
package handist.glb.examples.nqueens;

import static apgas.Constructs.here;

import java.io.Serializable;

import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.Bag;

public class NQueens implements Bag<NQueens, LongSum>, Serializable {

	private static final long serialVersionUID = -6118047016331246031L;

	public static int[] extendRight(final int[] src, final int newValue) {
		final int[] res = new int[src.length + 1];
		System.arraycopy(src, 0, res, 0, src.length);
		res[src.length] = newValue;
		return res;
	}

	/*
	 * <a> contains array of <n> queen positions. Returns 1 if none of the queens
	 * conflict, and returns 0 otherwise.
	 */
	public static boolean isBoardValid(final int n, final int[] a) {
		int i, j;
		int p, q;
		for (i = 0; i < n; i++) {
			p = a[i];
			for (j = (i + 1); j < n; j++) {
				q = a[j];
				if (q == p || q == p - (j - i) || q == p + (j - i)) {
					return false;
				}
			}
		}
		return true;
	}

	public int[][] a;
	public long count = 0;
	public int[] depth;
	public final int INIT_SIZE;
	public final int QUEENS;
	public long result = 0;

	public int size;

	public final int THRESHOLD;

	public NQueens(final int queens, final int threshold, final int initSize) {
		THRESHOLD = threshold;
		QUEENS = queens;
		INIT_SIZE = initSize;
		a = new int[initSize][];
		depth = new int[initSize];
		size = 0;
	}

	@Override
	public long getCurrentTaskCount() {
		return size;
	}

	@Override
	public LongSum getResult() {
		return new LongSum(result);
	}

	public void grow() {
		final int capacity = depth.length * 2;
		final int[][] b = new int[capacity][];

		for (int i = 0; i < size; i++) {
			b[i] = new int[a[i].length];
			System.arraycopy(a[i], 0, b[i], 0, a[i].length);
		}

		a = b;
		final int[] d = new int[capacity];
		System.arraycopy(depth, 0, d, 0, size);
		depth = d;
	}

	public void init() {
		push(new int[0], 0);
	}

	@Override
	public void initStaticTasks(int workerId) {
		// Never called because computeDynamic is used
	}

	@Override
	public boolean isEmpty() {
		if (size == 0) {
			return true;
		}
		return false;
	}

	@Override
	public boolean isSplittable() {
		if (size >= 2) {
			return true;
		}
		return false;
	}

	@Override
	public void merge(NQueens other) {
		if ((null == other) || other.isEmpty()) {
			System.err.println(here() + " merge: bag was empty!!!");
			return;
		}
		final int otherSize = other.size;
		final int newSize = size + otherSize;
		final int thisSize = size;
		while (newSize >= depth.length) {
			grow();
		}

		System.arraycopy(other.depth, 0, depth, thisSize, otherSize);

		// for (int i = 0; i < other.depth.length; i++) {
		for (int i = 0; i < otherSize; i++) {
			a[i + thisSize] = new int[other.a[i].length];
			System.arraycopy(other.a[i], 0, a[i + thisSize], 0, other.a[i].length);
		}

		size = newSize;

		result += other.result;
	}

	public void nqueensKernelPar() {
		final int top = --size;
		final int currentA[] = a[top];
		final int currentD = depth[top];

		for (int i = 0; i < QUEENS; i++) {
			final int ii = i;
			final int[] b = extendRight(currentA, ii);

			++count;
			if (isBoardValid((currentD + 1), b)) {
				if (currentD < THRESHOLD) {
					push(b, currentD + 1);
				} else {
					final int[] b2 = new int[QUEENS];
					try {
						System.arraycopy(b, 0, b2, 0, b.length);

					} catch (final Throwable t) {
						t.printStackTrace();
					}
					nqueensKernelSeq(b2, depth[top] + 1);
				}
			}
		}
	}

	public void nqueensKernelSeq(final int[] a, final int depth) {
		if (QUEENS == depth) {
			result++;
			return;
		}

		for (int i = 0; i < QUEENS; i++) {
			a[depth] = i;
			++count;
			if (isBoardValid((depth + 1), a)) {
				nqueensKernelSeq(a, depth + 1);
			}
		}
	}

	@Override
	public int process(int workAmount, LongSum sharedObject) {
		int i = 0;
		for (; ((i < workAmount) && (size > 0)); ++i) {
			nqueensKernelPar();
		}
		return i;
	}

	public void push(int[] b, int d) {
		while (size >= depth.length) {
			grow();
		}
		a[size] = new int[b.length];
		System.arraycopy(b, 0, a[size], 0, b.length);
		depth[size++] = d;
	}

	@Override
	public NQueens split(boolean takeAll) {
		if ((size == 0) || (size == 1 && !takeAll)) {
			return new NQueens(QUEENS, THRESHOLD, INIT_SIZE);
		}

		/*
		 * Stealing 1/6 appeared to be faster than 1/2
		 */
		int otherHalf = size * (1 / 6);
		if (otherHalf == 0) {
			otherHalf = 1;
		}

		final int myHalf = size - otherHalf;

		final NQueens loot = new NQueens(QUEENS, THRESHOLD, INIT_SIZE);

		final int[] lootD = new int[otherHalf];
		final int[][] lootA = new int[otherHalf][];

		// von unten
		System.arraycopy(depth, 0, lootD, 0, otherHalf);
		System.arraycopy(depth, otherHalf, depth, 0, myHalf);

		for (int i = 0; i < otherHalf; i++) {
			lootA[i] = new int[a[i].length];
			System.arraycopy(a[i], 0, lootA[i], 0, a[i].length);
		}

		int j = 0;
		for (int i = otherHalf; i < size; i++) {
			a[j] = new int[a[i].length];
			System.arraycopy(a[i], 0, a[j], 0, a[i].length);
			j++;
		}

		size = myHalf;

		loot.a = lootA;
		loot.depth = lootD;
		loot.size = otherHalf;

		return loot;
	}

	@Override
	public void submit(LongSum sum) {
		sum.sum += result;
	}
}
