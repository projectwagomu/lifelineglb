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

import apgas.Configuration;
import apgas.Constructs;
import apgas.Place;
import handist.glb.multiworker.lifeline.MyHypercubeStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link GLBMultiWorkerConfiguration} class defines the names of the system properties used to
 * configure the global runtime.
 *
 * <p>This Class provides only String, Integer, Boolean and Double values at the moment. If you need
 * more, you have to add them manually. In {@code Configuration.get()}.
 */
public final class GLBMultiWorkerConfiguration<T> {

  public static void printAllConfigs() {
    Constructs.finish(
        () -> {
          for (final Place place : Constructs.places()) {
            Constructs.at(
                place,
                () -> {
                  GLBMultiWorkerConfiguration.printConfigs();
                });
          }
        });
  }

  public static void printConfigs() {
    List<GLBMultiWorkerConfiguration> allConfigs = new ArrayList<>();
    allConfigs.add(GLB_MULTIWORKER_N);
    allConfigs.add(GLB_MULTIWORKER_W);
    allConfigs.add(GLB_MULTIWORKER_LIFELINESTRATEGY);
    allConfigs.add(GLB_MULTIWORKER_WORKERPERPLACE);
    allConfigs.add(GLB_MULTIWORKER_BENCHMARKREPETITIONS);
    allConfigs.add(GLB_MULTIWORKER_MALLEABILITY);
    allConfigs.add(GLB_MULTIWORKER_MALLEABILITY_DELAY);
    allConfigs.add(GLB_MULTIWORKER_MALLEABILITY_MALLPLACES);
    allConfigs.add(GLB_MULTIWORKER_MALLEABILITY_ADD);

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("GLB Multiworker config on ");
    stringBuilder.append(Constructs.here() + ": \n");
    for (final GLBMultiWorkerConfiguration c : allConfigs) {
      stringBuilder.append("  " + c.getName() + "=" + c.get() + "\n");
    }
    System.out.println(stringBuilder.toString());
  }

  public static final String GLB_MULTIWORKER_N_PROPERTY = "glb.multiworker.n";
  public static final GLBMultiWorkerConfiguration<Integer> GLB_MULTIWORKER_N =
      new GLBMultiWorkerConfiguration<>(GLB_MULTIWORKER_N_PROPERTY, 511, Integer.class);

  public static final String GLB_MULTIWORKER_W_PROPERTY = "glb.multiworker.w";
  public static final GLBMultiWorkerConfiguration<Integer> GLB_MULTIWORKER_W =
      new GLBMultiWorkerConfiguration<>(GLB_MULTIWORKER_W_PROPERTY, 3, Integer.class);

  public static final String GLB_MULTIWORKER_LIFELINESTRATEGY_PROPERTY =
      "glb.multiworker.lifelinestrategy";
  public static final GLBMultiWorkerConfiguration<String> GLB_MULTIWORKER_LIFELINESTRATEGY =
      new GLBMultiWorkerConfiguration<>(
          GLB_MULTIWORKER_LIFELINESTRATEGY_PROPERTY,
          MyHypercubeStrategy.class.getCanonicalName(),
          String.class);

  public static final String GLB_MULTIWORKER_BENCHMARKREPETITIONS_PROPERTY =
      "glb.multiworker.benchmarkrepetitions";
  public static final GLBMultiWorkerConfiguration<Integer> GLB_MULTIWORKER_BENCHMARKREPETITIONS =
      new GLBMultiWorkerConfiguration<>(
          GLB_MULTIWORKER_BENCHMARKREPETITIONS_PROPERTY, 1, Integer.class);

  public static final String GLB_MULTIWORKER_WORKERPERPLACE_PROPERTY =
      "glb.multiworker.workerperplace";
  public static final GLBMultiWorkerConfiguration<Integer> GLB_MULTIWORKER_WORKERPERPLACE =
      new GLBMultiWorkerConfiguration<>(
          GLB_MULTIWORKER_WORKERPERPLACE_PROPERTY,
          Math.max(1, Configuration.APGAS_THREADS.get() / 3),
          Integer.class);

  public static final String GLB_MULTIWORKER_MALLEABILITY_PROPERTY = "glb.multiworker.malleability";
  public static final GLBMultiWorkerConfiguration<Boolean> GLB_MULTIWORKER_MALLEABILITY =
      new GLBMultiWorkerConfiguration<>(
          GLB_MULTIWORKER_MALLEABILITY_PROPERTY, false, Boolean.class);

  public static final String GLB_MULTIWORKER_MALLEABILITY_DELAY_PROPERTY =
      "glb.multiworker.malleability.delay";
  public static final GLBMultiWorkerConfiguration<Integer> GLB_MULTIWORKER_MALLEABILITY_DELAY =
      new GLBMultiWorkerConfiguration<>(
          // for testing right now, default should be 0
          GLB_MULTIWORKER_MALLEABILITY_DELAY_PROPERTY, 10, Integer.class);

  public static final String GLB_MULTIWORKER_MALLEABILITY_MALLPLACES_PROPERTY =
      "glb.multiworker.malleability.mallplaces";
  public static final GLBMultiWorkerConfiguration<Integer> GLB_MULTIWORKER_MALLEABILITY_MALLPLACES =
      new GLBMultiWorkerConfiguration<>(
          GLB_MULTIWORKER_MALLEABILITY_MALLPLACES_PROPERTY, 0, Integer.class);

  public static final String GLB_MULTIWORKER_MALLEABILITY_ADD_PROPERTY =
      "glb.multiworker.malleability.add";
  public static final GLBMultiWorkerConfiguration<Boolean> GLB_MULTIWORKER_MALLEABILITY_ADD =
      new GLBMultiWorkerConfiguration<>(
          GLB_MULTIWORKER_MALLEABILITY_ADD_PROPERTY, true, Boolean.class);

  private final String name;
  private final Class<T> propertyType;
  private T defaultValue;
  private T cachedValue;

  /**
   * Constructor
   *
   * @param name The PropertyName of the Configuration Value
   * @param propertyType The Type of the Property-Value
   */
  private GLBMultiWorkerConfiguration(final String name, final Class<T> propertyType) {
    this.name = name;
    this.propertyType = propertyType;
  }

  /**
   * Constructor
   *
   * @param name The PropertyName of the Configuration Value
   * @param defaultValue A default Value to use if no one is provided via the System-Properties
   * @param propertyType The Type of the Property-Value
   */
  private GLBMultiWorkerConfiguration(
      final String name, final T defaultValue, final Class<T> propertyType) {
    this.name = name;
    this.setDefaultValue(defaultValue);
    this.defaultValue = defaultValue;
    this.propertyType = propertyType;
  }

  /**
   * retrieve the PropertyValue of the Configuration.
   *
   * <p>This returns the default value if provided and no other Value was set or retrieved via the
   * System-Properties. If a Value is set via the System-Properties this will override the default
   * Value. If a Value is set via the setter Method, this Value will override the default Value as
   * well as the System-Property Value.
   *
   * @return The Value of this Configuration
   */
  public synchronized T get() {

    if (this.cachedValue != null) {
      return cachedValue;
    }

    String value = System.getProperty(this.name);
    if (value == null) {
      if (this.defaultValue != null) {
        this.set(this.defaultValue);
      }
      return defaultValue;
    }

    if (this.propertyType.equals(Boolean.class)) {
      Boolean aBoolean = Boolean.valueOf(value);
      this.cachedValue = (T) aBoolean;
      return this.cachedValue;
    }

    if (this.propertyType.equals(Integer.class)) {
      Integer anInt = Integer.valueOf(value);
      this.cachedValue = (T) anInt;
      return this.cachedValue;
    }

    if (this.propertyType.equals(Double.class)) {
      Double aDouble = Double.valueOf(value);
      this.cachedValue = (T) aDouble;
      return this.cachedValue;
    }

    if (this.propertyType.equals(String.class)) {
      this.cachedValue = (T) value;
      return this.cachedValue;
    }

    return (T) value;
  }

  /**
   * sets the default value to use if no System-Property is present. This can be overridden by a set
   * call.
   *
   * @param defaultValue The Value to use as default
   */
  public synchronized void setDefaultValue(T defaultValue) {
    this.defaultValue = defaultValue;
    if (System.getProperty(this.name) == null) {
      System.setProperty(this.name, String.valueOf(this.defaultValue));
    }
  }

  /**
   * set the given Value as Value for this Configuration.
   *
   * @param value The Value to Set for this Configuration
   */
  public synchronized void set(T value) {
    this.cachedValue = value;
    System.setProperty(this.name, String.valueOf(this.cachedValue));
  }

  /**
   * getter
   *
   * @return The Name of the Configuration
   */
  public synchronized String getName() {
    return name;
  }
}
