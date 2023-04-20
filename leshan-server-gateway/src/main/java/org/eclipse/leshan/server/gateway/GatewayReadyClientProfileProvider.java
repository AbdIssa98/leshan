/*******************************************************************************
 * Copyright (c) 2023 Sierra Wireless and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.server.gateway;

import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.request.Identity;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.profile.ClientProfile;
import org.eclipse.leshan.server.profile.ClientProfileProvider;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationStore;

public class GatewayReadyClientProfileProvider implements ClientProfileProvider {

    private final RegistrationStore registrationStore;
    private final LwM2mModelProvider modelProvider;

    public GatewayReadyClientProfileProvider(RegistrationStore registrationStore, LwM2mModelProvider modelProvider) {
        this.registrationStore = registrationStore;
        this.modelProvider = modelProvider;
    }

    @Override
    public ClientProfile getProfile(Identity identity) {
        Registration registration = registrationStore.getRegistrationByIdentity(identity);
        LwM2mModel model = modelProvider.getObjectModel(registration);
        return new ClientProfile(registration, model) {
            @Override
            public String getRootPath() {
                String alternatePath = super.getRootPath();

                String prefix = registration.getApplicationData().get(GatewayAppData.IOT_DEVICE_PREFIX);
                if (prefix != null) {
                    // This is an IotDevice so use prefix as alternate/root path
                    // TODO Are we sure about this behavior when alternate/root path is used on a GateWay ?
                    return alternatePath + prefix;
                } else {
                    // This is not an IotDevice, so use default behavior
                    return alternatePath;
                }
            }
        };
    }
}