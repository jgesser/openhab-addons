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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.DigestAuthentication;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
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

    private final Logger logger = LoggerFactory.getLogger(IntelbrasDVRHandler.class);

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
                getChannelTitles();
                updateStatus(ThingStatus.ONLINE);
            } catch (Exception e) {
                logger.error("Error when connecting to DVR", e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                        "Error when connecting to DVR");
            }
        });
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.warn("Unsupported command '{}' to channel '{}'", command, channelUID);
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

        if (response.getStatus() != HttpStatus.OK_200) {
            throw new RuntimeException("Non success response: " + response.toString());
        }

        return response;
    }

    public void getChannelTitles() throws InterruptedException, ExecutionException, TimeoutException {
        executeGet(GET_CHANNEL_TITLE_URL);
    }

    public ContentResponse getSnapshot(Integer id) throws InterruptedException, ExecutionException, TimeoutException {
        return executeGet(GET_SNAPSHOT_URL, id);
    }
}
