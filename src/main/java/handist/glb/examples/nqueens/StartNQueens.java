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

import static apgas.Constructs.places;

import apgas.Configuration;
import handist.glb.examples.util.ExampleHelper;
import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.GLBFactory;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import handist.glb.multiworker.GLBcomputer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class StartNQueens {

  static final int QSIZE_DEFAULT = 4096;
  static final int QUEENS_DEFAULT = 15;
  static final int THRESHOLD_DEFAULT = 7;

  public static void main(String[] args) {
    ExampleHelper.printStartMessage(StartNQueens.class.getName());
    ExampleHelper.configureAPGAS(false);
    Configuration.printAllConfigs();
    GLBMultiWorkerConfiguration.printAllConfigs();
    final CommandLine cmd = parseArguments(args);

    final int queens = Integer.parseInt(cmd.getOptionValue("n", String.valueOf(QUEENS_DEFAULT)));
    final int threshold =
        Integer.parseInt(cmd.getOptionValue("t", String.valueOf(THRESHOLD_DEFAULT)));
    final int qSize = Integer.parseInt(cmd.getOptionValue("q", String.valueOf(QSIZE_DEFAULT)));
    System.out.println(
        "NQueens config:\n"
            + "  queens="
            + queens
            + "\n"
            + "  threshold="
            + threshold
            + "\n"
            + "  qSize="
            + qSize
            + "\n");

    final int repetitions =
        GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_BENCHMARKREPETITIONS.get();

    for (int i = 0; i < repetitions; i++) {

      final NQueens nQueens = new NQueens(queens, threshold, qSize);
      nQueens.init();

      final GLBcomputer<LongSum, NQueens> glb =
          new GLBFactory<LongSum, NQueens>().setupGLB(places());

      final int _queens = queens;
      final int _threshold = threshold;
      final int _qSize = qSize;
      final LongSum sum =
          glb.computeDynamic(
              nQueens, () -> new LongSum(0), () -> new NQueens(_queens, _threshold, _qSize));

      System.out.println(
          "Run "
              + (i + 1)
              + "/"
              + repetitions
              + "; "
              + sum.sum
              + "; "
              + glb.getLog().computationTime / 1e9
              + "; ");

      System.out.println("Process time: " + glb.getLog().computationTime / 1e9 + " seconds");

      glb.getLog().printShort(System.out);
      glb.getLog().printAll(System.out);
      System.out.println();
      System.out.println("#############################################################");
      NQueensResults.proveCorrectness(queens, sum.sum);
      System.out.println("#############################################################");
      System.out.println();
    }
  }

  private static CommandLine parseArguments(String[] args) {
    final Options options = new Options();

    options.addOption("n", true, "Number of queens");
    options.addOption("t", true, "Threshold");
    options.addOption("q", true, "Queue Size");

    final CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;
    try {
      cmd = parser.parse(options, args);
    } catch (final ParseException e) {
      e.printStackTrace();
    }
    return cmd;
  }
}
