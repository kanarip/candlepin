/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.candlepin.auth.Principal;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.util.CrlFileUtil;

import com.google.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CRLException;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;



/**
 * CrlResource
 */
@Path("/crl")
public class CrlResource {

    private Configuration config;
    private CrlFileUtil crlFileUtil;
    private CertificateSerialCurator certificateSerialCurator;


    @Inject
    public CrlResource(Configuration config, CrlFileUtil crlFileUtil,
        CertificateSerialCurator certificateSerialCurator) {

        this.config = config;

        this.crlFileUtil = crlFileUtil;
        this.certificateSerialCurator = certificateSerialCurator;
    }

    /**
     * Retrieves the Certificate Revocation List
     *
     * @return a String object
     * @throws CRLException if there is issue generating the CRL
     * @throws IOException if there is a problem serializing the CRL
     * @httpcode 200
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    public Response getCurrentCrl(@Context Principal principal) throws CRLException {
        String filePath = getCrlFilePath();
        File crlFile = new File(filePath);

        try {
            crlFile.createNewFile();
            this.crlFileUtil.syncCRLWithDB(crlFile);

            return Response.ok().entity(new FileInputStream(crlFile)).build();
        }
        catch (IOException e) {
            throw new IseException(e.getMessage(), e);
        }
    }

    /**
     * Deletes a Certificate from the Revocation List
     *
     * @param serialIds list of certificate serial ids
     * @throws CRLException if there is a problem updating the CRL object
     * @throws IOException if there is a problem reading the CRL file
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public void unrevoke(@QueryParam("serial") String[] serialIds)
        throws CRLException, IOException {

        String filePath = getCrlFilePath();
        File crlFile = new File(filePath);

        try {
            List<BigInteger> serials = new LinkedList<BigInteger>();
            for (CertificateSerial serial : certificateSerialCurator.listBySerialIds(serialIds)) {
                serials.add(serial.getSerial());
            }

            if (serials.size() > 0) {
                this.crlFileUtil.updateCRLFile(crlFile, null, serials);
            }
        }
        catch (IOException e) {
            throw new IseException(e.getMessage(), e);
        }
    }

    private String getCrlFilePath() {
        String filePath = config.getString(ConfigProperties.CRL_FILE_PATH);

        if (filePath == null) {
            throw new IseException("CRL file path not defined in config file");
        }

        return filePath;
    }
}
