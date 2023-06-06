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

import java.io.Serializable;

import handist.glb.multiworker.Fold;

/**
 * Implementation of the {@link Fold} interface that performs the addition on
 * {@code long} integers. The class also implements interface
 * {@link Serializable} in order to be used by the GLB library.
 *
 * @author Patrick Finnerty
 */
public class LongSum implements Fold<LongSum>, Serializable {

	/** Serial Version UID */
	private static final long serialVersionUID = 3582168956043482749L;

	/** Long in which the sum is performed */
	public long sum;

	/**
	 * Constructor
	 *
	 * @param s initial value for the sum
	 */
	public LongSum(long s) {
		sum = s;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see apgas.glb.Fold#fold(apgas.glb.Fold)
	 */
	@Override
	public void fold(LongSum f) {
		sum += f.sum;
	}

	@Override
	public String toString() {
		return String.valueOf(sum);
	}
}
