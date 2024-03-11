/*******************************************************************************
 * Copyright (c) 2022 Sierra Wireless and others.
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
package org.eclipse.leshan.server.profile;

import java.util.Arrays;
import java.util.List;

import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.peer.LwM2mIdentity;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultClientProfileProvider implements ClientProfileProvider {

    private final RegistrationStore registrationStore;
    private final LwM2mModelProvider modelProvider;
    private final static Logger LOG = LoggerFactory.getLogger(DefaultClientProfileProvider.class);

    public DefaultClientProfileProvider(RegistrationStore registrationStore, LwM2mModelProvider modelProvider) {
        this.registrationStore = registrationStore;
        this.modelProvider = modelProvider;
    }

    @Override
    public ClientProfile getProfile(LwM2mIdentity identity) {
        Registration registration = registrationStore.getRegistrationByIdentity(identity);

        if (registration != null) {
            LwM2mModel model = modelProvider.getObjectModel(registration);
            return new ClientProfile(registration, model);
        } else
            return null;
    }

    @Override
    public ClientProfile getProfile(LwM2mIdentity parentIdentity, String prefix) {
        ClientProfile parentProfile = getProfile(parentIdentity);
        LOG.debug("The prefix is: [{}]", prefix);
        if (prefix != null) {
            Registration parentReg = parentProfile.getRegistration();
            LOG.debug("the end point of the retrieved registration is (should be parent): [{}]",
                    parentReg.getEndpoint());
            // get child devices associated to the parent registration
            String childDeviceEpString = parentReg.getAdditionalRegistrationAttributes()
                    .get("minion_child_endpointnames");
            LOG.debug("the child endpoints attriubute has the value: [{}]", childDeviceEpString);
            // Split the input string using ","
            String[] stringArray = childDeviceEpString.split(",");

            // Convert the array to a List
            List<String> childDevicesEndpointList = Arrays.asList(stringArray);

            for (String childDeviceEndPoint : childDevicesEndpointList) {
                Registration childDeviceRegistration = registrationStore.getRegistrationByEndpoint(childDeviceEndPoint);
                if (childDeviceRegistration.getRootPath().replace("/", "").equals(prefix)) {
                    LwM2mModel model = modelProvider.getObjectModel(childDeviceRegistration);
                    LOG.debug("child device registration in client profile function:",
                            childDeviceRegistration.getEndpoint());
                    return new ClientProfile(childDeviceRegistration, model);
                }
            }

            return null;
        }
        // if there was no prefix then the parent profile is returned
        return parentProfile;
    }

}
