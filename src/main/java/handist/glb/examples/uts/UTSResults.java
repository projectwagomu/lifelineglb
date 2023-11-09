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
package handist.glb.examples.uts;

public class UTSResults {

  /** Correct results for seed = 19, branching = 4, index == depth */
  private static final long[] results =
      new long[] {
        0L,
        6L,
        65L,
        254L,
        944L,
        3987L,
        16000L,
        63914L,
        257042L,
        1031269L,
        4130071L,
        16526523L,
        66106929L,
        264459392L,
        1057675516L,
        4230646601L,
        16922208327L,
        67688164184L,
        270751679750L,
        1083006612025L
      };

  public static long getResult(int index) {
    if (index > results.length - 1) {
      System.out.println("UTSResults: depth " + index + " was requested, but ist not available");
      return 0;
    }
    return results[index];
  }

  public static boolean proveCorrectness(int depth, int seed, int branching, long result) {
    if (depth > results.length - 1 || seed != 19 || branching != 4) {
      System.out.println(
          "UTSResults: depth="
              + depth
              + ", seed="
              + seed
              + ", branching="
              + branching
              + " was requested, but ist not available");
      return false;
    }
    final boolean correctness = results[depth] == result;
    if (correctness) {
      System.out.println("Result is correct");
    } else {
      System.out.println("Result is NOT correct!!!!");
    }
    return correctness;
  }
}
