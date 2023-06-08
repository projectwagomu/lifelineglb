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

import java.io.Serializable;

public class SyntheticTask implements Serializable {

	private static final long serialVersionUID = 2282792464012580417L;

	byte[] ballast;
	long depth;
	long duration;
	long seed;

	public SyntheticTask(long ballastInBytes, long duration) {
		ballast = new byte[(int) ballastInBytes];
		this.duration = duration;
	}

	public SyntheticTask(long ballastInBytes, long seed, long depth, long duration) {
		this(ballastInBytes, duration);
		this.seed = seed;
		this.depth = depth;
	}
}
