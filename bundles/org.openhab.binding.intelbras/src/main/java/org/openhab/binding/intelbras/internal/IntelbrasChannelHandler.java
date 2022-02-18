/**
 * Gesser Tecnologia LTDA - ME.
 * 15 de fev de 2022
 */
package org.openhab.binding.intelbras.internal;

import static org.openhab.binding.intelbras.internal.IntelbrasBindingConstants.CHANNEL_SNAPSHOT;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.api.ContentResponse;
import org.openhab.core.library.types.RawType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Julio Gesser
 */
@NonNullByDefault
public class IntelbrasChannelHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(IntelbrasChannelHandler.class);

    private IntelbrasChannelConfig config = new IntelbrasChannelConfig();

    @Nullable
    private ScheduledFuture<?> snapshotRefreshTask;

    public IntelbrasChannelHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(IntelbrasChannelConfig.class);

        if (config.id < 1) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Parameter id must greater than 0!");
            return;
        }

        if (config.snapshotRefreshInterval < 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Parameter snapshotRefreshInterval must greater or equal to 0!");
            return;
        }

        try {
            if (config.snapshotRefreshInterval > 0) {
                snapshotRefreshTask = scheduler.scheduleWithFixedDelay(() -> refreshAll(), 0,
                        config.snapshotRefreshInterval, TimeUnit.SECONDS);
            } else {
                updateStatus(ThingStatus.ONLINE);
            }
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                    "Unable to update channel info. Check Bridge status for details.");
        }
    }

    @Override
    public void dispose() {
        if (snapshotRefreshTask != null) {
            snapshotRefreshTask.cancel(false);
        }
    }

    private void refreshAll() {
        refresh(getThing().getChannel(CHANNEL_SNAPSHOT).getUID());
    }

    private void refresh(ChannelUID channelUID) {
        try {
            ContentResponse response = getSnapshot();

            updateState(channelUID.getId(), new RawType(response.getContent(),
                    response.getMediaType() != null ? response.getMediaType() : RawType.DEFAULT_MIME_TYPE));

            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            logger.error("Error when connecting to DVR", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    "Unable to update station info. Check Bridge status for details.");
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            refresh(channelUID);
            return;
        }
        logger.error("Unsupported command '{}' to channel '{}'", command, channelUID);
    }

    public ContentResponse getSnapshot() throws InterruptedException, ExecutionException, TimeoutException {
        return getDVR().getSnapshot(config.id);
    }

    private @Nullable IntelbrasDVRHandler getDVR() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() != null && bridge.getHandler() instanceof IntelbrasDVRHandler) {
            return (IntelbrasDVRHandler) bridge.getHandler();
        }
        return null;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(IntelbrasChannelActions.class);
    }
}
