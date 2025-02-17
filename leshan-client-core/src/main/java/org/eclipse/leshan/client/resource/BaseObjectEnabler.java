/*******************************************************************************
 * Copyright (c) 2015 Sierra Wireless and others.
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
 *     Achim Kraus (Bosch Software Innovations GmbH) - deny delete of resource
 *     Achim Kraus (Bosch Software Innovations GmbH) - use ServerIdentity to
 *                                                     protect the security object
 *     Achim Kraus (Bosch Software Innovations GmbH) - add resource checks for
 *                                                     REPLACE/UPDAT implementation
 *     Michał Wadowski (Orange)                      - Improved compliance with rfc6690.
 *******************************************************************************/
package org.eclipse.leshan.client.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.leshan.client.LwM2mClient;
import org.eclipse.leshan.client.resource.listener.ObjectListener;
import org.eclipse.leshan.client.servers.LwM2mServer;
import org.eclipse.leshan.client.util.LinkFormatHelper;
import org.eclipse.leshan.core.LwM2mId;
import org.eclipse.leshan.core.link.lwm2m.LwM2mLink;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.BootstrapDeleteRequest;
import org.eclipse.leshan.core.request.BootstrapDiscoverRequest;
import org.eclipse.leshan.core.request.BootstrapReadRequest;
import org.eclipse.leshan.core.request.BootstrapWriteRequest;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.core.request.CreateRequest;
import org.eclipse.leshan.core.request.DeleteRequest;
import org.eclipse.leshan.core.request.DiscoverRequest;
import org.eclipse.leshan.core.request.DownlinkRequest;
import org.eclipse.leshan.core.request.ExecuteRequest;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.WriteAttributesRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.response.BootstrapDeleteResponse;
import org.eclipse.leshan.core.response.BootstrapDiscoverResponse;
import org.eclipse.leshan.core.response.BootstrapReadResponse;
import org.eclipse.leshan.core.response.BootstrapWriteResponse;
import org.eclipse.leshan.core.response.CreateResponse;
import org.eclipse.leshan.core.response.DeleteResponse;
import org.eclipse.leshan.core.response.DiscoverResponse;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteAttributesResponse;
import org.eclipse.leshan.core.response.WriteResponse;

/**
 * A abstract implementation of {@link LwM2mObjectEnabler}. It could be use as base for any {@link LwM2mObjectEnabler}
 * implementation.
 */
public abstract class BaseObjectEnabler implements LwM2mObjectEnabler {

    protected final int id;
    protected final TransactionalObjectListener transactionalListener;
    protected final ObjectModel objectModel;

    private LwM2mClient lwm2mClient;
    private LinkFormatHelper linkFormatHelper;

    public BaseObjectEnabler(int id, ObjectModel objectModel) {
        this.id = id;
        this.objectModel = objectModel;
        this.transactionalListener = createTransactionListener();
    }

    protected TransactionalObjectListener createTransactionListener() {
        return new TransactionalObjectListener(this);
    }

    @Override
    public synchronized int getId() {
        return id;
    }

    @Override
    public synchronized ObjectModel getObjectModel() {
        return objectModel;
    }

    @Override
    public List<Integer> getAvailableResourceIds(int instanceId) {
        // By default we consider that all resources defined in the model are supported
        ArrayList<Integer> resourceIds = new ArrayList<>(objectModel.resources.keySet());
        Collections.sort(resourceIds);
        return resourceIds;
    }

    @Override
    public synchronized CreateResponse create(LwM2mServer server, CreateRequest request) {
        try {
            beginTransaction(LwM2mPath.OBJECT_DEPTH);

            if (!server.isSystem()) {
                if (id == LwM2mId.SECURITY || id == LwM2mId.OSCORE) {
                    return CreateResponse.notFound();
                }
            } else if (server.isLwm2mBootstrapServer()) {
                // create is not supported for bootstrap
                CreateResponse.methodNotAllowed();
            }

            if (request.unknownObjectInstanceId()) {
                if (missingMandatoryResource(request.getResources())) {
                    return CreateResponse.badRequest("mandatory writable resources missing!");
                }
            } else {
                for (LwM2mObjectInstance instance : request.getObjectInstances()) {
                    if (missingMandatoryResource(instance.getResources().values())) {
                        return CreateResponse.badRequest("mandatory writable resources missing!");
                    }
                }
            }

            return doCreate(server, request);

        } finally {
            endTransaction(LwM2mPath.OBJECT_DEPTH);
        }
    }

    protected CreateResponse doCreate(LwM2mServer server, CreateRequest request) {
        // This should be a not implemented error, but this is not defined in the spec.
        return CreateResponse.internalServerError("not implemented");
    }

    @Override
    public synchronized ReadResponse read(LwM2mServer server, ReadRequest request) {
        LwM2mPath path = request.getPath();

        // read is not supported for bootstrap
        if (server.isLwm2mBootstrapServer()) {
            return ReadResponse.methodNotAllowed();
        }

        if (!server.isSystem()) {
            // read the security or oscore object is forbidden
            if (id == LwM2mId.SECURITY || id == LwM2mId.OSCORE) {
                return ReadResponse.notFound();
            }

            // check if the resource is readable.
            if (path.isResource() || path.isResourceInstance()) {
                ResourceModel resourceModel = objectModel.resources.get(path.getResourceId());
                if (resourceModel == null) {
                    return ReadResponse.notFound();
                } else if (!resourceModel.operations.isReadable()) {
                    return ReadResponse.methodNotAllowed();
                } else if (path.isResourceInstance() && !resourceModel.multiple) {
                    return ReadResponse.badRequest("invalid path : resource is not multiple");
                }
            }
        }

        return doRead(server, request);

        // TODO we could do a validation of response.getContent by comparing with resourceSpec information
    }

    protected ReadResponse doRead(LwM2mServer server, ReadRequest request) {
        // This should be a not implemented error, but this is not defined in the spec.
        return ReadResponse.internalServerError("not implemented");
    }

    @Override
    public BootstrapReadResponse read(LwM2mServer server, BootstrapReadRequest request) {
        // read is not supported for bootstrap
        if (server.isLwm2mServer()) {
            return BootstrapReadResponse.methodNotAllowed();
        }

        if (!server.isSystem()) {
            LwM2mPath path = request.getPath();

            // BootstrapRead can only target object 1 and 2
            if (path.getObjectId() != 1 && path.getObjectId() != 2) {
                return BootstrapReadResponse.badRequest("bootstrap read can only target Object 1 (Server) or 2 (ACL)");
            }
        }
        return doRead(server, request);
    }

    protected BootstrapReadResponse doRead(LwM2mServer server, BootstrapReadRequest request) {
        // This should be a not implemented error, but this is not defined in the spec.
        return BootstrapReadResponse.internalServerError("not implemented");
    }

    @Override
    public synchronized WriteResponse write(LwM2mServer server, WriteRequest request) {
        try {
            beginTransaction(LwM2mPath.OBJECT_DEPTH);

            LwM2mPath path = request.getPath();

            // write is not supported for bootstrap, use bootstrap write
            if (server.isLwm2mBootstrapServer()) {
                return WriteResponse.methodNotAllowed();
            }

            // write the security or oscore object is forbidden
            if (!server.isSystem() && (id == LwM2mId.SECURITY || id == LwM2mId.OSCORE)) {
                return WriteResponse.notFound();
            }

            if (path.isResource() || path.isResourceInstance()) {
                // resource write:
                // check if the resource is writable
                if (id != LwM2mId.SECURITY && id != LwM2mId.OSCORE) {
                    // security and oscore resources are writable by SYSTEM
                    ResourceModel resourceModel = objectModel.resources.get(path.getResourceId());
                    if (resourceModel == null) {
                        return WriteResponse.notFound();
                    } else if (!resourceModel.operations.isWritable()) {
                        return WriteResponse.methodNotAllowed();
                    } else if (path.isResourceInstance() && !resourceModel.multiple) {
                        return WriteResponse.badRequest("invalid path : resource is not multiple");
                    }
                }
            } else if (path.isObjectInstance()) {
                // instance write:
                // check if all resources are writable
                if (id != LwM2mId.SECURITY && id != LwM2mId.OSCORE) {
                    // security and oscore resources are writable by SYSTEM
                    ObjectModel model = getObjectModel();
                    for (Integer writeResourceId : ((LwM2mObjectInstance) request.getNode()).getResources().keySet()) {
                        ResourceModel resourceModel = model.resources.get(writeResourceId);
                        if (null != resourceModel && !resourceModel.operations.isWritable()) {
                            return WriteResponse.methodNotAllowed();
                        }
                    }
                }

                if (request.isReplaceRequest()) {
                    if (missingMandatoryResource(((LwM2mObjectInstance) request.getNode()).getResources().values())) {
                        return WriteResponse.badRequest("mandatory writable resources missing!");
                    }
                }
            }

            // TODO we could do a validation of request.getNode() by comparing with resourceSpec information

            return doWrite(server, request);
        } finally {
            endTransaction(LwM2mPath.OBJECT_DEPTH);
        }
    }

    protected WriteResponse doWrite(LwM2mServer server, WriteRequest request) {
        // This should be a not implemented error, but this is not defined in the spec.
        return WriteResponse.internalServerError("not implemented");
    }

    @Override
    public synchronized BootstrapWriteResponse write(LwM2mServer server, BootstrapWriteRequest request) {

        // We should not get a bootstrapWriteRequest from a LWM2M server
        if (server.isLwm2mServer()) {
            return BootstrapWriteResponse.internalServerError("bootstrap write request from LWM2M server");
        }

        return doWrite(server, request);
    }

    protected BootstrapWriteResponse doWrite(LwM2mServer server, BootstrapWriteRequest request) {
        // This should be a not implemented error, but this is not defined in the spec.
        return BootstrapWriteResponse.internalServerError("not implemented");
    }

    @Override
    public synchronized DeleteResponse delete(LwM2mServer server, DeleteRequest request) {
        if (!server.isSystem()) {
            if (server.isLwm2mBootstrapServer())
                return DeleteResponse.methodNotAllowed();

            // delete the security object is forbidden
            if (id == LwM2mId.SECURITY || id == LwM2mId.OSCORE) {
                return DeleteResponse.notFound();
            }

            if (id == LwM2mId.DEVICE) {
                return DeleteResponse.methodNotAllowed();
            }
        }

        return doDelete(server, request);
    }

    protected DeleteResponse doDelete(LwM2mServer server, DeleteRequest request) {
        // This should be a not implemented error, but this is not defined in the spec.
        return DeleteResponse.internalServerError("not implemented");
    }

    @Override
    public synchronized BootstrapDeleteResponse delete(LwM2mServer server, BootstrapDeleteRequest request) {
        if (!server.isSystem()) {
            if (server.isLwm2mServer()) {
                return BootstrapDeleteResponse.internalServerError("bootstrap delete request from LWM2M server");
            }
            if (id == LwM2mId.DEVICE) {
                return BootstrapDeleteResponse.badRequest("Device object instance is not deletable");
            }
        }
        return doDelete(server, request);
    }

    protected BootstrapDeleteResponse doDelete(LwM2mServer server, BootstrapDeleteRequest request) {
        // This should be a not implemented error, but this is not defined in the spec.
        return BootstrapDeleteResponse.internalServerError("not implemented");
    }

    @Override
    public synchronized ExecuteResponse execute(LwM2mServer server, ExecuteRequest request) {
        LwM2mPath path = request.getPath();

        // execute is not supported for bootstrap
        if (server.isLwm2mBootstrapServer()) {
            return ExecuteResponse.methodNotAllowed();
        }

        // execute on security object is forbidden
        if (id == LwM2mId.SECURITY || id == LwM2mId.OSCORE) {
            return ExecuteResponse.notFound();
        }

        // only resource could be executed
        if (!path.isResource()) {
            return ExecuteResponse.badRequest(null);
        }

        // check if the resource is writable
        ResourceModel resourceModel = objectModel.resources.get(path.getResourceId());
        if (resourceModel == null) {
            return ExecuteResponse.notFound();
        } else if (!resourceModel.operations.isExecutable()) {
            return ExecuteResponse.methodNotAllowed();
        }

        return doExecute(server, request);
    }

    protected ExecuteResponse doExecute(LwM2mServer server, ExecuteRequest request) {
        // This should be a not implemented error, but this is not defined in the spec.
        return ExecuteResponse.internalServerError("not implemented");
    }

    @Override
    public synchronized WriteAttributesResponse writeAttributes(LwM2mServer server, WriteAttributesRequest request) {
        // execute is not supported for bootstrap
        if (server.isLwm2mBootstrapServer()) {
            return WriteAttributesResponse.methodNotAllowed();
        }
        // TODO should be implemented here to be available for all object enabler
        // This should be a not implemented error, but this is not defined in the spec.
        return WriteAttributesResponse.internalServerError("not implemented");
    }

    @Override
    public synchronized DiscoverResponse discover(LwM2mServer server, DiscoverRequest request) {

        if (server.isLwm2mBootstrapServer()) {
            // discover is not supported for bootstrap
            return DiscoverResponse.methodNotAllowed();
        }

        if (id == LwM2mId.SECURITY || id == LwM2mId.OSCORE) {
            return DiscoverResponse.notFound();
        }
        return doDiscover(server, request);

    }

    protected DiscoverResponse doDiscover(LwM2mServer server, DiscoverRequest request) {

        LwM2mPath path = request.getPath();
        if (path.isObject()) {
            // Manage discover on object
            LwM2mLink[] ObjectLinks = linkFormatHelper.getObjectDescription(this, null);
            return DiscoverResponse.success(ObjectLinks);

        } else if (path.isObjectInstance()) {
            // Manage discover on instance
            if (!getAvailableInstanceIds().contains(path.getObjectInstanceId()))
                return DiscoverResponse.notFound();

            LwM2mLink[] instanceLink = linkFormatHelper.getInstanceDescription(this, path.getObjectInstanceId(), null);
            return DiscoverResponse.success(instanceLink);

        } else if (path.isResource()) {
            // Manage discover on resource
            if (!getAvailableInstanceIds().contains(path.getObjectInstanceId()))
                return DiscoverResponse.notFound();

            ResourceModel resourceModel = getObjectModel().resources.get(path.getResourceId());
            if (resourceModel == null)
                return DiscoverResponse.notFound();

            if (!getAvailableResourceIds(path.getObjectInstanceId()).contains(path.getResourceId()))
                return DiscoverResponse.notFound();

            LwM2mLink resourceLink = linkFormatHelper.getResourceDescription(this, path.getObjectInstanceId(),
                    path.getResourceId(), null);
            return DiscoverResponse.success(new LwM2mLink[] { resourceLink });
        }
        return DiscoverResponse.badRequest(null);
    }

    @Override
    public synchronized BootstrapDiscoverResponse discover(LwM2mServer server, BootstrapDiscoverRequest request) {

        if (!server.isLwm2mBootstrapServer()) {
            return BootstrapDiscoverResponse.badRequest("not a bootstrap server");
        }

        return doDiscover(server, request);
    }

    protected BootstrapDiscoverResponse doDiscover(LwM2mServer server, BootstrapDiscoverRequest request) {

        LwM2mPath path = request.getPath();
        if (path.isObject()) {
            // Manage discover on object
            LwM2mLink[] ObjectLinks = linkFormatHelper.getBootstrapObjectDescription(this);
            return BootstrapDiscoverResponse.success(ObjectLinks);
        }
        return BootstrapDiscoverResponse.badRequest("invalid path");
    }

    @Override
    public synchronized ObserveResponse observe(LwM2mServer server, ObserveRequest request) {
        LwM2mPath path = request.getPath();

        // observe is not supported for bootstrap
        if (server.isLwm2mBootstrapServer())
            return ObserveResponse.methodNotAllowed();

        if (!server.isSystem()) {
            // observe or read of the security and oscore object are forbidden
            if (id == LwM2mId.SECURITY || id == LwM2mId.OSCORE)
                return ObserveResponse.notFound();

            // check if the resource is readable.
            if (path.isResource() || path.isResourceInstance()) {
                ResourceModel resourceModel = objectModel.resources.get(path.getResourceId());
                if (resourceModel == null) {
                    return ObserveResponse.notFound();
                } else if (!resourceModel.operations.isReadable()) {
                    return ObserveResponse.methodNotAllowed();
                } else if (path.isResourceInstance() && !resourceModel.multiple) {
                    return ObserveResponse.badRequest("invalid path : resource is not multiple");
                }
            }
        }
        return doObserve(server, request);
    }

    protected ObserveResponse doObserve(LwM2mServer server, ObserveRequest request) {
        ReadResponse readResponse = this.read(server, new ReadRequest(request.getPath().toString()));
        return new ObserveResponse(readResponse.getCode(), readResponse.getContent(), null, null,
                readResponse.getErrorMessage());
    }

    protected boolean missingMandatoryResource(Collection<LwM2mResource> resources) {
        // check, if all mandatory writable resources are provided
        // Collect all mandatory writable resource IDs from the model
        Set<Integer> mandatoryResources = new HashSet<>();
        for (ResourceModel resourceModel : getObjectModel().resources.values()) {
            if (resourceModel.mandatory
                    && (LwM2mId.SECURITY == id || LwM2mId.OSCORE == id || resourceModel.operations.isWritable()))
                mandatoryResources.add(resourceModel.id);
        }
        // Afterwards remove the provided resource IDs from that set
        for (LwM2mResource resource : resources) {
            mandatoryResources.remove(resource.getId());
        }
        return !mandatoryResources.isEmpty();
    }

    @Override
    public void addListener(ObjectListener listener) {
        transactionalListener.addListener(listener);
    }

    @Override
    public void removeListener(ObjectListener listener) {
        transactionalListener.removeListener(listener);
    }

    @Override
    public synchronized void beginTransaction(byte level) {
        transactionalListener.beginTransaction(level);
    }

    @Override
    public synchronized void endTransaction(byte level) {
        transactionalListener.endTransaction(level);
    }

    @Override
    public void init(LwM2mClient client, LinkFormatHelper linkFormatHelper) {
        this.lwm2mClient = client;
        this.linkFormatHelper = linkFormatHelper;
    }

    public LwM2mClient getLwm2mClient() {
        return lwm2mClient;
    }

    protected void fireInstancesAdded(int... instanceIds) {
        transactionalListener.objectInstancesAdded(this, instanceIds);
    }

    protected void fireInstancesRemoved(int... instanceIds) {
        transactionalListener.objectInstancesRemoved(this, instanceIds);
    }

    protected void fireResourcesChanged(LwM2mPath... paths) {
        transactionalListener.resourceChanged(paths);
    }

    @Override
    public ContentFormat getDefaultEncodingFormat(DownlinkRequest<?> request) {
        return ContentFormat.DEFAULT;
    }
}
