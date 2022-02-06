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

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.V2_CTRL_DEVICE_CONFIG_PATH;

import java.io.IOException;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.api.RestResult;
import org.openhab.binding.lgthinq.internal.api.RestUtils;
import org.openhab.binding.lgthinq.internal.api.TokenResult;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqDeviceV1MonitorExpiredException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqDeviceV1OfflineException;
import org.openhab.binding.lgthinq.internal.errors.RefreshTokenException;
import org.openhab.binding.lgthinq.lgservices.model.DevicePowerState;
import org.openhab.binding.lgthinq.lgservices.model.DeviceTypes;
import org.openhab.binding.lgthinq.lgservices.model.ResultCodes;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACCapability;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACSnapshot;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACTargetTmp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * The {@link LGThinQACApiV2ClientServiceImpl}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class LGThinQACApiV2ClientServiceImpl extends LGThinQAbstractApiClientService<ACCapability, ACSnapshot>
        implements LGThinQACApiClientService {
    private static final LGThinQACApiClientService instance;
    private static final Logger logger = LoggerFactory.getLogger(LGThinQACApiV2ClientServiceImpl.class);

    static {
        instance = new LGThinQACApiV2ClientServiceImpl(ACCapability.class, ACSnapshot.class);
    }

    protected LGThinQACApiV2ClientServiceImpl(Class<ACCapability> capabilityClass, Class<ACSnapshot> snapshotClass) {
        super(capabilityClass, snapshotClass);
    }

    public static LGThinQACApiClientService getInstance() {
        return instance;
    }

    private Map<String, String> getCommonV2Headers(String language, String country, String accessToken,
            String userNumber) {
        return getCommonHeaders(language, country, accessToken, userNumber);
    }

    private RestResult sendControlCommands(String bridgeName, String deviceId, String controlPath, String controlKey,
            String command, String keyName, int value) throws Exception {
        TokenResult token = tokenManager.getValidRegisteredToken(bridgeName);
        UriBuilder builder = UriBuilder.fromUri(token.getGatewayInfo().getApiRootV2())
                .path(String.format(V2_CTRL_DEVICE_CONFIG_PATH, deviceId, controlPath));
        Map<String, String> headers = getCommonV2Headers(token.getGatewayInfo().getLanguage(),
                token.getGatewayInfo().getCountry(), token.getAccessToken(), token.getUserInfo().getUserNumber());
        String payload = String.format("{\n" + "\"ctrlKey\": \"%s\",\n" + "\"command\": \"%s\",\n"
                + "\"dataKey\": \"%s\",\n" + "\"dataValue\": %d}", controlKey, command, keyName, value);
        return RestUtils.postCall(builder.build().toURL().toString(), headers, payload);
    }

    private RestResult sendBasicControlCommands(String bridgeName, String deviceId, String command, String keyName,
            int value) throws Exception {
        return sendControlCommands(bridgeName, deviceId, "control-sync", "basicCtrl", command, keyName, value);
    }

    @Override
    public void turnDevicePower(String bridgeName, String deviceId, DevicePowerState newPowerState)
            throws LGThinqApiException {
        try {
            RestResult resp = sendBasicControlCommands(bridgeName, deviceId, "Operation", "airState.operation",
                    newPowerState.commandValue());
            handleV2GenericErrorResult(resp);
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting device power", e);
        }
    }

    @Override
    public void turnCoolJetMode(String bridgeName, String deviceId, String modeOnOff) throws LGThinqApiException {
        try {
            RestResult resp = sendBasicControlCommands(bridgeName, deviceId, "Operation", "airState.wMode.jet",
                    Integer.parseInt(modeOnOff));
            handleV2GenericErrorResult(resp);
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting cool jet mode", e);
        }
    }

    @Override
    public void changeOperationMode(String bridgeName, String deviceId, int newOpMode) throws LGThinqApiException {
        try {
            RestResult resp = sendBasicControlCommands(bridgeName, deviceId, "Set", "airState.opMode", newOpMode);
            handleV2GenericErrorResult(resp);
        } catch (LGThinqApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting operation mode", e);
        }
    }

    @Override
    public void changeFanSpeed(String bridgeName, String deviceId, int newFanSpeed) throws LGThinqApiException {
        try {
            RestResult resp = sendBasicControlCommands(bridgeName, deviceId, "Set", "airState.windStrength",
                    newFanSpeed);
            handleV2GenericErrorResult(resp);
        } catch (LGThinqApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting operation mode", e);
        }
    }

    @Override
    public void changeTargetTemperature(String bridgeName, String deviceId, ACTargetTmp newTargetTemp)
            throws LGThinqApiException {
        try {
            RestResult resp = sendBasicControlCommands(bridgeName, deviceId, "Set", "airState.tempState.target",
                    newTargetTemp.commandValue());
            handleV2GenericErrorResult(resp);
        } catch (LGThinqApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting operation mode", e);
        }
    }

    /**
     * Start monitor data form specific device. This is old one, <b>works only on V1 API supported devices</b>.
     * 
     * @param deviceId Device ID
     * @return Work1 to be uses to grab data during monitoring.
     * @throws LGThinqApiException If some communication error occur.
     */
    @Override
    public String startMonitor(String bridgeName, String deviceId)
            throws LGThinqApiException, LGThinqDeviceV1OfflineException, IOException {
        throw new UnsupportedOperationException("Not supported in V2 API.");
    }

    private void handleV2GenericErrorResult(@Nullable RestResult resp) throws LGThinqApiException {
        Map<String, Object> metaResult;
        if (resp == null) {
            return;
        }
        if (resp.getStatusCode() != 200) {
            logger.error("Error returned by LG Server API. The reason is:{}", resp.getJsonResponse());
            throw new LGThinqApiException(
                    String.format("Error returned by LG Server API. The reason is:%s", resp.getJsonResponse()));
        } else {
            try {
                metaResult = objectMapper.readValue(resp.getJsonResponse(), new TypeReference<Map<String, Object>>() {
                });
                String code = (String) metaResult.get("resultCode");
                if (!ResultCodes.OK.containsResultCode("" + metaResult.get("resultCode"))) {
                    logger.error("LG API report error processing the request -> resultCode=[{}], message=[{}]", code,
                            getErrorCodeMessage(code));
                    throw new LGThinqApiException(
                            String.format("Status error executing endpoint. resultCode must be 0000, but was:%s",
                                    metaResult.get("resultCode")));
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unknown error occurred deserializing json stream", e);
            }

        }
    }

    @Override
    public void stopMonitor(String bridgeName, String deviceId, String workId)
            throws LGThinqApiException, RefreshTokenException, IOException, LGThinqDeviceV1OfflineException {
        throw new UnsupportedOperationException("Not supported in V2 API.");
    }

    @Override
    public @Nullable ACSnapshot getMonitorData(@NonNull String bridgeName, @NonNull String deviceId,
            @NonNull String workId, DeviceTypes deviceType, @NonNull ACCapability deviceCapability)
            throws LGThinqApiException, LGThinqDeviceV1MonitorExpiredException, IOException {
        throw new UnsupportedOperationException("Not supported in V2 API.");
    }

    @Override
    protected void beforeGetDataDevice(@NonNull String bridgeName, @NonNull String deviceId)
            throws LGThinqApiException {
        try {
            RestResult resp = sendControlCommands(bridgeName, deviceId, "control", "allEventEnable", "Set",
                    "airState.mon.timeout", 70);
            handleV2GenericErrorResult(resp);
        } catch (LGThinqApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting operation mode", e);
        }
    }

    @Override
    public double getInstantPowerConsumption(@NonNull String bridgeName, @NonNull String deviceId)
            throws LGThinqApiException {
        throw new UnsupportedOperationException("Not supporte for this device");
    }
}