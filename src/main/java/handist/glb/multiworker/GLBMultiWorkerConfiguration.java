/*
 *  This file is part of the X10 project (http://x10-lang.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  (C) Copyright IBM Corporation 2006-2016.
 */

package handist.glb.multiworker;

import java.util.ArrayList;
import java.util.List;

import apgas.Configuration;
import apgas.Constructs;
import apgas.Place;
import handist.glb.multiworker.lifeline.MyHypercubeStrategy;

/**
 * The {@link GLBMultiWorkerConfiguration} class defines the names of the system
 * properties used to configure the global runtime.
 *
 * <p>
 * This Class provides only String, Integer, Boolean and Double values at the
 * moment. If you need more, you have to add them manually. In
 * {@code Configuration.get()}.
 */
public final class GLBMultiWorkerConfiguration<T> {

	public static final String GLB_MULTIWORKER_BENCHMARKREPETITIONS_PROPERTY = "glb.multiworker.benchmarkrepetitions";

	public static final String GLB_MULTIWORKER_LIFELINESTRATEGY_PROPERTY = "glb.multiworker.lifelinestrategy";

	public static final String GLB_MULTIWORKER_N_PROPERTY = "glb.multiworker.n";

	public static final String GLB_MULTIWORKER_W_PROPERTY = "glb.multiworker.w";

	public static final String GLB_MULTIWORKER_WORKERPERPLACE_PROPERTY = "glb.multiworker.workerperplace";
	public static final GLBMultiWorkerConfiguration<Integer> GLBOPTION_MULTIWORKER_BENCHMARKREPETITIONS = new GLBMultiWorkerConfiguration<>(
			GLB_MULTIWORKER_BENCHMARKREPETITIONS_PROPERTY, 1, Integer.class);

	public static final GLBMultiWorkerConfiguration<String> GLBOPTION_MULTIWORKER_LIFELINESTRATEGY = new GLBMultiWorkerConfiguration<>(
			GLB_MULTIWORKER_LIFELINESTRATEGY_PROPERTY, MyHypercubeStrategy.class.getCanonicalName(), String.class);

	public static final GLBMultiWorkerConfiguration<Integer> GLBOPTION_MULTIWORKER_N = new GLBMultiWorkerConfiguration<>(
			GLB_MULTIWORKER_N_PROPERTY, 511, Integer.class);

	public static final GLBMultiWorkerConfiguration<Integer> GLBOPTION_MULTIWORKER_W = new GLBMultiWorkerConfiguration<>(
			GLB_MULTIWORKER_W_PROPERTY, 3, Integer.class);
	public static final GLBMultiWorkerConfiguration<Integer> GLBOPTION_MULTIWORKER_WORKERPERPLACE = new GLBMultiWorkerConfiguration<>(
			GLB_MULTIWORKER_WORKERPERPLACE_PROPERTY, Math.max(1, Configuration.CONFIG_APGAS_THREADS.get() / 3),
			Integer.class);

	public static void printAllConfigs() {
		Constructs.finish(() -> {
			for (final Place place : Constructs.places()) {
				Constructs.at(place, () -> {
					GLBMultiWorkerConfiguration.printConfigs();
				});
			}
		});
	}

	@SuppressWarnings("rawtypes")
	public static <T> void printConfigs() {
		final List<GLBMultiWorkerConfiguration> allConfigs = new ArrayList<>();
		allConfigs.add(GLBOPTION_MULTIWORKER_N);
		allConfigs.add(GLBOPTION_MULTIWORKER_W);
		allConfigs.add(GLBOPTION_MULTIWORKER_LIFELINESTRATEGY);
		allConfigs.add(GLBOPTION_MULTIWORKER_WORKERPERPLACE);
		allConfigs.add(GLBOPTION_MULTIWORKER_BENCHMARKREPETITIONS);

		final StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("GLB Multiworker config on ");
		stringBuilder.append(Constructs.here() + ": \n");
		for (final GLBMultiWorkerConfiguration c : allConfigs) {
			stringBuilder.append("  " + c.getName() + "=" + c.get() + "\n");
		}
		System.out.println(stringBuilder.toString());
	}

	private T cachedValue;
	private T defaultValue;
	private final String name;
	private final Class<T> propertyType;

	/**
	 * Constructor
	 *
	 * @param name         The PropertyName of the Configuration Value
	 * @param propertyType The Type of the Property-Value
	 */
	private GLBMultiWorkerConfiguration(final String name, final Class<T> propertyType) {
		this.name = name;
		this.propertyType = propertyType;
	}

	/**
	 * Constructor
	 *
	 * @param name         The PropertyName of the Configuration Value
	 * @param defaultValue A default Value to use if no one is provided via the
	 *                     System-Properties
	 * @param propertyType The Type of the Property-Value
	 */
	private GLBMultiWorkerConfiguration(final String name, final T defaultValue, final Class<T> propertyType) {
		this.name = name;
		this.setDefaultValue(defaultValue);
		this.defaultValue = defaultValue;
		this.propertyType = propertyType;
	}

	/**
	 * retrieve the PropertyValue of the Configuration.
	 *
	 * <p>
	 * This returns the default value if provided and no other Value was set or
	 * retrieved via the System-Properties. If a Value is set via the
	 * System-Properties this will override the default Value. If a Value is set via
	 * the setter Method, this Value will override the default Value as well as the
	 * System-Property Value.
	 *
	 * @return The Value of this Configuration
	 */
	@SuppressWarnings("unchecked")
	public synchronized T get() {

		if (cachedValue != null) {
			return cachedValue;
		}

		final String value = System.getProperty(name);
		if (value == null) {
			if (defaultValue != null) {
				this.set(defaultValue);
			}
			return defaultValue;
		}

		if (propertyType.equals(Boolean.class)) {
			final Boolean aBoolean = Boolean.valueOf(value);
			cachedValue = (T) aBoolean;
			return cachedValue;
		}

		if (propertyType.equals(Integer.class)) {
			final Integer anInt = Integer.valueOf(value);
			cachedValue = (T) anInt;
			return cachedValue;
		}

		if (propertyType.equals(Double.class)) {
			final Double aDouble = Double.valueOf(value);
			cachedValue = (T) aDouble;
			return cachedValue;
		}

		if (propertyType.equals(String.class)) {
			cachedValue = (T) value;
			return cachedValue;
		}

		return (T) value;
	}

	/**
	 * getter
	 *
	 * @return The Name of the Configuration
	 */
	public synchronized String getName() {
		return name;
	}

	/**
	 * set the given Value as Value for this Configuration.
	 *
	 * @param value The Value to Set for this Configuration
	 */
	public synchronized void set(T value) {
		cachedValue = value;
		System.setProperty(name, String.valueOf(cachedValue));
	}

	/**
	 * sets the default value to use if no System-Property is present. This can be
	 * overridden by a set call.
	 *
	 * @param defaultValue The Value to use as default
	 */
	public synchronized void setDefaultValue(T defaultValue) {
		this.defaultValue = defaultValue;
		if (System.getProperty(name) == null) {
			System.setProperty(name, String.valueOf(this.defaultValue));
		}
	}
}
