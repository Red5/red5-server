package org.red5.codec;

public enum AudioChannelOrder {

    Unspecified(0), // Only the channel count is specified, without any further information about the channel order
    Native(1), // The native channel order; the channels are in the same order in which as defined in AudioChannel
    Custom(2); // The channel order does not correspond to any predefined order and is stored as an explicit map

    private final int order;

    AudioChannelOrder(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }

    public static AudioChannelOrder valueOf(int order) {
        for (AudioChannelOrder aco : values()) {
            if (aco.getOrder() == order) {
                return aco;
            }
        }
        return Unspecified;
    }

}
