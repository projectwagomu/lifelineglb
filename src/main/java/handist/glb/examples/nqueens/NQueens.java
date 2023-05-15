package handist.glb.examples.nqueens;

import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.Bag;

import java.io.Serializable;

import static apgas.Constructs.here;

public class NQueens implements Bag<NQueens, LongSum>, Serializable {

  public final int QUEENS;
  public final int THRESHOLD;
  public final int INIT_SIZE;
  public long count = 0;
  public long result = 0;
  public int[][] a;
  public int[] depth;
  public int size;

  public NQueens(final int queens, final int threshold, final int initSize) {
    this.THRESHOLD = threshold;
    this.QUEENS = queens;
    this.INIT_SIZE = initSize;
    this.a = new int[initSize][];
    this.depth = new int[initSize];
    this.size = 0;
  }

  public static int[] extendRight(final int[] src, final int newValue) {
    final int[] res = new int[src.length + 1];
    System.arraycopy(src, 0, res, 0, src.length);
    res[src.length] = newValue;
    return res;
  }

  /*
   * <a> contains array of <n> queen positions.  Returns 1
   * if none of the queens conflict, and returns 0 otherwise.
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

  public void push(int[] b, int d) {
    while (this.size >= this.depth.length) {
      grow();
    }
    this.a[this.size] = new int[b.length];
    System.arraycopy(b, 0, this.a[this.size], 0, b.length);
    this.depth[this.size++] = d;
  }

  public void grow() {
    int capacity = this.depth.length * 2;
    int[][] b = new int[capacity][];

    for (int i = 0; i < this.size; i++) {
      b[i] = new int[this.a[i].length];
      System.arraycopy(this.a[i], 0, b[i], 0, this.a[i].length);
    }

    this.a = b;
    int[] d = new int[capacity];
    System.arraycopy(this.depth, 0, d, 0, this.size);
    this.depth = d;
  }

  public void nqueensKernelPar() {
    int top = --this.size;
    int currentA[] = a[top];
    int currentD = depth[top];

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

          } catch (Throwable t) {
            t.printStackTrace();
          }
          nqueensKernelSeq(b2, depth[top] + 1);
        }
      }
    }
  }

  public void nqueensKernelSeq(final int[] a, final int depth) {
    if (QUEENS == depth) {
      this.result++;
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
  public boolean isEmpty() {
    if (this.size == 0) {
      return true;
    } else {
      return false;
    }
    //    return this.size == 0;
  }

  @Override
  public boolean isSplittable() {
    if (this.size >= 2) {
      return true;
    } else {
      return false;
    }
    //    return this.size > 2;
  }

  @Override
  public void merge(NQueens other) {
    if ((null == other) || other.isEmpty()) {
      System.err.println(here() + " merge: bag was empty!!!");
      return;
    }
    int otherSize = other.size;
    int newSize = this.size + otherSize;
    int thisSize = this.size;
    while (newSize >= this.depth.length) {
      this.grow();
    }

    System.arraycopy(other.depth, 0, this.depth, thisSize, otherSize);

    //    for (int i = 0; i < other.depth.length; i++) {
    for (int i = 0; i < otherSize; i++) {
      a[i + thisSize] = new int[other.a[i].length];
      System.arraycopy(other.a[i], 0, a[i + thisSize], 0, other.a[i].length);
    }

    this.size = newSize;

    this.result += other.result;
  }

  @Override
  public int process(int workAmount, LongSum sharedObject) {
    int i = 0;
    for (; ((i < workAmount) && (this.size > 0)); ++i) {
      this.nqueensKernelPar();
    }
    return i;
  }

  @Override
  public NQueens split(boolean takeAll) {
    // TODO return leeres NQueens Object oder null?
    if (this.size == 0) {
      return new NQueens(QUEENS, THRESHOLD, INIT_SIZE);
    }
    if (this.size == 1 && !takeAll) {
      return new NQueens(QUEENS, THRESHOLD, INIT_SIZE);
    }

    // TODO split half?
    int otherHalf = this.size * (1 / 6);
    if (otherHalf == 0) {
      otherHalf = 1;
    }

    int myHalf = this.size - otherHalf;

    final NQueens loot = new NQueens(QUEENS, THRESHOLD, INIT_SIZE);

    int[] lootD = new int[otherHalf];
    int[][] lootA = new int[otherHalf][];

    // von unten
    System.arraycopy(this.depth, 0, lootD, 0, otherHalf);
    System.arraycopy(this.depth, otherHalf, this.depth, 0, myHalf);

    for (int i = 0; i < otherHalf; i++) {
      lootA[i] = new int[a[i].length];
      System.arraycopy(this.a[i], 0, lootA[i], 0, a[i].length);
    }

    int j = 0;
    for (int i = otherHalf; i < this.size; i++) {
      this.a[j] = new int[a[i].length];
      System.arraycopy(this.a[i], 0, this.a[j++], 0, a[i].length);
    }

    this.size = myHalf;

    loot.a = lootA;
    loot.depth = lootD;
    loot.size = otherHalf;

    return loot;
  }

  @Override
  public void submit(LongSum sum) {
    sum.sum += result;
  }

  @Override
  public LongSum getResult() {
    return new LongSum(result);
  }

  @Override
  public long getCurrentTaskCount() {
    return size;
  }

  @Override
  public void initStaticTasks(int workerId) {
    // Never called because computeDynamic is used
  }

  public void init() {
    push(new int[0], 0);
  }
}
