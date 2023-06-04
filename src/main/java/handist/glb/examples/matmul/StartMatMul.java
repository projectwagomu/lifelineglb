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
package handist.glb.examples.matmul;

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

/**
 * Matrix Multiplication benchmark
 *
 * @author Jonas Posner
 *
 */
public class StartMatMul {

	static final int BSIZE_DEFAULT = 4;

	static final int MSIZE_DEFAULT = 256;

	public static void main(String[] args) {
		ExampleHelper.printStartMessage(StartMatMul.class.getName());
		ExampleHelper.configureAPGAS(false);
		Configuration.printAllConfigs();
		GLBMultiWorkerConfiguration.printAllConfigs();
		final CommandLine cmd = parseArguments(args);

		final int msize = Integer.parseInt(cmd.getOptionValue("m", String.valueOf(MSIZE_DEFAULT)));
		final int bsize = Integer.parseInt(cmd.getOptionValue("b", String.valueOf(BSIZE_DEFAULT)));

		System.out.println("MatMul config:\n" + "  msize=" + msize + "\n" + "  bsize=" + bsize + "\n");

		// final int workerPerPlace =
		// GLBMultiWorkerConfiguration.GLB_MULTIWORKER_WORKERPERPLACE.get();
		// final int max = places().size() * workerPerPlace;
		// final int numberTasks = msize * msize;
		// final int taskPerWorker = numberTasks / max;

		// Condition kommt aus MatMul.initStaticTasks()
		// if ((taskPerWorker * max) != numberTasks) {
		// System.out.println("Error in Parameters!!!!");
		// System.exit(42);
		// }

		final int repetitions = GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_BENCHMARKREPETITIONS.get();

		for (int i = 0; i < repetitions; i++) {

			final int _msize = msize;
			final int _bsize = bsize;
			final SerializableSupplier<MatMul> workerInitializer = () -> {
				final MatMul matMul = new MatMul(_msize, _bsize);
				matMul.init();
				return matMul;
			};

			final SerializableSupplier<MatMul> queueInitializer = () -> {
				final MatMul matMul = new MatMul(_msize, _bsize);
				return matMul;
			};

			final GLBcomputer<LongSum, MatMul> glb = new GLBFactory<LongSum, MatMul>().setupGLB(places());

			final LongSum sum = glb.computeStatic(() -> new LongSum(0l), queueInitializer, workerInitializer);

			System.out.println("Run " + (i + 1) + "/" + repetitions + "; " + sum + "; "
					+ glb.getLog().computationTime / 1e9 + "; ");

			System.out.println("Process time: " + glb.getLog().computationTime / 1e9 + " seconds");

			glb.getLog().printShort(System.out);
			glb.getLog().printAll(System.out);
			System.out.println();
			System.out.println("#############################################################");
			proveCorrectness(_msize, sum.sum);
			System.out.println("#############################################################");
			System.out.println();
		}
	}

	private static CommandLine parseArguments(String[] args) {
		final Options options = new Options();

		options.addOption("m", true, "msize, default=" + MSIZE_DEFAULT);
		options.addOption("b", true, "bsize, default=" + BSIZE_DEFAULT);

		final CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (final ParseException e) {
			e.printStackTrace();
		}
		return cmd;
	}

	private static void proveCorrectness(int msize, long sum) {
		if ((msize * msize) != sum) {
			System.out.println("Result is NOT correct!!!!");
			return;
		}
		System.out.println("Result is correct");
	}
}
