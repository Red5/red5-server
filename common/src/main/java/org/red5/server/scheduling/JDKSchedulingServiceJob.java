/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
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

    private Logger log = LoggerFactory.getLogger(JDKSchedulingServiceJob.class);

    /**
     * Job data map
     */
    private Map<String, Object> jobDataMap;

    public void setJobDataMap(Map<String, Object> jobDataMap) {
        log.debug("Set job data map: {}", jobDataMap);
        this.jobDataMap = jobDataMap;
    }

    public void run() {
        //log.debug("execute");
        IScheduledJob job = null;
        try {
            ISchedulingService service = (ISchedulingService) jobDataMap.get(ISchedulingService.SCHEDULING_SERVICE);
            job = (IScheduledJob) jobDataMap.get(ISchedulingService.SCHEDULED_JOB);
            job.execute(service);
        } catch (Throwable e) {
            if (job == null) {
                log.warn("Job not found");
            } else {
                log.warn("Job {} execution failed", job.toString(), e);
            }
        }
    }

}
