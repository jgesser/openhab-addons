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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

import org.openhab.binding.semsportal.internal.SEMSPortalBindingConstants;

import com.google.gson.annotations.SerializedName;

/**
 * Facade for easy access to the SEMS portal data response. Data is distributed over different parts of the response
 * object
 *
 * @author Iwan Bron - Initial contribution
 */
public class StationStatus {
    // the portal reports the last update of the inverters in US format, regardless of the date format of the station
    private static final String LAST_UPDATE_DATE_FORMAT = "MM/dd/yyyy";

    @SerializedName("kpi")
    private KeyPerformanceIndicators keyPerformanceIndicators;
    // not part of the status response: set from the response of the separate inverters request
    private List<Station> stations = List.of();

    public void setStations(List<Station> stations) {
        this.stations = stations;
    }

    public Double getCurrentOutput() {
        return keyPerformanceIndicators.getCurrentOutput();
    }

    public Double getDayTotal() {
        return stations.isEmpty() ? null : stations.get(0).getDayTotal();
    }

    public Double getMonthTotal() {
        return stations.isEmpty() ? null : stations.get(0).getMonthTotal();
    }

    public Double getOverallTotal() {
        return stations.isEmpty() ? null : stations.get(0).getOverallTotal();
    }

    public Double getDayIncome() {
        return keyPerformanceIndicators.getDayIncome();
    }

    public Double getTotalIncome() {
        return keyPerformanceIndicators.getTotalIncome();
    }

    public boolean isOperational() {
        return stations.isEmpty() ? false : stations.get(0).getStatus() == 1;
    }

    public ZonedDateTime getLastUpdate() {
        if (stations.isEmpty()) {
            return null;
        }
        DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(LAST_UPDATE_DATE_FORMAT)
                .appendLiteral(" ").appendPattern(SEMSPortalBindingConstants.TIME_FORMAT).toFormatter()
                .withZone(ZoneId.systemDefault());
        Instant instant = formatter.parse(stations.get(0).getLastUpdate(), Instant::from);
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
