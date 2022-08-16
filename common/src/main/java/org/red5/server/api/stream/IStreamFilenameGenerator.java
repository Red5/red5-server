/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.api.stream;

import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeService;

/**
 * A class that can generate filenames for streams.
 *
 * @author The Red5 Project
 * @author Joachim Bauch (bauch@struktur.de)
 */
public interface IStreamFilenameGenerator extends IScopeService {

    /** Name of the bean to setup a custom filename generator in an application. */
    public static String BEAN_NAME = "streamFilenameGenerator";

    /** Possible filename generation types. */
    public static enum GenerationType {
        PLAYBACK, RECORD, APPEND
    };

    /**
     * Generate a filename without an extension.
     *
     * @param scope
     *            Scope to use
     * @param name
     *            Stream name
     * @param type
     *            Generation strategy (either playback or record)
     * @return Full filename
     */
    public String generateFilename(IScope scope, String name, GenerationType type);

    /**
     * Generate a filename with an extension.
     *
     * @param scope
     *            Scope to use
     * @param name
     *            Stream filename
     * @param extension
     *            Extension
     * @param type
     *            Generation strategy (either playback or record)
     * @return Full filename with extension
     */
    public String generateFilename(IScope scope, String name, String extension, GenerationType type);

    /**
     * True if returned filename is an absolute path, else relative to application.
     *
     * If relative to application, you need to use
     *
     * <pre>
     * scope.getContext().getResources(fileName)[0].getFile()
     * </pre>
     *
     * to resolve this to a file.
     *
     * If absolute (ie returns true) simply use
     *
     * <pre>
     * new File(generateFilename(scope, name))
     * </pre>
     *
     * @return true if an absolute path; else false
     */
    public boolean resolvesToAbsolutePath();

}
