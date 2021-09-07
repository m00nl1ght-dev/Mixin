/*
 * This file is part of Mixin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.asm.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.util.Constants;
import org.spongepowered.asm.util.IConsumer;
import org.spongepowered.asm.util.ReEntranceLock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * Mixin Service base class
 */
public abstract class MixinServiceAbstract implements IMixinService {

    /**
     * Logger adapter, replacement for log4j2 logger as services should use
     * their own loggers now in order to avoid contamination
     */
    private static ILogger logger;

    /**
     * Cached logger adapters 
     */
    private static final Map<String, ILogger> loggers = new HashMap<String, ILogger>();

    /**
     * Transformer re-entrance lock, shared between the mixin transformer and
     * the metadata service
     */
    protected final ReEntranceLock lock = new ReEntranceLock(1);

    /**
     * Detected side name
     */
    private String sideName;
    
    protected MixinServiceAbstract() {
        if (MixinServiceAbstract.logger == null) {
            MixinServiceAbstract.logger = this.getLogger("mixin");
        }
    }
    
    /* (non-Javadoc)
     * @see org.spongepowered.asm.service.IMixinService
     *      #getMinCompatibilityLevel()
     */
    @Override
    public CompatibilityLevel getMinCompatibilityLevel() {
        return null;
    }
    
    /* (non-Javadoc)
     * @see org.spongepowered.asm.service.IMixinService
     *      #getMaxCompatibilityLevel()
     */
    @Override
    public CompatibilityLevel getMaxCompatibilityLevel() {
        return null;
    }

    /* (non-Javadoc)
     * @see org.spongepowered.asm.service.IMixinService#getReEntranceLock()
     */
    @Override
    public ReEntranceLock getReEntranceLock() {
        return this.lock;
    }

    /* (non-Javadoc)
     * @see org.spongepowered.asm.service.IMixinService#getSideName()
     */
    @Override
    public final String getSideName() {
        if (this.sideName != null) {
            return this.sideName;
        }
        
        return Constants.SIDE_UNKNOWN;
    }

    @Override
    public synchronized ILogger getLogger(final String name) {
        ILogger logger = MixinServiceAbstract.loggers.get(name);
        if (logger == null) {
            MixinServiceAbstract.loggers.put(name, logger = this.createLogger(name));
        }
        return logger;
    }

    protected ILogger createLogger(final String name) {
        return new LoggerAdapterDefault(name);
    }

}
