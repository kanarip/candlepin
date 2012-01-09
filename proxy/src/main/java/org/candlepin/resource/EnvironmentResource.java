/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.resource;

import org.candlepin.auth.interceptor.Verify;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentCurator;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.xnap.commons.i18n.I18n;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * REST API for managing Environments.
 */
@Path("/environments")
public class EnvironmentResource {

    private EnvironmentCurator envCurator;
    private I18n i18n;
    private ContentCurator contentCurator;
    private EnvironmentContentCurator envContentCurator;

    @Inject
    public EnvironmentResource(EnvironmentCurator envCurator, I18n i18n,
        ContentCurator contentCurator, EnvironmentContentCurator envContentCurator) {

        this.envCurator = envCurator;
        this.i18n = i18n;
        this.contentCurator = contentCurator;
        this.envContentCurator = envContentCurator;
    }


    /**
     * @param envId
     * @httpcode 200
     * @httpcode 404
     * @return environment requested
     */
    @GET
    @Path("/{env_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Environment getEnv(
        @PathParam("env_id") @Verify(Environment.class) String envId) {
        Environment e = envCurator.find(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", envId));
        }
        return e;
    }

    /**
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("/{env_id}")
    public void deleteEnv(@PathParam("env_id") @Verify(Environment.class) String envId) {
        Environment e = envCurator.find(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr("No such environment: {0}", envId));
        }
        envCurator.delete(e);
    }

    /**
     * List all environments on the server. Only available to super admins.
     *
     * @return list of all environments
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "environments")
    public List<Environment> getEnvironments() {
        return envCurator.listAll();
    }

    /**
     * Promote content into an environment.
     *
     * Consumers registered to this environment will now receive this content in
     * their entitlement certificates.
     *
     * @httpcode 200
     * @httpcode 404
     * @return the environment content
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{env_id}/content/{content_id}")
    public EnvironmentContent promoteContent(
            @PathParam("env_id") @Verify(Environment.class) String envId,
            @PathParam("content_id") String contentId,
            @QueryParam("enabled") Boolean enabled) {

        Environment env = lookupEnvironment(envId);
        Content content = lookupContent(contentId);

        EnvironmentContent envContent = new EnvironmentContent(env, content, enabled);
        env.getEnvironmentContent().add(envContent);

        envContentCurator.create(envContent);
        return envContent;
    }

    /**
     * Demote content from an environment.
     *
     * Consumer's registered to this environment will no see this content in their
     * entitlement certificates. (after they are regenerated and synced to clients)
     *
     * @httpcode 200
     * @httpcode 404
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{env_id}/content/{content_id}")
    public void removeContent(@PathParam("env_id") @Verify(Environment.class) String envId,
                              @PathParam("content_id") String contentId) {

        Environment e = lookupEnvironment(envId);
        Content c = lookupContent(contentId);
        EnvironmentContent envContent =
            envContentCurator.lookupByEnvironmentAndContent(e, c);
        envContentCurator.delete(envContent);
    }

    private Environment lookupEnvironment(String envId) {
        Environment e = envCurator.find(envId);
        if (e == null) {
            throw new NotFoundException(i18n.tr(
                "No such environment : {0}", envId));
        }
        return e;
    }

    private Content lookupContent(String contentId) {
        Content content = contentCurator.find(contentId);
        if (content == null) {
            throw new NotFoundException(i18n.tr(
                "No such content: {0}", contentId));
        }
        return content;
    }


}