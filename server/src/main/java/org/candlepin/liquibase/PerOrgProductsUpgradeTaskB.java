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
package org.candlepin.liquibase;

import liquibase.database.Database;
import liquibase.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;



/**
 * The PerOrgProductsUpgradeTaskB performs the post-db upgrade on the pre-existing cp_* tables. This
 * task also checks if any changes have been made to product or content information since the
 */
public class PerOrgProductsUpgradeTaskB extends LiquibaseCustomTask {

    public PerOrgProductsUpgradeTaskB(Database database) {
        super(database);
    }

    public PerOrgProductsUpgradeTaskB(Database database, CustomTaskLogger logger) {
        super(database, logger);
    }

    /**
     * Executes the multi-org upgrade task.
     *
     * @throws DatabaseException
     *  if an error occurs while performing a database operation
     *
     * @throws SQLException
     *  if an error occurs while executing an SQL statement
     */
    public void execute() throws DatabaseException, SQLException {

        // Store the connection's auto commit setting, so we may temporarily clobber it.
        boolean autocommit = this.connection.getAutoCommit();

        try {
            this.connection.setAutoCommit(false);

            // Get org count
            ResultSet result = this.executeQuery("SELECT count(id) FROM cp_owner");
            result.next();
            int count = result.getInt(1);
            result.close();

            // Migrate orgs
            ResultSet orgids = this.executeQuery("SELECT id FROM cp_owner");
            for (int index = 1; orgids.next(); ++index) {
                String orgid = orgids.getString(1);

                this.logger.info(String.format(
                    "Migrating data for org %s (%d of %d)",
                    orgid, index, count
                ));

                // This is basically the same as task A, except we need to check for things that
                // need to be re-migrated because it's changed since the last time this has run.

                // Also, we can now do our updates to cp_pool


                // this.migrateProductData(orgid);
                // this.migrateActivationKeyData(orgid);
                // this.migratePoolData(orgid);
                // this.migrateSubscriptionData(orgid);
            }

            orgids.close();
            this.connection.commit();
        }
        finally {
            // Restore original autocommit state
            this.connection.setAutoCommit(autocommit);
        }
    }

    /**
     * Migrates product data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate product data
     */
    @SuppressWarnings("checkstyle:methodlength")
    private void updateMigratedProductData(String orgid) throws DatabaseException, SQLException {

        Map<String, String> productCache = new HashMap<String, String>();
        Map<String, String> contentCache = new HashMap<String, String>();

        this.logger.info("Migrating product data for org " + orgid);

        // Retrieve a list of products such that:
        //  - The products are referenced by the old tables/columns
        //  - The products are either newer than the migrated info or have not yet been migrated
        ResultSet productids = this.executeQuery(
            "SELECT cp_product.id, cpo_products.uuid " +
            "FROM " +
            "  (SELECT p.product_id_old AS product_id " +
            "    FROM cp_pool p " +
            "    WHERE p.owner_id = ? " +
            "      AND NOT NULLIF(p.product_id_old, '') IS NULL " +
            "  UNION " +
            "  SELECT p.derived_product_id_old " +
            "    FROM cp_pool p " +
            "    WHERE p.owner_id = ? " +
            "      AND NOT NULLIF(p.derived_product_id_old, '') IS NULL " +
            "  UNION " +
            "  SELECT pp.product_id " +
            "    FROM cp_pool p " +
            "    JOIN cp_pool_products pp " +
            "      ON p.id = pp.pool_id " +
            "    WHERE p.owner_id = ? " +
            "      AND NOT NULLIF(pp.product_id, '') IS NULL " +
            "  UNION " +
            "  SELECT s.product_id " +
            "    FROM cp_subscription s " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(s.product_id, '') IS NULL " +
            "  UNION " +
            "  SELECT s.derivedproduct_id " +
            "    FROM cp_subscription s " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(s.derivedproduct_id, '') IS NULL " +
            "  UNION " +
            "  SELECT sp.product_id " +
            "    FROM cp_subscription_products sp " +
            "    JOIN cp_subscription s " +
            "      ON s.id = sp.subscription_id " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(sp.product_id, '') IS NULL " +
            "  UNION " +
            "  SELECT sdp.product_id " +
            "    FROM cp_sub_derivedprods sdp " +
            "    JOIN cp_subscription s " +
            "      ON s.id = sdp.subscription_id " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(sdp.product_id, '') IS NULL) AS plist " +
            "  INNER JOIN cp_product " +
            "    ON cp_product.id = plist.product_id " +
            "  LEFT JOIN cpo_products " +
            "    ON cpo_products.product_id = cp_product.id " +
            "WHERE " +
            "  cpo_products.uuid IS NULL " +
            "  OR ( " +
            "    cpo_products.owner_id = ? " +
            "    AND ( " +
            "      cp_product.created > cpo_products.created " +
            "      OR cp_product.updated > cpo_products.updated " +
            "    ) " +
            "  ) ",
            orgid, orgid, orgid, orgid, orgid, orgid, orgid, orgid
        );

        // TODO:
        // Should we also clean up dangling references to products...?

        while (productids.next()) {
            String productid = productids.getString(1);
            String productuuid = productids.getString(2);

            if (productuuid != null) {
                // Product changed -- need to update it

                // Need to update the product here. We're going to be lazy and delete all of the
                // existing product data, and then re-insert it. Allows us to do this in two queries
                // instead of three, more complex, queries for each related set.

                // We can just do the deletes here, then fall into the migration code below for the
                // remainder.
            }
            else {
                // New product entirely
                productuuid = this.generateUUID();

                this.executeUpdate(
                    "INSERT INTO cpo_products " +
                    "SELECT ?, created, updated, multiplier, ?, ?, name " +
                    "FROM cp_product WHERE id = ?",
                    productuuid, orgid, productid, productid
                );
            }




            // Make sure we haven't already migrated this product.
            if (productCache.get(productid) != null) {
                this.logger.warn(String.format(
                    "Skipping migration for already-migrated product \"%s\"",
                    productid
                ));

                continue;
            }

            productCache.put(productid, productuuid);

            this.logger.info(
                String.format("Mapping org/prod \"%s\"/\"%s\" to UUID \"%s\"", orgid, productid, productuuid)
            );

            // Migration information from pre-existing tables to cpo_* tables. This should always
            // be 0, 1 or an exception.
            int migrated =

            if (migrated < 1) {
                this.logger.error(String.format(
                    "Unable to migrate product \"%s\"; likely a dangling reference to a product that " +
                    "no longer exists.",
                    productid
                ));

                continue;
            }

            this.executeUpdate(
                "INSERT INTO cpo_pool_provided_products " +
                "SELECT pool_id, ? " +
                "FROM cp_pool_products pp, cp_pool p WHERE pp.pool_id = p.id " +
                "    AND p.owner_id = ? AND pp.product_id = ? AND pp.dtype = 'provided' ",
                productuuid, orgid, productid
            );

            this.executeUpdate(
                "INSERT INTO cpo_pool_derived_products " +
                "SELECT pool_id, ? " +
                "FROM cp_pool_products pp, cp_pool p WHERE pp.pool_id = p.id " +
                "     AND p.owner_id = ? AND pp.product_id = ? AND pp.dtype = 'derived' ",
                productuuid, orgid, productid
            );

            ResultSet attributes = this.executeQuery(
                "SELECT id, created, updated, name, value, product_id " +
                "FROM cp_product_attribute WHERE product_id = ?",
                productid
            );

            while (attributes.next()) {
                this.executeUpdate(
                    "INSERT INTO cpo_product_attributes" +
                    "  (id, created, updated, name, value, product_uuid) " +
                    "VALUES(?, ?, ?, ?, ?, ?)",
                    this.generateUUID(), attributes.getTimestamp(2), attributes.getTimestamp(3),
                    attributes.getString(4), attributes.getString(5), productuuid
                );
            }

            attributes.close();

            ResultSet certificates = this.executeQuery(
                "SELECT id, created, updated, cert, privatekey, product_id " +
                "FROM cp_product_certificate WHERE product_id = ?",
                productid
            );

            while (certificates.next()) {
                this.executeUpdate(
                    "INSERT INTO cpo_product_certificates" +
                    "  (id, created, updated, cert, privatekey, product_uuid) " +
                    "VALUES(?, ?, ?, ?, ?, ?)",
                    this.generateUUID(), certificates.getTimestamp(2), certificates.getTimestamp(3),
                    certificates.getBytes(4), certificates.getBytes(5), productuuid
                );
            }

            certificates.close();

            this.executeUpdate(
                "INSERT INTO cpo_product_dependent_products " +
                "SELECT ?, element " +
                "FROM cp_product_dependent_products WHERE cp_product_id = ?",
                productuuid, productid
            );

            // Update new product columns on existing tables:
            this.executeUpdate(
                "UPDATE cp_pool " +
                "SET product_uuid = ? " +
                "WHERE product_id_old = ? AND owner_id = ?",
                productuuid, productid, orgid
            );

            this.executeUpdate(
                "UPDATE cp_pool " +
                "SET derived_product_uuid = ? " +
                "WHERE derived_product_id_old = ? AND owner_id = ?",
                productuuid, productid, orgid
            );

            // Update product's content
            ResultSet contentids = this.executeQuery(
                "SELECT content_id FROM cp_product_content WHERE product_id = ?",
                productid
            );

            while (contentids.next()) {
                String contentid = contentids.getString(1);
                String contentuuid = contentCache.get(contentid);

                if (contentuuid == null) {
                    contentuuid = this.generateUUID();
                    contentCache.put(contentid, contentuuid);

                    // update cpo_content
                    this.executeUpdate(
                        "INSERT INTO cpo_content " +
                        "SELECT ?, id, created, updated, ?, contenturl, gpgurl, label, " +
                        "       metadataexpire, name, releasever, requiredtags, type, " +
                        "       vendor, arches " +
                        "FROM cp_content WHERE id = ?",
                        contentuuid, orgid, contentid
                    );

                    this.executeUpdate(
                        "INSERT INTO cpo_content_modified_products " +
                        "SELECT ?, element " +
                        "FROM cp_content_modified_products " +
                        "WHERE cp_content_id = ?",
                        contentuuid, contentid
                    );

                    ResultSet content = this.executeQuery(
                        "SELECT id, created, updated, contentid, enabled, environment_id " +
                        "FROM cp_env_content WHERE contentid = ?",
                        contentid
                    );

                    while (content.next()) {
                        this.executeUpdate(
                            "INSERT INTO cpo_environment_content" +
                            "  (id, created, updated, content_uuid, enabled, environment_id) " +
                            "VALUES(?, ?, ?, ?, ?, ?)",
                            this.generateUUID(), content.getTimestamp(2), content.getTimestamp(3),
                            contentuuid, content.getBoolean(5), content.getString(6)
                        );
                    }

                    content.close();
                }

                this.executeUpdate(
                    "INSERT INTO cpo_product_content " +
                    "SELECT ?, ?, enabled, created, updated " +
                    "FROM cp_product_content WHERE product_id = ? AND content_id = ?",
                    productuuid, contentuuid, productid, contentid
                );
            }

            contentids.close();
        }

        productids.close();
    }

    /**
     * Migrates activation key data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate activation key data
     */
    private void migrateActivationKeyData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("Migrating activation key data for org " + orgid);

        this.executeUpdate(
            "INSERT INTO cpo_activation_key_products(key_id, product_uuid) " +
            "SELECT AK.id, (SELECT uuid FROM cpo_products " +
            "  WHERE owner_id = ? AND product_id = AKP.product_id) " +
            "FROM cp_activation_key AK " +
            "  JOIN cp_activationkey_product AKP ON AKP.key_id = AK.id " +
            "WHERE AK.owner_id = ?",
            orgid, orgid
        );
    }

    /**
     * Migrates pool data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate pool data
     */
    private void migratePoolData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("Migrating pool data for org " + orgid);

        ResultSet pools = this.executeQuery("SELECT id FROM cp_pool WHERE owner_id = ?", orgid);

        while (pools.next()) {
            String poolid = pools.getString(1);

            // Migrate pool source subscription info
            ResultSet sourcesub = this.executeQuery(
                "SELECT id, subscriptionid, subscriptionsubkey, pool_id, created, updated " +
                "FROM cp_pool_source_sub WHERE pool_id = ?",
                poolid
            );

            while (sourcesub.next()) {
                this.executeUpdate(
                    "INSERT INTO cpo_pool_source_sub " +
                    "  (id, subscription_id, subscription_sub_key, pool_id, created, updated)" +
                    "VALUES(?, ?, ?, ?, ?, ?)",
                    this.generateUUID(), sourcesub.getString(2), sourcesub.getString(3), poolid,
                    sourcesub.getTimestamp(5), sourcesub.getTimestamp(6)
                );
            }

            sourcesub.close();
        }

        pools.close();
    }

    /**
     * Migrates subscription data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate subscription data
     */
    @SuppressWarnings("checkstyle:methodlength")
    private void migrateSubscriptionData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("Migrating subscription data for org " + orgid);

        ResultSet subscriptiondata = this.executeQuery(
            "SELECT id, certificate_id, cdn_id, upstream_entitlement_id, upstream_consumer_id, " +
            "  upstream_pool_id " +
            "FROM cp_subscription WHERE owner_id = ?",
            orgid
        );

        while (subscriptiondata.next()) {
            String subid = subscriptiondata.getString(1);

            String upstreamEntitlementId = subscriptiondata.getString(4);
            String upstreamConsumerId = subscriptiondata.getString(5);
            String upstreamPoolId = subscriptiondata.getString(6);

            // If the subscription is lacking upstream information, it's likely a custom sub. We'll
            // need to remove the source sub information from its corresponding pool (if it exists)
            if (upstreamEntitlementId == null || upstreamConsumerId == null || upstreamPoolId == null) {
                int count = this.executeUpdate(
                    "DELETE FROM cp_pool_source_sub WHERE subscriptionid = ?", subid
                );

                // If we didn't delete anything, the pools haven't been refreshed after the sub was
                // added, so we'll need to migrate it ourselves.
                if (count == 0) {
                    this.executeUpdate(
                        "INSERT INTO cp_pool (" +
                        "  id, created, updated, activesubscription, accountnumber, contractnumber, " +
                        "  enddate, quantity, startdate, owner_id, ordernumber, type, product_uuid, " +
                        "  cdn_id, certificate_id, version) " +
                        "SELECT ?, S.created, S.updated, ?, " +
                        "  S.accountnumber, S.contractnumber, S.enddate, S.quantity, S.startdate, " +
                        "  S.owner_id, S.ordernumber, 'NORMAL', " +
                        "  (SELECT uuid FROM cpo_products " +
                        "    WHERE owner_id = S.owner_id AND product_id = S.product_id), " +
                        "  S.cdn_id, S.certificate_id, 0 " +
                        "FROM cp_subscription S WHERE id = ?",
                        this.generateUUID(), true, subid
                    );
                }
            }

            // ...otherwise we need to migrate upstream information to the master pool
            else {
                // Update any master pools which make use of this subscription information
                this.executeUpdate(
                    "UPDATE cp_pool SET " +
                    "  certificate_id = ?, " +
                    "  cdn_id = ?, " +
                    "  upstream_entitlement_id = ?, " +
                    "  upstream_consumer_id = ?, " +
                    "  upstream_pool_id = ? " +
                    "WHERE cp_pool.id IN (" +
                    "  SELECT SS.pool_id FROM cp_pool_source_sub SS " +
                    "  WHERE SS.subscriptionid = ? AND SS.subscriptionsubkey = 'master'" +
                    ")",
                    subscriptiondata.getString(2), subscriptiondata.getString(3),
                    upstreamEntitlementId, upstreamConsumerId, upstreamPoolId, subid
                );
            }
        }

        subscriptiondata.close();
    }

}
