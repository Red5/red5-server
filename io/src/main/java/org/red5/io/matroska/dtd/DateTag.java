package org.red5.io.matroska.dtd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Date;

import org.red5.io.matroska.ParserUtils;
import org.red5.io.matroska.VINT;

/**
 * http://matroska.org/technical/specs/index.html Date - signed 8 octets integer in nanoseconds with 0 indicating the precise beginning of the millennium (at 2001-01-01T00:00:00,000000000 UTC)
 * 
 */
public class DateTag extends UnsignedIntegerTag {
    public static final long NANO_MULTIPLIER = 1000;

    public static final long DELAY = 978285600000L; // beginning of the millennium (at 2001-01-01T00:00:00,000000000 UTC)

    private Date value;

    /**
     * Constructor
     * 
     * @see Tag#Tag(String, VINT)
     *
     * @param name
     *            - the name of tag to be created
     * @param id
     *            - the id of tag to be created
     * @throws IOException
     *             - in case of IO error
     */
    public DateTag(String name, VINT id) throws IOException {
        super(name, id);
    }

    /**
     * Constructor
     * 
     * @see Tag#Tag(String, VINT, VINT, InputStream)
     * 
     * @param name
     *            - the name of tag to be created
     * @param id
     *            - the id of tag to be created
     * @param size
     *            - the size of tag to be created
     * @param inputStream
     *            - stream to read tag data from
     * @throws IOException
     *             - in case of IO error
     */
    public DateTag(String name, VINT id, VINT size, InputStream inputStream) throws IOException {
        super(name, id, size, inputStream);
    }

    /**
     * @see Tag#parse(InputStream)
     */
    @Override
    public void parse(InputStream inputStream) throws IOException {
        long _val = ParserUtils.parseInteger(inputStream, (int) getSize());
        long val = _val / NANO_MULTIPLIER + DELAY;
        super.setValue(_val);
        value = new Date(val);
    }

    /**
     * @see Tag#putValue(ByteBuffer)
     */
    @Override
    protected void putValue(ByteBuffer bb) throws IOException {
        super.putValue(bb);
    }

    /**
     * setter for value, updates the size of this tag
     * 
     * @param value
     *            - value to be set
     * @return - this for chaining
     */
    public DateTag setValue(final Date value) {
        this.value = value;
        super.setValue((value.getTime() - DELAY) * NANO_MULTIPLIER);
        return this;
    }

    /**
     * getter for value as {@link Date}
     * 
     * @return - value as {@link Date}
     */
    public Date getDate() {
        return value;
    }

    /**
     * method to get "pretty" represented {@link Tag}
     */
    @Override
    public String toString() {
        return (super.toString() + " = " + value);
    }
}
