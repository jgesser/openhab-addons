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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lgthinq.lgservices.model.washer.WasherCapability;
import org.openhab.binding.lgthinq.lgservices.model.washer.WasherSnapshot;

/**
 * The {@link LGThinQWMApiClientService}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public interface LGThinQWMApiClientService extends LGThinQApiClientService<WasherCapability, WasherSnapshot> {
}