package org.red5.client.net.rtmp;

import java.util.Map;

import org.red5.io.utils.ObjectMap;
import org.red5.server.api.IConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientMethodHander {

    private Logger log = LoggerFactory.getLogger(ClientMethodHander.class);

    public void onStatus(IConnection conn, ObjectMap<String, Object> status) {
        log.debug("onStatus: {}", status);

        String code = status.get("code").toString();
        if ("NetStream.Play.Stop".equals(code)) {
            log.debug("Playback stopped");
            conn.close();
        }

    }

    public void onPlayStatus(IConnection conn, Map<Object, Object> info) {
        log.info("onPlayStatus: {}", info);

    }

}