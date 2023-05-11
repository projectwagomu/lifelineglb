/*
 *  This file is part of the Handy Tools for Distributed Computing project
 *  HanDist (https://github.com/handist)
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  (C) copyright CS29 Fine 2018-2019.
 */
package handist.glb.examples.util;

import handist.glb.multiworker.Fold;

import java.io.Serializable;

/**
 * Implementation of the {@link Fold} interface that performs the addition on {@code long} integers.
 * The class also implements interface {@link Serializable} in order to be used by the GLB library.
 *
 * @author Patrick Finnerty
 */
public class DoubleArraySum implements Fold<DoubleArraySum>, Serializable {

  /** Serial Version UID */
  private static final long serialVersionUID = 5568942300441138923L;

  @Override
  public String toString() {
    double foundValue = 0.0d;
    int foundI = 0;
    for (int i = sum.length - 1; i > 0; i--) {
      if (sum[i] != 0.0) {
        foundValue = sum[i];
        foundI = i;
        break;
      }
    }
    return "(" + foundI + ") -> " + sub("" + foundValue, 0, 8);
  }

  /** Double array in which the sum is performed */
  public double[] sum;

  /*
   * (non-Javadoc)
   *
   * @see apgas.glb.Fold#fold(apgas.glb.Fold)
   */
  @Override
  public void fold(DoubleArraySum other) {
    if (this.sum.length != other.sum.length) {
      System.out.println(
          "Error: this.sum.length != other.sum.length: "
              + this.sum.length
              + " !="
              + other.sum.length);
    }
    for (int i = 0; i < other.sum.length; i++) {
      sum[i] += other.sum[i];
    }
  }

  /**
   * Constructor
   *
   * @param size for sum array
   */
  public DoubleArraySum(int size) {
    sum = new double[size];
  }

  public void printSumArray() {
    for (int i = 0; i < this.sum.length; i++) {
      if (this.sum[i] != 0.0) {
        System.out.println("(" + i + ") -> " + sub("" + this.sum[i], 0, 8));
      }
    }
  }

  /** substring helper function */
  public static String sub(String str, int start, int end) {
    return (str.substring(start, Math.min(end, str.length())));
  }
}
