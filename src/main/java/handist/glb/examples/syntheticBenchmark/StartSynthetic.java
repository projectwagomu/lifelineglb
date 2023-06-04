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
package handist.glb.examples.syntheticBenchmark;

import static apgas.Constructs.places;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import apgas.Configuration;
import handist.glb.examples.util.ExampleHelper;
import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.GLBFactory;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;
import handist.glb.multiworker.GLBcomputer;
import handist.glb.multiworker.SerializableSupplier;

public class StartSynthetic {

	static final long BALLAST_DEFAULT = 0;
	static final boolean STATICMODE_DEFAULT = false;
	static final long TASK_DURATION_VARIANCE_DEFAULT = 20;

	static final long TASKSPERWORKER_DEFAULT = 1_000_000;
	static final long TOTALDURATION_DEFAULT = 50_000;

	public static void main(String[] args) {
		ExampleHelper.printStartMessage(StartSynthetic.class.getName());
		ExampleHelper.configureAPGAS(false);
		Configuration.printAllConfigs();
		GLBMultiWorkerConfiguration.printAllConfigs();
		final CommandLine cmd = parseArguments(args);

		final boolean staticMode = cmd.hasOption("static");
		final long ballast = Long.parseLong(cmd.getOptionValue("b", String.valueOf(BALLAST_DEFAULT)));
		final long tasksPerWorker = Long.parseLong(cmd.getOptionValue("t", String.valueOf(TASKSPERWORKER_DEFAULT)));
		final long totalDuration = Long.parseLong(cmd.getOptionValue("g", String.valueOf(TOTALDURATION_DEFAULT)));
		final long taskDurationVariance = Long
				.parseLong(cmd.getOptionValue("u", String.valueOf(TASK_DURATION_VARIANCE_DEFAULT)));

		System.out.println("Synthetic config:\n" + (staticMode ? "  static" : "  dynamic") + "\n" + "  ballast="
				+ ballast + "\n" + "  tasksPerWorker=" + tasksPerWorker + "\n" + "  totalDuration(in milliseconds)="
				+ totalDuration + "\n" + "  totalDuration(in seconds)=" + totalDuration / 1e3 + "\n"
				+ "  taskDurationVariance=" + taskDurationVariance + "\n");

		final int repetitions = GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_BENCHMARKREPETITIONS.get();

		final int workerPerPlace = GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();
		final int wholeNumPlaces;
		wholeNumPlaces = places().size();

		long expectedResult = 0;

		for (int i = 0; i < repetitions; i++) {

			final LongSum sum;
			final GLBcomputer<LongSum, SyntheticQueue> glb;

			if (staticMode) {

				expectedResult = wholeNumPlaces * workerPerPlace * tasksPerWorker;

				final SerializableSupplier<SyntheticQueue> workerInitializer = () -> {
					final SyntheticQueue sq = new SyntheticQueue(taskDurationVariance, 0, true);
					sq.initStatic(ballast, tasksPerWorker, totalDuration, taskDurationVariance);
					return sq;
				};

				final SerializableSupplier<SyntheticQueue> queueInitializer = () -> {
					final SyntheticQueue s = new SyntheticQueue(taskDurationVariance, 0, true);
					return s;
				};

				glb = new GLBFactory<LongSum, SyntheticQueue>().setupGLB(places());

				sum = glb.computeStatic(() -> new LongSum(0), queueInitializer, workerInitializer);

			} else { // dynamic
				// maxChildren=0, wird gleich durch initDynamic gesetzt
				final SyntheticQueue syntheticQueue = new SyntheticQueue(taskDurationVariance, 0, false);
				expectedResult = syntheticQueue.initDynamic(tasksPerWorker, totalDuration, ballast);
				final long _maxChildren = syntheticQueue.maxChildren;
				// final float _variance = syntheticQueue.variance;
				// final long _depth = syntheticQueue.depth;

				glb = new GLBFactory<LongSum, SyntheticQueue>().setupGLB(places());

				sum = glb.computeDynamic(syntheticQueue, () -> new LongSum(0),
						() -> new SyntheticQueue(taskDurationVariance, _maxChildren, false));
			}

			System.out.println("Run " + (i + 1) + "/" + repetitions + "; " + sum.sum + "; "
					+ glb.getLog().computationTime / 1e9 + "; ");

			System.out.println("Process time: " + glb.getLog().computationTime / 1e9 + " seconds");

			glb.getLog().printShort(System.out);
			glb.getLog().printAll(System.out);
			System.out.println();
			System.out.println("#############################################################");
			System.out.println("Result is " + (expectedResult != sum.sum ? "NOT " : "") + "correct");
			System.out.println("#############################################################");
			System.out.println();
		}
	}

	private static CommandLine parseArguments(String[] args) {
		final Options options = new Options();

		options.addOption("static", false, "Static initialization (Default dynamic)");
		options.addOption("dynamic", false, "Dynamic initialization (Default)");
		options.addOption("b", true, "Task ballast in bytes (Default 0 KiB)");
		options.addOption("t", true, "Task count per worker (only used in static version) (Default 1_000_000)");
		options.addOption("g", true, "Total duration in milliseconds (Default 10_000)");
		options.addOption("u", true, "Task duration variance percentage (Default 0");

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
