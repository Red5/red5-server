package org.red5.codec;

/**
 * <p>AudioChannelOrder class.</p>
 *
 * @author mondain
 */
public enum AudioChannelOrder {

    Unspecified(0), // Only the channel count is specified, without any further information about the channel order
    Native(1), // The native channel order; the channels are in the same order in which as defined in AudioChannel
    Custom(2); // The channel order does not correspond to any predefined order and is stored as an explicit map

    private final int order;

    AudioChannelOrder(int order) {
        this.order = order;
    }

    /**
     * <p>Getter for the field <code>order</code>.</p>
     *
     * @return a int
     */
    public int getOrder() {
        return order;
    }

    /**
     * <p>valueOf.</p>
     *
     * @param order a int
     * @return a {@link org.red5.codec.AudioChannelOrder} object
     */
    public static AudioChannelOrder valueOf(int order) {
        for (AudioChannelOrder aco : values()) {
            if (aco.getOrder() == order) {
                return aco;
            }
        }
        return Unspecified;
    }

}
