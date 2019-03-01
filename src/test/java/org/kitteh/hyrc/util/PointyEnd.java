package org.kitteh.hyrc.util;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.kitteh.hyrc.endpoint.TargetedMessage;
import org.kitteh.hyrc.endpoint.link.Link;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Stick them with it.
 */
public class PointyEnd extends Link {
    private static final Constructor<LinkFilterLoader> LOADER_CONSTRUCTOR;
    private static final Method RECEIVE_MESSAGE;

    static {
        try {
            LOADER_CONSTRUCTOR = LinkFilterLoader.class.getDeclaredConstructor(Link.class);
            LOADER_CONSTRUCTOR.setAccessible(true);
            RECEIVE_MESSAGE = Link.class.getDeclaredMethod("filterMessage", TargetedMessage.class);
            RECEIVE_MESSAGE.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private LinkFilterLoader loader;

    public PointyEnd() {
        super(null, "", "", null);
    }

    public final @NonNull LinkFilterLoader getLoader() {
        if (this.loader != null) {
            return loader;
        }
        try {
            return this.loader = LOADER_CONSTRUCTOR.newInstance(this);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public void message(@Nullable TargetedMessage message) {
        try {
            RECEIVE_MESSAGE.invoke(this, message);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
