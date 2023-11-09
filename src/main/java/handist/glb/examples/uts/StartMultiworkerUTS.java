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

public class StartMultiworkerUTS {

  static final int BRANCHING_DEFAULT = 4;
  static final int DEPTH_DEFAULT = 13;
  static final int QSIZE_DEFAULT = 64;
  static final int SEED_DEFAULT = 19;

  public static void main(String[] args) {
    ExampleHelper.printStartMessage(StartMultiworkerUTS.class.getName());
    ExampleHelper.configureAPGAS(false);
    Configuration.printAllConfigs();
    GLBMultiWorkerConfiguration.printAllConfigs();
    final CommandLine cmd = parseArguments(args);

    final int branching =
        Integer.parseInt(cmd.getOptionValue("b", String.valueOf(BRANCHING_DEFAULT)));
    final int seed = Integer.parseInt(cmd.getOptionValue("s", String.valueOf(SEED_DEFAULT)));
    final int depth = Integer.parseInt(cmd.getOptionValue("d", String.valueOf(DEPTH_DEFAULT)));
    final int qSize = Integer.parseInt(cmd.getOptionValue("q", String.valueOf(QSIZE_DEFAULT)));

    System.out.println(
        "UTS config:\n"
            + "  branching="
            + branching
            + "\n"
            + "  seed="
            + seed
            + "\n"
            + "  depth="
            + depth
            + "\n"
            + "  qSize="
            + qSize
            + "\n");

    final int repetitions =
        GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_BENCHMARKREPETITIONS.get();

    for (int i = 0; i < repetitions; i++) {

      final MultiworkerUTS multiworkerUTS = new MultiworkerUTS(qSize);
      multiworkerUTS.seed(seed, depth);

      final GLBcomputer<LongSum, MultiworkerUTS> glb =
          new GLBFactory<LongSum, MultiworkerUTS>().setupGLB(places());

      final int _qSize = qSize;
      final LongSum sum =
          glb.computeDynamic(
              multiworkerUTS, () -> new LongSum(0), () -> new MultiworkerUTS(_qSize));

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
      UTSResults.proveCorrectness(depth, seed, branching, sum.sum);
      System.out.println("#############################################################");
      System.out.println();
    }
  }

  private static CommandLine parseArguments(String[] args) {
    final Options options = new Options();
    options.addOption("b", true, "Branching factor");
    options.addOption("s", true, "Seed (0 <= r < 2^31)");
    options.addOption("d", true, "Tree depth");
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
