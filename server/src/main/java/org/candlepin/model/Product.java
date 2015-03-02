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
package org.candlepin.model;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;



/**
 * Represents a Product that can be consumed and entitled. Products define the
 * software or entity they want to entitle i.e. RHEL Server. They also contain
 * descriptive meta data that might limit the Product i.e. 4 cores per server
 * with 4 guests.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cpo_products")
public class Product extends AbstractHibernateObject implements Linkable {

    public static final  String UEBER_PRODUCT_POSTFIX = "_ueber_product";

    // Object ID
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    private String uuid;

    // Internal RH product ID,
    @Column(name="product_id")
    @NotNull
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Owner owner;

    /**
     * How many entitlements per quantity
     */
    @Column
    private Long multiplier;

    // NOTE: we need a product "type" so we can tell what class of
    // product we are...

    @OneToMany(mappedBy = "product")
    @Cascade({ org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN })
    private Set<ProductAttribute> attributes;

    @ElementCollection
    @CollectionTable(name = "cpo_product_content",
                     joinColumns = @JoinColumn(name = "product_uuid"))
    @Column(name = "element")
    @LazyCollection(LazyCollectionOption.EXTRA) // allows .size() without loading all data
    private List<ProductContent> productContent;

    @ManyToMany(mappedBy = "providedProducts")
    private List<Subscription> subscriptions;

    @ElementCollection
    @CollectionTable(name = "cpo_product_dependent_products",
                     joinColumns = @JoinColumn(name = "product_uuid"))
    @Column(name = "element")
    private Set<String> dependentProductIds;

    protected Product() {
    }

    /**
     * Constructor Use this variant when creating a new object to persist.
     *
     * @param productId The Red Hat product ID for the new product.
     * @param name Human readable Product name
     */
    public Product(String productId, String name, Owner owner) {
        this(productId, name, owner, 1L);
    }

    public Product(String productId, String name, Owner owner, Long multiplier) {

        setId(productId);
        setName(name);
        setOwner(owner);
        setMultiplier(multiplier);
        setAttributes(new HashSet<ProductAttribute>());
        setProductContent(new LinkedList<ProductContent>());
        setSubscriptions(new LinkedList<Subscription>());
        setDependentProductIds(new HashSet<String>());
    }

    public Product(String productId, String name, Owner owner, String variant, String version,
        String arch, String type) {
        this(productId, name, owner, 1L);

        setAttribute("version", version);
        setAttribute("variant", variant);
        setAttribute("type", type);
        setAttribute("arch", arch);
    }

    public static Product createUeberProductForOwner(Owner owner) {
        return new Product(null, ueberProductNameForOwner(owner), owner, 1L);
    }

    /**
     * Retrieves this product's database UUID. While the product ID may exist multiple times
     * in the database (if in use by multiple owners), this UUID uniquely identifies a
     * product instance.
     *
     * @return
     *  this product's database UUID.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Sets this product's object ID. Note that this ID is used to uniquely identify this
     * particular object and has no baring on the Red Hat product ID.
     *
     * @param id
     *  The object ID to assign to this product.
     */
    public void setUuid(String id) {
        this.uuid = id;
    }

    /**
     * Retrieves this product's ID. Assigned by the content provider, and may exist in
     * multiple owners, thus may not be unique in itself.
     *
     * @return
     *  this product's ID.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the product ID for this product. The product ID is the Red Hat product ID and should not
     * be confused with the object ID.
     *
     * @param productId
     *  The new product ID for this product.
     */
    public void setId(String productId) {
        this.id = productId;
    }

    /**
     * @return the product name
     */
    public String getName() {
        return name;
    }

    /**
     * sets the product name.
     *
     * @param name name of the product
     */
    public void setName(String name) {
        this.name = name;
    }


    /**
     * @return The product's owner/organization
     */
    public Owner getOwner() {
        return this.owner;
    }

    /**
     * Sets the product's owner.
     *
     * @param owner
     * The new owner/organization for this product.
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * @return the number of entitlements to create from a single subscription
     */
    public Long getMultiplier() {
        return multiplier;
    }

    /**
     * @param multiplier the multiplier to set
     */
    public void setMultiplier(Long multiplier) {
        if (multiplier == null) {
            this.multiplier = 1L;
        }
        else {
            this.multiplier = Math.max(1L, multiplier);
        }
    }

    public void setAttributes(Set<ProductAttribute> attributes) {
        this.attributes = attributes;
    }

    public void setAttribute(String key, String value) {
        ProductAttribute existing = getAttribute(key);
        if (existing != null) {
            existing.setValue(value);
        }
        else {
            ProductAttribute attr = new ProductAttribute(key, value);
            attr.setProduct(this);
            addAttribute(attr);
        }
    }

    public void addAttribute(ProductAttribute attrib) {
        if (this.attributes == null) {
            this.attributes = new HashSet<ProductAttribute>();
        }
        attrib.setProduct(this);
        this.attributes.add(attrib);
    }

    public Set<ProductAttribute> getAttributes() {
        return attributes;
    }

    public ProductAttribute getAttribute(String key) {
        if (attributes != null) {
            for (ProductAttribute a : attributes) {
                if (a.getName().equals(key)) {
                    return a;
                }
            }
        }
        return null;
    }

    public String getAttributeValue(String key) {
        if (attributes != null) {
            for (ProductAttribute a : attributes) {
                if (a.getName().equals(key)) {
                    return a.getValue();
                }
            }
        }
        return null;
    }

    @XmlTransient
    public Set<String> getAttributeNames() {
        Set<String> toReturn = new HashSet<String>();

        if (attributes != null) {
            for (ProductAttribute attribute : attributes) {
                toReturn.add(attribute.getName());
            }
        }
        return toReturn;
    }

    public boolean hasAttribute(String key) {
        if (attributes != null) {
            for (ProductAttribute attribute : attributes) {
                if (attribute.getName().equals(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasContent(String contentId) {
        if (this.getProductContent() != null) {
            for (ProductContent pc : getProductContent()) {
                if (pc.getContent().getId().equals(contentId)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Product [id = " + id + ", name = " + name + "]";
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof Product)) {
            return false;
        }

        Product another = (Product) anObject;

        return getId().equals(another.getId()) && name.equals(another.getName());
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return id.hashCode() * 31;
        }
        return 31;
    }

    /**
     * @param content
     */
    public void addContent(Content content) {
        if (productContent == null) {
            productContent = new LinkedList<ProductContent>();
        }
        productContent.add(new ProductContent(this, content, false));
    }

    /**
     * @param content
     */
    public void addEnabledContent(Content content) {
        if (productContent == null) {
            productContent = new LinkedList<ProductContent>();
        }
        productContent.add(new ProductContent(this, content, true));
    }

    /**
     * @param productContent the productContent to set
     */
    public void setProductContent(List<ProductContent> productContent) {
        this.productContent = productContent;
    }

    /**
     * @return the productContent
     */
    public List<ProductContent> getProductContent() {
        return productContent;
    }

    public void addProductContent(ProductContent c) {
        c.setProduct(this);
        this.getProductContent().add(c);
    }

    // FIXME: this seems wrong, shouldn't this reset the content
    // not add to it?
    public void setContent(Set<Content> content) {
        if (content == null) {
            return;
        }
        if (productContent == null) {
            productContent = new LinkedList<ProductContent>();
        }
        for (Content newContent : content) {
            productContent.add(new ProductContent(this, newContent, false));
        }
    }

    @XmlTransient
    public List<Subscription> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<Subscription> subscriptions) {
        this.subscriptions = subscriptions;
    }

    /**
     * @param dependentProductIds the dependentProductIds to set
     */
    public void setDependentProductIds(Set<String> dependentProductIds) {
        this.dependentProductIds = dependentProductIds;
    }

    /**
     * @return the dependentProductIds
     */
    public Set<String> getDependentProductIds() {
        return dependentProductIds;
    }

    @Override
    public String getHref() {
        return "/owners/" + getOwner().getKey() + "/products/" + getId();
    }

    @Override
    public void setHref(String href) {
        /*
         * No-op, here to aid with updating objects which have nested objects
         * that were originally sent down to the client in HATEOAS form.
         */
    }

    /**
     * Returns true if this product has a content set which modifies the given
     * product:
     *
     * @param productId
     * @return true if this product modifies the given product ID
     */
    public boolean modifies(String productId) {
        if (getProductContent() != null) {
            for (ProductContent pc : getProductContent()) {
                if (pc.getContent().getModifiedProductIds().contains(productId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String ueberProductNameForOwner(Owner o) {
        return o.getKey() + UEBER_PRODUCT_POSTFIX;
    }

    @XmlTransient
    public List<String> getSkuDisabledContentIds() {
        List<String> skuDisabled = new ArrayList<String>();
        if(this.hasAttribute("content_override_disabled") &&
               this.getAttributeValue("content_override_disabled").length() > 0) {
            StringTokenizer stDisable = new StringTokenizer(
                    this.getAttributeValue("content_override_disabled"), ",");
            while (stDisable.hasMoreElements()) {
                skuDisabled.add((String)stDisable.nextElement());
            }
        }
        return skuDisabled;
    }

    @XmlTransient
    public List<String> getSkuEnabledContentIds() {
        List<String> skuEnabled = new ArrayList<String>();
        if(this.hasAttribute("content_override_enabled") &&
               this.getAttributeValue("content_override_enabled").length() > 0) {
            StringTokenizer stActive = new StringTokenizer(
                    this.getAttributeValue("content_override_enabled"), ",");
            while (stActive.hasMoreElements()) {
                skuEnabled.add((String)stActive.nextElement());
            }
        }
        return skuEnabled;
    }

}
