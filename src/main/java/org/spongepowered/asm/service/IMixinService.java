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

import java.io.InputStream;
import java.util.Collection;

import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.util.ReEntranceLock;

/**
 * Mixin Service interface. Mixin services connect the mixin subsytem to the
 * underlying environment. It is something of a god interface at present because
 * it contains all of the current functionality accessors for calling into
 * launchwrapper. In the future once support for modlauncher is added, it is
 * anticipated that the interface can be split down into sub-services which
 * handle different aspects of interacting with the environment.
 */
public interface IMixinService {
    
    /**
     * Get the friendly name for this service
     */
    public abstract String getName();

    /**
     * True if this service type is valid in the current environment
     */
    public abstract boolean isValid();
    
    /**
     * Get the transformer re-entrance lock for this service, the transformer
     * uses this lock to track transformer re-entrance when co-operative load
     * and transform is performed by the service.
     */
    public abstract ReEntranceLock getReEntranceLock();
    
    /**
     * Return the class provider for this service. <b>This component is required
     * and services must not return <tt>null</tt>.</b>
     */
    public abstract IClassProvider getClassProvider();
    
    /**
     * Return the class bytecode provider for this service. <b>This component is
     * required and services must not return <tt>null</tt>.</b>
     */
    public abstract IClassBytecodeProvider getBytecodeProvider();
    
    /**
     * Return the transformer provider for this service. <b>This component is
     * optional and is allowed to be <tt>null</tt> for services which do not
     * support transformers, or don't support interacting with them.</b>
     */
    public abstract ITransformerProvider getTransformerProvider();
    
    /**
     * Return the class tracker for this service. <b>This component is optional
     * and is allowed to be <tt>null</tt> for services which do not support this
     * functionality.</b>
     */
    public abstract IClassTracker getClassTracker();
    
    /**
     * Return the audit trail for this service. <b>This component is optional
     * and is allowed to be <tt>null</tt> for services which do not support this
     * functionality.</b>
     */
    public abstract IMixinAuditTrail getAuditTrail();
    
    /**
     * Get a resource as a stream from the appropriate classloader, this is
     * delegated via the service so that the service can choose the correct
     * classloader from which to obtain the resource.
     * 
     * @param name resource path
     * @return input stream or null if resource not found
     */
    public abstract InputStream getResourceAsStream(String name);

    /**
     * Get the detected side name for this environment
     */
    public abstract String getSideName();
    
    /**
     * Get the minimum compatibility level supported by this service. Can return
     * <tt>null</tt> if the service has no specific minimum compatibility level,
     * however if a value is returned, it will be used as the minimum
     * compatibility level and no lower levels will be supported. 
     * 
     * @return minimum supported {@link CompatibilityLevel} or null
     */
    public abstract CompatibilityLevel getMinCompatibilityLevel();
    
    /**
     * Get the maximum compatibility level supported by this service. Can return
     * <tt>null</tt> if the service has no specific maximum compatibility level.
     * If a value is returned, a warning will be raised if a configuration
     * attempts to se a higher compatibility level.
     * 
     * @return minimum supported {@link CompatibilityLevel} or null
     */
    public abstract CompatibilityLevel getMaxCompatibilityLevel();

}
