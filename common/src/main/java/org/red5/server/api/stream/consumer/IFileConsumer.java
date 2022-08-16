package org.red5.server.api.stream.consumer;

import org.red5.server.net.rtmp.event.IRTMPEvent;

public interface IFileConsumer {

    void setAudioDecoderConfiguration(IRTMPEvent audioConfig);

    void setVideoDecoderConfiguration(IRTMPEvent videoConfig);

}
