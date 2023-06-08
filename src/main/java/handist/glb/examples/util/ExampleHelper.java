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

import static apgas.Constructs.here;
import static apgas.Constructs.places;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import apgas.Configuration;
import apgas.Constructs;
import apgas.GlobalRuntime;
import apgas.Place;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;

public class ExampleHelper {

	public static void configureAPGAS(boolean verbose) {
		GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.setDefaultValue(2);
		Configuration.CONFIG_APGAS_PLACES.setDefaultValue(2);
		Configuration.CONFIG_APGAS_THREADS.setDefaultValue(6);
		Configuration.CONFIG_APGAS_IMMEDIATE_THREADS.setDefaultValue(4);
		Configuration.CONFIG_APGAS_BACKUPCOUNT.setDefaultValue(1);
		GlobalRuntime.getRuntime();

		if (verbose) {
			System.out.println(
					"APGAS config: places=" + places().size() + ", threads=" + Configuration.CONFIG_APGAS_THREADS.get()
							+ ", immediate=" + Configuration.CONFIG_APGAS_IMMEDIATE_THREADS.get() + ", resilient="
							+ Configuration.CONFIG_APGAS_RESILIENT.get() + ", backupcount="
							+ Configuration.CONFIG_APGAS_BACKUPCOUNT.get());
		}
	}

	public static void printAllFJsScheduled(final int delay) {
		for (final Place p : places()) {
			Constructs.immediateAsyncAt(p, () -> {
				final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

				scheduler.scheduleAtFixedRate(() -> {
					ForkJoinPool fj;
					fj = (ForkJoinPool) GlobalRuntime.getRuntime().getExecutorService();
					System.out.println(here() + " " + fj);
				}, 0, delay, TimeUnit.SECONDS);
			});
		}
	}

	public static void printStartMessage(String className) {
		System.out.println(
				className + " starts, date: " + ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
	}
}
