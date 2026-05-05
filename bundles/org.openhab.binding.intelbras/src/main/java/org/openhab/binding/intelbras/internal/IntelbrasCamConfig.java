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

/**
 * Configuration for a standalone {@code intelbras:cam} Thing.
 * Extends {@link IntelbrasDVRConfig} (adds connectivity parameters) with the
 * {@code snapshotRefreshInterval} that only makes sense on per-camera Things.
 *
 * @author Julio Gesser
 */
public class IntelbrasCamConfig extends IntelbrasDVRConfig {

    public Integer snapshotRefreshInterval = 0;
}
