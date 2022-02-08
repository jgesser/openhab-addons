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
package org.openhab.binding.lgthinq.internal.discovery;

import static org.openhab.binding.lgthinq.internal.LGThinqBindingConstants.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.errors.LGThinqException;
import org.openhab.binding.lgthinq.internal.handler.LGThinqBridgeHandler;
import org.openhab.binding.lgthinq.lgservices.LGThinqApiClientService;
import org.openhab.binding.lgthinq.lgservices.LGThinqApiV1ClientServiceImpl;
import org.openhab.binding.lgthinq.lgservices.LGThinqApiV2ClientServiceImpl;
import org.openhab.binding.lgthinq.lgservices.model.LGDevice;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LGThinqDiscoveryService}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class LGThinqDiscoveryService extends AbstractDiscoveryService implements DiscoveryService, ThingHandlerService {

    private final Logger logger = LoggerFactory.getLogger(LGThinqDiscoveryService.class);
    private @Nullable LGThinqBridgeHandler bridgeHandler;
    private @Nullable ThingUID bridgeHandlerUID;
    private final LGThinqApiClientService lgApiV1ClientService, lgApiV2ClientService;

    public LGThinqDiscoveryService() {
        super(SUPPORTED_THING_TYPES, SEARCH_TIME);
        lgApiV1ClientService = LGThinqApiV1ClientServiceImpl.getInstance();
        lgApiV2ClientService = LGThinqApiV2ClientServiceImpl.getInstance();
    }

    @Override
    protected void startScan() {
    }

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof LGThinqBridgeHandler) {
            bridgeHandler = (LGThinqBridgeHandler) handler;
            bridgeHandlerUID = handler.getThing().getUID();
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler;
    }

    @Override
    public void activate() {
        if (bridgeHandler != null) {
            bridgeHandler.registerDiscoveryListener(this);
        }
    }

    @Override
    public void deactivate() {
        ThingHandlerService.super.deactivate();
    }

    public void removeLgDeviceDiscovery(LGDevice device) {
        try {
            ThingUID thingUID = getThingUID(device);
            thingRemoved(thingUID);
        } catch (LGThinqException e) {
            logger.error("Error getting Thing UID");
        }
    }

    public void addLgDeviceDiscovery(LGDevice device) {
        String modelId = device.getModelName();
        ThingUID thingUID;
        ThingTypeUID thingTypeUID;
        try {
            // load capability to cache and troubleshooting
            lgApiV2ClientService.loadDeviceCapability(device.getDeviceId(), device.getModelJsonUri(), false);
            thingUID = getThingUID(device);
            thingTypeUID = getThingTypeUID(device);
        } catch (LGThinqException e) {

            logger.debug("Discovered unsupported LG device of type '{}'({}) and model '{}' with id {}",
                    device.getDeviceType(), device.getDeviceTypeId(), modelId, device.getDeviceId());
            return;
        } catch (IOException e) {
            logger.error("Error getting device capabilities", e);
            return;
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put(DEVICE_ID, device.getDeviceId());
        properties.put(DEVICE_ALIAS, device.getAlias());
        properties.put(MODEL_URL_INFO, device.getModelJsonUri());
        properties.put(PLATFORM_TYPE, device.getPlatformType());
        try {
            // registry the capabilities of the thing
            if (PLATFORM_TYPE_V1.equals(device.getPlatformType())) {
                lgApiV1ClientService.getCapability(device.getDeviceId(), device.getModelJsonUri(), true);
            } else {
                lgApiV2ClientService.getCapability(device.getDeviceId(), device.getModelJsonUri(), true);
            }

        } catch (Exception ex) {
            logger.error(
                    "Error trying to get device capabilities in discovery service. Fallback to the defaults values",
                    ex);
        }
        properties.put(Thing.PROPERTY_MODEL_ID, modelId);

        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID)
                .withProperties(properties).withBridge(bridgeHandlerUID).withRepresentationProperty(DEVICE_ID)
                .withLabel(device.getAlias()).build();

        thingDiscovered(discoveryResult);
    }

    private ThingUID getThingUID(LGDevice device) throws LGThinqException {
        ThingTypeUID thingTypeUID = getThingTypeUID(device);
        return new ThingUID(thingTypeUID,
                Objects.requireNonNull(bridgeHandlerUID, "bridgeHandleUid should never be null here"),
                device.getDeviceId());
    }

    private ThingTypeUID getThingTypeUID(LGDevice device) throws LGThinqException {
        // Short switch, but is here because it is going to be increase after new LG Devices were added
        switch (device.getDeviceType()) {
            case AIR_CONDITIONER:
                return THING_TYPE_AIR_CONDITIONER;
            case WASHING_MACHINE:
                return THING_TYPE_WASHING_MACHINE;
            default:
                throw new LGThinqException(String.format("device type [%s] not supported", device.getDeviceType()));
        }
    }
}