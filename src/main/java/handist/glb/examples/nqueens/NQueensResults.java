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

public class NQueensResults {

  private static final long[] results =
      new long[] {
        0,
        1,
        0,
        0,
        2,
        10, /* 5 */
        4,
        40,
        92,
        352,
        724, /* 10 */
        2680,
        14200,
        73712,
        365596,
        2279184, /* 15 */
        14772512,
        95815104,
        666090624,
        4968057848L,
        39029188884L, /* 20 */
      };

  public static long getResult(int index) {
    if (index > results.length - 1) {
      System.out.println(
          "NQueensResults: queens " + index + " was requested, but ist not available");
      return 0;
    }
    return results[index];
  }

  public static boolean proveCorrectness(int index, long result) {
    if (index > results.length - 1) {
      System.out.println(
          "NQueensResults: queens " + index + " was requested, but ist not available");
      return false;
    }
    final boolean correctness = results[index] == result;
    if (correctness) {
      System.out.println("Result is correct");
    } else {
      System.out.println("Result is NOT correct!!!!");
    }
    return correctness;
  }
}
