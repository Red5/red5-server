package org.red5.server.api.stream.consumer;

import org.red5.server.net.rtmp.event.IRTMPEvent;

/**
 * <p>IFileConsumer interface.</p>
 *
 * @author mondain
 */
public interface IFileConsumer {

    /**
     * <p>setAudioDecoderConfiguration.</p>
     *
     * @param audioConfig a {@link org.red5.server.net.rtmp.event.IRTMPEvent} object
     */
    void setAudioDecoderConfiguration(IRTMPEvent audioConfig);

    /**
     * <p>setVideoDecoderConfiguration.</p>
     *
     * @param videoConfig a {@link org.red5.server.net.rtmp.event.IRTMPEvent} object
     */
    void setVideoDecoderConfiguration(IRTMPEvent videoConfig);

}
