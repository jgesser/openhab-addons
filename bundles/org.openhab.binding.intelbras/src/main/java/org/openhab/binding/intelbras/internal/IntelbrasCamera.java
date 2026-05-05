/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.intelbras.internal;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.api.ContentResponse;

/**
 * Implemented by any Thing handler that can return a camera snapshot.
 * Allows {@link IntelbrasChannelActions} to work with both channel Things
 * (backed by a DVR bridge) and standalone cam Things.
 *
 * @author Julio Gesser
 */
interface IntelbrasCamera {
    ContentResponse getSnapshot() throws InterruptedException, ExecutionException, TimeoutException;
}
