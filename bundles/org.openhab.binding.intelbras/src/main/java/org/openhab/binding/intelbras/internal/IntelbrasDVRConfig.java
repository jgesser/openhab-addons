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
package org.openhab.binding.intelbras.internal;

/**
 * The {@link IntelbrasDVRConfig} class contains fields mapping thing configuration parameters.
 *
 * @author Julio Gesser - Initial contribution
 */
public class IntelbrasDVRConfig {

    public String baseURL = "";
    public String username = "";
    public String password = "";
    public IntelbrasAuthMode authMode = IntelbrasAuthMode.DIGEST;
    public Integer refreshInterval = 60;
}
