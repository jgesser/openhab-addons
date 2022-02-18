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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link IntelbrasBindingConstants} class defines common constants, which are used across the whole binding.
 *
 * @author Julio Gesser - Initial contribution
 */
@NonNullByDefault
public class IntelbrasBindingConstants {

    private static final String BINDING_ID = "intelbras";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_DVR = new ThingTypeUID(BINDING_ID, "dvr");
    public static final ThingTypeUID THING_TYPE_CHANNEL = new ThingTypeUID(BINDING_ID, "channel");

    // List of all Channel ids
    public static final String CHANNEL_SNAPSHOT = "snapshot";
}
