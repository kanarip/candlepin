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
package org.candlepin.auth.permissions;

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

/**
 *
 */
public class ConsumerPoolPermission extends TypedPermission<Pool> {

    private Consumer consumer;

    public ConsumerPoolPermission(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public Class<Pool> getTargetType() {
        return Pool.class;
    }

    @Override
    public boolean canAccessTarget(Pool target, SubResource subResource,
        Access required) {
        // should we mess with username restrictions here?
        // Don't allow access to any sub-resources, this is just to view the pools
        // themselves, not their entitlements for example.
        return (subResource.equals(SubResource.NONE) &&
            target.getOwner().getKey().equals(consumer.getOwner().getKey()));
    }

    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (entityClass.equals(Pool.class)) {
            return Restrictions.eq("owner", consumer.getOwner());
        }
        return null;
    }

    @Override
    public Owner getOwner() {
        return consumer.getOwner();
    }


}
