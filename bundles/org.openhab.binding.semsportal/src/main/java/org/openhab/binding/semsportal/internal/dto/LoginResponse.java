/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.semsportal.internal.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Response to a {@link LoginRequest} to the portal.
 *
 * @author Iwan Bron - Initial contribution
 */
public class LoginResponse extends BaseResponse {
    @SerializedName("data")
    private SEMSToken token;
    // regional API of the account (e.g. https://hk.semsportal.com/api/), to be used for all other calls
    @SerializedName("api")
    private String api;

    public SEMSToken getToken() {
        return token;
    }

    public String getApi() {
        return api;
    }
}
