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

import dev.m00nl1ght.clockwork.utils.logger.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.util.Constants;
import org.spongepowered.asm.util.ReEntranceLock;

import java.util.HashMap;
import java.util.Map;

/**
 * Mixin Service base class
 */
public abstract class MixinServiceAbstract implements IMixinService {

    private final Logger mainLogger;

    private final Map<String, Logger> loggers = new HashMap<String, Logger>();

    /**
     * Transformer re-entrance lock, shared between the mixin transformer and
     * the metadata service
     */
    protected final ReEntranceLock lock = new ReEntranceLock(1);
    
    protected MixinServiceAbstract() {
        mainLogger = this.getLogger("mixin");
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

    @Override
    public synchronized Logger getLogger(final String name) {
        Logger logger = loggers.get(name);
        if (logger == null) {
            loggers.put(name, logger = this.createLogger(name));
        }
        return logger;
    }

    @Override
    public Logger getLogger() {
        return mainLogger;
    }

    protected abstract Logger createLogger(final String name);

}
