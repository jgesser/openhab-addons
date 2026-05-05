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

import static org.openhab.binding.intelbras.internal.IntelbrasBindingConstants.CHANNEL_RECORD_MODE;
import static org.openhab.binding.intelbras.internal.IntelbrasBindingConstants.CHANNEL_TITLE;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.api.ContentResponse;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link IntelbrasDVRHandler} is responsible for handling commands, which are sent to one of the channels.
 *
 * @author Julio Gesser - Initial contribution
 */
@NonNullByDefault
public class IntelbrasDVRHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(IntelbrasDVRHandler.class);

    @Nullable
    private ScheduledFuture<?> refreshTask;

    private final IntelbrasApiClient api;

    private IntelbrasDVRConfig config = new IntelbrasDVRConfig();

    public IntelbrasDVRHandler(Bridge bridge, HttpClientFactory httpClientFactory) {
        super(bridge);
        this.api = new IntelbrasApiClient(bridge.getUID().getAsString(), httpClientFactory);
    }

    @Override
    public void initialize() {
        config = getConfigAs(IntelbrasDVRConfig.class);

        if (config.ipAddress.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Parameter ipAddress must not be empty!");
            return;
        }

        if (config.username.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Parameter username must not be empty!");
            return;
        }
        if (config.password.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Parameter password must not be empty!");
            return;
        }
        if (config.authMode != IntelbrasAuthMode.BASIC && config.authMode != IntelbrasAuthMode.DIGEST) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Unknown authentication method!");
            return;
        }

        api.configure(config.ipAddress, config.username, config.password, config.authMode);
        try {
            api.start();
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Failed to start HTTP client");
            return;
        }
        logger.debug("Authentication mode '{}' configured for thing '{}'", config.authMode, thing.getUID());

        scheduler.execute(() -> {
            try {
                refreshAll();
                updateStatus(ThingStatus.ONLINE);

                if (config.refreshInterval > 0) {
                    refreshTask = scheduler.scheduleWithFixedDelay(() -> {
                        try {
                            refreshAll();
                            updateStatus(ThingStatus.ONLINE);
                        } catch (Exception e) {
                            logger.error("Error refreshing DVR '{}': {}", thing.getUID(), e.getMessage());
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                    "Communication error: " + e.getMessage());
                        }
                    }, config.refreshInterval, config.refreshInterval, TimeUnit.SECONDS);
                }

            } catch (Exception e) {
                logger.error("Error when connecting to DVR", e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                        "Error when connecting to DVR");
            }
        });

    }

    @Override
    public void dispose() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        try {
            api.stop();
        } catch (Exception e) {
            logger.debug("Error stopping HTTP client for thing '{}'", thing.getUID(), e);
        }
    }

    private void refreshAll() {
        refreshTitles();
        refreshRecordModes();
    }

    public void refreshTitles() {
        refreshStates(CHANNEL_TITLE, IntelbrasApiClient.KEY_CHANNEL_TITLE, this::getChannelTitles, StringType::new);
    }

    public void refreshRecordModes() {
        refreshStates(CHANNEL_RECORD_MODE, IntelbrasApiClient.KEY_RECORD_MODE, this::getRecordModes,
                s -> new DecimalType(Integer.parseInt(s)));
    }

    private void refreshStates(String channelName, String mapKey, Supplier<ContentResponse> responseSupplier,
            Function<String, State> getState) {
        String response = responseSupplier.get().getContentAsString();
        Map<String, String> map = IntelbrasApiClient.parseKeyValueResponse(response);

        for (Thing thing : getThing().getThings()) {
            Channel channel = thing.getChannel(channelName);
            if (channel == null) {
                logger.debug("Channel '{}' not found in thing '{}'", channelName, thing.getUID());
                continue;
            }
            IntelbrasDVRChannelHandler handler = (IntelbrasDVRChannelHandler) thing.getHandler();
            if (handler == null) {
                logger.debug("Handler for channel '{}' not found in thing '{}'", channelName, thing.getUID());
                continue;
            }
            Integer camId = handler.getCameraId();
            String value = map.get(String.format(mapKey, camId.intValue() - 1));
            logger.debug("Got value '{}' for channel '{}' and camera ID '{}'" , value, channel.getUID(), camId);
            State state = value == null ? UnDefType.NULL : getState.apply(value);
            updateState(channel.getUID(), state);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.error("Unsupported command '{}' to channel '{}'", command, channelUID);
    }

    private ContentResponse getChannelTitles() {
        try {
            return api.getChannelTitles();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ContentResponse getRecordModes() {
        try {
            return api.getRecordModes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ContentResponse getSnapshot(Integer id) throws InterruptedException, ExecutionException, TimeoutException {
        return api.getSnapshot(id.intValue());
    }

    public ContentResponse setRecordMode(Integer id, Integer mode)
            throws InterruptedException, ExecutionException, TimeoutException {
        return api.setRecordMode(id.intValue(), mode.intValue());
    }
}
