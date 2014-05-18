package org.kitteh.craftirc.endpoint.filter;

import org.kitteh.craftirc.CraftIRC;
import org.kitteh.craftirc.endpoint.Endpoint;
import org.kitteh.craftirc.endpoint.TargetedMessage;
import org.kitteh.craftirc.exceptions.CraftIRCInvalidConfigException;
import org.kitteh.craftirc.util.loadable.Loadable;

import java.util.Map;

/**
 * This is a filter.
 */
public abstract class Filter extends Loadable {
    private Endpoint endpoint;
    private Endpoint.EndpointFilterLoader loader;

    /**
     * Gets the Endpoint using this Filter instance.
     *
     * @return the Endpoint in use
     */
    protected Endpoint getEndpoint() {
        return this.endpoint;
    }

    Endpoint.EndpointFilterLoader getLoader() {
        return this.loader;
    }

    /**
     * Processes an incoming message. Should be capable of handling calls
     * from multiple threads at once.
     *
     * @param message message to process
     */
    public abstract void processMessage(TargetedMessage message);

    @Override
    protected final void load(CraftIRC plugin, Map<Object, Object> data) throws CraftIRCInvalidConfigException {
        if (data.containsKey(FilterRegistry.Target.EndpointLoader)) {
            this.loader = (Endpoint.EndpointFilterLoader) data.get(FilterRegistry.Target.EndpointLoader);
            this.endpoint = this.loader.getEndpoint();
        }
        this.load(data);
    }

    /**
     * Loads this filter's data.
     *
     * @param data information to load
     * @throws CraftIRCInvalidConfigException
     */
    protected abstract void load(Map<Object, Object> data) throws CraftIRCInvalidConfigException;
}