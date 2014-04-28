package org.kitteh.craftirc.endpoint;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Defines a message.
 * <p/>
 * Messages are immutable, created by their originating
 * {@link org.kitteh.craftirc.endpoint.Endpoint}.
 */
public final class Message {
    private final Map<String, Object> data;
    private final String defaultMessage;
    private final Endpoint source;

    /**
     * Creates a new message.
     *
     * @param source originator of this message
     * @param defaultMessage this default message
     * @param data all associated data
     */
    public Message(Endpoint source, String defaultMessage, Map<String, Object> data) {
        this.source = source;
        this.defaultMessage = defaultMessage;
        this.data = data;
    }

    /**
     * Gets the message's data.
     *
     * @return an immutable map representing the data
     */
    public Map<String, Object> getData() {
        return ImmutableMap.copyOf(this.data);
    }

    /**
     * Gets the default message as created by the source
     * {@link org.kitteh.craftirc.endpoint.Endpoint}.
     *
     * @return the default message
     */
    public String getDefaultMessage() {
        return this.defaultMessage;
    }

    /**
     * Gets the source {@link org.kitteh.craftirc.endpoint.Endpoint} of this
     * message.
     *
     * @return the source Endpoint
     */
    public Endpoint getSource() {
        return this.source;
    }
}