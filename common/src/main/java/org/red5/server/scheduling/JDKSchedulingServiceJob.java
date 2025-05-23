/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.scheduling;

import java.util.Map;

import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled job that is registered in the Quartz scheduler.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class JDKSchedulingServiceJob implements Runnable {

    private final static Logger log = LoggerFactory.getLogger(JDKSchedulingServiceJob.class);

    /**
     * Job data map
     */
    private final Map<String, Object> jobDataMap;

    private final String jobName;

    // set this flag to prevent removal within the internal run() of the scheduled job
    private final boolean autoRemove;

    /**
     * <p>Constructor for JDKSchedulingServiceJob.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param dataMap a {@link java.util.Map} object
     */
    public JDKSchedulingServiceJob(String name, Map<String, Object> dataMap) {
        this.jobDataMap = dataMap;
        log.debug("Set job data map: {}", jobDataMap);
        this.jobName = name;
        this.autoRemove = true;
    }

    /**
     * <p>Constructor for JDKSchedulingServiceJob.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param dataMap a {@link java.util.Map} object
     * @param autoRemove a boolean
     */
    public JDKSchedulingServiceJob(String name, Map<String, Object> dataMap, boolean autoRemove) {
        this.jobDataMap = dataMap;
        log.debug("Set job data map: {}", jobDataMap);
        this.jobName = name;
        this.autoRemove = autoRemove;
    }

    /**
     * <p>run.</p>
     */
    public void run() {
        //log.debug("execute");
        ISchedulingService service = (ISchedulingService) jobDataMap.get(ISchedulingService.SCHEDULING_SERVICE);
        IScheduledJob job = null;
        try {
            job = (IScheduledJob) jobDataMap.get(ISchedulingService.SCHEDULED_JOB);
            if (job != null) {
                job.execute(service);
            }
        } catch (Throwable e) {
            if (job != null) {
                log.warn("Job {} execution failed", job.toString(), e);
            }
        } finally {
            // remove the job
            if (autoRemove) {
                service.removeScheduledJob(jobName);
                // clear the map
                jobDataMap.clear();
            }

        }
    }

}
