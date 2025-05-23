package org.red5.client.net.rtmp.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.object.Output;
import org.red5.io.object.Serializer;
import org.red5.server.api.Red5;
import org.red5.server.api.IConnection.Encoding;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IServiceCall;
import org.red5.server.net.ICommand;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMPProtocolEncoder;
import org.red5.server.net.rtmp.event.Invoke;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.net.rtmp.status.StatusObject;
import org.red5.server.service.Call;
import org.slf4j.LoggerFactory;

/**
 * Class to specifically handle client side situations.
 *
 * @author mondain
 */
public class RTMPClientProtocolEncoder extends RTMPProtocolEncoder {

    {
        log = LoggerFactory.getLogger(RTMPClientProtocolEncoder.class);
    }

    /**
     * {@inheritDoc}
     *
     * Encode notification event and fill given byte buffer.
     */
    @Override
    protected void encodeCommand(IoBuffer out, ICommand command) {
        log.debug("encodeCommand - command: {}", command);
        RTMPConnection conn = (RTMPConnection) Red5.getConnectionLocal();
        Output output = new org.red5.io.amf.Output(out);
        final IServiceCall call = command.getCall();
        final boolean isPending = (call.getStatus() == Call.STATUS_PENDING);
        log.debug("Call: {} pending: {}", call, isPending);
        if (!isPending) {
            log.debug("Call has been executed, send result");
            Serializer.serialize(output, call.isSuccess() ? "_result" : "_error");
        } else {
            log.debug("This is a pending call, send request");
            // for request we need to use AMF3 for client mode if the connection is AMF3
            if (conn.getEncoding() == Encoding.AMF3) {
                output = new org.red5.io.amf3.Output(out);
            }
            final String action = (call.getServiceName() == null) ? call.getServiceMethodName() : call.getServiceName() + '.' + call.getServiceMethodName();
            Serializer.serialize(output, action);
        }
        if (command instanceof Invoke) {
            Serializer.serialize(output, Integer.valueOf(command.getTransactionId()));
            Serializer.serialize(output, command.getConnectionParams());
        }
        if (call.getServiceName() == null && "connect".equals(call.getServiceMethodName())) {
            // response to initial connect, always use AMF0
            output = new org.red5.io.amf.Output(out);
        } else {
            if (conn.getEncoding() == Encoding.AMF3) {
                output = new org.red5.io.amf3.Output(out);
            } else {
                output = new org.red5.io.amf.Output(out);
            }
        }
        if (!isPending && (command instanceof Invoke)) {
            IPendingServiceCall pendingCall = (IPendingServiceCall) call;
            if (!call.isSuccess()) {
                log.debug("Call was not successful");
                StatusObject status = generateErrorResult(StatusCodes.NC_CALL_FAILED, call.getException());
                pendingCall.setResult(status);
            }
            Object res = pendingCall.getResult();
            log.debug("Writing result: {}", res);
            Serializer.serialize(output, res);
        } else {
            log.debug("Writing params");
            final Object[] args = call.getArguments();
            if (args != null) {
                for (Object element : args) {
                    Serializer.serialize(output, element);
                }
            }
        }
        if (command.getData() != null) {
            out.setAutoExpand(true);
            out.put(command.getData());
        }
    }
}
