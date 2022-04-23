/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.scheduling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.jmx.mxbeans.JDKSchedulingServiceMXBean;
import org.slf4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * Scheduling service that uses JDK ScheduledExecutor as backend.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
@ManagedResource(objectName = "org.red5.server:name=schedulingService,type=JDKSchedulingService")
public class JDKSchedulingService implements ISchedulingService, JDKSchedulingServiceMXBean, InitializingBean, DisposableBean {

    private static Logger log = Red5LoggerFactory.getLogger(JDKSchedulingService.class);

    /**
     * Service scheduler
     */
    protected ScheduledExecutorService scheduler;

    protected int threadCount = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * Storage for job futures keyed by name
     */
    protected ConcurrentMap<String, ScheduledFuture<?>> keyMap = new ConcurrentHashMap<>();

    protected AtomicInteger jobDetailCounter = new AtomicInteger();

    private boolean interruptOnRemove = true;

    /** Constructs a new QuartzSchedulingService. */
    public void afterPropertiesSet() throws Exception {
        log.debug("Initializing...");
        scheduler = Executors.newScheduledThreadPool(threadCount);
    }

    /**
     * @return the threadCount
     */
    public int getThreadCount() {
        return threadCount;
    }

    /**
     * @param threadCount
     *            the threadCount to set
     */
    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    /** {@inheritDoc} */
    public String addScheduledJob(int interval, IScheduledJob job) {
        String name = getJobName();
        // Store reference to applications job and service
        Map<String, Object> jobData = new HashMap<>();
        jobData.put(ISchedulingService.SCHEDULING_SERVICE, this);
        jobData.put(ISchedulingService.SCHEDULED_JOB, job);
        // runnable task
        JDKSchedulingServiceJob schedJob = new JDKSchedulingServiceJob();
        schedJob.setJobDataMap(jobData);
        // schedule it to run at interval
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(schedJob, interval, interval, TimeUnit.MILLISECONDS);
        // add to the key map
        keyMap.put(name, future);
        return name;
    }

    /** {@inheritDoc} */
    public String addScheduledOnceJob(Date date, IScheduledJob job) {
        String name = getJobName();
        // Store reference to applications job and service
        Map<String, Object> jobData = new HashMap<>();
        jobData.put(ISchedulingService.SCHEDULING_SERVICE, this);
        jobData.put(ISchedulingService.SCHEDULED_JOB, job);
        // runnable task
        JDKSchedulingServiceJob schedJob = new JDKSchedulingServiceJob();
        schedJob.setJobDataMap(jobData);
        // calculate the delay
        long delay = date.getTime() - System.currentTimeMillis();
        // schedule it to run once after the specified delay
        ScheduledFuture<?> future = scheduler.schedule(schedJob, delay, TimeUnit.MILLISECONDS);
        // add to the key map
        keyMap.put(name, future);
        return name;
    }

    /** {@inheritDoc} */
    public String addScheduledOnceJob(long timeDelta, IScheduledJob job) {
        // Create trigger that fires once in <timeDelta> milliseconds
        String name = getJobName();
        // Store reference to applications job and service
        Map<String, Object> jobData = new HashMap<>();
        jobData.put(ISchedulingService.SCHEDULING_SERVICE, this);
        jobData.put(ISchedulingService.SCHEDULED_JOB, job);
        // runnable task
        JDKSchedulingServiceJob schedJob = new JDKSchedulingServiceJob();
        schedJob.setJobDataMap(jobData);
        // schedule it to run once after the specified delay
        ScheduledFuture<?> future = scheduler.schedule(schedJob, timeDelta, TimeUnit.MILLISECONDS);
        // add to the key map
        keyMap.put(name, future);
        return name;
    }

    /** {@inheritDoc} */
    public String addScheduledJobAfterDelay(int interval, IScheduledJob job, int delay) {
        String name = getJobName();
        // Store reference to applications job and service
        Map<String, Object> jobData = new HashMap<>();
        jobData.put(ISchedulingService.SCHEDULING_SERVICE, this);
        jobData.put(ISchedulingService.SCHEDULED_JOB, job);
        // runnable task
        JDKSchedulingServiceJob schedJob = new JDKSchedulingServiceJob();
        schedJob.setJobDataMap(jobData);
        // schedule it to run at interval
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(schedJob, delay, interval, TimeUnit.MILLISECONDS);
        // add to the key map
        keyMap.put(name, future);
        return name;
    }

    /**
     * Getter for job name.
     *
     * @return Job name
     */
    public String getJobName() {
        return String.format("ScheduledJob_%d", jobDetailCounter.getAndIncrement());
    }

    /** {@inheritDoc} */
    public List<String> getScheduledJobNames() {
        if (scheduler != null) {
            return new ArrayList<>(keyMap.keySet());
        } else {
            log.warn("No scheduler is available");
        }
        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    public void pauseScheduledJob(String name) {
        throw new RuntimeException("Pause is not supported for ScheduledFuture");
    }

    /** {@inheritDoc} */
    public void resumeScheduledJob(String name) {
        throw new RuntimeException("Pause/resume is not supported for ScheduledFuture");
    }

    /** {@inheritDoc} */
    public void removeScheduledJob(String name) {
        try {
            ScheduledFuture<?> future = keyMap.remove(name);
            if (future != null) {
                future.cancel(interruptOnRemove);
            } else {
                log.debug("No key found for job: {}", name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void destroy() throws Exception {
        if (scheduler != null) {
            log.debug("Destroying...");
            scheduler.shutdownNow();
        }
        keyMap.clear();
    }

    public boolean isInterruptOnRemove() {
        return interruptOnRemove;
    }

    public void setInterruptOnRemove(boolean interruptOnRemove) {
        this.interruptOnRemove = interruptOnRemove;
    }

}
