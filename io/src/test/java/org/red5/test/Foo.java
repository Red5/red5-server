package org.red5.test;

import org.red5.io.amf3.IDataInput;
import org.red5.io.amf3.IDataOutput;
import org.red5.io.amf3.IExternalizable;

/**
 * Used for testing AMF3 Vectors
 * 
 * @author Paul
 */
public class Foo implements IExternalizable {

    @Override
    public void readExternal(IDataInput input) {
    }

    @Override
    public void writeExternal(IDataOutput output) {
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Foo[]";
    }
}