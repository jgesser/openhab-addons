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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.DigestAuthentication;
import org.eclipse.jetty.http.HttpStatus;
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
import org.slf4j.helpers.MessageFormatter;

/**
 * The {@link IntelbrasDVRHandler} is responsible for handling commands, which are sent to one of the channels.
 *
 * @author Julio Gesser - Initial contribution
 */
@NonNullByDefault
public class IntelbrasDVRHandler extends BaseBridgeHandler {

    private static final String GET_CHANNEL_TITLE_URL = "cgi-bin/configManager.cgi?action=getConfig&name=ChannelTitle";
    private static final String GET_SNAPSHOT_URL = "cgi-bin/snapshot.cgi?channel={}";
    private static final String GET_SETCONFIG_RECORD_MODE = "cgi-bin/configManager.cgi?action=setConfig&RecordMode[{}].Mode={}";
    private static final String GET_GETCONFIG_RECORD_MODE = "cgi-bin/configManager.cgi?action=getConfig&name=RecordMode";
    private static final String KEY_CHANNEL_TITLE = "table.ChannelTitle[%d].Name";
    private static final String KEY_RECORD_MODE = "table.RecordMode[%d].Mode";

    private final Logger logger = LoggerFactory.getLogger(IntelbrasDVRHandler.class);

    @Nullable
    private ScheduledFuture<?> refreshTask;

    private HttpClient httpClient;

    private IntelbrasDVRConfig config = new IntelbrasDVRConfig();

    public IntelbrasDVRHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        config = getConfigAs(IntelbrasDVRConfig.class);

        if (config.baseURL.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Parameter baseURL must not be empty!");
            return;
        }
        if (!config.baseURL.endsWith("/")) {
            config.baseURL = config.baseURL + "/";
        }

        if (config.username.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Parameter username must not be empty!");
            return;
        }
        if (config.password.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Parameter username must not be empty!");
            return;
        }

        try {
            AuthenticationStore authStore = httpClient.getAuthenticationStore();
            URI uri = new URI(config.baseURL);
            switch (config.authMode) {
                case BASIC:
                    authStore.addAuthentication(
                            new BasicAuthentication(uri, Authentication.ANY_REALM, config.username, config.password));
                    logger.debug("Basic Authentication configured for thing '{}'", thing.getUID());
                    break;
                case DIGEST:
                    authStore.addAuthentication(
                            new DigestAuthentication(uri, Authentication.ANY_REALM, config.username, config.password));
                    logger.debug("Digest Authentication configured for thing '{}'", thing.getUID());
                    break;
                default:
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Unknown authentication method!");
                    return;
            }
        } catch (URISyntaxException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "failed to create authentication: baseUrl is invalid");
        }

        scheduler.execute(() -> {
            try {
                refreshAll();
                updateStatus(ThingStatus.ONLINE);
            } catch (Exception e) {
                logger.error("Error when connecting to DVR", e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                        "Error when connecting to DVR");
            }
        });

        if (config.refreshInterval > 0) {
            refreshTask = scheduler.scheduleWithFixedDelay(() -> {
                try {
                    refreshAll();
                } catch (Exception e) {
                    logger.error("Error when connecting to DVR", e);
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Unable to update DVR info.");
                }
            }, config.refreshInterval, config.refreshInterval, TimeUnit.SECONDS);
        }
    }

    @Override
    public void dispose() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
    }

    private void refreshAll() throws InterruptedException, ExecutionException, TimeoutException {
        refreshTitles();
        refreshRecordModes();
    }

    public void refreshTitles() {
        refreshStates(CHANNEL_TITLE, KEY_CHANNEL_TITLE, this::getChannelTitles, StringType::new);
    }

    public void refreshRecordModes() {
        refreshStates(CHANNEL_RECORD_MODE, KEY_RECORD_MODE, this::getRecordModes,
                s -> new DecimalType(Integer.parseInt(s)));
    }

    private void refreshStates(String channelName, String mapKey, Supplier<ContentResponse> responseSupplier,
            Function<String, State> getState) {
        try {
            String response = responseSupplier.get().getContentAsString();
            Map<String, String> map = Stream.of(response.split("\n"))
                    .map(s -> s.trim())
                    .map(s -> s.split("="))
                    .filter(s -> s.length == 2)
                    .collect(Collectors.toMap(s -> s[0], s -> s[1]));

            for (Thing thing : getThing().getThings()) {
                Channel channel = thing.getChannel(channelName);
                Integer camId = ((IntelbrasChannelHandler) thing.getHandler()).getCameraId();
                String value = map.get(String.format(mapKey, camId.intValue() - 1));
                logger.debug("Got value '{}' for channel '{}' and camera ID '{}", value, channel.getUID(), camId);
                State state = value == null ? UnDefType.NULL : getState.apply(value);
                updateState(channel.getUID(), state);
            }
        } catch (Exception e) {
            logger.error("Error when connecting to DVR", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Unable to update DVR info.");
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.error("Unsupported command '{}' to channel '{}'", command, channelUID);
    }

    private ContentResponse executeGet(String uri, Object... args)
            throws InterruptedException, ExecutionException, TimeoutException {
        String url = config.baseURL + uri;
        if (args.length > 0) {
            url = MessageFormatter.arrayFormat(url, args).getMessage();
        }
        logger.debug("Performing GET request: {}", url);

        ContentResponse response = httpClient.GET(url);
        logger.debug("Received response from GET: {}, BODY: {}", response.toString());

        if (!HttpStatus.isSuccess(response.getStatus())) {
            throw new RuntimeException("Non success response: " + response.toString());
        }

        return response;
    }

    private ContentResponse getChannelTitles() {
        try {
            return executeGet(GET_CHANNEL_TITLE_URL);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ContentResponse getRecordModes() {
        try {
            return executeGet(GET_GETCONFIG_RECORD_MODE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ContentResponse getSnapshot(Integer id) throws InterruptedException, ExecutionException, TimeoutException {
        return executeGet(GET_SNAPSHOT_URL, id);
    }

    public ContentResponse setRecordMode(Integer id, Integer mode)
            throws InterruptedException, ExecutionException, TimeoutException {
        return executeGet(GET_SETCONFIG_RECORD_MODE, id.intValue() - 1, mode);
    }
}
