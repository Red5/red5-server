package org.red5.client;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;
import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.IAttributeStore;
import org.red5.server.api.Red5;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.so.IClientSharedObject;
import org.red5.server.api.so.ISharedObjectBase;
import org.red5.server.api.so.ISharedObjectListener;

public class SharedObjectClient extends RTMPClient implements IPendingServiceCallback, ISharedObjectListener, ClientExceptionHandler {

    private IClientSharedObject obj;

    private String soName;

    public SharedObjectClient(String server, int port, String app, String soName) {
        // set shared object name
        this.soName = soName;
        // if we'll be handling onBWChecks etc, add a service provider
        this.setServiceProvider(this);
        this.setExceptionHandler(this);
        // standard connect without params
        //this.connect(server, port, app, this);

        // connect with params
        String username = RandomStringUtils.randomAlphabetic(8);
        String room = soName;
        String param2 = "m";
        boolean webcam = false;
        int param4 = 0;
        int param5 = 1;
        String message = "Testing";
        boolean param7 = false;
        String link = "http://www.example.com";
        int param9 = 0;

        Object[] connectCallArguments = new Object[] { username, room, param2, webcam, param4, param5, message, param7, link, param9 };
        this.connectionParams = this.makeDefaultConnectionParams(server, port, app);
        this.connect(server, port, connectionParams, this, connectCallArguments);
    }

    @Override
    public void resultReceived(IPendingServiceCall call) {
        log.debug("Received pending call: {}", call);
        Object result = call.getResult();
        if (result instanceof ObjectMap) {
            obj = getSharedObject(soName, false);
            obj.connect(Red5.getConnectionLocal());
            obj.addSharedObjectListener(this);
        }
    }

    @Override
    public void handleException(Throwable throwable) {
        log.error("{}", new Object[] { throwable.getCause() });
    }

    /**
     * @return the obj
     */
    public IClientSharedObject getSharedObject() {
        return obj;
    }

    @Override
    public void onSharedObjectUpdate(ISharedObjectBase so, String key, Object value) {
        log.debug("onSharedObjectUpdate - key: {} value: {}", key, value.toString());

    }

    @Override
    public void onSharedObjectClear(ISharedObjectBase so) {
        log.debug("onSharedObjectClear");
    }

    @Override
    public void onSharedObjectConnect(ISharedObjectBase so) {
        log.debug("onSharedObjectConnect");
    }

    @Override
    public void onSharedObjectDelete(ISharedObjectBase so, String arg1) {
        log.debug("onSharedObjectDelete - arg1: {}", arg1);
    }

    @Override
    public void onSharedObjectDisconnect(ISharedObjectBase so) {
        log.debug("onSharedObjectDisconnect");
    }

    @Override
    public void onSharedObjectSend(ISharedObjectBase so, String arg1, List<?> list) {
        log.debug("onSharedObjectSend - arg1: {} list: {}", arg1, list);
    }

    @Override
    public void onSharedObjectUpdate(ISharedObjectBase so, IAttributeStore attrs) {
        log.debug("onSharedObjectUpdate - attrs: {}", attrs);
    }

    @Override
    public void onSharedObjectUpdate(ISharedObjectBase so, Map<String, Object> map) {
        log.debug("onSharedObjectUpdate - map: {}", map);
    }

}