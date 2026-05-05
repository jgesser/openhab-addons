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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * Intelbras/Dahua device API client.
 * Encapsulates the Jetty {@link HttpClient} lifecycle, authentication (Digest and Basic),
 * and all CGI endpoint calls. Both {@link IntelbrasDVRHandler} (bridge) and {@link IntelbrasCamHandler}
 * (standalone Thing) use this class so that the HTTP/auth logic is implemented in one place.
 *
 * @author Julio Gesser
 */
@NonNullByDefault
class IntelbrasApiClient {

    static final String GET_CHANNEL_TITLE_URL = "cgi-bin/configManager.cgi?action=getConfig&name=ChannelTitle";
    static final String GET_SNAPSHOT_URL = "cgi-bin/snapshot.cgi";
    static final String GET_SNAPSHOT_CHANNEL_PARAM = "?channel={}";
    static final String GET_SETCONFIG_RECORD_MODE = "cgi-bin/configManager.cgi?action=setConfig&RecordMode[{}].Mode={}";
    static final String GET_GETCONFIG_RECORD_MODE = "cgi-bin/configManager.cgi?action=getConfig&name=RecordMode";
    static final String KEY_CHANNEL_TITLE = "table.ChannelTitle[%d].Name";
    static final String KEY_RECORD_MODE = "table.RecordMode[%d].Mode";

    private final Logger logger = LoggerFactory.getLogger(IntelbrasApiClient.class);

    private final HttpClient jettyClient;

    private String baseURL = "";
    private String username = "";
    private String password = "";
    private IntelbrasAuthMode authMode = IntelbrasAuthMode.DIGEST;

    IntelbrasApiClient(String uid, HttpClientFactory httpClientFactory) {
        this.jettyClient = httpClientFactory.createHttpClient(uid.replace(":", "-"),
                new SslContextFactory.Client(true));
    }

    void configure(String ipAddress, String username, String password, IntelbrasAuthMode authMode) {
        this.baseURL = "http://" + ipAddress + "/";
        this.username = username;
        this.password = password;
        this.authMode = authMode;
    }

    void start() throws Exception {
        jettyClient.start();
    }

    void stop() throws Exception {
        jettyClient.stop();
    }

    ContentResponse executeGet(String uri, Object... args)
            throws InterruptedException, ExecutionException, TimeoutException {
        String url = baseURL + uri;
        if (args.length > 0) {
            url = MessageFormatter.arrayFormat(url, args).getMessage();
        }
        logger.debug("Performing GET request: {}", url);

        // First request — no auth, to receive the server's Digest challenge.
        ContentResponse response = jettyClient.newRequest(url).send();

        if (response.getStatus() == HttpStatus.UNAUTHORIZED_401 && authMode == IntelbrasAuthMode.DIGEST) {
            String wwwAuth = response.getHeaders().get("WWW-Authenticate");
            if (wwwAuth != null) {
                try {
                    // Send a fresh request with the computed Digest header.
                    // A new request is used (rather than a retry on the same connection) because
                    // these devices send Connection:close on the 401 response.
                    String authHeader = DigestAuthenticator.buildHeader(wwwAuth, "GET", url, username, password);
                    response = jettyClient.newRequest(url).header("Authorization", authHeader).send();
                } catch (Exception e) {
                    throw new RuntimeException("Digest auth failed for " + url + ": " + e.getMessage(), e);
                }
            }
        } else if (response.getStatus() == HttpStatus.UNAUTHORIZED_401 && authMode == IntelbrasAuthMode.BASIC) {
            String credentials = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            response = jettyClient.newRequest(url).header("Authorization", "Basic " + credentials).send();
        }

        logger.debug("Received response from GET: {}, STATUS: {}", url, response.getStatus());

        if (!HttpStatus.isSuccess(response.getStatus())) {
            throw new RuntimeException("Non success response from GET: " + url + " : " + response.toString());
        }

        return response;
    }

    ContentResponse getSnapshot(int id) throws InterruptedException, ExecutionException, TimeoutException {
        return executeGet(GET_SNAPSHOT_URL + (id > 0 ? GET_SNAPSHOT_CHANNEL_PARAM : ""), id);
    }

    ContentResponse getChannelTitles() throws InterruptedException, ExecutionException, TimeoutException {
        return executeGet(GET_CHANNEL_TITLE_URL);
    }

    ContentResponse getRecordModes() throws InterruptedException, ExecutionException, TimeoutException {
        return executeGet(GET_GETCONFIG_RECORD_MODE);
    }

    ContentResponse setRecordMode(int id, int mode) throws InterruptedException, ExecutionException, TimeoutException {
        return executeGet(GET_SETCONFIG_RECORD_MODE, id - 1, mode);
    }

    /**
     * Parses a Dahua/Intelbras CGI key=value response body into a map.
     * Lines that do not contain exactly one '=' are ignored.
     */
    static Map<String, String> parseKeyValueResponse(String response) {
        return Stream.of(response.split("\n"))
                .map(String::trim)
                .map(s -> s.split("="))
                .filter(s -> s.length == 2)
                .collect(Collectors.toMap(s -> s[0], s -> s[1]));
    }
}
