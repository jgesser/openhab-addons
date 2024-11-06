/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.lgthinq.internal.handler;

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_DASHBOARD_GRP_ID;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_EXTENDED_INFO_GRP_ID;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_ACTIVE_SAVING;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_DOOR_OPEN;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_EXPRESS_COOL_MODE;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_EXPRESS_FREEZE_MODE;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_FREEZER_TEMP_ID;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_FRESH_AIR_FILTER;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_FRIDGE_TEMP_ID;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_ICE_PLUS;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_REF_TEMP_UNIT;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_SMART_SAVING_MODE_V2;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_SMART_SAVING_SWITCH_V1;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_VACATION_MODE;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CHANNEL_RE_WATER_FILTER;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.PROP_INFO_DEVICE_ALIAS;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.PROP_INFO_MODEL_URL_INFO;
import static org.openhab.binding.lgthinq.lgservices.LGServicesConstants.CAP_RE_FRESH_AIR_FILTER_MAP;
import static org.openhab.binding.lgthinq.lgservices.LGServicesConstants.CAP_RE_WATER_FILTER;
import static org.openhab.binding.lgthinq.lgservices.LGServicesConstants.LG_API_PLATFORM_TYPE_V2;
import static org.openhab.binding.lgthinq.lgservices.LGServicesConstants.RE_CELSIUS_UNIT_VALUES;
import static org.openhab.binding.lgthinq.lgservices.LGServicesConstants.RE_DOOR_CLOSE_VALUES;
import static org.openhab.binding.lgthinq.lgservices.LGServicesConstants.RE_DOOR_OPEN_VALUES;
import static org.openhab.binding.lgthinq.lgservices.LGServicesConstants.RE_FAHRENHEIT_UNIT_VALUES;
import static org.openhab.binding.lgthinq.lgservices.LGServicesConstants.RE_TEMP_UNIT_CELSIUS;
import static org.openhab.binding.lgthinq.lgservices.LGServicesConstants.RE_TEMP_UNIT_FAHRENHEIT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lgthinq.internal.LGThinQStateDescriptionProvider;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientService;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientServiceFactory;
import org.openhab.binding.lgthinq.lgservices.LGThinQFridgeApiClientService;
import org.openhab.binding.lgthinq.lgservices.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.model.DeviceTypes;
import org.openhab.binding.lgthinq.lgservices.model.devices.fridge.FridgeCanonicalSnapshot;
import org.openhab.binding.lgthinq.lgservices.model.devices.fridge.FridgeCapability;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LGThinQFridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Nemer Daud - Initial contribution
 * @author Arne Seime - Complementary sensors
 */
@NonNullByDefault
public class LGThinQFridgeHandler extends LGThinQAbstractDeviceHandler<FridgeCapability, FridgeCanonicalSnapshot> {
    public final ChannelGroupUID channelGroupExtendedInfoUID;
    public final ChannelGroupUID channelGroupDashboardUID;
    private final ChannelUID fridgeTempChannelUID;
    private final ChannelUID freezerTempChannelUID;
    private final ChannelUID doorChannelUID;
    private final ChannelUID smartSavingModeChannelUID;
    private final ChannelUID activeSavingChannelUID;
    private final ChannelUID icePlusChannelUID;
    private final ChannelUID expressFreezeModeChannelUID;
    private final ChannelUID expressCoolModeChannelUID;
    private final ChannelUID vacationModeChannelUID;
    private final ChannelUID freshAirFilterChannelUID;
    private final ChannelUID waterFilterChannelUID;
    private final ChannelUID tempUnitUID;
    private String tempUnit = RE_TEMP_UNIT_CELSIUS;
    private final Logger logger = LoggerFactory.getLogger(LGThinQFridgeHandler.class);

    private final LGThinQFridgeApiClientService lgThinqFridgeApiClientService;

    public LGThinQFridgeHandler(Thing thing, LGThinQStateDescriptionProvider stateDescriptionProvider,
            ItemChannelLinkRegistry itemChannelLinkRegistry, HttpClientFactory httpClientFactory) {
        super(thing, stateDescriptionProvider, itemChannelLinkRegistry);
        lgThinqFridgeApiClientService = LGThinQApiClientServiceFactory.newFridgeApiClientService(lgPlatformType,
                httpClientFactory);
        channelGroupDashboardUID = new ChannelGroupUID(getThing().getUID(), CHANNEL_DASHBOARD_GRP_ID);
        channelGroupExtendedInfoUID = new ChannelGroupUID(getThing().getUID(), CHANNEL_EXTENDED_INFO_GRP_ID);
        fridgeTempChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_RE_FRIDGE_TEMP_ID);
        freezerTempChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_RE_FREEZER_TEMP_ID);
        doorChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_RE_DOOR_OPEN);
        tempUnitUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_RE_REF_TEMP_UNIT);
        icePlusChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_RE_ICE_PLUS);
        expressFreezeModeChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_RE_EXPRESS_FREEZE_MODE);
        expressCoolModeChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_RE_EXPRESS_COOL_MODE);
        vacationModeChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_RE_VACATION_MODE);
        smartSavingModeChannelUID = new ChannelUID(channelGroupDashboardUID,
                LG_API_PLATFORM_TYPE_V2.equals(lgPlatformType) ? CHANNEL_RE_SMART_SAVING_MODE_V2
                        : CHANNEL_RE_SMART_SAVING_SWITCH_V1);
        activeSavingChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_RE_ACTIVE_SAVING);
        freshAirFilterChannelUID = new ChannelUID(channelGroupExtendedInfoUID, CHANNEL_RE_FRESH_AIR_FILTER);
        waterFilterChannelUID = new ChannelUID(channelGroupExtendedInfoUID, CHANNEL_RE_WATER_FILTER);
    }

    private Unit<Temperature> getTemperatureUnit(FridgeCanonicalSnapshot shot) {
        if (!(RE_CELSIUS_UNIT_VALUES.contains(shot.getTempUnit())
                || RE_FAHRENHEIT_UNIT_VALUES.contains(shot.getTempUnit()))) {
            logger.warn(
                    "Temperature Unit not recognized (must be Celsius or Fahrenheit). Ignoring and considering Celsius as default");
            return SIUnits.CELSIUS;
        }
        return RE_CELSIUS_UNIT_VALUES.contains(shot.getTempUnit()) ? SIUnits.CELSIUS : ImperialUnits.FAHRENHEIT;
    }

    @Override
    protected void updateDeviceChannels(FridgeCanonicalSnapshot shot) {
        Unit<Temperature> unTemp = getTemperatureUnit(shot);
        if (isLinked(fridgeTempChannelUID)) {
            updateState(fridgeTempChannelUID,
                    new QuantityType<>(decodeTempValue(fridgeTempChannelUID, shot.getFridgeTemp().intValue()), unTemp));
        }
        if (isLinked(freezerTempChannelUID)) {
            updateState(freezerTempChannelUID, new QuantityType<>(
                    decodeTempValue(freezerTempChannelUID, shot.getFreezerTemp().intValue()), unTemp));
        }
        if (isLinked(doorChannelUID)) {
            updateState(doorChannelUID, parseDoorStatus(shot.getDoorStatus()));
        }
        if (isLinked(expressFreezeModeChannelUID)) {
            updateState(expressFreezeModeChannelUID, new StringType(shot.getExpressMode()));
        }
        if (isLinked(expressCoolModeChannelUID)) {
            updateState(expressCoolModeChannelUID,
                    "ON".equals(shot.getExpressCoolMode()) ? OnOffType.ON : OnOffType.OFF);
        }
        if (isLinked(vacationModeChannelUID)) {
            updateState(vacationModeChannelUID, "ON".equals(shot.getEcoFriendlyMode()) ? OnOffType.ON : OnOffType.OFF);
        }
        if (isLinked(freshAirFilterChannelUID)) {
            updateState(freshAirFilterChannelUID, new StringType(shot.getFreshAirFilterState()));
        }
        if (isLinked(waterFilterChannelUID)) {
            updateState(waterFilterChannelUID, new StringType(shot.getWaterFilterUsedMonth()));
        }

        updateState(tempUnitUID, new StringType(shot.getTempUnit()));
        if (!tempUnit.equals(shot.getTempUnit())) {
            tempUnit = shot.getTempUnit();
            try {
                // force update states after first snapshot fetched to fit changes in temperature unit
                updateChannelDynStateDescription();
            } catch (Exception ex) {
                logger.error("Unexpected error updating dynamic state description.", ex);
            }
        }
    }

    private State parseDoorStatus(String doorStatus) {
        if (RE_DOOR_CLOSE_VALUES.contains(doorStatus)) {
            return OpenClosedType.CLOSED;
        } else if (RE_DOOR_OPEN_VALUES.contains(doorStatus)) {
            return OpenClosedType.OPEN;
        } else {
            return UnDefType.UNDEF;
        }
    }

    protected Integer decodeTempValue(ChannelUID ch, Integer value) {
        FridgeCapability refCap;
        try {
            refCap = getCapabilities();
        } catch (LGThinqApiException e) {
            logger.error("Error getting capability of the device. It's mostly like a bug", e);
            return 0;
        }
        // temperature channels are little different. First we need to get the tempUnit in the first snapshot,
        Map<String, String> convertionMap = new HashMap<>();
        convertionMap = getConvertionMap(ch, refCap);
        String strValue = convertionMap.get(value.toString());
        if (strValue == null) {
            logger.error(
                    "Temperature value informed [{}] can't be converted based on the cap file. It mostly like a bug",
                    value);
            return 0;
        }
        try {
            return Integer.valueOf(strValue);
        } catch (Exception ex) {
            logger.error("Temperature value informed [{}] can't be parsed to number. It mostly like a bug", value, ex);
            return 0;
        }
    }

    protected Integer encodeTempValue(ChannelUID ch, Integer value) {
        FridgeCapability refCap;
        try {
            refCap = getCapabilities();
        } catch (LGThinqApiException e) {
            logger.error("Error getting capability of the device. It's mostly like a bug", e);
            return 0;
        }
        // temperature channels are little different. First we need to get the tempUnit in the first snapshot,
        final Map<String, String> convertionMap = new HashMap<>();
        getConvertionMap(ch, refCap);
        final Map<String, String> invertedMap = new HashMap<>();
        convertionMap.forEach((k, v) -> {
            invertedMap.put(v, k);
        });

        String strValue = invertedMap.get(value.toString());
        if (strValue == null) {
            logger.error("Temperature value informed can't be converted based on the cap file. It mostly like a bug");
            return 0;
        }
        try {
            return Integer.valueOf(strValue);
        } catch (Exception ex) {
            logger.error("Temperature value converted can't be cast to Integer. It mostly like a bug", ex);
            return 0;
        }
    }

    private Map<String, String> getConvertionMap(ChannelUID ch, FridgeCapability refCap) {
        Map<String, String> convertionMap;
        if (fridgeTempChannelUID.equals(ch)) {
            convertionMap = RE_TEMP_UNIT_FAHRENHEIT.equals(tempUnit) ? refCap.getFridgeTempFMap()
                    : refCap.getFridgeTempCMap();
        } else if (freezerTempChannelUID.equals(ch)) {
            convertionMap = RE_TEMP_UNIT_FAHRENHEIT.equals(tempUnit) ? refCap.getFreezerTempFMap()
                    : refCap.getFreezerTempCMap();
        } else {
            throw new IllegalStateException("Conversion Map Channel temperature not mapped. It's most likely a bug");
        }
        return convertionMap;
    }

    @Override
    public LGThinQApiClientService<FridgeCapability, FridgeCanonicalSnapshot> getLgThinQAPIClientService() {
        return lgThinqFridgeApiClientService;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    protected DeviceTypes getDeviceType() {
        return DeviceTypes.AIR_CONDITIONER;
    }

    @Override
    public String getDeviceAlias() {
        return emptyIfNull(getThing().getProperties().get(PROP_INFO_DEVICE_ALIAS));
    }

    @Override
    public String getDeviceUriJsonConfig() {
        return emptyIfNull(getThing().getProperties().get(PROP_INFO_MODEL_URL_INFO));
    }

    @Override
    public void onDeviceRemoved() {
    }

    @Override
    public void onDeviceDisconnected() {
    }

    @Override
    public void updateChannelDynStateDescription() throws LGThinqApiException {
        FridgeCapability cap = getCapabilities();
        manageDynChannel(icePlusChannelUID, CHANNEL_RE_ICE_PLUS, "Switch", !cap.getIcePlusMap().isEmpty());
        manageDynChannel(expressFreezeModeChannelUID, CHANNEL_RE_EXPRESS_FREEZE_MODE, "String",
                !cap.getExpressFreezeModeMap().isEmpty());
        manageDynChannel(expressCoolModeChannelUID, CHANNEL_RE_EXPRESS_COOL_MODE, "Switch",
                cap.isExpressCoolModePresent());
        manageDynChannel(vacationModeChannelUID, CHANNEL_RE_VACATION_MODE, "Switch", cap.isEcoFriendlyModePresent());

        Unit<Temperature> unTemp = getTemperatureUnit(getLastShot());
        if (SIUnits.CELSIUS.equals(unTemp)) {
            loadChannelTempStateOption(cap.getFridgeTempCMap(), fridgeTempChannelUID, unTemp);
            loadChannelTempStateOption(cap.getFreezerTempCMap(), freezerTempChannelUID, unTemp);
        } else {
            loadChannelTempStateOption(cap.getFridgeTempFMap(), fridgeTempChannelUID, unTemp);
            loadChannelTempStateOption(cap.getFreezerTempFMap(), freezerTempChannelUID, unTemp);
        }
        loadChannelStateOption(cap.getActiveSavingMap(), activeSavingChannelUID);
        loadChannelStateOption(cap.getExpressFreezeModeMap(), expressFreezeModeChannelUID);
        loadChannelStateOption(cap.getActiveSavingMap(), activeSavingChannelUID);
        loadChannelStateOption(cap.getSmartSavingMap(), smartSavingModeChannelUID);
        loadChannelStateOption(cap.getTempUnitMap(), tempUnitUID);
        loadChannelStateOption(CAP_RE_FRESH_AIR_FILTER_MAP, freshAirFilterChannelUID);
        loadChannelStateOption(CAP_RE_WATER_FILTER, waterFilterChannelUID);
    }

    private void loadChannelStateOption(Map<String, String> cap, ChannelUID channelUID) {
        final List<StateOption> faOptions = new ArrayList<>();
        cap.forEach((k, v) -> faOptions.add(new StateOption(k, v)));
        stateDescriptionProvider.setStateOptions(channelUID, faOptions);
    }

    private void loadChannelTempStateOption(Map<String, String> cap, ChannelUID channelUID, Unit<Temperature> unTemp) {
        final List<StateOption> faOptions = new ArrayList<>();
        cap.forEach((k, v) -> {
            try {
                QuantityType<Temperature> t = new QuantityType<>(Integer.valueOf(v), unTemp);
                faOptions.add(new StateOption(t.toString(), t.toString()));
            } catch (NumberFormatException ex) {
                logger.debug("Error converting invalid temperature number: {}. This can be safely ignored", v);
            }
        });
        stateDescriptionProvider.setStateOptions(channelUID, faOptions);
    }

    @Override
    protected void processCommand(AsyncCommandParams params) throws LGThinqApiException {
        FridgeCanonicalSnapshot lastShot = getLastShot();
        Map<String, Object> cmdSnap = lastShot.getRawData();
        Command command = params.command;
        String simpleChannelUID;
        simpleChannelUID = getSimpleChannelUID(params.channelUID);
        switch (simpleChannelUID) {
            case CHANNEL_RE_FREEZER_TEMP_ID:
            case CHANNEL_RE_FRIDGE_TEMP_ID: {
                int targetTemp;
                if (command instanceof DecimalType) {
                    targetTemp = ((DecimalType) command).intValue();
                } else if (command instanceof QuantityType) {
                    targetTemp = ((QuantityType<?>) command).intValue();
                } else {
                    logger.warn("Received command different of Numeric in TargetTemp Channel. Ignoring");
                    break;
                }

                if (CHANNEL_RE_FRIDGE_TEMP_ID.equals(simpleChannelUID)) {
                    targetTemp = encodeTempValue(fridgeTempChannelUID, targetTemp);
                    lgThinqFridgeApiClientService.setFridgeTemperature(getBridgeId(), getDeviceId(), getCapabilities(),
                            targetTemp, lastShot.getTempUnit(), cmdSnap);
                } else {
                    targetTemp = encodeTempValue(freezerTempChannelUID, targetTemp);
                    lgThinqFridgeApiClientService.setFreezerTemperature(getBridgeId(), getDeviceId(), getCapabilities(),
                            targetTemp, lastShot.getTempUnit(), cmdSnap);
                }
                break;
            }
            case CHANNEL_RE_ICE_PLUS: {
                if (command instanceof OnOffType) {
                    lgThinqFridgeApiClientService.setIcePlus(getBridgeId(), getDeviceId(), getCapabilities(),
                            OnOffType.ON.equals(command), cmdSnap);
                } else {
                    logger.warn("Received command different of OnOff in IcePlus Channel. It's mostly like a bug");
                }
                break;
            }
            case CHANNEL_RE_EXPRESS_FREEZE_MODE: {
                String targetExpressMode;
                if (command instanceof StringType) {
                    targetExpressMode = ((StringType) command).toString();
                } else {
                    logger.warn("Received command different of String in ExpressMode Channel. It's mostly like a bug");
                    break;
                }

                lgThinqFridgeApiClientService.setExpressMode(getBridgeId(), getDeviceId(), targetExpressMode);
                break;
            }
            case CHANNEL_RE_EXPRESS_COOL_MODE: {
                if (command instanceof OnOffType) {
                    lgThinqFridgeApiClientService.setExpressCoolMode(getBridgeId(), getDeviceId(),
                            OnOffType.ON.equals(command));
                } else {
                    logger.warn(
                            "Received command different of OnOffType in ExpressCoolMode Channel. It's mostly like a bug");
                }
                break;
            }
            case CHANNEL_RE_VACATION_MODE: {
                if (command instanceof OnOffType) {
                    lgThinqFridgeApiClientService.setEcoFriendlyMode(getBridgeId(), getDeviceId(),
                            OnOffType.ON.equals(command));
                } else {
                    logger.warn(
                            "Received command different of OnOffType in VacationMode Channel. It's most likely a bug");
                }
                break;
            }
            default: {
                logger.warn("Command {} to the channel {} not supported. Ignored.", command, params.channelUID);
            }
        }
    }
}
