/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.remoting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.util.EntityUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.compatibility.flex.messaging.messages.AcknowledgeMessage;
import org.red5.compatibility.flex.messaging.messages.AcknowledgeMessageExt;
import org.red5.compatibility.flex.messaging.messages.AsyncMessageExt;
import org.red5.compatibility.flex.messaging.messages.CommandMessage;
import org.red5.compatibility.flex.messaging.messages.Constants;
import org.red5.compatibility.flex.messaging.messages.Message;
import org.red5.io.amf3.Input;
import org.red5.io.amf3.Output;
import org.red5.io.object.Deserializer;
import org.red5.io.object.RecordSet;
import org.red5.io.object.Serializer;
import org.red5.io.utils.ObjectMap;
import org.red5.server.net.remoting.RemotingClient;
import org.red5.server.net.remoting.RemotingHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client interface for remoting calls directed at an LCDS or BlazeDS style service.
 * 
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class DSRemotingClient extends RemotingClient {

    protected static Logger log = LoggerFactory.getLogger(DSRemotingClient.class);

    /** The datasource id (assigned by the server). DsId */
    private String dataSourceId = "nil";

    /** The request sequence number */
    private int sequenceCounter = 1;

    /**
     * Dummy constructor used by the spring configuration.
     */
    public DSRemotingClient() {
        log.debug("DSRemotingClient created");
    }

    /**
     * Create new remoting client for the given url.
     * 
     * @param url
     *            URL to connect to
     */
    public DSRemotingClient(String url) {
        super(url, DEFAULT_TIMEOUT);
        log.debug("DSRemotingClient created  - url: {}", url);
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    /**
     * Encode the method call.
     * 
     * @param method
     *            Remote method being called
     * @param params
     *            Method parameters
     * @return Byte buffer with data to perform remoting call
     */
    private IoBuffer encodeInvoke(String method, Object[] params) {
        log.debug("RemotingClient encodeInvoke - method: {} params: {}", method, params);
        IoBuffer result = IoBuffer.allocate(1024);
        result.setAutoExpand(true);
        //force version 3
        result.putShort((short) 3);
        // Headers
        Collection<RemotingHeader> hdr = headers.values();
        result.putShort((short) hdr.size());
        for (RemotingHeader header : hdr) {
            Output.putString(result, header.getName());
            result.put(header.getMustUnderstand() ? (byte) 0x01 : (byte) 0x00);

            IoBuffer tmp = IoBuffer.allocate(1024);
            tmp.setAutoExpand(true);
            Output tmpOut = new Output(tmp);
            Serializer.serialize(tmpOut, header.getValue());
            tmp.flip();
            // Size of header data
            result.putInt(tmp.limit());
            // Header data
            result.put(tmp);
            tmp.free();
            tmp = null;
        }
        // One body
        result.putShort((short) 1);
        // Method name
        Output.putString(result, method);
        // Client callback for response
        //Output.putString(result, "");
        //responseURI 
        Output.putString(result, "/" + sequenceCounter++);
        // Serialize parameters
        IoBuffer tmp = IoBuffer.allocate(1024);
        tmp.setAutoExpand(true);
        Output tmpOut = new Output(tmp);
        //if the params are null send the NULL AMF type
        //this should fix APPSERVER-296
        if (params == null) {
            tmpOut.writeNull();
        } else {
            tmpOut.writeArray(params);
        }
        tmp.flip();
        // Store size and parameters
        result.putInt(tmp.limit());
        result.put(tmp);
        tmp.free();
        tmp = null;

        result.flip();
        return result;
    }

    /**
     * Process any headers sent in the response.
     * 
     * @param in
     *            Byte buffer with response data
     */
    @Override
    protected void processHeaders(IoBuffer in) {
        log.debug("RemotingClient processHeaders - buffer limit: {}", (in != null ? in.limit() : 0));
        int version = in.getUnsignedShort(); // skip
        log.debug("Version: {}", version);
        // the version by now, AMF3 is not yet implemented
        int count = in.getUnsignedShort();
        log.debug("Count: {}", count);
        Input input = new Input(in);
        for (int i = 0; i < count; i++) {
            String name = input.getString();
            log.debug("Name: {}", name);
            boolean required = (in.get() == 0x01);
            log.debug("Required: {}", required);
            Object value = null;
            int len = in.getInt();
            log.debug("Length: {}", len);
            // look for junk bytes ff,ff,ff,ff
            if (len == -1) {
                in.get(); //02
                len = in.getShort();
                log.debug("Corrected length: {}", len);
                value = input.readString(len);
            } else {
                value = Deserializer.deserialize(input, Object.class);
            }
            log.debug("Value: {}", value);
            // XXX: this is pretty much untested!!!
            if (RemotingHeader.APPEND_TO_GATEWAY_URL.equals(name)) {
                // Append string to gateway url
                appendToUrl = (String) value;
            } else if (RemotingHeader.REPLACE_GATEWAY_URL.equals(name)) {
                // Replace complete gateway url
                url = (String) value;
                // XXX: reset the <appendToUrl< here?
            } else if (RemotingHeader.PERSISTENT_HEADER.equals(name)) {
                // Send a new header with each following request
                if (value instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> valueMap = (Map<String, Object>) value;
                    RemotingHeader header = new RemotingHeader((String) valueMap.get("name"), (Boolean) valueMap.get("mustUnderstand"), valueMap.get("data"));
                    headers.put(header.getName(), header);
                } else {
                    log.error("Expected Map but received {}", value);
                }
            } else {
                log.warn("Unsupported remoting header \"{}\" received with value \"{}\"", name, value);
            }
        }
    }

    /**
     * Decode response received from remoting server.
     * 
     * @param data
     *            Result data to decode
     * @return Object deserialized from byte buffer data
     */
    private Object decodeResult(IoBuffer data) {
        log.debug("decodeResult - data limit: {}", (data != null ? data.limit() : 0));
        processHeaders(data);

        Input input = new Input(data);
        String target = null;

        byte b = data.get();
        //look for SOH
        if (b == 0) {
            log.debug("NUL: {}", b); //0
            log.debug("SOH: {}", data.get()); //1
        } else if (b == 1) {
            log.debug("SOH: {}", b); //1			
        }

        int targetUriLength = data.getShort();
        log.debug("targetUri length: {}", targetUriLength);
        target = input.readString(targetUriLength);

        log.debug("NUL: {}", data.get()); //0

        //should be junk bytes ff, ff, ff, ff
        int count = data.getInt();
        if (count == -1) {
            log.debug("DC1: {}", data.get()); //17
            count = 1;
        } else {
            data.position(data.position() - 4);
            count = data.getShort();
        }

        if (count != 1) {
            throw new RuntimeException("Expected exactly one result but got " + count);
        }

        String[] targetParts = target.split("[/]");
        if (targetParts.length > 1) {
            log.debug("Result sequence number: {}", targetParts[1]);
            target = targetParts[2];
        } else {
            target = target.substring(1);
        }
        log.debug("Target: {}", target);
        if ("onResult".equals(target)) {
            //read return value
            return input.readObject();
        } else if ("onStatus".equals(target)) {
            //read return value
            return input.readObject();
        }
        //read return value
        return Deserializer.deserialize(input, Object.class);
    }

    /**
     * Invoke a method synchronously on the remoting server.
     * 
     * @param method
     *            Method name
     * @param params
     *            Parameters passed to method
     * @return the result of the method call
     */
    @Override
    public Object invokeMethod(String method, Object[] params) {
        log.debug("invokeMethod url: {}", (url + appendToUrl));
        IoBuffer resultBuffer = null;
        IoBuffer data = encodeInvoke(method, params);
        //setup POST
        HttpPost post = null;
        try {
            post = new HttpPost(url + appendToUrl);
            post.addHeader("Content-Type", CONTENT_TYPE);
            post.setEntity(new InputStreamEntity(data.asInputStream(), data.limit()));
            // execute the method
            HttpResponse response = client.execute(post);
            int code = response.getStatusLine().getStatusCode();
            log.debug("HTTP response code: {}", code);
            if (code / 100 != 2) {
                throw new RuntimeException("Didn't receive success from remoting server");
            } else {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    //fix for Trac #676
                    int contentLength = (int) entity.getContentLength();
                    //default the content length to 16 if post doesn't contain a good value
                    if (contentLength < 1) {
                        contentLength = 16;
                    }
                    // get the response as bytes
                    byte[] bytes = EntityUtils.toByteArray(entity);
                    resultBuffer = IoBuffer.wrap(bytes);
                    resultBuffer.flip();
                    Object result = decodeResult(resultBuffer);
                    if (result instanceof RecordSet) {
                        // Make sure we can retrieve paged results
                        ((RecordSet) result).setRemotingClient(this);
                    }
                    return result;
                }
            }
        } catch (Exception ex) {
            log.error("Error while invoking remoting method.", ex);
            post.abort();
        } finally {
            if (resultBuffer != null) {
                resultBuffer.free();
                resultBuffer = null;
            }
            data.free();
            data = null;
        }
        return null;
    }

    /**
     * Used for debugging byte stream.
     * 
     * @param data
     *            IoBuffer
     */
    @SuppressWarnings("unused")
    private static final void dump(IoBuffer data) {
        log.debug("Hex: {}", data.getHexDump());
        int pos = data.position();
        byte[] bar = new byte[data.limit() - data.position()];
        data.get(bar);
        log.debug("Str {}", new String(bar));
        bar = null;
        data.position(pos);
    }

    @SuppressWarnings("rawtypes")
    public static void main(String[] args) {
        //blazeds my-polling-amf http://localhost:8400/meta/messagebroker/amfpolling
        DSRemotingClient client = new DSRemotingClient("http://localhost:8400/meta/messagebroker/amfpolling");
        try {
            //send ping
            CommandMessage msg = new CommandMessage();
            msg.setCorrelationId("");
            msg.setDestination("");
            //create / set headers
            ObjectMap<String, Object> headerMap = new ObjectMap<String, Object>();
            headerMap.put(Message.FLEX_CLIENT_ID_HEADER, "nil");
            headerMap.put(Message.MESSAGING_VERSION, 1);
            msg.setHeaders(headerMap);
            msg.setOperation(Constants.CLIENT_PING_OPERATION);
            msg.setBody(new Object[] {});

            Object response = client.invokeMethod("null", new Object[] { msg });
            log.debug("Response: {}\n{}", response.getClass().getName(), response);
            if (response instanceof AcknowledgeMessage || response instanceof AcknowledgeMessageExt) {
                log.info("Got first ACK");
                AcknowledgeMessage ack = (AcknowledgeMessage) response;
                Object id = ack.getHeader(Message.FLEX_CLIENT_ID_HEADER);
                if (id != null) {
                    log.info("Got DSId: {}", id);
                    client.setDataSourceId((String) id);
                }
            }
            //wait a second for a dsid
            do {
                Thread.sleep(1000);
                log.info("Done with sleeping");
            } while (client.getDataSourceId().equals("nil"));
            //send subscribe
            msg = new CommandMessage();
            msg.setCorrelationId("");
            msg.setDestination("Red5Chat");
            headerMap = new ObjectMap<String, Object>();
            headerMap.put(Message.FLEX_CLIENT_ID_HEADER, client.getDataSourceId());
            headerMap.put(Message.ENDPOINT_HEADER, "my-polling-amf");
            msg.setHeaders(headerMap);
            msg.setOperation(Constants.SUBSCRIBE_OPERATION);
            msg.setBody(new Object[] {});

            response = client.invokeMethod("null", new Object[] { msg });

            if (response instanceof AcknowledgeMessage || response instanceof AcknowledgeMessageExt) {
                log.info("Got second ACK {}", ((AcknowledgeMessage) response));
            }

            //poll every 5 seconds for 60
            int loop = 12;
            do {
                Thread.sleep(5000);
                log.info("Done with sleeping");
                //send poll 
                //0 messages - returns DSK
                //n messages - CommandMessage with internal DSA
                msg = new CommandMessage();
                msg.setCorrelationId("");
                msg.setDestination("Red5Chat");
                headerMap = new ObjectMap<String, Object>();
                headerMap.put(Message.FLEX_CLIENT_ID_HEADER, client.getDataSourceId());
                msg.setHeaders(headerMap);
                msg.setOperation(Constants.POLL_OPERATION);
                msg.setBody(new Object[] {});

                response = client.invokeMethod("null", new Object[] { msg });
                if (response instanceof AcknowledgeMessage) {
                    AcknowledgeMessage ack = (AcknowledgeMessage) response;
                    log.info("Got ACK response {}", ack);
                } else if (response instanceof CommandMessage) {
                    CommandMessage com = (CommandMessage) response;
                    log.info("Got COM response {}", com);
                    ArrayList list = (ArrayList) com.getBody();
                    log.info("Child message body: {}", ((AsyncMessageExt) list.get(0)).getBody());
                }
            } while (--loop > 0);

        } catch (Exception e) {
            log.warn("Exception {}", e);
        }
    }

}
