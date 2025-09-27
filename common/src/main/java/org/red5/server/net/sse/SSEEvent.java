/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.sse;

/**
 * Represents a Server-Sent Event with all supported fields.
 * This class provides a builder pattern for creating SSE events
 * that comply with the W3C Server-Sent Events specification.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SSEEvent {

    private String id;

    private String event;

    private String data;

    private Integer retry;

    /**
     * Default constructor.
     */
    public SSEEvent() {
    }

    /**
     * Constructor with data only.
     *
     * @param data the event data
     */
    public SSEEvent(String data) {
        this.data = data;
    }

    /**
     * Constructor with event type and data.
     *
     * @param event the event type
     * @param data the event data
     */
    public SSEEvent(String event, String data) {
        this.event = event;
        this.data = data;
    }

    /**
     * Full constructor.
     *
     * @param id the event ID
     * @param event the event type
     * @param data the event data
     * @param retry the retry timeout
     */
    public SSEEvent(String id, String event, String data, Integer retry) {
        this.id = id;
        this.event = event;
        this.data = data;
        this.retry = retry;
    }

    /**
     * Gets the event ID.
     *
     * @return the event ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the event ID.
     *
     * @param id the event ID
     * @return this instance for chaining
     */
    public SSEEvent setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Gets the event type.
     *
     * @return the event type
     */
    public String getEvent() {
        return event;
    }

    /**
     * Sets the event type.
     *
     * @param event the event type
     * @return this instance for chaining
     */
    public SSEEvent setEvent(String event) {
        this.event = event;
        return this;
    }

    /**
     * Gets the event data.
     *
     * @return the event data
     */
    public String getData() {
        return data;
    }

    /**
     * Sets the event data.
     *
     * @param data the event data
     * @return this instance for chaining
     */
    public SSEEvent setData(String data) {
        this.data = data;
        return this;
    }

    /**
     * Gets the retry timeout.
     *
     * @return the retry timeout in milliseconds
     */
    public Integer getRetry() {
        return retry;
    }

    /**
     * Sets the retry timeout.
     *
     * @param retry the retry timeout in milliseconds
     * @return this instance for chaining
     */
    public SSEEvent setRetry(Integer retry) {
        this.retry = retry;
        return this;
    }

    /**
     * Creates a new SSE event builder.
     *
     * @return new SSEEvent instance
     */
    public static SSEEvent builder() {
        return new SSEEvent();
    }

    /**
     * Creates a simple message event.
     *
     * @param message the message
     * @return new SSEEvent with the message
     */
    public static SSEEvent message(String message) {
        return new SSEEvent(message);
    }

    /**
     * Creates an event with type and data.
     *
     * @param event the event type
     * @param data the event data
     * @return new SSEEvent
     */
    public static SSEEvent of(String event, String data) {
        return new SSEEvent(event, data);
    }

    /**
     * Creates an event with ID, type and data.
     *
     * @param id the event ID
     * @param event the event type
     * @param data the event data
     * @return new SSEEvent
     */
    public static SSEEvent of(String id, String event, String data) {
        return new SSEEvent(id, event, data, null);
    }

    /**
     * Converts this event to SSE format string.
     *
     * @return SSE formatted string
     */
    public String toSSEFormat() {
        StringBuilder sb = new StringBuilder();

        if (id != null) {
            sb.append("id: ").append(id).append("\n");
        }

        if (event != null) {
            sb.append("event: ").append(event).append("\n");
        }

        if (retry != null) {
            sb.append("retry: ").append(retry).append("\n");
        }

        if (data != null) {
            // Handle multi-line data
            String[] lines = data.split("\n");
            for (String line : lines) {
                sb.append("data: ").append(line).append("\n");
            }
        }

        sb.append("\n"); // End of event
        return sb.toString();
    }

    @Override
    public String toString() {
        return "SSEEvent{" + "id='" + id + '\'' + ", event='" + event + '\'' + ", data='" + data + '\'' + ", retry=" + retry + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        SSEEvent sseEvent = (SSEEvent) o;

        if (id != null ? !id.equals(sseEvent.id) : sseEvent.id != null)
            return false;
        if (event != null ? !event.equals(sseEvent.event) : sseEvent.event != null)
            return false;
        if (data != null ? !data.equals(sseEvent.data) : sseEvent.data != null)
            return false;
        return retry != null ? retry.equals(sseEvent.retry) : sseEvent.retry == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (event != null ? event.hashCode() : 0);
        result = 31 * result + (data != null ? data.hashCode() : 0);
        result = 31 * result + (retry != null ? retry.hashCode() : 0);
        return result;
    }
}