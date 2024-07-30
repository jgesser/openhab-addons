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

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lgthinq.internal.LGThinQStateDescriptionProvider;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqException;
import org.openhab.binding.lgthinq.lgservices.LGThinQACApiClientService;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientService;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientServiceFactory;
import org.openhab.binding.lgthinq.lgservices.model.DevicePowerState;
import org.openhab.binding.lgthinq.lgservices.model.DeviceTypes;
import org.openhab.binding.lgthinq.lgservices.model.LGDevice;
import org.openhab.binding.lgthinq.lgservices.model.devices.ac.ACCanonicalSnapshot;
import org.openhab.binding.lgthinq.lgservices.model.devices.ac.ACCapability;
import org.openhab.binding.lgthinq.lgservices.model.devices.ac.ACTargetTmp;
import org.openhab.binding.lgthinq.lgservices.model.devices.ac.ExtendedDeviceInfo;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.types.Command;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link LGThinQAirConditionerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class LGThinQAirConditionerHandler extends LGThinQAbstractDeviceHandler<ACCapability, ACCanonicalSnapshot> {

    public final ChannelGroupUID channelGroupExtendedInfoUID;
    public final ChannelGroupUID channelGroupDashboardUID;
    private final ChannelUID powerChannelUID;
    private final ChannelUID opModeChannelUID;
    private final ChannelUID hpAirWaterSwitchChannelUID;
    private final ChannelUID fanSpeedChannelUID;
    private final ChannelUID targetTempChannelUID;
    private final ChannelUID currTempChannelUID;
    private final ChannelUID minTempChannelUID;
    private final ChannelUID maxTempChannelUID;
    private final ChannelUID jetModeChannelUID;
    private final ChannelUID airCleanChannelUID;
    private final ChannelUID autoDryChannelUID;
    private final ChannelUID stepUpDownChannelUID;
    private final ChannelUID stepLeftRightChannelUID;
    private final ChannelUID energySavingChannelUID;
    private final ChannelUID extendedInfoCollectorChannelUID;
    private final ChannelUID currentPowerEnergyChannelUID;
    private final ChannelUID remainingFilterChannelUID;

    private double minTempConstraint = 16, maxTempConstraint = 30;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(LGThinQAirConditionerHandler.class);
    @NonNullByDefault
    private final LGThinQACApiClientService lgThinqACApiClientService;

    public LGThinQAirConditionerHandler(Thing thing, LGThinQStateDescriptionProvider stateDescriptionProvider,
            ItemChannelLinkRegistry itemChannelLinkRegistry, HttpClientFactory httpClientFactory) {
        super(thing, stateDescriptionProvider, itemChannelLinkRegistry);
        lgThinqACApiClientService = LGThinQApiClientServiceFactory.newACApiClientService(lgPlatformType,
                httpClientFactory);
        channelGroupDashboardUID = new ChannelGroupUID(getThing().getUID(), CHANNEL_DASHBOARD_GRP_ID);
        channelGroupExtendedInfoUID = new ChannelGroupUID(getThing().getUID(), CHANNEL_EXTENDED_INFO_GRP_ID);

        opModeChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_MOD_OP_ID);
        hpAirWaterSwitchChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_AIR_WATER_SWITCH_ID);
        targetTempChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_TARGET_TEMP_ID);
        minTempChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_MIN_TEMP_ID);
        maxTempChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_MAX_TEMP_ID);
        currTempChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_CURRENT_TEMP_ID);
        fanSpeedChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_FAN_SPEED_ID);
        jetModeChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_COOL_JET_ID);
        airCleanChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_AIR_CLEAN_ID);
        autoDryChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_AUTO_DRY_ID);
        energySavingChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_ENERGY_SAVING_ID);
        stepUpDownChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_STEP_UP_DOWN_ID);
        stepLeftRightChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_STEP_LEFT_RIGHT_ID);
        powerChannelUID = new ChannelUID(channelGroupDashboardUID, CHANNEL_POWER_ID);
        extendedInfoCollectorChannelUID = new ChannelUID(channelGroupExtendedInfoUID,
                CHANNEL_EXTENDED_INFO_COLLECTOR_ID);
        currentPowerEnergyChannelUID = new ChannelUID(channelGroupExtendedInfoUID, CHANNEL_CURRENT_POWER_ID);
        remainingFilterChannelUID = new ChannelUID(channelGroupExtendedInfoUID, CHANNEL_REMAINING_FILTER_ID);
    }

    @Override
    public void initialize() {
        super.initialize();
        try {
            ACCapability cap = getCapabilities();
            if (!isExtraInfoCollectorSupported()) {
                ThingBuilder builder = editThing()
                        .withoutChannels(this.getThing().getChannelsOfGroup(channelGroupExtendedInfoUID.getId()));
                updateThing(builder.build());
            } else if (!cap.isEnergyMonitorAvailable()) {
                ThingBuilder builder = editThing().withoutChannel(currentPowerEnergyChannelUID);
                updateThing(builder.build());
            } else if (!cap.isFilterMonitorAvailable()) {
                ThingBuilder builder = editThing().withoutChannel(remainingFilterChannelUID);
                updateThing(builder.build());
            }
        } catch (LGThinqApiException e) {
            logger.warn("Error getting capability of the device: {}", getDeviceId());
        }
    }

    @Override
    protected void updateDeviceChannels(ACCanonicalSnapshot shot) {
        updateState(powerChannelUID,
                DevicePowerState.DV_POWER_ON.equals(shot.getPowerStatus()) ? OnOffType.ON : OnOffType.OFF);
        updateState(opModeChannelUID, new DecimalType(shot.getOperationMode()));
        if (DeviceTypes.HEAT_PUMP.equals(getDeviceType())) {
            updateState(hpAirWaterSwitchChannelUID, new DecimalType(shot.getHpAirWaterTempSwitch()));
        }
        updateState(fanSpeedChannelUID, new DecimalType(shot.getAirWindStrength()));
        updateState(currTempChannelUID, new DecimalType(shot.getCurrentTemperature()));
        updateState(targetTempChannelUID, new DecimalType(shot.getTargetTemperature()));
        try {
            ACCapability acCap = getCapabilities();
            if (getThing().getChannel(stepUpDownChannelUID) != null) {
                updateState(stepUpDownChannelUID, new DecimalType((int) shot.getStepUpDownMode()));
            }
            if (getThing().getChannel(stepLeftRightChannelUID) != null) {
                updateState(stepLeftRightChannelUID, new DecimalType((int) shot.getStepLeftRightMode()));
            }
            if (getThing().getChannel(jetModeChannelUID) != null) {
                Double commandCoolJetOn = Double.valueOf(acCap.getCoolJetModeCommandOn());
                updateState(jetModeChannelUID,
                        commandCoolJetOn.equals(shot.getCoolJetMode()) ? OnOffType.ON : OnOffType.OFF);
            }
            if (getThing().getChannel(airCleanChannelUID) != null) {
                Double commandAirCleanOn = Double.valueOf(acCap.getAirCleanModeCommandOn());
                updateState(airCleanChannelUID,
                        commandAirCleanOn.equals(shot.getAirCleanMode()) ? OnOffType.ON : OnOffType.OFF);
            }
            if (getThing().getChannel(energySavingChannelUID) != null) {
                Double energySavingOn = Double.valueOf(acCap.getEnergySavingModeCommandOn());
                updateState(energySavingChannelUID,
                        energySavingOn.equals(shot.getEnergySavingMode()) ? OnOffType.ON : OnOffType.OFF);
            }
            if (getThing().getChannel(autoDryChannelUID) != null) {
                Double autoDryOn = Double.valueOf(acCap.getCoolJetModeCommandOn());
                updateState(autoDryChannelUID, autoDryOn.equals(shot.getAutoDryMode()) ? OnOffType.ON : OnOffType.OFF);
            }
            if (DeviceTypes.HEAT_PUMP.equals(getDeviceType())) {
                // HP has different combination of min and max target temperature depending on the switch mode and
                // operation
                // mode
                String opModeValue = acCap.getOpMode().get(getLastShot().getOperationMode().toString());
                if (CAP_HP_AIR_SWITCH.equals(shot.getHpAirWaterTempSwitch())) {
                    if (opModeValue.equals(CAP_ACHP_OP_MODE_COOL_KEY)) {
                        minTempConstraint = shot.getHpAirTempCoolMin();
                        maxTempConstraint = shot.getHpAirTempCoolMax();
                    } else if (opModeValue.equals(CAP_ACHP_OP_MODE_HEAT_KEY)) {
                        minTempConstraint = shot.getHpAirTempHeatMin();
                        maxTempConstraint = shot.getHpAirTempHeatMax();
                    }
                } else if (CAP_HP_WATER_SWITCH.equals(shot.getHpAirWaterTempSwitch())) {
                    if (opModeValue.equals(CAP_ACHP_OP_MODE_COOL_KEY)) {
                        minTempConstraint = shot.getHpWaterTempCoolMin();
                        maxTempConstraint = shot.getHpWaterTempCoolMax();
                    } else if (opModeValue.equals(CAP_ACHP_OP_MODE_HEAT_KEY)) {
                        minTempConstraint = shot.getHpWaterTempHeatMin();
                        maxTempConstraint = shot.getHpWaterTempHeatMax();
                    }
                } else {
                    logger.error("Invalid value received by HP snapshot fo the air/water switch property: {}",
                            shot.getHpAirWaterTempSwitch());
                }
                updateState(minTempChannelUID, new DecimalType(BigDecimal.valueOf(minTempConstraint)));
                updateState(maxTempChannelUID, new DecimalType(BigDecimal.valueOf(maxTempConstraint)));
            }

        } catch (LGThinqApiException e) {
            logger.error("Unexpected Error getting ACCapability Capabilities", e);
        } catch (NumberFormatException e) {
            logger.warn("command value for capability is not numeric.", e);
        }
    }

    @Override
    public void updateChannelDynStateDescription() throws LGThinqApiException {
        ACCapability acCap = getCapabilities();
        manageDynChannel(jetModeChannelUID, CHANNEL_COOL_JET_ID, "Switch", acCap.isJetModeAvailable());
        manageDynChannel(autoDryChannelUID, CHANNEL_AUTO_DRY_ID, "Switch", acCap.isAutoDryModeAvailable());
        manageDynChannel(airCleanChannelUID, CHANNEL_AIR_CLEAN_ID, "Switch", acCap.isAirCleanAvailable());
        manageDynChannel(energySavingChannelUID, CHANNEL_ENERGY_SAVING_ID, "Switch", acCap.isEnergySavingAvailable());
        manageDynChannel(stepUpDownChannelUID, CHANNEL_STEP_UP_DOWN_ID, "Number", acCap.isStepUpDownAvailable());
        manageDynChannel(stepLeftRightChannelUID, CHANNEL_STEP_LEFT_RIGHT_ID, "Number",
                acCap.isStepLeftRightAvailable());
        manageDynChannel(stepLeftRightChannelUID, CHANNEL_STEP_LEFT_RIGHT_ID, "Number",
                acCap.isStepLeftRightAvailable());

        if (!acCap.getFanSpeed().isEmpty()) {
            List<StateOption> options = new ArrayList<>();
            acCap.getFanSpeed()
                    .forEach((k, v) -> options.add(new StateOption(k, emptyIfNull(CAP_AC_FAN_SPEED.get(v)))));
            stateDescriptionProvider.setStateOptions(fanSpeedChannelUID, options);
        }
        if (!acCap.getOpMode().isEmpty()) {
            List<StateOption> options = new ArrayList<>();
            acCap.getOpMode().forEach((k, v) -> options.add(new StateOption(k, emptyIfNull(CAP_AC_OP_MODE.get(v)))));
            stateDescriptionProvider.setStateOptions(opModeChannelUID, options);
        }
        if (!acCap.getStepLeftRight().isEmpty()) {
            List<StateOption> options = new ArrayList<>();
            acCap.getStepLeftRight().forEach(
                    (k, v) -> options.add(new StateOption(k, emptyIfNull(CAP_AC_STEP_LEFT_RIGHT_MODE.get(v)))));
            stateDescriptionProvider.setStateOptions(stepLeftRightChannelUID, options);
        }
        if (!acCap.getStepUpDown().isEmpty()) {
            List<StateOption> options = new ArrayList<>();
            acCap.getStepUpDown()
                    .forEach((k, v) -> options.add(new StateOption(k, emptyIfNull(CAP_AC_STEP_UP_DOWN_MODE.get(v)))));
            stateDescriptionProvider.setStateOptions(stepUpDownChannelUID, options);
        }
    }

    @Override
    public LGThinQApiClientService<ACCapability, ACCanonicalSnapshot> getLgThinQAPIClientService() {
        return lgThinqACApiClientService;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    protected DeviceTypes getDeviceType() {
        if (THING_TYPE_HEAT_PUMP.equals(getThing().getThingTypeUID())) {
            return DeviceTypes.HEAT_PUMP;
        } else if (THING_TYPE_AIR_CONDITIONER.equals(getThing().getThingTypeUID())) {
            return DeviceTypes.AIR_CONDITIONER;
        } else {
            throw new IllegalArgumentException(
                    "DeviceTypeUuid [" + getThing().getThingTypeUID() + "] not expected for AirConditioner/HeatPump");
        }
    }

    @Override
    public void onDeviceAdded(LGDevice device) {
        // TODO - handle it. Think if it's needed
    }

    @Override
    public String getDeviceAlias() {
        return emptyIfNull(getThing().getProperties().get(DEVICE_ALIAS));
    }

    @Override
    public String getDeviceUriJsonConfig() {
        return emptyIfNull(getThing().getProperties().get(MODEL_URL_INFO));
    }

    @Override
    public void onDeviceRemoved() {
        // TODO - HANDLE IT, Think if it's needed
    }

    @Override
    public void onDeviceDisconnected() {
        // TODO - HANDLE IT, Think if it's needed
    }

    protected void resetExtraInfoChannels() {
        updateState(currentPowerEnergyChannelUID, UnDefType.UNDEF);
        if (!isExtraInfoCollectorEnabled()) { // if collector is enabled we can keep the current value
            updateState(remainingFilterChannelUID, UnDefType.UNDEF);
        }
    }

    protected void processCommand(AsyncCommandParams params) throws LGThinqApiException {
        Command command = params.command;
        switch (getSimpleChannelUID(params.channelUID)) {
            case CHANNEL_MOD_OP_ID: {
                if (params.command instanceof DecimalType) {
                    lgThinqACApiClientService.changeOperationMode(getBridgeId(), getDeviceId(),
                            ((DecimalType) command).intValue());
                } else {
                    logger.warn("Received command different of Numeric in Mod Operation. Ignoring");
                }
                break;
            }
            case CHANNEL_FAN_SPEED_ID: {
                if (command instanceof DecimalType) {
                    lgThinqACApiClientService.changeFanSpeed(getBridgeId(), getDeviceId(),
                            ((DecimalType) command).intValue());
                } else {
                    logger.warn("Received command different of Numeric in FanSpeed Channel. Ignoring");
                }
                break;
            }
            case CHANNEL_STEP_UP_DOWN_ID: {
                if (command instanceof DecimalType) {
                    lgThinqACApiClientService.changeStepUpDown(getBridgeId(), getDeviceId(), getLastShot(),
                            ((DecimalType) command).intValue());
                } else {
                    logger.warn("Received command different of Numeric in Step Up/Down Channel. Ignoring");
                }
                break;
            }
            case CHANNEL_STEP_LEFT_RIGHT_ID: {
                if (command instanceof DecimalType) {
                    lgThinqACApiClientService.changeStepLeftRight(getBridgeId(), getDeviceId(), getLastShot(),
                            ((DecimalType) command).intValue());
                } else {
                    logger.warn("Received command different of Numeric in Step Left/Right Channel. Ignoring");
                }
                break;
            }
            case CHANNEL_POWER_ID: {
                if (command instanceof OnOffType) {
                    lgThinqACApiClientService.turnDevicePower(getBridgeId(), getDeviceId(),
                            command == OnOffType.ON ? DevicePowerState.DV_POWER_ON : DevicePowerState.DV_POWER_OFF);
                } else {
                    logger.warn("Received command different of OnOffType in Power Channel. Ignoring");
                }
                break;
            }
            case CHANNEL_COOL_JET_ID: {
                if (command instanceof OnOffType) {
                    lgThinqACApiClientService.turnCoolJetMode(getBridgeId(), getDeviceId(),
                            command == OnOffType.ON ? getCapabilities().getCoolJetModeCommandOn()
                                    : getCapabilities().getCoolJetModeCommandOff());
                } else {
                    logger.warn("Received command different of OnOffType in CoolJet Mode Channel. Ignoring");
                }
                break;
            }
            case CHANNEL_AIR_CLEAN_ID: {
                if (command instanceof OnOffType) {
                    lgThinqACApiClientService.turnAirCleanMode(getBridgeId(), getDeviceId(),
                            command == OnOffType.ON ? getCapabilities().getAirCleanModeCommandOn()
                                    : getCapabilities().getAirCleanModeCommandOff());
                } else {
                    logger.warn("Received command different of OnOffType in AirClean Mode Channel. Ignoring");
                }
                break;
            }
            case CHANNEL_AUTO_DRY_ID: {
                if (command instanceof OnOffType) {
                    lgThinqACApiClientService.turnAutoDryMode(getBridgeId(), getDeviceId(),
                            command == OnOffType.ON ? getCapabilities().getAutoDryModeCommandOn()
                                    : getCapabilities().getAutoDryModeCommandOff());
                } else {
                    logger.warn("Received command different of OnOffType in AutoDry Mode Channel. Ignoring");
                }
                break;
            }
            case CHANNEL_ENERGY_SAVING_ID: {
                if (command instanceof OnOffType) {
                    lgThinqACApiClientService.turnEnergySavingMode(getBridgeId(), getDeviceId(),
                            command == OnOffType.ON ? getCapabilities().getEnergySavingModeCommandOn()
                                    : getCapabilities().getEnergySavingModeCommandOff());
                } else {
                    logger.warn("Received command different of OnOffType in EvergySaving Mode Channel. Ignoring");
                }
                break;
            }
            case CHANNEL_TARGET_TEMP_ID: {
                double targetTemp;
                if (command instanceof DecimalType) {
                    targetTemp = ((DecimalType) command).doubleValue();
                } else if (command instanceof QuantityType) {
                    targetTemp = ((QuantityType<?>) command).doubleValue();
                } else {
                    logger.warn("Received command different of Numeric in TargetTemp Channel. Ignoring");
                    break;
                }
                // analise temperature constraints
                if (targetTemp > maxTempConstraint || targetTemp < minTempConstraint) {
                    // values out of range
                    logger.error("Target Temperature: {} is out of range: {} - {}. Ignoring command", targetTemp,
                            minTempConstraint, maxTempConstraint);
                    break;
                }
                lgThinqACApiClientService.changeTargetTemperature(getBridgeId(), getDeviceId(),
                        ACTargetTmp.statusOf(targetTemp));
                break;
            }
            case CHANNEL_EXTENDED_INFO_COLLECTOR_ID: {
                break;
            }
            default: {
                logger.error("Command {} to the channel {} not supported. Ignored.", command, params.channelUID);
            }
        }
    }
    // =========== Energy Colletor Implementation =============

    @Override
    protected boolean isExtraInfoCollectorSupported() {
        try {
            return getCapabilities().isEnergyMonitorAvailable() || getCapabilities().isFilterMonitorAvailable();
        } catch (LGThinqApiException e) {
            logger.warn("Can't get capabilities of the device: {}", getDeviceId());
        }
        return false;
    }

    @Override
    protected boolean isExtraInfoCollectorEnabled() {
        return OnOffType.ON.toString().equals(getItemLinkedValue(extendedInfoCollectorChannelUID));
    }

    @Override
    protected Map<String, Object> collectExtraInfoState() throws LGThinqException {
        ExtendedDeviceInfo info = lgThinqACApiClientService.getExtendedDeviceInfo(getBridgeId(), getDeviceId());
        return mapper.convertValue(info, new TypeReference<>() {
        });
    }

    @Override
    protected void updateExtraInfoStateChannels(Map<String, Object> energyStateAttributes) throws LGThinqException {
        logger.debug("Calling updateExtraInfoStateChannels for device: {}", getDeviceId());
        String instantPowerConsumption = (String) energyStateAttributes.get(EXTENDED_ATTR_INSTANT_POWER);
        String filterUsed = (String) energyStateAttributes.get(EXTENDED_ATTR_FILTER_USED_TIME);
        String filterTimelife = (String) energyStateAttributes.get(EXTENDED_ATTR_FILTER_MAX_TIME_TO_USE);
        if (instantPowerConsumption == null) {
            updateState(currentPowerEnergyChannelUID, UnDefType.NULL);
        } else if (NumberUtils.isCreatable(instantPowerConsumption)) {
            double ip = Double.parseDouble(instantPowerConsumption);
            updateState(currentPowerEnergyChannelUID, new QuantityType<>(ip, Units.WATT));
        } else {
            updateState(currentPowerEnergyChannelUID, UnDefType.UNDEF);
        }

        if (filterTimelife == null || filterUsed == null) {
            updateState(remainingFilterChannelUID, UnDefType.NULL);
        } else if (NumberUtils.isCreatable(filterTimelife) && NumberUtils.isCreatable(filterUsed)) {
            double used = Double.parseDouble(filterUsed);
            double max = Double.parseDouble(filterTimelife);
            double perc = (1 - ((double) used / max)) * 100;
            updateState(remainingFilterChannelUID, new QuantityType<>(perc, Units.PERCENT));
        } else {
            updateState(remainingFilterChannelUID, UnDefType.UNDEF);
        }
    }
}
