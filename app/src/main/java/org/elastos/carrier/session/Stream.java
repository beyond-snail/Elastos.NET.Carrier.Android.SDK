/*
 * Copyright (c) 2018 Elastos Foundation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.elastos.carrier.session;

import org.elastos.carrier.Log;
import org.elastos.carrier.exceptions.ElastosException;

/**
 * The class representing Carrier stream.
 */
public class Stream {
    private static final String TAG = "CarrierStream";

    private long contextCookie = 0;
    private long nativeCookie = 0;

    private int streamId;
    private StreamType type;

    public static int PROPERTY_COMPRESS = 0x01;
    public static int PROPERTY_PLAIN = 0x02;
    public static int PROPERTY_RELIABLE = 0x04;
    public static int PROPERTY_MULTIPLEXING = 0x08;
    public static int PROPERTY_PORT_FORWARDING = 0x10;

    /* Jni native methods */
    private native boolean get_transport_info(int streamId, TransportInfo info);
    private native int write_stream_data(int streamId, byte[] data);

    private native int open_channel(int streamId, String cookie);
    private native boolean close_channel(int streamId, int channel);
    private native int write_channel_data(int streamId, int channel, byte[] data);
    private native boolean pend_channel(int streamId, int channel);
    private native boolean resume_channel(int streamId, int channel);

    private native int open_port_forwarding(int sreamId, String service,
                                            PortForwardingProtocol protocol,
                                            String host, String port);
    private native boolean close_port_forwarding(int streamId, int portForwarding);

    private static native int get_error_code();

    private Stream(StreamType type) {
        streamId = 0;
        this.type = type;
    }

    public int getStreamId() {
        return streamId;
    }

    /**
     * Get the carrier stream type.
     *
     * @return
     *      The stream type defined in StreamType.
     */
    public StreamType getType() {
        return type;
    }

	/**
	 * Get tranport info of carrier stream.
     *
     * @return
     *      The transport info of this stream.
     */
    public TransportInfo getTransportInfo() throws ElastosException {
        TransportInfo info = new TransportInfo();

        if (!get_transport_info(streamId, info)) {
            throw new ElastosException(get_error_code());
        }

        return info;
    }

    /**
     * Send outgoing data to remote peer.
     *
     * If the stream is in multiplexing mode, application can not call this function
     * to send data. If this function is called on multiplexing mode stream, it will
     * throw exception.
     *
     * @param
     *      data        The outgoing data
     *
     * @return
     *      Bytes of data sent on success
     *
     * @throws
     *      ElastosException
     */
    public int writeData(byte[] data) throws ElastosException {
        if (data == null || data.length == 0)
            throw new IllegalArgumentException();

        int bytes = write_stream_data(streamId, data);
        if (bytes < 0)
            throw new ElastosException(get_error_code());

        return bytes;
    }

    /**
     * Open a new channel on multiplexing stream.
     *
     * If the stream is not multiplexing this function will throw exception.
     *
     * @param
     *      cookie      The application defined data passed to remote peer
     *
     * @return
     *      New channel ID
     *
     * @throws
     *      ElastosException
     */
    public int openChannel(String cookie) throws ElastosException {

        if (cookie == null || cookie.length() == 0)
            throw new IllegalArgumentException();

        int channel = open_channel(streamId, cookie);
        if (channel < 0)
            throw new ElastosException(get_error_code());

        Log.d(TAG, String.format("Channel %d on component %d created", channel));

        return channel;
    }

    /**
     * Close a new channel on multiplexing stream.
     *
     * If the stream is not multiplexing this function will throw exception.
     *
     * @param
     *      channel     The channel ID to close
     *
     * @throws
     *      ElastosException
     */
    public void closeChannel(int channel) throws ElastosException {
        if (channel <= 0)
            throw new IllegalArgumentException();

        boolean result = close_channel(streamId, channel);
        if (!result)
            throw new ElastosException(get_error_code());

        Log.d(TAG, String.format("Channel %d on component %d closed", channel));
    }

    /**
     * Send outgoing data to remote peer.
     *
     * If the stream is not multiplexing this function will throw exception.
     *
     * @param
     *      channel     [in] The channel ID
     * @param
     *      data        [in] The outgoing data
     *
     * @return
     *      Bytes of data sent on success.
     */
    public int writeData(int channel, byte[] data) throws ElastosException {
        if (channel <= 0 || data == null || data.length == 0)
            throw new IllegalArgumentException();

        int result = write_channel_data(streamId, channel, data);
        if (result < 0)
            throw new ElastosException(get_error_code());

        return result;
    }

    /**
     * Request remote peer to pend channel data sending.
     *
     * If the stream is not multiplexing this function will throw exception.
     *
     * @param
     *      channel     The channel ID
     *
     * @throws
     *      ElastosException
     */
    public void pendChannel(int channel) throws ElastosException {
        if (channel <= 0)
            throw new IllegalArgumentException();

        if (!pend_channel(streamId, channel))
            throw new ElastosException(get_error_code());
    }

    /**
     * Request remote peer to resume channel data sending.
     *
     * If the stream is not multiplexing this function will throw exception.
     *
     * @param
     *      channel     The channel ID
     *
     * @throws
     *      ElastosException
     */
    public void resumeChannel(int channel) throws ElastosException {
        if (channel <= 0)
            throw new IllegalArgumentException();

        if (!resume_channel(streamId, channel))
            throw new ElastosException(get_error_code());
    }

    /**
     * Open a port forwarding to remote service over multiplexing.
     *
     * If the stream is not multiplexing this function will throw exception.
     *
     * @param
     *      service     The remote service name
     * @param
     *      protocol    Port forwarding protocol
     * @param
     *      host        Local host or ip to binding
     *                  If host is null, port forwarding will bind to localhost
     * @param
     *      port        Local port to binding, can not be nil.
     *
     * @return
     *      Port forwarding ID
     *
     * @throws
     *      ElastosException
     */
    public int openPortFowarding(String service, PortForwardingProtocol protocol,
                                 String host, String port) throws ElastosException {

        if (service == null || service.length() == 0)
            throw new IllegalArgumentException();

        int pfId = open_port_forwarding(streamId,  service, protocol, host, port);
        if (pfId < 0)
            throw new ElastosException(get_error_code());

        Log.d(TAG, String.format("Port forwarding %d to service %s created, " +
                "and currently listening on %s://%s:%s",
                pfId, service, protocol, host, port));

        return pfId;
    }

    /**
     * Close a port forwarding.
     *
     * If the stream is not multiplexing this function will throw exception.
     *
     * @param
     *      portForwarding  The portforwarding ID.
     *
     * @throws
     *      ElastosException
     */
    public void closePortForwarding(int portForwarding) throws ElastosException {
        if (portForwarding <= 0)
            throw new IllegalArgumentException();

        boolean result = close_port_forwarding(streamId, portForwarding);
        if (!result)
            throw new ElastosException(get_error_code());

        Log.d(TAG, String.format("Port forwarding %d closed nicely", portForwarding));
    }
}
