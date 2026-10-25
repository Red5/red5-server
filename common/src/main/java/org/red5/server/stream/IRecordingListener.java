package org.red5.server.stream;

import org.red5.server.api.IConnection;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.stream.consumer.FileConsumer;
import org.red5.server.stream.recording.RecordingRefusedException;

/**
 * Recording listener interface.
 * <br>
 * An application can supply its own implementation, typically extending {@link RecordingListener}, as a prototype bean named
 * {@value #BEAN_NAME} in its context; broadcast streams create their recording listener from it, and use {@link RecordingListener}
 * otherwise. The bean must be a prototype, since each recording needs its own listener.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public interface IRecordingListener extends IStreamListener {

    /**
     * Name of the prototype bean an application can declare to supply its own recording listener.
     */
    public static final String BEAN_NAME = "recordingListener";

    /**
     * Initialize the listener.
     *
     * @param conn
     *            Stream source connection
     * @param name
     *            Stream name
     * @param isAppend
     *            Append mode
     * @return true if initialization completes and false otherwise
     * @throws RecordingRefusedException
     *             if the application's recording budget or the principal's quota does not allow the recording
     */
    public boolean init(IConnection conn, String name, boolean isAppend);

    /**
     * Initialize the listener.
     *
     * @param scope
     *            Stream source scope
     * @param name
     *            Stream name
     * @param isAppend
     *            Append mode
     * @return true if initialization completes and false otherwise
     * @throws RecordingRefusedException
     *             if the application's recording budget does not allow the recording
     */
    public boolean init(IScope scope, String name, boolean isAppend);

    /**
     * Start the recording.
     */
    public void start();

    /**
     * Stop the recording.
     */
    public void stop();

    /** {@inheritDoc} */
    public void packetReceived(IBroadcastStream stream, IStreamPacket packet);

    /**
     * <p>isRecording.</p>
     *
     * @return recording state, true if recording and false otherwise
     */
    public boolean isRecording();

    /**
     * <p>isAppending.</p>
     *
     * @return appending state, true if appending and false otherwise
     */
    public boolean isAppending();

    /**
     * <p>getFileConsumer.</p>
     *
     * @return the recordingConsumer
     */
    public FileConsumer getFileConsumer();

    /**
     * <p>setFileConsumer.</p>
     *
     * @param recordingConsumer
     *            the recordingConsumer to set
     */
    public void setFileConsumer(FileConsumer recordingConsumer);

    /**
     * <p>getFileName.</p>
     *
     * @return the fileName
     */
    public String getFileName();

    /**
     * <p>setFileName.</p>
     *
     * @param fileName
     *            the fileName to set
     */
    public void setFileName(String fileName);

}
