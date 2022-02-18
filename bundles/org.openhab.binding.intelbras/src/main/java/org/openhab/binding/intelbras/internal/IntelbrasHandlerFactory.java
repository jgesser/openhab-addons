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

import static org.openhab.binding.intelbras.internal.IntelbrasBindingConstants.THING_TYPE_CHANNEL;
import static org.openhab.binding.intelbras.internal.IntelbrasBindingConstants.THING_TYPE_DVR;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link IntelbrasHandlerFactory} is responsible for creating things and thing handlers.
 *
 * @author Julio Gesser - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.intelbras", service = ThingHandlerFactory.class)
public class IntelbrasHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_DVR, THING_TYPE_CHANNEL);

    private HttpClientFactory httpClientFactory;

    @Activate
    public IntelbrasHandlerFactory(@Reference HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_DVR.equals(thingTypeUID)) {
            return new IntelbrasDVRHandler((Bridge) thing, httpClientFactory.getCommonHttpClient());
        }
        if (THING_TYPE_CHANNEL.equals(thingTypeUID)) {
            return new IntelbrasChannelHandler(thing);
        }

        return null;
    }
}
