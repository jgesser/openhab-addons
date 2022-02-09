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
package org.openhab.binding.lgthinq.lgservices;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqDeviceV1MonitorExpiredException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqDeviceV1OfflineException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqException;
import org.openhab.binding.lgthinq.lgservices.model.*;

/**
 * The {@link LGThinqApiClientService}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public interface LGThinqApiClientService {

    List<LGDevice> listAccountDevices(String bridgeName) throws LGThinqApiException;

    Map<String, Object> getDeviceSettings(String bridgeName, String deviceId) throws LGThinqApiException;

    /**
     * Retrieve actual data from device (its sensors and points states).
     * 
     * @param deviceId device number
     * @return return snapshot state of the device
     * @throws LGThinqApiException if some error interacting with LG API Server occur.
     */
    @Nullable
    Snapshot getDeviceData(@NonNull String bridgeName, @NonNull String deviceId) throws LGThinqApiException;

    void turnDevicePower(String bridgeName, String deviceId, DevicePowerState newPowerState) throws LGThinqApiException;

    String startMonitor(String bridgeName, String deviceId)
            throws LGThinqApiException, LGThinqDeviceV1OfflineException, IOException;

    Capability getCapability(String deviceId, String uri, boolean forceRecreate) throws LGThinqApiException;

    File loadDeviceCapability(String deviceId, String uri, boolean forceRecreate)
            throws LGThinqApiException, IOException;

    void stopMonitor(String bridgeName, String deviceId, String workId) throws LGThinqException, IOException;

    @Nullable
    Snapshot getMonitorData(@NonNull String bridgeName, @NonNull String deviceId, @NonNull String workerId,
            DeviceTypes deviceType) throws LGThinqApiException, LGThinqDeviceV1MonitorExpiredException, IOException;
}
