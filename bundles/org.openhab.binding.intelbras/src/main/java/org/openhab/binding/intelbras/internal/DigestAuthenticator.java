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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Computes RFC 2617 Digest Authentication headers compatible with Intelbras/Dahua devices.
 * <p>
 * Jetty's built-in {@code DigestAuthentication} generates headers with quoted {@code nc} and an explicit
 * {@code algorithm} field that these devices reject. This class produces headers in the exact format accepted by the
 * firmware (same as curl --digest).
 *
 * @author Julio Gesser
 */
class DigestAuthenticator {

    private DigestAuthenticator() {
    }

    /**
     * Builds a {@code Digest} Authorization header from a {@code WWW-Authenticate} challenge.
     *
     * @param wwwAuthenticate
     *            the value of the {@code WWW-Authenticate} response header
     * @param method
     *            the HTTP method (e.g. {@code "GET"})
     * @param requestUrl
     *            the full request URL
     * @param username
     *            the username
     * @param password
     *            the password
     * @return the value to use in the {@code Authorization} request header
     */
    static String buildHeader(String wwwAuthenticate, String method, String requestUrl,
            String username, String password) throws Exception {
        Map<String, String> params = parseParams(wwwAuthenticate);

        String realm = params.getOrDefault("realm", "");
        String nonce = params.getOrDefault("nonce", "");
        String qop = params.get("qop");
        String opaque = params.get("opaque");

        URI uri = new URI(requestUrl);
        String requestUri = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");

        String ha1 = md5(username + ":" + realm + ":" + password);
        String ha2 = md5(method + ":" + requestUri);

        String cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String nc = "00000001";

        String response;
        if ("auth".equalsIgnoreCase(qop)) {
            response = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2);
        } else {
            response = md5(ha1 + ":" + nonce + ":" + ha2);
        }

        StringBuilder auth = new StringBuilder("Digest ");
        auth.append("username=\"").append(username).append("\", ");
        auth.append("realm=\"").append(realm).append("\", ");
        auth.append("nonce=\"").append(nonce).append("\", ");
        auth.append("uri=\"").append(requestUri).append("\"");
        if ("auth".equalsIgnoreCase(qop)) {
            auth.append(", qop=auth");
            auth.append(", nc=").append(nc);
            auth.append(", cnonce=\"").append(cnonce).append("\"");
        }
        auth.append(", response=\"").append(response).append("\"");
        if (opaque != null && !opaque.isEmpty()) {
            auth.append(", opaque=\"").append(opaque).append("\"");
        }
        return auth.toString();
    }

    private static Map<String, String> parseParams(String wwwAuthenticate) {
        Map<String, String> params = new HashMap<>();
        Matcher m = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))").matcher(wwwAuthenticate);
        while (m.find()) {
            params.put(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
        }
        return params;
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
