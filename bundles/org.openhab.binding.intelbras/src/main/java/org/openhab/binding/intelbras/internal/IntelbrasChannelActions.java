/**
 * Gesser Tecnologia LTDA - ME.
 * 16 de fev de 2022
 */
package org.openhab.binding.intelbras.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;

/**
 * @author Julio Gesser
 */
@ThingActionsScope(name = "intelbras")
@NonNullByDefault
public class IntelbrasChannelActions implements ThingActions {

    @Nullable
    private IntelbrasChannelHandler handler;

    @Override
    public void setThingHandler(ThingHandler handler) {
        this.handler = (IntelbrasChannelHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "snapshot", description = "Takes a snapshot of the channel and returns the image content as a byte array")
    public Response getSnapshot() throws Exception {
        return new Response(handler.getSnapshot());
    }

    public static String getSnapshotAsBase64(ThingActions actions) throws Exception {
        if (actions instanceof IntelbrasChannelActions) {
            return ((IntelbrasChannelActions) actions).getSnapshot().getContentAsBase64();
        } else {
            throw new IllegalArgumentException("Instance is not an IntelbrasChannelActions class.");
        }
    }
}
