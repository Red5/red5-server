/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

/**
 * A service used to create and manage token buckets.
 * 
 * @author The Red5 Project
 * @author Steven Gong (steven.gong@gmail.com)
 */
public interface ITokenBucketService {
    public static final String KEY = "TokenBucketService";

    /**
     * Create a token bucket.
     * 
     * @param capacity
     *            Capacity of the bucket.
     * @param speed
     *            Speed of the bucket. Bytes per millisecond.
     * @return null if fail to create.
     */
    ITokenBucket createTokenBucket(long capacity, long speed);

    /**
     * Remove this bucket.
     * 
     * @param bucket
     *            Bucket to remove
     */
    void removeTokenBucket(ITokenBucket bucket);
}
