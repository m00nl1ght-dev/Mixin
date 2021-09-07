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
package org.spongepowered.asm.mixin.transformer;

import java.util.*;
import java.util.stream.Collectors;

import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.mixin.MixinEnvironment.Option;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelectorDynamic;
import org.spongepowered.asm.mixin.injection.selectors.TargetSelector;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.refmap.ReferenceMapper;
import org.spongepowered.asm.util.VersionNumber;

import com.google.common.base.Strings;

public final class MixinConfig implements IMixinConfig {

    public MixinConfig(String name) {
        this.name = name;
    }

    public static class InjectorOptions {

        int defaultRequire = 0;

        String defaultGroup = "default";

        String namespace;

        List<String> injectionPoints;

        List<String> dynamicSelectors;

        int maxShiftBy = InjectionPoint.DEFAULT_ALLOWED_SHIFT_BY;
        
    }

    public static class OverwriteOptions {

        boolean conformAccessModifiers;

        boolean requireOverwriteAnnotations;
        
    }

    private final ILogger logger = MixinService.getService().getLogger("mixin");

    /**
     * Minimum version of the mixin subsystem required to correctly apply mixins
     * in this configuration. 
     */
    private String minVersion;

    private CompatibilityLevel compatibilityLevel = CompatibilityLevel.DEFAULT;
    
    /**
     * Determines whether failures in this mixin config are considered terminal
     * errors. Use this setting to indicate that failing to apply a mixin in
     * this config is a critical error and should cause the game to shutdown.
     * Uses boxed boolean so that absent entries can be detected and assigned
     * via parent config where specified.
     */
    private boolean required;

    private int priority = IMixinConfig.DEFAULT_PRIORITY;
    
    /**
     * Default mixin priority. By default, mixins get a priority of 
     * {@link IMixinConfig#DEFAULT_PRIORITY DEFAULT_PRIORITY} unless a different
     * value is specified in the annotation. This setting allows the base 
     * priority for all mixins in this config to be set to an alternate value.
     */
    private int mixinPriority = IMixinConfig.DEFAULT_PRIORITY;

    /**
     * Package containing all mixins. This package will be monitored by the
     * transformer so that we can explode if some dummy tries to reference a
     * mixin class directly.
     */
    private String mixinPackage;
    
    /**
     * Mixin classes to load, mixinPackage will be prepended
     */
    private List<String> mixinClasses;
    
    /**
     * True to set the sourceFile property when applying mixins
     */
    private boolean setSourceFile = false;
    
    /**
     * True to output "mixing in" messages at INFO level rather than DEBUG 
     */
    private boolean verboseLogging;

    private InjectorOptions injectorOptions = new InjectorOptions();

    private OverwriteOptions overwriteOptions = new OverwriteOptions();

    private final transient String name;

    private transient IMixinConfigPlugin plugin;

    private transient IReferenceMapper refMapper = ReferenceMapper.DEFAULT_MAPPER;

    private final transient List<IListener> listeners = new ArrayList<IListener>();
    
    /**
     * Only emit the compatibility level warning for any increase in the class
     * version, track warned level here 
     */
    private transient int warnedClassVersion = 0;
    
    private transient Map<String, Object> decorations;

    public boolean init(MixinEnvironment environment) {
        this.verboseLogging |= environment.getOption(Option.DEBUG_VERBOSE);
        this.required = this.required && !environment.getOption(Option.IGNORE_REQUIRED);

        this.initCompatibilityLevel(environment);
        this.initExtensions(environment);

        if (this.plugin != null) this.plugin.onLoad(Strings.nullToEmpty(this.mixinPackage));
        if (!Strings.isNullOrEmpty(this.mixinPackage) && !this.mixinPackage.endsWith(".")) {
            this.mixinPackage += ".";
        }

        return this.checkVersion(environment);
    }

    private void initCompatibilityLevel(MixinEnvironment environment) {
        CompatibilityLevel currentLevel = environment.getCompatibilityLevel();
        if (this.compatibilityLevel == currentLevel) {
            return;
        }
        
        // Current level is higher than required but too new to support it
        if (currentLevel.isAtLeast(this.compatibilityLevel) && !currentLevel.canSupport(this.compatibilityLevel)) {
            throw new MixinInitialisationError(String.format("Mixin config %s requires compatibility level %s which is too old",
                    this.name, this.compatibilityLevel));
        }
        
        // Current level is lower than required but current level prohibits elevation
        if (!currentLevel.canElevateTo(this.compatibilityLevel)) {
            throw new MixinInitialisationError(String.format("Mixin config %s requires compatibility level %s which is prohibited by %s",
                    this.name, this.compatibilityLevel, currentLevel));
        }

        CompatibilityLevel minCompatibilityLevel = MixinEnvironment.getMinCompatibilityLevel();
        if (this.compatibilityLevel.isLessThan(minCompatibilityLevel)) {
            this.logger.log(this.verboseLogging ? Level.INFO : Level.DEBUG,
                    "Compatibility level {} specified by {} is lower than the default level supported by the current mixin service ({}).",
                    this.compatibilityLevel, this, minCompatibilityLevel);
        }

        // Required level is higher than highest version we support, this possibly
        // means that a shaded mixin dependency has been usurped by an old version,
        // or the mixin author is trying to elevate the compatibility level beyond
        // the versions currently supported
        if (CompatibilityLevel.MAX_SUPPORTED.isLessThan(this.compatibilityLevel)) {
            this.logger.log(this.verboseLogging ? Level.WARN : Level.DEBUG,
                    "Compatibility level {} specified by {} is higher than the maximum level supported by this version of mixin ({}).",
                    this.compatibilityLevel, this, CompatibilityLevel.MAX_SUPPORTED);
        }
    }

    /**
     * Called by MixinTargetContext when class version is elevated, allows us to
     * warn devs (or end-users with verbose turned on, for whatever reason) that
     * the current compatibility level is too low for the classes being
     * processed. The warning is only emitted at WARN for each new class version
     * and at DEBUG thereafter.
     * 
     * <p>The logic here is that we only really care about supported class
     * features, but a version of mixin which doesn't actually support newer
     * features may well be able to operate with classes *compiled* with a newer
     * JDK, but we don't actually know that for sure.
     */
    @Override
    public void checkCompatibilityLevel(MixinInfo mixin, int majorVersion, int minorVersion) {
        if (majorVersion <= this.compatibilityLevel.getClassMajorVersion()) {
            return;
        }
        
        Level logLevel = this.verboseLogging && majorVersion > this.warnedClassVersion ? Level.WARN : Level.DEBUG;
        String message = majorVersion > CompatibilityLevel.MAX_SUPPORTED.getClassMajorVersion()
                ? "the current version of Mixin" : "the declared compatibility level";
        this.warnedClassVersion = majorVersion;
        this.logger.log(logLevel, "{}: Class version {} required is higher than the class version supported by {} ({} supports class version {})",
                mixin, majorVersion, message, this.compatibilityLevel, this.compatibilityLevel.getClassMajorVersion());
    }
    
    private void initExtensions(MixinEnvironment environment) {
        if (this.injectorOptions.injectionPoints != null) {
            for (String injectionPointClassName : this.injectorOptions.injectionPoints) {
                this.initInjectionPoint(environment, injectionPointClassName, this.injectorOptions.namespace);
            }
        }
        
        if (this.injectorOptions.dynamicSelectors != null) {
            for (String dynamicSelectorClassName : this.injectorOptions.dynamicSelectors) {
                this.initDynamicSelector(environment, dynamicSelectorClassName, this.injectorOptions.namespace);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void initInjectionPoint(MixinEnvironment environment, String className, String namespace) {
        try {
            Class<?> injectionPointClass = this.findExtensionClass(environment, className, InjectionPoint.class, "injection point");
            if (injectionPointClass != null) {
                try {
                    injectionPointClass.getMethod("find", String.class, InsnList.class, Collection.class);
                } catch (NoSuchMethodException cnfe) {
                    this.logger.error("Unable to register injection point {} for {}, the class is not compatible with this version of Mixin",
                            className, this, cnfe);
                    return;
                }
    
                environment.getInjectionPointRegistry().register((Class<? extends InjectionPoint>)injectionPointClass, namespace);
            }
        } catch (Throwable th) {
            this.logger.catching(th);
        }
    }

    @SuppressWarnings("unchecked")
    private void initDynamicSelector(MixinEnvironment environment, String className, String namespace) {
        try {
            Class<?> dynamicSelectorClass = this.findExtensionClass(environment, className, ITargetSelectorDynamic.class, "dynamic selector");
            if (dynamicSelectorClass != null) {
                environment.getTargetSelectorRegistry().register((Class<? extends ITargetSelectorDynamic>)dynamicSelectorClass, namespace);
            }
        } catch (Throwable th) {
            this.logger.catching(th);
        }
    }
    
    private Class<?> findExtensionClass(MixinEnvironment environment, String className, Class<?> superType, String extensionType) {
        Class<?> extensionClass = null;
        try {
            extensionClass = environment.getService().getClassProvider().findClass(className, true);
        } catch (ClassNotFoundException cnfe) {
            this.logger.error("Unable to register {} {} for {}, the specified class was not found", extensionType, className, this, cnfe);
            return null;
        }
        
        if (!superType.isAssignableFrom(extensionClass)) {
            this.logger.error("Unable to register {} {} for {}, class is not assignable to {}", extensionType, className, this, superType);
            return null;
        }
        return extensionClass;
    }

    private boolean checkVersion(MixinEnvironment environment) throws MixinInitialisationError {
        if (this.minVersion == null) {
            this.logger.error("Mixin config {} does not specify \"minVersion\" property", this.name);
        }
        
        VersionNumber minVersion = VersionNumber.parse(this.minVersion);
        VersionNumber curVersion = VersionNumber.parse(environment.getVersion());
        if (minVersion.compareTo(curVersion) > 0) {
            this.logger.warn("Mixin config {} requires mixin subsystem version {} but {} was found. The mixin config will not be applied.",
                    this.name, minVersion, curVersion);
            
            if (this.required) {
                throw new MixinInitialisationError("Required mixin config " + this.name + " requires mixin subsystem version " + minVersion);
            }
            
            return false;
        }
        
        return true;
    }

    @Override
    public void addListener(IListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public List<IListener> getListeners() {
        return listeners;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(String minVersion) {
        this.minVersion = minVersion;
    }

    @Override
    public boolean isRequired() {
        return this.required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public String getName() {
        return this.name;
    }

    /**
     * Get the package containing all mixin classes
     */
    @Override
    public String getMixinPackage() {
        return Strings.nullToEmpty(this.mixinPackage);
    }

    public void setMixinPackage(String mixinPackage) {
        this.mixinPackage = mixinPackage;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Get the default priority for mixins in this config. Values specified in
     * the mixin annotation still override this value
     */
    @Override
    public int getDefaultMixinPriority() {
        return this.mixinPriority;
    }

    public void setDefaultMixinPriority(int mixinPriority) {
        this.mixinPriority = mixinPriority;
    }

    /**
     * Get the defined value for the {@link Inject#require} parameter on
     * injectors defined in mixins in this configuration.
     * 
     * @return default require value
     */
    @Override
    public int getDefaultRequiredInjections() {
        return this.injectorOptions.defaultRequire;
    }
    
    /**
     * Get the defined injector group for injectors
     * 
     * @return default group name
     */
    @Override
    public String getDefaultInjectorGroup() {
        String defaultGroup = this.injectorOptions.defaultGroup;
        return defaultGroup != null && !defaultGroup.isEmpty() ? defaultGroup : "default";
    }
    
    /**
     * Get whether visibility levelfor overwritten methods should be conformed
     * to the target class
     * 
     * @return true if conform is enabled
     */
    @Override
    public boolean conformOverwriteVisibility() {
        return this.overwriteOptions.conformAccessModifiers;
    }
    
    /**
     * Get whether {@link Overwrite} annotations are required to enable
     * overwrite behaviour for mixins in this config
     * 
     * @return true to require overwriting methods to be annotated
     */
    @Override
    public boolean requireOverwriteAnnotations() {
        return this.overwriteOptions.requireOverwriteAnnotations;
    }
    
    /**
     * Get the maximum allowed value of {@link At#by}. High values of shift can
     * indicate very brittle injectors and in general should be replaced with
     * slices. This value determines the warning/error threshold (behaviour
     * determined by the environment) for the value of <tt>by</tt>.
     * 
     * @return defined shift warning threshold for this config
     */
    @Override
    public int getMaxShiftByValue() {
        return Math.min(Math.max(this.injectorOptions.maxShiftBy, 0), InjectionPoint.MAX_ALLOWED_SHIFT_BY);
    }

    /**
     * Get the list of mixin classes we will be applying
     */
    @Override
    public List<String> getClasses() {
        return Strings.isNullOrEmpty(this.mixinPackage)
                ? Collections.<String>emptyList()
                : mixinClasses.stream()
                .map(c -> this.mixinPackage + c)
                .collect(Collectors.toUnmodifiableList());
    }

    public void setMixinClasses(List<String> mixinClasses) {
        this.mixinClasses = mixinClasses;
    }

    /**
     * Get whether to propogate the source file attribute from a mixin onto the
     * target class
     */
    @Override
    public boolean shouldSetSourceFile() {
        return this.setSourceFile;
    }

    public void setSetSourceFile(boolean setSourceFile) {
        this.setSourceFile = setSourceFile;
    }

    /**
     * Get the reference remapper for injectors
     */
    @Override
    public IReferenceMapper getReferenceMapper() {
        return this.refMapper;
    }

    public void setRefMapper(IReferenceMapper refMapper) {
        this.refMapper = refMapper;
    }

    @Override
    public IMixinConfigPlugin getPlugin() {
        return this.plugin;
    }

    public void setPlugin(IMixinConfigPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public <V> void decorate(String key, V value) {
        if (this.decorations == null) {
            this.decorations = new HashMap<String, Object>();
        }
        if (this.decorations.containsKey(key)) {
            throw new IllegalArgumentException(String.format("Decoration with key '%s' already exists on config %s", key, this));
        }
        this.decorations.put(key, value);
    }
    
    /**
     * Get whether this node is decorated with the specified key
     * 
     * @param key meta key
     * @return true if the specified decoration exists
     */
    @Override
    public boolean hasDecoration(String key) {
        return this.decorations != null && this.decorations.get(key) != null;
    }
    
    /**
     * Get the specified decoration
     * 
     * @param key meta key
     * @param <V> value type
     * @return decoration value or null if absent
     */
    @Override
    @SuppressWarnings("unchecked")
    public <V> V getDecoration(String key) {
        return (V) (this.decorations == null ? null : this.decorations.get(key));
    }

    public Level getLoggingLevel() {
        return this.verboseLogging ? Level.INFO : Level.DEBUG;
    }

    @Override
    public boolean isVerboseLogging() {
        return this.verboseLogging;
    }

    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    /**
     * Get whether this config's package matches the supplied class name
     * 
     * @param className Class name to check
     * @return True if the specified class name is in this config's mixin package
     */
    @Override
    public boolean packageMatch(String className) {
        return !Strings.isNullOrEmpty(this.mixinPackage) && className.startsWith(this.mixinPackage);
    }

}
