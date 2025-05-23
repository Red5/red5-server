/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import java.io.IOException;

import javax.management.MXBean;

import org.red5.server.api.stream.ResourceExistException;
import org.red5.server.api.stream.ResourceNotFoundException;

/**
 * Represents live stream broadcasted from client. As Flash Media Server, Red5 supports recording mode for live streams, that is, broadcasted stream has broadcast mode. It can be either "live" or "record" and latter causes server-side application to record broadcasted stream.
 *
 * Note that recorded streams are recorded as FLV files. The same is correct for audio, because NellyMoser codec that Flash Player uses prohibits on-the-fly transcoding to audio formats like MP3 without paying of licensing fee or buying SDK.
 *
 * This type of stream uses two different pipes for live streaming and recording.
 *
 * @author mondain
 */
@MXBean
public interface ClientBroadcastStreamMXBean {

    /**
     * <p>start.</p>
     */
    public void start();

    /**
     * <p>startPublishing.</p>
     */
    public void startPublishing();

    /**
     * <p>stop.</p>
     */
    public void stop();

    /**
     * <p>close.</p>
     */
    public void close();

    /**
     * <p>saveAs.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param isAppend a boolean
     * @throws java.io.IOException if any.
     * @throws org.red5.server.api.stream.ResourceNotFoundException if any.
     * @throws org.red5.server.api.stream.ResourceExistException if any.
     */
    public void saveAs(String name, boolean isAppend) throws IOException, ResourceNotFoundException, ResourceExistException;

    /**
     * <p>getSaveFilename.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getSaveFilename();

    /**
     * <p>getPublishedName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getPublishedName();

    /**
     * <p>setPublishedName.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setPublishedName(String name);

}
