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

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.V1_CONTROL_OP;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.api.RestResult;
import org.openhab.binding.lgthinq.internal.api.RestUtils;
import org.openhab.binding.lgthinq.internal.api.TokenResult;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqDeviceV1OfflineException;
import org.openhab.binding.lgthinq.lgservices.model.Capability;
import org.openhab.binding.lgthinq.lgservices.model.ResultCodes;
import org.openhab.binding.lgthinq.lgservices.model.Snapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * The {@link LGThinQAbstractApiV1ClientService}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public abstract class LGThinQAbstractApiV1ClientService<C extends Capability, S extends Snapshot>
        extends LGThinQAbstractApiClientService<C, S> {
    private static final Logger logger = LoggerFactory.getLogger(LGThinQAbstractApiV1ClientService.class);

    protected LGThinQAbstractApiV1ClientService(Class<C> capabilityClass, Class<S> snapshotClass) {
        super(capabilityClass, snapshotClass);
    }

    @Override
    protected RestResult sendControlCommands(String bridgeName, String deviceId, String controlPath, String controlKey,
            String command, String keyName, String value) throws Exception {
        TokenResult token = tokenManager.getValidRegisteredToken(bridgeName);
        UriBuilder builder = UriBuilder.fromUri(token.getGatewayInfo().getApiRootV1()).path(V1_CONTROL_OP);
        Map<String, String> headers = getCommonHeaders(token.getGatewayInfo().getLanguage(),
                token.getGatewayInfo().getCountry(), token.getAccessToken(), token.getUserInfo().getUserNumber());

        String payload = String.format(
                "{\n" + "   \"lgedmRoot\":{\n" + "      \"cmd\": \"%s\"," + "      \"cmdOpt\": \"%s\","
                        + "      \"value\": {\"%s\": \"%s\"}," + "      \"deviceId\": \"%s\","
                        + "      \"workId\": \"%s\"," + "      \"data\": \"\"" + "   }\n" + "}",
                controlKey, command, keyName, value, deviceId, UUID.randomUUID());
        return RestUtils.postCall(builder.build().toURL().toString(), headers, payload);
    }

    @Override
    protected Map<String, Object> handleGenericErrorResult(@Nullable RestResult resp) throws LGThinqApiException {
        Map<String, Object> metaResult;
        Map<String, Object> envelope = Collections.emptyMap();
        if (resp == null) {
            return envelope;
        }
        if (resp.getStatusCode() != 200) {
            logger.error("Error returned by LG Server API. The reason is:{}", resp.getJsonResponse());
            throw new LGThinqApiException(
                    String.format("Error returned by LG Server API. The reason is:%s", resp.getJsonResponse()));
        } else {
            try {
                metaResult = objectMapper.readValue(resp.getJsonResponse(), new TypeReference<>() {
                });
                envelope = (Map<String, Object>) metaResult.get("lgedmRoot");
                String code = "" + envelope.get("returnCd");
                if (envelope.isEmpty()) {
                    throw new LGThinqApiException(String.format(
                            "Unexpected json body returned (without root node lgedmRoot): %s", resp.getJsonResponse()));
                } else if (!ResultCodes.OK.containsResultCode(code)) {
                    if (ResultCodes.DEVICE_NOT_RESPONSE.containsResultCode("" + envelope.get("returnCd"))
                            || "D".equals(envelope.get("deviceState"))) {
                        logger.debug("LG API report error processing the request -> resultCode=[{}], message=[{}]",
                                code, getErrorCodeMessage(code));
                        // Disconnected Device
                        throw new LGThinqDeviceV1OfflineException("Device is offline. No data available");
                    }
                    logger.error("LG API report error processing the request -> resultCode=[{}], message=[{}]", code,
                            getErrorCodeMessage(code));
                    throw new LGThinqApiException(
                            String.format("Status error executing endpoint. resultCode must be 0000, but was:%s",
                                    metaResult.get("returnCd")));
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unknown error occurred deserializing json stream", e);
            }
        }
        return envelope;
    }
}
