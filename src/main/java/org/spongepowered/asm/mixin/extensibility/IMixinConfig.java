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
package org.spongepowered.asm.mixin.extensibility;

import org.apache.logging.log4j.Level;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.transformer.MixinInfo;

import java.util.List;

/**
 * Interface for loaded mixin configurations
 */
public interface IMixinConfig {

    /**
     * Default priority for mixin configs and mixins
     */
    public static final int DEFAULT_PRIORITY = 1000;

    public abstract String getName();

    /**
     * Get the package containing all mixin classes
     * 
     * @return the base package name for this config
     */
    public abstract String getMixinPackage();

    /**
     * Get the priority
     * 
     * @return the priority
     */
    public abstract int getPriority();

    /**
     * Get the companion plugin, if available
     * 
     * @return the companion plugin instance or null if no plugin
     */
    public abstract IMixinConfigPlugin getPlugin();

    /**
     * True if this mixin is <em>required</em> (failure to apply a defined mixin
     * is an <em>error</em> condition).
     * 
     * @return true if this config is marked as required
     */
    public abstract boolean isRequired();

    /**
     * Get the default priority for mixins in this config. Values specified in
     * the mixin annotation still override this value
     */
    public int getDefaultMixinPriority();

    /**
     * Get the defined value for the {@link Inject#require} parameter on
     * injectors defined in mixins in this configuration.
     *
     * @return default require value
     */
    public int getDefaultRequiredInjections();

    /**
     * Get the defined injector group for injectors
     *
     * @return default group name
     */
    public String getDefaultInjectorGroup();

    /**
     * Get whether visibility levelfor overwritten methods should be conformed
     * to the target class
     *
     * @return true if conform is enabled
     */
    public boolean conformOverwriteVisibility();

    /**
     * Get whether {@link Overwrite} annotations are required to enable
     * overwrite behaviour for mixins in this config
     *
     * @return true to require overwriting methods to be annotated
     */
    public boolean requireOverwriteAnnotations();

    /**
     * Get the maximum allowed value of {@link At#by}. High values of shift can
     * indicate very brittle injectors and in general should be replaced with
     * slices. This value determines the warning/error threshold (behaviour
     * determined by the environment) for the value of <tt>by</tt>.
     *
     * @return defined shift warning threshold for this config
     */
    public int getMaxShiftByValue();

    /**
     * Get the logging level for this config
     */
    public Level getLoggingLevel();

    /**
     * Get whether verbose logging is enabled
     */
    public boolean isVerboseLogging();

    /**
     * Get the reference remapper for injectors
     */
    public IReferenceMapper getReferenceMapper();

    boolean packageMatch(String name);

    List<String> getClasses();

    void checkCompatibilityLevel(MixinInfo mixin, int majorVersion, int minorVersion);

    boolean shouldSetSourceFile();

    /**
     * Callback listener for certain mixin init steps
     */
    interface IListener {

        /**
         * Called when a mixin has been successfully prepared
         *
         * @param mixin mixin which was prepared
         */
        public abstract void onPrepare(MixinInfo mixin);

        /**
         * Called when a mixin has completed post-initialisation
         *
         * @param mixin mixin which completed postinit
         */
        public abstract void onInit(MixinInfo mixin);

    }

    public void addListener(IListener listener);

    public List<IListener> getListeners();

}
