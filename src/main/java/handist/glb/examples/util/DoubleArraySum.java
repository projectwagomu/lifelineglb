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

  /** Double array in which the sum is performed */
  public double[] sum;

  /**
   * Constructor
   *
   * @param size for sum array
   */
  public DoubleArraySum(int size) {
    sum = new double[size];
  }

  /** substring helper function */
  public static String sub(String str, int start, int end) {
    return (str.substring(start, Math.min(end, str.length())));
  }

  /*
   * (non-Javadoc)
   *
   * @see apgas.glb.Fold#fold(apgas.glb.Fold)
   */
  @Override
  public void fold(DoubleArraySum other) {
    if (sum.length != other.sum.length) {
      System.out.println(
          "Error: this.sum.length != other.sum.length: " + sum.length + " !=" + other.sum.length);
    }
    for (int i = 0; i < other.sum.length; i++) {
      sum[i] += other.sum[i];
    }
  }

  public void printSumArray() {
    for (int i = 0; i < sum.length; i++) {
      if (sum[i] != 0.0) {
        System.out.println("(" + i + ") -> " + sub("" + sum[i], 0, 8));
      }
    }
  }

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
}
