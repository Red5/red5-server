package org.red5.test;

import java.util.Date;

/**
 * Typed object stored in the legacy shared object files under src/test/resources/persistence/legacy. The class name and its properties
 * are part of those files, so neither may change.
 */
public class LegacyBean {

    private String name;

    private int count;

    private Date created;

    public LegacyBean() {
    }

    public LegacyBean(String name, int count, Date created) {
        this.name = name;
        this.count = count;
        this.created = created;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

}
