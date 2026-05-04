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
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;
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
import org.slf4j.helpers.MessageFormatter;

/**
 * The {@link IntelbrasDVRHandler} is responsible for handling commands, which are sent to one of the channels.
 *
 * @author Julio Gesser - Initial contribution
 */
@NonNullByDefault
public class IntelbrasDVRHandler extends BaseBridgeHandler {

    private static final String GET_CHANNEL_TITLE_URL = "cgi-bin/configManager.cgi?action=getConfig&name=ChannelTitle";
    private static final String GET_SNAPSHOT_URL = "cgi-bin/snapshot.cgi";
    private static final String GET_SNAPSHOT_CHANNEL_PARAM = "?channel={}";
    private static final String GET_SETCONFIG_RECORD_MODE = "cgi-bin/configManager.cgi?action=setConfig&RecordMode[{}].Mode={}";
    private static final String GET_GETCONFIG_RECORD_MODE = "cgi-bin/configManager.cgi?action=getConfig&name=RecordMode";
    private static final String KEY_CHANNEL_TITLE = "table.ChannelTitle[%d].Name";
    private static final String KEY_RECORD_MODE = "table.RecordMode[%d].Mode";

    private final Logger logger = LoggerFactory.getLogger(IntelbrasDVRHandler.class);

    @Nullable
    private ScheduledFuture<?> refreshTask;

    private final HttpClientFactory httpClientFactory;
    private HttpClient httpClient;

    private IntelbrasDVRConfig config = new IntelbrasDVRConfig();

    public IntelbrasDVRHandler(Bridge bridge, HttpClientFactory httpClientFactory) {
        super(bridge);
        this.httpClientFactory = httpClientFactory;
        this.httpClient = httpClientFactory.createHttpClient(bridge.getUID().getAsString().replace(":", "-"),
                new SslContextFactory.Client(true));
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
            httpClient.start();
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Failed to start HTTP client");
            return;
        }

        if (config.authMode != IntelbrasAuthMode.BASIC && config.authMode != IntelbrasAuthMode.DIGEST) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Unknown authentication method!");
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
            httpClient.stop();
        } catch (Exception e) {
            logger.debug("Error stopping HTTP client for thing '{}'", thing.getUID(), e);
        }
    }

    private void refreshAll() {
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
        String response = responseSupplier.get().getContentAsString();
        Map<String, String> map = Stream.of(response.split("\n"))
                .map(s -> s.trim())
                .map(s -> s.split("="))
                .filter(s -> s.length == 2)
                .collect(Collectors.toMap(s -> s[0], s -> s[1]));

        for (Thing thing : getThing().getThings()) {
            Channel channel = thing.getChannel(channelName);
            if (channel == null) {
                logger.debug("Channel '{}' not found in thing '{}'", channelName, thing.getUID());
                continue;
            }
            IntelbrasChannelHandler handler = (IntelbrasChannelHandler) thing.getHandler();
            if (handler == null) {
                logger.debug("Handler for channel '{}' not found in thing '{}'", channelName, thing.getUID());
                continue;
            }
            Integer camId = handler.getCameraId();
            String value = map.get(String.format(mapKey, camId.intValue() - 1));
            logger.debug("Got value '{}' for channel '{}' and camera ID '{}", value, channel.getUID(), camId);
            State state = value == null ? UnDefType.NULL : getState.apply(value);
            updateState(channel.getUID(), state);
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

        // First request — no auth, to receive the server's Digest challenge
        ContentResponse response = httpClient.newRequest(url).send();

        if (response.getStatus() == HttpStatus.UNAUTHORIZED_401 && config.authMode == IntelbrasAuthMode.DIGEST) {
            String wwwAuth = response.getHeaders().get("WWW-Authenticate");
            if (wwwAuth != null) {
                try {
                    // Send a fresh request with the computed Digest header.
                    // A new request is used (rather than a retry on the same connection) because
                    // these devices send Connection:close on the 401 response.
                    String authHeader = DigestAuthenticator.buildHeader(wwwAuth, "GET", url, config.username, config.password);
                    response = httpClient.newRequest(url).header("Authorization", authHeader).send();
                } catch (Exception e) {
                    throw new RuntimeException("Digest auth failed for " + url + ": " + e.getMessage(), e);
                }
            }
        } else if (response.getStatus() == HttpStatus.UNAUTHORIZED_401 && config.authMode == IntelbrasAuthMode.BASIC) {
            // Basic auth devices that require auth but don't preemptively receive the header
            java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
            String credentials = encoder.encodeToString(
                    (config.username + ":" + config.password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            response = httpClient.newRequest(url).header("Authorization", "Basic " + credentials).send();
        }

        logger.debug("Received response from GET: {}, STATUS: {}", url, response.getStatus());

        if (!HttpStatus.isSuccess(response.getStatus())) {
            throw new RuntimeException("Non success response from GET: " + url + " : " + response.toString());
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
        return executeGet(GET_SNAPSHOT_URL + (id.intValue() > 0 ? GET_SNAPSHOT_CHANNEL_PARAM : ""), id);
    }

    public ContentResponse setRecordMode(Integer id, Integer mode)
            throws InterruptedException, ExecutionException, TimeoutException {
        return executeGet(GET_SETCONFIG_RECORD_MODE, id.intValue() - 1, mode);
    }
}
