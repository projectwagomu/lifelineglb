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
import java.util.List;

import apgas.Place;
import apgas.util.PlaceLocalObject;

/**
 * Factory class used to provide computation service instances to the
 * programmer.
 *
 * <p>
 * Some specific preparations need to be made for the distributed computation.
 * This class handles this process, providing the factory method that will make
 * these preparations and return a computation service ready for use.
 *
 * @author Patrick Finnerty
 */
public final class GLBFactory<R extends Fold<R> & Serializable, B extends Bag<B, R> & Serializable> {

	public GLBcomputer<R, B> setupGLB(List<? extends Place> listPlaces) {

		return PlaceLocalObject.make(listPlaces, GLBcomputer::new);
	}
}
