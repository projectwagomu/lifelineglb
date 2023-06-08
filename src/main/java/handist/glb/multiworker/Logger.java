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
package handist.glb.multiworker;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Logger class for a distributed computation. Keeps information about the
 * global computation time and contains the specifics of each place. The runtime
 * information of each place are kept in instances of {@link PlaceLogger} class.
 *
 * @author Patrick Finnerty
 */
public class Logger {

	/** Elapsed computation time in nanosecond */
	public long computationTime;

	/** Elapsed time during initialization */
	public long initializationTime;

	/**
	 * Map containing the {@link PlaceLogger} instance of each place. The key
	 * represents the place id
	 */
	public Map<Integer, PlaceLogger> placeLogs;

	/** Elapsed result gathering time in nanosecond */
	public long resultGatheringTime;

	/**
	 * Constructor (package visibility)
	 *
	 * <p>
	 * Initializes a Logger instance with the computation and result time specified
	 * as parameter. It is assumed the result gathering starts directly after the
	 * computation.
	 */
	Logger() {
		placeLogs = new HashMap<>();
	}

	/**
	 * Adds the given {@link PlaceLogger} instance to the logs of each place. The
	 * idle time of the place logger is adjusted to match the total time of the
	 * computation (during the computation phase, each place starts slightly after
	 * the beginning of the first place as some time is needed for the computation
	 * to propagate across all places).
	 *
	 * @param l the {@link PlaceLogger} instance of a certain place in the
	 *          computation.
	 */
	synchronized void addPlaceLogger(PlaceLogger l) {
		final long loggerElapsed = l.lastEventTimeStamp - l.startTimeStamp;
		final long idleCorrection = computationTime - loggerElapsed;
		l.time[0] += idleCorrection;

		placeLogs.put(l.place, l);
	}

	/**
	 * Prints all PlaceLogs
	 *
	 * @param printStream the stream to which the logs should be printed to
	 */
	public void printAll(PrintStream printStream) {
		for (final PlaceLogger placeLogger : placeLogs.values()) {
			placeLogger.print(printStream);
		}
	}

	/**
	 * Displays the computation runtime information on the provided output stream in
	 * a <em>CSV</em> format.
	 *
	 * @param out the output stream on which the information is to be displayed
	 */
	public void printShort(PrintStream out) {
		out.println("Initialization time (s); " + initializationTime / 1e9);
		out.println("Computation time (s); " + computationTime / 1e9);
		out.println("Result gathering (s); " + resultGatheringTime / 1e9);

		// Print the general counters for each place
		out.println("Place;Worker Spawns;IntraQueueSplit;IntraQueueFed;InterQueueSplit;InterQueueFed;"
				+ "Rdm Steals Attempted;Rdm Steals Successes;" + "Rdm Steals Received;Rdm Steals Suffered;"
				+ "Lifeline Steals Attempts;Lifeline Steals Success;"
				+ "Lifeline Steals Received;Lifeline Steals Suffered;"
				+ "Lifeline Thread Active(s);Lifeline Thread Holding(s);"
				+ "Lifeline Thread Inactive(s);Lifeline Thread Woken Up;"
				+ "Information Sent;Information Received;Worker Yielding;");

		for (final PlaceLogger l : placeLogs.values()) {
			out.println(l.place + ";" + l.workerSpawned + ";" + l.intraQueueSplit + ";" + l.intraQueueFed + ";"
					+ l.interQueueSplit + ";" + l.interQueueFed + ";" + l.stealsAttempted + ";" + l.stealsSuccess + ";"
					+ l.stealsReceived + ";" + l.stealsSuffered + ";" + l.lifelineStealsAttempted + ";"
					+ l.lifelineStealsSuccess + ";" + l.lifelineStealsReceived + ";" + l.lifelineStealsSuffered + ";"
					+ l.lifelineThreadActive / 1e9 + ";" + l.lifelineThreadHold / 1e9 + ";"
					+ l.lifelineThreadInactive / 1e9 + ";" + l.lifelineThreadWokenUp + ";" + l.yieldingTime / 1e9
					+ ";");
		}
		out.println();

		// Print the time spent with all the workers on each place
		out.println("WORKER DATA");
		out.println("Nb of worker spawned");
		out.print("Place;");
		for (int i = 0; i < placeLogs.get(0).time.length; i++) {
			out.print(i + ";");
		}
		out.println();

		for (final PlaceLogger l : placeLogs.values()) {
			out.print(l.place + ";");
			for (final long i : l.time) {
				out.print(i / 1e9 + ";");
			}
			out.println();
		}

		out.println("Nb of worker stealing");
		out.print("Place;");
		for (int i = 0; i < placeLogs.get(0).timeStealing.length; i++) {
			out.print(i + ";");
		}
		out.println();
		for (final PlaceLogger l : placeLogs.values()) {
			out.print(l.place + ";");
			for (final long i : l.timeStealing) {
				out.print(i / 1e9 + ";");
			}
			out.println();
		}
	}

	/**
	 * Sets the passed timings.
	 *
	 * @param initStart          initialization start in nanosecond
	 * @param computationStart   starting timestamp in nanosecond
	 * @param computationEnd     end of computation timestamp in nanosecond
	 * @param resultGatheringEnd end of result gathring timestamp in nanosecond
	 */
	public void setTimings(long initStart, long computationStart, long computationEnd, long resultGatheringEnd) {
		initializationTime = computationStart - initStart;
		computationTime = computationEnd - computationStart;
		resultGatheringTime = resultGatheringEnd - computationEnd;
	}
}
