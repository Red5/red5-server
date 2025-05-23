/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmp;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.util.Arrays;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.bouncycastle.util.BigIntegers;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPHandshake;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.util.FileUtil;

/**
 * Performs handshaking for client connections.
 *
 * @author Paul Gregoire
 */
public class OutboundHandshake extends RTMPHandshake {

    private byte[] outgoingDigest = new byte[DIGEST_LENGTH];

    private byte[] incomingDigest = new byte[DIGEST_LENGTH];

    private byte[] swfHash;

    private int digestPosClient;

    private int digestPosServer;

    // client initial request C1
    private byte[] c1 = null;

    // server initial response S1
    private byte[] s1 = null;

    // whether or not verification is mandatory
    private boolean forceVerification;

    /**
     * <p>Constructor for OutboundHandshake.</p>
     */
    public OutboundHandshake() {
        super(RTMPConnection.RTMP_NON_ENCRYPTED);
    }

    /**
     * <p>Constructor for OutboundHandshake.</p>
     *
     * @param handshakeType a byte
     */
    public OutboundHandshake(byte handshakeType) {
        super(handshakeType);
    }

    /**
     * <p>Constructor for OutboundHandshake.</p>
     *
     * @param handshakeType a byte
     * @param algorithm a int
     */
    public OutboundHandshake(byte handshakeType, int algorithm) {
        this(handshakeType);
        this.algorithm = algorithm;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer doHandshake(IoBuffer input) {
        throw new UnsupportedOperationException("Not used, call server response decoders directly");
    }

    /**
     * {@inheritDoc}
     *
     * Creates the servers handshake bytes
     */
    @Override
    protected void createHandshakeBytes() {
        log.trace("createHandshakeBytes");
        BigInteger bi = new BigInteger((Constants.HANDSHAKE_SIZE * 8), random);
        handshakeBytes = BigIntegers.asUnsignedByteArray(bi);
        // prevent AOOB error that can occur, sometimes
        if (handshakeBytes.length < Constants.HANDSHAKE_SIZE) {
            // resize the handshake bytes
            ByteBuffer b = ByteBuffer.allocate(Constants.HANDSHAKE_SIZE);
            b.put(handshakeBytes);
            b.put((byte) 0x13);
            b.flip();
            handshakeBytes = b.array();
        }
    }

    /**
     * Create the first part of the outgoing connection request (C0 and C1).
     * <pre>
     * C0 = 0x03 (client handshake type - 0x03, 0x06, 0x08, or 0x09)
     * C1 = 1536 bytes from the client
     * </pre>
     *
     * @return outgoing handshake C0+C1
     */
    public IoBuffer generateClientRequest1() {
        log.debug("generateClientRequest1");
        IoBuffer request = IoBuffer.allocate(Constants.HANDSHAKE_SIZE + 1);
        // set the handshake type byte
        request.put(handshakeType);
        if (useEncryption() || swfSize > 0) {
            fp9Handshake = true;
            algorithm = 1;
        } else {
            //fp9Handshake = false;
        }
        // timestamp
        int time = 5;
        handshakeBytes[0] = (byte) (time >>> 24);
        handshakeBytes[1] = (byte) (time >>> 16);
        handshakeBytes[2] = (byte) (time >>> 8);
        handshakeBytes[3] = (byte) time;
        if (fp9Handshake) {
            // flash player version > 9.0.115.0
            handshakeBytes[4] = (byte) 0x80;
            handshakeBytes[5] = 0;
            handshakeBytes[6] = 7;
            handshakeBytes[7] = 2;
        } else {
            log.debug("Using pre-version 9.0.115.0 handshake");
            handshakeBytes[4] = 0;
            handshakeBytes[5] = 0;
            handshakeBytes[6] = 0;
            handshakeBytes[7] = 0;
        }
        if (log.isTraceEnabled()) {
            log.trace("Time and version handshake bytes: {}", Hex.encodeHexString(Arrays.copyOf(handshakeBytes, 8)));
        }
        // get the handshake digest
        c1 = new byte[Constants.HANDSHAKE_SIZE];
        if (fp9Handshake) {
            // handle encryption setup
            if (useEncryption()) {
                // create keypair
                KeyPair keys = generateKeyPair();
                // get public key
                outgoingPublicKey = getPublicKey(keys);
                log.debug("Client public key: {}", Hex.encodeHexString(outgoingPublicKey));
                // get the DH offset in the handshake bytes
                int clientDHOffset = getDHOffset(algorithm, handshakeBytes, 0);
                log.trace("Outgoing DH offset: {}", clientDHOffset);
                // adds the public key to handshake bytes
                System.arraycopy(outgoingPublicKey, 0, handshakeBytes, clientDHOffset, KEY_LENGTH);
                // perform special processing for each type if needed
                switch (handshakeType) {
                    case RTMPConnection.RTMP_ENCRYPTED:

                        break;
                    case RTMPConnection.RTMP_ENCRYPTED_XTEA:

                        break;
                    case RTMPConnection.RTMP_ENCRYPTED_BLOWFISH:

                        break;
                }
            }
            digestPosClient = getDigestOffset(algorithm, handshakeBytes, 0);
            log.debug("Client digest position offset: {} algorithm: {}", digestPosClient, algorithm);
            System.arraycopy(handshakeBytes, 0, c1, 0, Constants.HANDSHAKE_SIZE);
            calculateDigest(digestPosClient, handshakeBytes, 0, GENUINE_FP_KEY, 30, c1, digestPosClient);
            // local storage of outgoing digest
            System.arraycopy(c1, digestPosClient, outgoingDigest, 0, DIGEST_LENGTH);
            log.debug("Client digest: {}", Hex.encodeHexString(outgoingDigest));
            log.debug("Digest is valid: {}", verifyDigest(digestPosClient, c1, RTMPHandshake.GENUINE_FP_KEY, 30));
        }
        if (log.isTraceEnabled()) {
            log.trace("C1: {}", Hex.encodeHexString(c1));
        }
        // put the generated data into our request
        request.put(c1);
        request.flip();
        // clear original base bytes
        handshakeBytes = null;
        return request;
    }

    /**
     * Decodes the first server response (S1) and returns a client response (C2).
     * <pre>
     * S1 = 1536 bytes from the server
     * C2 = Copy of S1 bytes
     * </pre>
     *
     * @param in incoming handshake S1
     * @return client response C2
     */
    public IoBuffer decodeServerResponse1(IoBuffer in) {
        log.debug("decodeServerResponse1");
        IoBuffer response = null;
        // the handshake type byte is not included
        s1 = new byte[Constants.HANDSHAKE_SIZE];
        in.get(s1);
        //if (log.isTraceEnabled()) {
        //    log.trace("S1: {}", Hex.encodeHexString(serverSig));
        //}
        if (log.isDebugEnabled()) {
            log.debug("Server version {}", Hex.encodeHexString(Arrays.copyOfRange(s1, 4, 8)));
        }
        // skip key / digest stuff if we're not doing any encryption or server says it doesnt support it
        if (fp9Handshake && handshakeType == RTMPConnection.RTMP_NON_ENCRYPTED && s1[4] == 0) {
            log.debug("Switching to pre-fp9 handshake");
            fp9Handshake = false;
        }
        if (fp9Handshake) {
            // make sure this is a client we can communicate with
            //if (validate(serverSig)) {
            //    log.debug("Valid RTMP server detected, algorithm: {}", algorithm);
            //} else {
            //    log.info("Invalid RTMP connection data detected, you may experience errors");
            //}
            // get the server digest
            if (!getServerDigestPosition()) {
                return null;
            }
            // digest verification passed, store the digest locally
            System.arraycopy(s1, digestPosServer, incomingDigest, 0, DIGEST_LENGTH);
            log.debug("Server digest: {}", Hex.encodeHexString(incomingDigest));
            // generate the SWF verification token
            if (swfSize > 0) {
                calculateSwfVerification(s1, swfHash, swfSize);
            }
            if (useEncryption()) {
                // get the DH offset in the handshake bytes
                int serverDHOffset = getDHOffset(algorithm, s1, 0);
                log.trace("Incoming DH offset: {}", serverDHOffset);
                // get the servers public key
                incomingPublicKey = new byte[KEY_LENGTH];
                System.arraycopy(s1, serverDHOffset, incomingPublicKey, 0, KEY_LENGTH);
                log.debug("Server public key: {}", Hex.encodeHexString(incomingPublicKey));
                // create the RC4 ciphers
                initRC4Encryption(getSharedSecret(incomingPublicKey, keyAgreement));
                switch (handshakeType) {
                    case RTMPConnection.RTMP_ENCRYPTED:
                        // update 'encoder / decoder state' for the RC4 keys. Both parties *pretend* as if handshake part 2 (1536 bytes) was encrypted
                        // effectively this hides / discards the first few bytes of encrypted session which is known to increase the secure-ness of RC4
                        // RC4 state is just a function of number of bytes processed so far that's why we just run 1536 arbitrary bytes through the keys below
                        byte[] dummyBytes = new byte[Constants.HANDSHAKE_SIZE];
                        cipherIn.update(dummyBytes);
                        cipherOut.update(dummyBytes);
                        break;
                    case RTMPConnection.RTMP_ENCRYPTED_XTEA:

                        break;
                    case RTMPConnection.RTMP_ENCRYPTED_BLOWFISH:

                        break;
                }
            }
            // create the response
            BigInteger bi = new BigInteger(Constants.HANDSHAKE_SIZE * 8, random);
            byte[] c2 = BigIntegers.asUnsignedByteArray(bi);
            // calculate response now
            byte[] signatureResp = new byte[DIGEST_LENGTH];
            byte[] digestResp = new byte[DIGEST_LENGTH];
            calculateHMAC_SHA256(s1, digestPosServer, DIGEST_LENGTH, GENUINE_FP_KEY, GENUINE_FP_KEY.length, digestResp, 0);
            calculateHMAC_SHA256(c2, 0, Constants.HANDSHAKE_SIZE - DIGEST_LENGTH, digestResp, DIGEST_LENGTH, signatureResp, 0);
            log.debug("Calculated digest key from secure key and server digest: {}", Hex.encodeHexString(digestResp));
            // FP10 stuff
            if (handshakeType == RTMPConnection.RTMP_ENCRYPTED_XTEA) {
                log.debug("RTMPE type 8 XTEA");
                // encrypt signatureResp
                for (int i = 0; i < DIGEST_LENGTH; i += 8) {
                    //encryptXtea(signatureResp, i, digestResp[i] % 15);
                }
            } else if (handshakeType == RTMPConnection.RTMP_ENCRYPTED_BLOWFISH) {
                log.debug("RTMPE type 9 Blowfish");
                // encrypt signatureResp
                for (int i = 0; i < DIGEST_LENGTH; i += 8) {
                    //encryptBlowfish(signatureResp, i, digestResp[i] % 15);
                }
            }
            log.debug("Client signature calculated: {}", Hex.encodeHexString(signatureResp));
            response = IoBuffer.allocate(Constants.HANDSHAKE_SIZE);
            response.put(c2, 0, Constants.HANDSHAKE_SIZE - DIGEST_LENGTH);
            response.put(signatureResp);
            response.flip();
        } else {
            // send the server handshake back as a response
            response = IoBuffer.allocate(Constants.HANDSHAKE_SIZE);
            response.put(s1, 0, Constants.HANDSHAKE_SIZE);
            response.flip();
        }
        // send the response
        return response;
    }

    /**
     * Decodes the second server response (S2).
     * <pre>
     * S2 = Copy of C1 bytes
     * </pre>
     *
     * @return true if validation passes and false otherwise
     * @param buf a {@link org.apache.mina.core.buffer.IoBuffer} object
     */
    public boolean decodeServerResponse2(IoBuffer buf) {
        byte[] s2 = new byte[Constants.HANDSHAKE_SIZE];
        buf.get(s2);
        return decodeServerResponse2(s2);
    }

    /**
     * Decodes the second server response (S2).
     * <pre>
     * S2 = Copy of C1 bytes
     * </pre>
     *
     * @return true if validation passes and false otherwise
     * @param s2 an array of {@link byte} objects
     */
    public boolean decodeServerResponse2(byte[] s2) {
        log.debug("decodeServerResponse2");
        // the handshake type byte is not included in s2
        if (log.isTraceEnabled()) {
            log.trace("S2: {}\nC1: {}", Hex.encodeHexString(s2), Hex.encodeHexString(c1));
        }
        if (fp9Handshake) {
            if (s2[4] == 0 && s2[5] == 0 && s2[6] == 0 && s2[7] == 0) {
                log.warn("Server refused signed authentication");
            }
            // validate server response part 2, not really required for client
            byte[] signature = new byte[DIGEST_LENGTH];
            byte[] digest = new byte[DIGEST_LENGTH];
            calculateHMAC_SHA256(c1, digestPosClient, DIGEST_LENGTH, GENUINE_FMS_KEY, GENUINE_FMS_KEY.length, digest, 0);
            calculateHMAC_SHA256(s2, 0, Constants.HANDSHAKE_SIZE - DIGEST_LENGTH, digest, DIGEST_LENGTH, signature, 0);
            log.debug("Digest key: {}", Hex.encodeHexString(digest));
            // FP10 stuff
            if (handshakeType == RTMPConnection.RTMP_ENCRYPTED_XTEA) {
                log.debug("RTMPE type 8 XTEA");
                // encrypt signatureResp
                for (int i = 0; i < DIGEST_LENGTH; i += 8) {
                    //encryptXtea(signature, i, digest[i] % 15);
                }
            } else if (handshakeType == RTMPConnection.RTMP_ENCRYPTED_BLOWFISH) {
                log.debug("RTMPE type 9 Blowfish");
                // encrypt signatureResp
                for (int i = 0; i < DIGEST_LENGTH; i += 8) {
                    //encryptBlowfish(signature, i, digest[i] % 15);
                }
            }
            log.debug("Signature calculated: {}", Hex.encodeHexString(signature));
            log.debug("Server sent signature: {}", Hex.encodeHexString(s2));
            if (!Arrays.equals(signature, Arrays.copyOfRange(s2, (Constants.HANDSHAKE_SIZE - DIGEST_LENGTH), (Constants.HANDSHAKE_SIZE - DIGEST_LENGTH) + DIGEST_LENGTH))) {
                log.info("Server not genuine");
                return false;
            } else {
                log.debug("Compatible flash server");
            }
        } else {
            if (!Arrays.equals(s2, c1)) {
                log.info("Client signature doesn't match!");
            }
        }
        return true;
    }

    /**
     * Gets and verifies the server digest.
     *
     * @return true if the server digest is found and verified, false otherwise
     */
    private boolean getServerDigestPosition() {
        boolean result = false;
        //log.trace("BigEndian bytes: {}", Hex.encodeHexString(s1));
        log.trace("Trying algorithm: {}", algorithm);
        digestPosServer = getDigestOffset(algorithm, s1, 0);
        log.debug("Server digest position offset: {}", digestPosServer);
        if (!(result = verifyDigest(digestPosServer, s1, GENUINE_FMS_KEY, 36))) {
            // try a different position
            algorithm ^= 1;
            log.trace("Trying algorithm: {}", algorithm);
            digestPosServer = getDigestOffset(algorithm, s1, 0);
            log.debug("Server digest position offset: {}", digestPosServer);
            if (!(result = verifyDigest(digestPosServer, s1, GENUINE_FMS_KEY, 36))) {
                log.warn("Server digest verification failed");
                // if we dont mind that verification routines failed
                if (!forceVerification) {
                    return true;
                }
            } else {
                log.debug("Server digest verified");
            }
        } else {
            log.debug("Server digest verified");
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * Determines the validation scheme for given input.
     */
    @Override
    public boolean validate(byte[] handshake) {
        if (validateScheme(handshake, 0)) {
            algorithm = 0;
            return true;
        }
        if (validateScheme(handshake, 1)) {
            algorithm = 1;
            return true;
        }
        log.error("Unable to validate server");
        return false;
    }

    private boolean validateScheme(byte[] handshake, int scheme) {
        int digestOffset = -1;
        switch (scheme) {
            case 0:
                digestOffset = getDigestOffset1(handshake, 0);
                break;
            case 1:
                digestOffset = getDigestOffset2(handshake, 0);
                break;
            default:
                log.error("Unknown algorithm: {}", scheme);
        }
        log.debug("Algorithm: {} digest offset: {}", scheme, digestOffset);
        byte[] tempBuffer = new byte[Constants.HANDSHAKE_SIZE - DIGEST_LENGTH];
        System.arraycopy(handshake, 0, tempBuffer, 0, digestOffset);
        System.arraycopy(handshake, digestOffset + DIGEST_LENGTH, tempBuffer, digestOffset, Constants.HANDSHAKE_SIZE - digestOffset - DIGEST_LENGTH);
        byte[] tempHash = new byte[DIGEST_LENGTH];
        calculateHMAC_SHA256(tempBuffer, 0, tempBuffer.length, GENUINE_FMS_KEY, 36, tempHash, 0);
        log.debug("Hash: {}", Hex.encodeHexString(tempHash));
        boolean result = true;
        for (int i = 0; i < DIGEST_LENGTH; i++) {
            if (handshake[digestOffset + i] != tempHash[i]) {
                result = false;
                break;
            }
        }
        return result;
    }

    /**
     * Initialize SWF verification data.
     *
     * @param swfFilePath path to the swf file or null
     */
    public void initSwfVerification(String swfFilePath) {
        log.info("Initializing swf verification for: {}", swfFilePath);
        byte[] bytes = null;
        if (swfFilePath != null) {
            File localSwfFile = new File(swfFilePath);
            if (localSwfFile.exists() && localSwfFile.canRead()) {
                log.info("Swf file path: {}", localSwfFile.getAbsolutePath());
                bytes = FileUtil.readAsByteArray(localSwfFile);
            } else {
                bytes = "Red5 is awesome for handling non-accessable swf file".getBytes();
            }
        } else {
            bytes = new byte[42];
        }
        calculateHMAC_SHA256(bytes, 0, bytes.length, GENUINE_FP_KEY, 30, swfHash, 0);
        swfSize = bytes.length;
        log.info("Verification - size: {}, hash: {}", swfSize, Hex.encodeHexString(swfHash));
    }

    /**
     * <p>getHandshakeBytes.</p>
     *
     * @return an array of {@link byte} objects
     */
    public byte[] getHandshakeBytes() {
        return c1;
    }

    /**
     * <p>Setter for the field <code>forceVerification</code>.</p>
     *
     * @param forceVerification a boolean
     */
    public void setForceVerification(boolean forceVerification) {
        this.forceVerification = forceVerification;
    }

}
