/*
 *  Copyright 2002-2015 Barcelona Supercomputing Center (www.bsc.es)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package handist.glb.examples.matmul;

import apgas.Configuration;
import apgas.util.ConsolePrinter;
import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.Bag;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;

import java.io.Serializable;
import java.util.Random;

import static apgas.Constructs.*;

public class MatMul implements Bag<MatMul, LongSum>, Serializable {

  static transient Object initLock = new Object();
  private static transient double[][][] A;
  private static transient double[][][] B;
  private static transient double[][][] C;
  private final transient int msize;
  private final transient int bsize;
  // Task Queue:
  private int[] x;
  private int[] y;
  private int currentPos = 0;
  private long currentResult = 0;
  private transient long lastPrint;

  public MatMul(int msize, int bsize) {
    this.msize = msize;
    this.bsize = bsize;
    x = new int[msize * msize];
    y = new int[msize * msize];
    lastPrint = System.nanoTime();
  }

  public static void main(String[] args) {

    MatMul matMul = new MatMul(4, 2);
    System.out.println(matMul.isSplittable());
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

  private void addTask(int first, int second) {
    if ((currentPos + 1) >= x.length) {
      grow();
    }
    x[currentPos] = first;
    y[currentPos] = second;
    ++currentPos;
  }

  private void printTaskQueues() {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < currentPos; i++) {
      stringBuilder.append(x[i] + "," + y[i] + "; ");
    }
    System.out.println(here() + " : " + stringBuilder.toString());
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

  public void multiplyAccumulative(double[] a, double[] b, double[] c) {
    int M = (int) Math.sqrt(a.length);
    for (int i = 0; i < M; i++) {
      for (int j = 0; j < M; j++) {
        for (int k = 0; k < M; k++) {
          c[i * M + j] += a[i * M + k] * b[k * M + j];
        }
      }
    }
  }

  public double[] initializeBlock(int size, int seed) {
    Random random = new Random(seed);
    double[] block = new double[size * size];
    for (int i = 0; i < size * size; ++i) {
      block[i] = random.nextDouble() * 10.0;
    }
    return block;
  }

  private double[][][] initializeMatrix() {
    final double[][][] matrix = new double[msize][msize][bsize * bsize];
    finish(
        () -> {
          for (int i = 0; i < msize; ++i) {
            final int _i = i;
            async(
                () -> {
                  for (int j = 0; j < msize; ++j) {
                    int seed = Integer.parseInt(_i + "" + j);
                    matrix[_i][j] = initializeBlock(bsize, seed);
                  }
                });
          }
        });
    return matrix;
  }

  @Override
  public boolean isEmpty() {
    return this.currentPos <= 0;
  }

  @Override
  public boolean isSplittable() {
    return this.currentPos > 1;
  }

  @Override
  public void merge(MatMul matmul) {
    while (this.x.length < (matmul.currentPos + currentPos)) {
      this.grow();
    }

    System.arraycopy(matmul.x, 0, this.x, currentPos, matmul.currentPos);
    System.arraycopy(matmul.y, 0, this.y, currentPos, matmul.currentPos);
    this.currentPos += matmul.currentPos;

    this.currentResult += matmul.currentResult;
  }

  @Override
  public int process(int workAmount, LongSum sharedObject) {
    ConsolePrinter consolePrinter = ConsolePrinter.getInstance();

    int i = 0;
    while (!isEmpty() && workAmount > 0) {
      final int ii = x[currentPos - 1];
      final int jj = y[currentPos - 1];
      --currentPos;

      for (int k = 0; k < msize; k++) {
        multiplyAccumulative(A[ii][k], B[k][jj], C[ii][jj]);
      }

      if (Configuration.APGAS_CONSOLEPRINTER.get() == true) {
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

    MatMul split = new MatMul(msize, bsize);

    /*
     * 1/10 ist mit folgenden Konfiguration deutlich schneller (besseres Load Balancing) als 1/2.
     * Getestet am 09.12.20 in Kassel mit 1-8 Places, je 12 Worker:
     * -m 288 -b 64,
     * -m 576 -b 32
     */
    int splitSize = currentPos * 1 / 10;
    while (split.x.length < splitSize) {
      split.grow();
    }
    if (takeAll && splitSize == 0) {
      // Special case where the bag cannot be split. The whole content of this
      // bag is given away as a result.
      for (int i = 0; i < currentPos; i++) {
        split.x[i] = this.x[i];
        split.y[i] = this.y[i];
      }
      split.currentPos = this.currentPos;
      this.currentPos = 0; // This bag is now empty
    } else {
      // Split the bag as per usual
      int currentSplitPos = 0;
      for (int i = (currentPos - splitSize); i < currentPos; ++i) {
        split.x[currentSplitPos] = this.x[i];
        split.y[currentSplitPos] = this.y[i];
        currentSplitPos++;
      }
      split.currentPos = splitSize;
      this.currentPos -= splitSize;
    }
    return split;
  }

  @Override
  public void submit(LongSum longSum) {
    longSum.sum += currentResult;
  }

  @Override
  public LongSum getResult() {
    return new LongSum(currentResult);
  }

  @Override
  public long getCurrentTaskCount() {
    return this.currentPos;
  }

  @Override
  public void initStaticTasks(int localWorkerID) {
    int workerPerPlace = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_WORKERPERPLACE.get();
    final int globalWorkerID = (here().id * workerPerPlace) + localWorkerID;
    final int max = places().size() * workerPerPlace;
    final int numberTasks = msize * msize;
    final int taskPerWorker = numberTasks / max;
    ConsolePrinter.getInstance()
        .println(
            "localWorkerID="
                + localWorkerID
                + ", globalWorkerID= "
                + globalWorkerID
                + ", workerPerPlace="
                + workerPerPlace
                + ", max="
                + max
                + ", numberTasks="
                + numberTasks
                + ", taskPerWorker= "
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

        if (++taskCounter % taskPerWorker == 0) {
          if (currentPos > 0) { // mindestens 1 task wurde schon geaddet
            if (globalWorkerID == (max - 1)
                && (numberTasks > (((max - 1) * taskPerWorker) + currentPos))) {
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
    ConsolePrinter.getInstance()
        .println(
            "localWorkerID="
                + localWorkerID
                + ", globalWorkerID="
                + globalWorkerID
                + ", count of generated tasks="
                + currentPos);
    //    printTaskQueues();
  }
}
