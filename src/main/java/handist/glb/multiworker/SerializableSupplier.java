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
import java.util.function.Supplier;

/**
 * Serializable {@link Supplier} interface. Used to lift some type inference
 * issues that occur with our library depending on the compiler used.
 *
 * @author Patrick Finnerty
 * @param <T> Type parameter for the {@link Supplier}
 */
public interface SerializableSupplier<T> extends Supplier<T>, Serializable {
}
