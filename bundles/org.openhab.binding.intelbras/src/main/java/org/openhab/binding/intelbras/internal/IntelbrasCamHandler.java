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
import static org.openhab.binding.intelbras.internal.IntelbrasBindingConstants.CHANNEL_SNAPSHOT;
import static org.openhab.binding.intelbras.internal.IntelbrasBindingConstants.CHANNEL_TITLE;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.api.ContentResponse;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for a standalone {@code intelbras:cam} Thing — an Intelbras/Dahua IP camera
 * accessed directly without a DVR bridge.
 * <p>
 * Channel id is always 1. All HTTP communication (including Digest auth) is delegated
 * to {@link IntelbrasApiClient}, the same helper used by {@link IntelbrasDVRHandler}.
 *
 * @author Julio Gesser
 */
@NonNullByDefault
public class IntelbrasCamHandler extends BaseThingHandler implements IntelbrasCamera {

    private static final int CAM_ID = 1;

    private final Logger logger = LoggerFactory.getLogger(IntelbrasCamHandler.class);

    @Nullable
    private ScheduledFuture<?> refreshTask;
    @Nullable
    private ScheduledFuture<?> snapshotRefreshTask;

    private final IntelbrasApiClient api;

    private IntelbrasCamConfig config = new IntelbrasCamConfig();

    public IntelbrasCamHandler(Thing thing, HttpClientFactory httpClientFactory) {
        super(thing);
        this.api = new IntelbrasApiClient(thing.getUID().getAsString(), httpClientFactory);
    }

    @Override
    public void initialize() {
        config = getConfigAs(IntelbrasCamConfig.class);

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
        if (config.snapshotRefreshInterval < 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Parameter snapshotRefreshInterval must be >= 0!");
            return;
        }

        api.configure(config.ipAddress, config.username, config.password, config.authMode);
        try {
            api.start();
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Failed to start HTTP client");
            return;
        }

        logger.debug("Authentication mode '{}' configured for cam '{}'", config.authMode, thing.getUID());

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
                            logger.error("Error refreshing cam '{}': {}", thing.getUID(), e.getMessage());
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                    "Communication error: " + e.getMessage());
                        }
                    }, config.refreshInterval, config.refreshInterval, TimeUnit.SECONDS);
                }

                if (config.snapshotRefreshInterval > 0) {
                    snapshotRefreshTask = scheduler.scheduleWithFixedDelay(() -> {
                        try {
                            refreshSnapshot();
                        } catch (Exception e) {
                            logger.error("Error refreshing snapshot for cam '{}': {}", thing.getUID(), e.getMessage());
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                    "Snapshot error: " + e.getMessage());
                        }
                    }, 0, config.snapshotRefreshInterval, TimeUnit.SECONDS);
                }

            } catch (Exception e) {
                logger.error("Error when connecting to cam '{}'", thing.getUID(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                        "Error when connecting to cam");
            }
        });
    }

    @Override
    public void dispose() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        if (snapshotRefreshTask != null) {
            snapshotRefreshTask.cancel(false);
        }
        try {
            api.stop();
        } catch (Exception e) {
            logger.debug("Error stopping HTTP client for cam '{}'", thing.getUID(), e);
        }
    }

    private void refreshAll() throws InterruptedException, ExecutionException, TimeoutException {
        refreshTitle();
        refreshRecordMode();
    }

    private void refreshTitle() throws InterruptedException, ExecutionException, TimeoutException {
        String response = api.getChannelTitles().getContentAsString();
        Map<String, String> map = IntelbrasApiClient.parseKeyValueResponse(response);
        String value = map.get(String.format(IntelbrasApiClient.KEY_CHANNEL_TITLE, CAM_ID - 1));
        updateState(CHANNEL_TITLE, value != null ? new StringType(value) : UnDefType.NULL);
    }

    private void refreshRecordMode() throws InterruptedException, ExecutionException, TimeoutException {
        String response = api.getRecordModes().getContentAsString();
        Map<String, String> map = IntelbrasApiClient.parseKeyValueResponse(response);
        String value = map.get(String.format(IntelbrasApiClient.KEY_RECORD_MODE, CAM_ID - 1));
        updateState(CHANNEL_RECORD_MODE, value != null ? new DecimalType(Integer.parseInt(value)) : UnDefType.NULL);
    }

    private void refreshSnapshot() throws InterruptedException, ExecutionException, TimeoutException {
        ContentResponse response = api.getSnapshot(CAM_ID);
        updateState(CHANNEL_SNAPSHOT, new RawType(response.getContent(),
                response.getMediaType() != null ? response.getMediaType() : RawType.DEFAULT_MIME_TYPE));
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        switch (channelUID.getId()) {
            case CHANNEL_TITLE:
                if (command instanceof RefreshType) {
                    try {
                        refreshTitle();
                    } catch (Exception e) {
                        logger.error("Error refreshing title for cam '{}'", thing.getUID(), e);
                    }
                    return;
                }
                break;
            case CHANNEL_RECORD_MODE:
                if (command instanceof RefreshType) {
                    try {
                        refreshRecordMode();
                    } catch (Exception e) {
                        logger.error("Error refreshing record mode for cam '{}'", thing.getUID(), e);
                    }
                    return;
                }
                if (command instanceof DecimalType) {
                    try {
                        api.setRecordMode(CAM_ID, ((DecimalType) command).intValue());
                        updateState(channelUID, (DecimalType) command);
                    } catch (Exception e) {
                        logger.error("Error setting record mode for cam '{}'", thing.getUID(), e);
                    }
                    return;
                }
                break;
            case CHANNEL_SNAPSHOT:
                if (command instanceof RefreshType) {
                    try {
                        refreshSnapshot();
                    } catch (Exception e) {
                        logger.error("Error refreshing snapshot for cam '{}'", thing.getUID(), e);
                    }
                    return;
                }
                break;
        }
        logger.error("Unsupported command '{}' to channel '{}'", command, channelUID);
    }

    @Override
    public ContentResponse getSnapshot() throws InterruptedException, ExecutionException, TimeoutException {
        return api.getSnapshot(CAM_ID);
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(IntelbrasChannelActions.class);
    }
}
