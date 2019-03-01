/*
 * * Copyright (C) 2014-2019 Matt Baxter http://kitteh.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.kitteh.hyrc;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.kitteh.hyrc.endpoint.EndpointManager;
import org.kitteh.hyrc.endpoint.filter.FilterManager;
import org.kitteh.hyrc.endpoint.link.LinkManager;
import org.kitteh.hyrc.exceptions.HyRCInvalidConfigException;
import org.kitteh.hyrc.exceptions.HyRCUnableToStartException;
import org.kitteh.hyrc.exceptions.HyRCWillLeakTearsException;
import org.kitteh.hyrc.irc.BotManager;
import org.kitteh.hyrc.util.Logger;
import org.kitteh.hyrc.util.shutdownable.Shutdownable;

import org.checkerframework.checker.nullness.qual.NonNull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * HyRC's core. Be sure to call {@link #shutdown()} when finished to
 * ensure that all running operations complete and clean up threads.
 */
public final class HyRC {
    private static Logger logger;

    public static @NonNull Logger log() {
        if (HyRC.logger == null) {
            throw new HyRCWillLeakTearsException();
        }
        return HyRC.logger;
    }

    private BotManager botManager;
    private EndpointManager endpointManager;
    private FilterManager filterManager;
    private LinkManager linkManager;
    private final Set<Shutdownable> shutdownables = new CopyOnWriteArraySet<>();

    public @NonNull BotManager getBotManager() {
        return this.botManager;
    }

    public @NonNull EndpointManager getEndpointManager() {
        return this.endpointManager;
    }

    public @NonNull FilterManager getFilterManager() {
        return this.filterManager;
    }

    public @NonNull LinkManager getLinkManager() {
        return this.linkManager;
    }

    /**
     * Starts tracking a feature which can be shut down.
     *
     * @param shutdownable feature to track
     */
    public void trackShutdownable(@NonNull Shutdownable shutdownable) {
        this.shutdownables.add(shutdownable);
    }

    /**
     * Starts up HyRC.
     * <p/>
     * The {@link Logger} provided to HyRC will be utilized for a child
     * logger which will prefix all messages with "[HyRC] ".
     *
     * @param logger a logger for HyRC to use
     * @param dataFolder the folder in which config.yml is located
     * @throws HyRCUnableToStartException if startup fails
     */
    public HyRC(@NonNull Logger logger, @NonNull File dataFolder) throws HyRCUnableToStartException {
        try {
            HyRC.logger = logger;

            File configFile = new File(dataFolder, "config.yml");
            if (!configFile.exists()) {
                log().info("No config.yml found, creating a default configuration.");
                this.saveDefaultConfig(dataFolder);
            }

            YAMLConfigurationLoader yamlConfigurationLoader = YAMLConfigurationLoader.builder().setPath(configFile.toPath()).build();
            ConfigurationNode root = yamlConfigurationLoader.load();

            if (root.isVirtual()) {
                throw new HyRCInvalidConfigException("Config doesn't appear valid. Would advise starting from scratch.");
            }

            ConfigurationNode repeatableFilters = root.getNode("repeatable-filters");

            ConfigurationNode botsNode = root.getNode("bots");
            List<? extends ConfigurationNode> bots;
            if (botsNode.isVirtual() || (bots = botsNode.getChildrenList()).isEmpty()) {
                throw new HyRCInvalidConfigException("No bots defined!");
            }

            ConfigurationNode endpointsNode = root.getNode("endpoints");
            List<? extends ConfigurationNode> endpoints;
            if (endpointsNode.isVirtual() || (endpoints = endpointsNode.getChildrenList()).isEmpty()) {
                throw new HyRCInvalidConfigException("No endpoints defined! Would advise starting from scratch.");
            }

            ConfigurationNode linksNode = root.getNode("links");
            List<? extends ConfigurationNode> links;
            if (linksNode.isVirtual() || (links = linksNode.getChildrenList()).isEmpty()) {
                throw new HyRCInvalidConfigException("No links defined! How can your endpoints be useful?");
            }

            this.filterManager = new FilterManager(this, repeatableFilters);
            this.botManager = new BotManager(this, bots);
            this.endpointManager = new EndpointManager(this, endpoints);
            this.linkManager = new LinkManager(this, links);
        } catch (Exception e) {
            throw new HyRCUnableToStartException("Could not start HyRC!", e);
        }
    }

    /**
     * Shuts down any running threads and finishes any other running
     * operations. This method calls {@link Shutdownable#shutdown()} on all
     * {@link Shutdownable} registered to
     * {@link #trackShutdownable(Shutdownable)}.
     */
    public void shutdown() {
        this.shutdownables.forEach(Shutdownable::shutdown);
        // And lastly...
        HyRC.logger = null;
    }

    private void saveDefaultConfig(@NonNull File dataFolder) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        try {
            URL url = this.getClass().getClassLoader().getResource("config.yml");
            if (url == null) {
                log().warning("Could not find a default config to copy!");
                return;
            }

            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            InputStream input = connection.getInputStream();

            File outFile = new File(dataFolder, "config.yml");
            OutputStream output = new FileOutputStream(outFile);

            byte[] buffer = new byte[1024];
            int lengthRead;
            while ((lengthRead = input.read(buffer)) > 0) {
                output.write(buffer, 0, lengthRead);
            }

            output.close();
            input.close();
        } catch (IOException ex) {
            log().severe("Exception while saving default config", ex);
        }
    }
}
