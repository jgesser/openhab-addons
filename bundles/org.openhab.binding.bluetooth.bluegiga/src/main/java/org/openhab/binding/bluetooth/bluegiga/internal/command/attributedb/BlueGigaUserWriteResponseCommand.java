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
package org.openhab.binding.bluetooth.bluegiga.internal.command.attributedb;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.bluetooth.bluegiga.internal.BlueGigaDeviceCommand;

/**
 * Class to implement the BlueGiga command <b>userWriteResponse</b>.
 * <p>
 * This command is used by the GATT server to acknowledge to the remote device that the
 * attribute's value was written. This feature again allows the user application to
 * acknowledged the attribute write operations instead of the Smart stack doing it
 * automatically. Bluetooth The command should be used when an event is received where the
 * reason why value has changed Value corresponds to
 * attributes_attribute_change_reason_write_request_user. This response must be sent
 * within 30 seconds or otherwise a timeout will occur.
 * <p>
 * This class provides methods for processing BlueGiga API commands.
 * <p>
 * Note that this code is autogenerated. Manual changes may be overwritten.
 *
 * @author Chris Jackson - Initial contribution of Java code generator
 */
@NonNullByDefault
public class BlueGigaUserWriteResponseCommand extends BlueGigaDeviceCommand {
    public static final int COMMAND_CLASS = 0x02;
    public static final int COMMAND_METHOD = 0x04;

    /**
     * 0: User Read Request is responded with data. In case of an error an application specific error
     * code can be sent.
     * <p>
     * BlueGiga API type is <i>uint8</i> - Java type is {@link int}
     */
    private int attError;

    /**
     * 0: User Read Request is responded with data. In case of an error an application specific error
     * code can be sent.
     *
     * @param attError the attError to set as {@link int}
     */
    public void setAttError(int attError) {
        this.attError = attError;
    }

    @Override
    public int[] serialize() {
        // Serialize the header
        serializeHeader(COMMAND_CLASS, COMMAND_METHOD);

        // Serialize the fields
        serializeUInt8(connection);
        serializeUInt8(attError);

        return getPayload();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("BlueGigaUserWriteResponseCommand [connection=");
        builder.append(connection);
        builder.append(", attError=");
        builder.append(attError);
        builder.append(']');
        return builder.toString();
    }
}
