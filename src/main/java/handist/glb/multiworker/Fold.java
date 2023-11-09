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

import java.io.Serializable;

/**
 * Abstraction of a result computed by the {@link GLBcomputer}. The programmer can implement their
 * own data structure implementing this interface and use it as the {@link Bag}'s second parameter
 * type.
 *
 * <p>The {@link Fold} interface can be seen as a binary operation whose operands are two instances
 * of the implementing class and whose result is also a instance of the implementing class. This
 * operation is embodied by the {@link #fold(Fold)} method: the operands are the given parameter
 * {@code r} and {@code this} and the result is stored in {@code this}.
 *
 * <p>When the {@link GLBcomputer} computation ends, there will be as many {@link Fold}
 * implementation instances as there were places used for the computation. There is no guarantee as
 * to the order in which these (potentially many) instances will be folded into a single instance.
 * Therefore the {@link #fold(Fold)} implementation has to be symmetric in order for results to be
 * consistent from a computation to an other.
 *
 * <p>Implementation classes should implement the interface with themselves as parameter type as
 * well as the {@link Serializable} interface to ensure proper transfer from a place to an other.
 *
 * <p>Below is a simple example of a potential {@link Fold} implementation of a Sum of integers:
 *
 * <pre>
 * public class Sum implements Fold&lt;Sum&gt;, Serializable {
 *
 * 	private static final long serialVersionUID = 3582168956043482749L;
 *
 * 	public int sum;
 *
 *  &#64;Override
 *  public void fold(Sum r) {
 *    sum += r.sum;
 *  }
 *
 *  public Sum(int s) {
 *    sum = s;
 *  }
 * }
 * </pre>
 *
 * @param <R> implementing class itself (reflective-type method implementation)
 * @author Patrick Finnerty
 */
public interface Fold<R extends Fold<?> & Serializable> {

  /**
   * Folds (merges) the given parameter's result into this instance.
   *
   * @param r the Fold to be folded into {@code this}.
   */
  void fold(R r);

  @Override
  String toString();
}
