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

import org.eclipse.leshan.core.link.lwm2m.LwM2mLinkParser;
import org.eclipse.leshan.core.node.codec.LwM2mDecoder;
import org.eclipse.leshan.core.node.codec.LwM2mEncoder;
import org.eclipse.leshan.server.LeshanServer;
import org.eclipse.leshan.server.endpoint.LwM2mServerEndpointsProvider;
import org.eclipse.leshan.server.endpoint.ServerEndpointToolbox;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.queue.ClientAwakeTimeProvider;
import org.eclipse.leshan.server.queue.PresenceServiceImpl;
import org.eclipse.leshan.server.registration.RegistrationIdProvider;
import org.eclipse.leshan.server.registration.RegistrationServiceImpl;
import org.eclipse.leshan.server.registration.RegistrationStore;
import org.eclipse.leshan.server.request.DownlinkRequestSender;
import org.eclipse.leshan.server.security.Authorizer;
import org.eclipse.leshan.server.security.SecurityStore;
import org.eclipse.leshan.server.security.ServerSecurityInfo;

public class GatewayReadyLeshanServer extends LeshanServer {

    private final GatewayService gatewayService;

    public GatewayReadyLeshanServer(LwM2mServerEndpointsProvider endpointsProvider, RegistrationStore registrationStore,
            SecurityStore securityStore, Authorizer authorizer, LwM2mModelProvider modelProvider, LwM2mEncoder encoder,
            LwM2mDecoder decoder, boolean noQueueMode, ClientAwakeTimeProvider awakeTimeProvider,
            RegistrationIdProvider registrationIdProvider, boolean updateRegistrationOnNotification,
            LwM2mLinkParser linkParser, ServerSecurityInfo serverSecurityInfo) {
        super(endpointsProvider, registrationStore, securityStore, authorizer, modelProvider, encoder, decoder,
                noQueueMode, awakeTimeProvider, registrationIdProvider, updateRegistrationOnNotification, linkParser,
                serverSecurityInfo);

        gatewayService = new IotDevicesRegistrationHandler((RegistrationServiceImpl) getRegistrationService(),
                registrationIdProvider);
    }

    @Override
    protected ServerEndpointToolbox createServerEndpointToolbox(LwM2mDecoder decoder, LwM2mEncoder encoder,
            LwM2mLinkParser linkParser, RegistrationStore registrationStore, LwM2mModelProvider modelProvider) {
        return new ServerEndpointToolbox(decoder, encoder, linkParser,
                new GatewayReadyClientProfileProvider(registrationStore, modelProvider));
    }

    @Override
    protected DownlinkRequestSender createRequestSender(LwM2mServerEndpointsProvider endpointsProvider,
            RegistrationServiceImpl registrationService, LwM2mModelProvider modelProvider,
            PresenceServiceImpl presenceService) {
        // TODO we should also create a GatewayReady sender.
        return super.createRequestSender(endpointsProvider, registrationService, modelProvider, presenceService);
    }

    public GatewayService getGatewayService() {
        return gatewayService;
    }
}