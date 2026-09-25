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

import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * POJO containing the response to the inverters request, with the real time data of each inverter of a station
 *
 * @author Julio Vilmar Gesser - Initial contribution
 */
public class InverterPointsResponse extends BaseResponse {

    @SerializedName("data")
    private InverterPoints inverters;

    public List<Station> getInverters() {
        return inverters == null || inverters.inverterPoints == null ? List.of() : inverters.inverterPoints;
    }

    private static class InverterPoints {
        private List<Station> inverterPoints;
    }
}
