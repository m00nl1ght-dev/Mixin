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
package org.spongepowered.asm.mixin;

import dev.m00nl1ght.clockwork.utils.logger.Logger;
import dev.m00nl1ght.clockwork.utils.profiler.AbstractProfilable;
import dev.m00nl1ght.clockwork.utils.profiler.ProfilerEntry;
import dev.m00nl1ght.clockwork.utils.profiler.ProfilerGroup;
import dev.m00nl1ght.clockwork.utils.profiler.impl.FactoryProfilerGroup;
import dev.m00nl1ght.clockwork.utils.profiler.impl.SimpleProfilerGroup;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.extensibility.IEnvironmentTokenProvider;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.selectors.TargetSelector;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.obfuscation.RemapperChain;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.util.ITokenProvider;
import org.spongepowered.asm.util.JavaVersion;
import org.spongepowered.asm.util.LanguageFeatures;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.asm.util.asm.ASM;

import java.util.*;

/**
 * The mixin environment manages global state information for the mixin
 * subsystem.
 */
public final class MixinEnvironment extends AbstractProfilable<SimpleProfilerGroup, ProfilerSection> implements ITokenProvider {

    public static final String VERSION = "0.8.3";

    public static enum Option {
        
        /**
         * Enable all debugging options
         */
        DEBUG_ALL("debug"),
        
        /**
         * Enable post-mixin class export. This causes all classes to be written
         * to the .mixin.out directory within the runtime directory
         * <em>after</em> mixins are applied, for debugging purposes. 
         */
        DEBUG_EXPORT(Option.DEBUG_ALL, "export"),
        
        /**
         * Export filter, if omitted allows all transformed classes to be
         * exported. If specified, acts as a filter for class names to export
         * and only matching classes will be exported. This is useful when using
         * Fernflower as exporting can be otherwise very slow. The following
         * wildcards are allowed:
         * 
         * <dl>
         *   <dt>*</dt><dd>Matches one or more characters except dot (.)</dd>
         *   <dt>**</dt><dd>Matches any number of characters</dd>
         *   <dt>?</dt><dd>Matches exactly one character</dd>
         * </dl>
         */
        DEBUG_EXPORT_FILTER(Option.DEBUG_EXPORT, "filter", false),
        
        /**
         * Allow fernflower to be disabled even if it is found on the classpath
         */
        DEBUG_EXPORT_DECOMPILE(Option.DEBUG_EXPORT, Inherit.ALLOW_OVERRIDE, "decompile"),
        
        /**
         * Run fernflower in a separate thread. In general this will allow
         * export to impact startup time much less (decompiling normally adds
         * about 20% to load times) with the trade-off that crashes may lead to
         * undecompiled exports.
         */
        DEBUG_EXPORT_DECOMPILE_THREADED(Option.DEBUG_EXPORT_DECOMPILE, Inherit.ALLOW_OVERRIDE, "async"),
        
        /**
         * By default, if the runtime export decompiler is active, mixin generic
         * signatures are merged into target classes. However this can cause
         * problems with some runtime subsystems which attempt to reify generics
         * using the signature data. Set this option to <tt>false</tt> to
         * disable generic signature merging. 
         */
        DEBUG_EXPORT_DECOMPILE_MERGESIGNATURES(Option.DEBUG_EXPORT_DECOMPILE, Inherit.ALLOW_OVERRIDE, "mergeGenericSignatures"),
        
        /**
         * Run the CheckClassAdapter on all classes after mixins are applied,
         * also enables stricter checks on mixins for use at dev-time, promotes
         * some warning-level messages to exceptions 
         */
        DEBUG_VERIFY(Option.DEBUG_ALL, "verify"),
        
        /**
         * Enable verbose mixin logging (elevates all DEBUG level messages to
         * INFO level) 
         */
        DEBUG_VERBOSE(Option.DEBUG_ALL, "verbose"),
        
        /**
         * Elevates failed injections to an error condition, see
         * {@link Inject#expect} for details
         */
        DEBUG_INJECTORS(Option.DEBUG_ALL, "countInjections"),
        
        /**
         * Enable strict checks
         */
        DEBUG_STRICT(Option.DEBUG_ALL, Inherit.INDEPENDENT, "strict"),
        
        /**
         * If false (default), {@link Unique} public methods merely raise a
         * warning when encountered and are not merged into the target. If true,
         * an exception is thrown instead
         */
        DEBUG_UNIQUE(Option.DEBUG_STRICT, "unique"),
        
        /**
         * Enable strict checking for mixin targets
         */
        DEBUG_TARGETS(Option.DEBUG_STRICT, "targets"),
        
        /**
         * Enable the performance profiler for all mixin operations (normally it
         * is only enabled during mixin prepare operations)
         */
        DEBUG_PROFILER(Option.DEBUG_ALL, Inherit.ALLOW_OVERRIDE, "profiler"),

        /**
         * Dumps the bytecode for the target class to disk when mixin
         * application fails
         */
        DUMP_TARGET_ON_FAILURE("dumpTargetOnFailure"),
        
        /**
         * Enable all checks 
         */
        CHECK_ALL("checks"),
        
        /**
         * Checks that all declared interface methods are implemented on a class
         * after mixin application.
         */
        CHECK_IMPLEMENTS(Option.CHECK_ALL, "interfaces"),
        
        /**
         * If interface check is enabled, "strict mode" (default) applies the
         * implementation check even to abstract target classes. Setting this
         * option to <tt>false</tt> causes abstract targets to be skipped when
         * generating the implementation report.
         */
        CHECK_IMPLEMENTS_STRICT(Option.CHECK_IMPLEMENTS, Inherit.ALLOW_OVERRIDE, "strict"),
        
        /**
         * Ignore all constraints on mixin annotations, output warnings instead
         */
        IGNORE_CONSTRAINTS("ignoreConstraints"),

        /**
         * Enables the hot-swap agent
         */
        HOT_SWAP("hotSwap"),
        
        /**
         * Parent for environment settings
         */
        ENVIRONMENT(Inherit.ALWAYS_FALSE, "env"),
        
        /**
         * Force refmap obf type when required 
         */
        OBFUSCATION_TYPE(Option.ENVIRONMENT, Inherit.ALWAYS_FALSE, "obf"),
        
        /**
         * Disable refmap when required 
         */
        DISABLE_REFMAP(Option.ENVIRONMENT, Inherit.INDEPENDENT, "disableRefMap"),
        
        /**
         * Rather than disabling the refMap, you may wish to remap existing
         * refMaps at runtime. This can be achieved by setting this property and
         * supplying values for <tt>mixin.env.refMapRemappingFile</tt> and
         * <tt>mixin.env.refMapRemappingEnv</tt>. Though those properties can be
         * ignored if starting via <tt>GradleStart</tt> (this property is also
         * automatically enabled if loading via GradleStart). 
         */
        REFMAP_REMAP(Option.ENVIRONMENT, Inherit.INDEPENDENT, "remapRefMap"),
        
        /**
         * If <tt>mixin.env.remapRefMap</tt> is enabled, this setting can be
         * used to override the name of the SRG file to read mappings from. The
         * mappings must have a source type of <tt>searge</tt> and a target type
         * matching the current development environment. If the source type is
         * not <tt>searge</tt> then the <tt>mixin.env.refMapRemappingEnv</tt>
         * should be set to the correct source environment type.
         */
        REFMAP_REMAP_RESOURCE(Option.ENVIRONMENT, Inherit.INDEPENDENT, "refMapRemappingFile", ""),
        
        /**
         * When using <tt>mixin.env.refMapRemappingFile</tt>, this setting
         * overrides the default source environment (searge). However note that
         * the specified environment type must exist in the orignal refmap.
         */
        REFMAP_REMAP_SOURCE_ENV(Option.ENVIRONMENT, Inherit.INDEPENDENT, "refMapRemappingEnv", "searge"),
        
        /**
         * When <tt>mixin.env.remapRefMap</tt> is enabled and a refmap is
         * available for a mixin config, certain injection points are allowed to
         * fail over to a "permissive" match which ignores the member descriptor
         * in the refmap. To disable this behaviour, set this property to
         * <tt>false</tt>.
         */
        REFMAP_REMAP_ALLOW_PERMISSIVE(Option.ENVIRONMENT, Inherit.INDEPENDENT, "allowPermissiveMatch", true, "true"),
        
        /**
         * Globally ignore the "required" attribute of all configurations
         */
        IGNORE_REQUIRED(Option.ENVIRONMENT, Inherit.INDEPENDENT, "ignoreRequired"),

        /**
         * Default compatibility level to operate at
         */
        DEFAULT_COMPATIBILITY_LEVEL(Option.ENVIRONMENT, Inherit.INDEPENDENT, "compatLevel"),
        
        /**
         * Behaviour when the maximum defined {@link At#by} value is exceeded in
         * a mixin. Currently the behaviour is to <tt>warn</tt>. In later
         * versions of Mixin this may be promoted to <tt>error</tt>.
         * 
         * <p>Available values for this option are:</p>
         * 
         * <dl>
         *   <dt>ignore</dt>
         *   <dd>Pre-0.7 behaviour, no action is taken when a violation is
         *     encountered</dd>
         *   <dt>warn</dt>
         *   <dd>Current behaviour, a <tt>WARN</tt>-level message is raised for
         *     violations</dd>
         *   <dt>error</dt>
         *   <dd>Violations throw an exception</dd>
         * </dl>
         */
        SHIFT_BY_VIOLATION_BEHAVIOUR(Option.ENVIRONMENT, Inherit.INDEPENDENT, "shiftByViolation", "warn"),
        
        /**
         * Behaviour for initialiser injections, current supported options are
         * "default" and "safe"
         */
        INITIALISER_INJECTION_MODE("initialiserInjectionMode", "default");

        /**
         * Type of inheritance for options
         */
        private enum Inherit {
            
            /**
             * If the parent is set, this option will be set too.
             */
            INHERIT,
            
            /**
             * If the parent is set, this option will be set too. However
             * setting the option explicitly to <tt>false</tt> will override the
             * parent value. 
             */
            ALLOW_OVERRIDE,
            
            /**
             * This option ignores the value of the parent option, parent is
             * only used for grouping. 
             */
            INDEPENDENT,
            
            /**
             * This option is always <tt>false</tt>.
             */
            ALWAYS_FALSE
            
        }

        /**
         * Prefix for mixin options
         */
        private static final String PREFIX = "mixin";
        
        /**
         * Parent option to this option, if non-null then this option is enabled
         * if 
         */
        final Option parent;
        
        /**
         * Inheritance behaviour for this option 
         */
        final Inherit inheritance;

        /**
         * Java property name
         */
        final String property;
        
        /**
         * Default value for string properties
         */
        final String defaultValue;
        
        /**
         * Whether this property is boolean or not
         */
        final boolean isFlag;
        
        /**
         * Number of parents 
         */
        final int depth;

        private Option(String property) {
            this(null, property, true);
        }
        
        private Option(Inherit inheritance, String property) {
            this(null, inheritance, property, true);
        }
        
        private Option(String property, boolean flag) {
            this(null, property, flag);
        }

        private Option(String property, String defaultStringValue) {
            this(null, Inherit.INDEPENDENT, property, false, defaultStringValue);
        }
        
        private Option(Option parent, String property) {
            this(parent, Inherit.INHERIT, property, true);
        }
        
        private Option(Option parent, Inherit inheritance, String property) {
            this(parent, inheritance, property, true);
        }
        
        private Option(Option parent, String property, boolean isFlag) {
            this(parent, Inherit.INHERIT, property, isFlag, null);
        }
        
        private Option(Option parent, Inherit inheritance, String property, boolean isFlag) {
            this(parent, inheritance, property, isFlag, null);
        }
        
        private Option(Option parent, String property, String defaultStringValue) {
            this(parent, Inherit.INHERIT, property, false, defaultStringValue);
        }
        
        private Option(Option parent, Inherit inheritance, String property, String defaultStringValue) {
            this(parent, inheritance, property, false, defaultStringValue);
        }
        
        private Option(Option parent, Inherit inheritance, String property, boolean isFlag, String defaultStringValue) {
            this.parent = parent;
            this.inheritance = inheritance;
            this.property = (parent != null ? parent.property : Option.PREFIX) + "." + property;
            this.defaultValue = defaultStringValue;
            this.isFlag = isFlag;
            int depth = 0;
            for (; parent != null; depth++) {
                parent = parent.parent;
            }
            this.depth = depth;
        }
        
        Option getParent() {
            return this.parent;
        }
        
        String getProperty() {
            return this.property;
        }
        
        @Override
        public String toString() {
            return this.isFlag ? String.valueOf(this.getBooleanValue()) : this.getStringValue();
        }
        
        private boolean getLocalBooleanValue(boolean defaultValue) {
            return Boolean.parseBoolean(System.getProperty(this.property, Boolean.toString(defaultValue)));
        }
        
        private boolean getInheritedBooleanValue() {
            return this.parent != null && this.parent.getBooleanValue();
        }
        
        final boolean getBooleanValue() {
            if (this.inheritance == Inherit.ALWAYS_FALSE) {
                return false;
            }
            
            boolean local = this.getLocalBooleanValue(false);
            if (this.inheritance == Inherit.INDEPENDENT) {
                return local;
            }

            boolean inherited = local || this.getInheritedBooleanValue();
            return this.inheritance == Inherit.INHERIT ? inherited : this.getLocalBooleanValue(inherited);
        }

        final String getStringValue() {
            return (this.inheritance == Inherit.INDEPENDENT || this.parent == null || this.parent.getBooleanValue())
                    ? System.getProperty(this.property, this.defaultValue) : this.defaultValue;
        }

        @SuppressWarnings("unchecked")
        <E extends Enum<E>> E getEnumValue(E defaultValue) {
            String value = System.getProperty(this.property, defaultValue.name());
            try {
                return (E)Enum.valueOf(defaultValue.getClass(), value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return defaultValue;
            }
        }
    }
    
    /**
     * Operational compatibility level for the mixin subsystem
     */
    public static enum CompatibilityLevel {
        
        /**
         * Java 6 (1.6) or above is required
         */
        JAVA_6(6, Opcodes.V1_6, 0),
        
        /**
         * Java 7 (1.7) or above is required
         */
        JAVA_7(7, Opcodes.V1_7, 0) {

            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_7;
            }
            
        },
        
        /**
         * Java 8 (1.8) or above is required
         */
        JAVA_8(8, Opcodes.V1_8, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES) {

            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_8;
            }
            
        },
        
        /**
         * Java 9 or above is required
         */
        JAVA_9(9, Opcodes.V9, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES
                | LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES) {
            
            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_9 && ASM.isAtLeastVersion(6);
            }
            
        },
        
        /**
         * Java 10 or above is required
         */
        JAVA_10(10, Opcodes.V10, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES
                | LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES) {
            
            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_10 && ASM.isAtLeastVersion(6, 1);
            }
            
        },
        
        /**
         * Java 11 or above is required
         */
        JAVA_11(11, Opcodes.V11, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES
                | LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES | LanguageFeatures.NESTING | LanguageFeatures.DYNAMIC_CONSTANTS) {
            
            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_11 && ASM.isAtLeastVersion(7);
            }
            
        },
        
        /**
         * Java 12 or above is required
         */
        JAVA_12(12, Opcodes.V12, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES
                | LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES | LanguageFeatures.NESTING | LanguageFeatures.DYNAMIC_CONSTANTS) {
            
            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_12 && ASM.isAtLeastVersion(7);
            }
            
        },
        
        /**
         * Java 13 or above is required
         */
        JAVA_13(13, Opcodes.V13, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES
                | LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES | LanguageFeatures.NESTING | LanguageFeatures.DYNAMIC_CONSTANTS) {
            
            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_13 && ASM.isAtLeastVersion(7);
            }
            
        },
        
        /**
         * Java 14 or above is required. Records are a preview feature in this
         * release.
         */
        JAVA_14(14, Opcodes.V14, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES
                | LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES | LanguageFeatures.NESTING | LanguageFeatures.DYNAMIC_CONSTANTS
                | LanguageFeatures.RECORDS) {
            
            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_14 && ASM.isAtLeastVersion(8);
            }
            
        },
        
        /**
         * Java 15 or above is required. Records and sealed classes are preview
         * features in this release.
         */
        JAVA_15(15, Opcodes.V15, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES
                | LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES | LanguageFeatures.NESTING | LanguageFeatures.DYNAMIC_CONSTANTS
                | LanguageFeatures.RECORDS | LanguageFeatures.SEALED_CLASSES) {
            
            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_15 && ASM.isAtLeastVersion(9);
            }
            
        },
        
        /**
         * Java 16 or above is required
         */
        JAVA_16(16, Opcodes.V16, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES
                | LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES | LanguageFeatures.NESTING | LanguageFeatures.DYNAMIC_CONSTANTS
                | LanguageFeatures.RECORDS | LanguageFeatures.SEALED_CLASSES) {
            
            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_16 && ASM.isAtLeastVersion(9);
            }
            
        },
        
        /**
         * Java 17 or above is required
         */
        JAVA_17(17, Opcodes.V17, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES
                | LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES | LanguageFeatures.NESTING | LanguageFeatures.DYNAMIC_CONSTANTS
                | LanguageFeatures.RECORDS | LanguageFeatures.SEALED_CLASSES) {
            
            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_17 && ASM.isAtLeastVersion(9, 1);
            }
            
        },
        
        /**
         * Java 18 or above is required
         */
        JAVA_18(18, Opcodes.V18, LanguageFeatures.METHODS_IN_INTERFACES | LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES
                | LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES | LanguageFeatures.NESTING | LanguageFeatures.DYNAMIC_CONSTANTS
                | LanguageFeatures.RECORDS | LanguageFeatures.SEALED_CLASSES) {
            
            @Override
            boolean isSupported() {
                return JavaVersion.current() >= JavaVersion.JAVA_18 && ASM.isAtLeastVersion(9, 2);
            }
            
        };
        
        /**
         * Default compatibility level to use if not specified by the service 
         */
        public static CompatibilityLevel DEFAULT = CompatibilityLevel.JAVA_6;
        
        /**
         * Maximum compatibility level actually supported. Other compatibility
         * levels might exist but we don't actually have any internal code in
         * place which supports those features. This is mainly used to indicate
         * that mixin classes compiled with newer JDKs might have bytecode-level
         * class features that this version of mixin doesn't understand, even
         * when the current ASM or JRE do.
         * 
         * <p>This is particularly important for the case where a config
         * declares a higher version (eg. JAVA_14) which has been added to the 
         * enum but no code actually exists within Mixin as a library to handle
         * language features from that version. In other words adding values to
         * this enum doesn't magically add support for language features, and
         * this field should point to the highest <em>known <b>supported</b>
         * </em> version regardless of other <em>known</em> versions.</p>
         * 
         * <p>This comment mainly added to avoid stuff in the future like
         * PR #500 which demonstrates that the nature of compatibility levels
         * in mixin are not understood that well.</p>
         */
        public static CompatibilityLevel MAX_SUPPORTED = CompatibilityLevel.JAVA_13;
        
        private final int ver;
        
        private final int classVersion;
        
        private final int languageFeatures;
        
        private CompatibilityLevel maxCompatibleLevel;
        
        private CompatibilityLevel(int ver, int classVersion, int languageFeatures) {
            this.ver = ver;
            this.classVersion = classVersion;
            this.languageFeatures = languageFeatures;
        }
        
        /**
         * Get whether this compatibility level is supported in the current
         * environment
         */
        boolean isSupported() {
            return true;
        }
        
        /**
         * Class version expected at this compatibility level
         * 
         * @deprecated Use getClassVersion
         */
        @Deprecated
        public int classVersion() {
            return this.classVersion;
        }
        
        /**
         * Class version expected at this compatibility level
         */
        public int getClassVersion() {
            return this.classVersion;
        }
        
        /**
         * Get the major class version expected at this compatibility level
         */
        public int getClassMajorVersion() {
            return this.classVersion & 0xFFFF;
        }
        
        /**
         * Get all supported language features
         */
        public int getLanguageFeatures() {
            return this.languageFeatures;
        }

        /**
         * Get whether this environment supports non-abstract methods in
         * interfaces, true in Java 1.8 and above
         * 
         * @deprecated Use {@link #supports(int)} instead
         */
        @Deprecated
        public boolean supportsMethodsInInterfaces() {
            return (this.languageFeatures & LanguageFeatures.METHODS_IN_INTERFACES) != 0;
        }
        
        /**
         * Get whether the specified {@link LanguageFeatures} is supported by
         * this runtime.
         * 
         * @param languageFeatures language feature (or features) to check
         * @return true if all specified language features are supported
         */
        public boolean supports(int languageFeatures) {
            return (this.languageFeatures & languageFeatures) == languageFeatures;
        }
        
        /**
         * Get whether this level is the same or greater than the specified
         * level
         * 
         * @param level level to compare to
         * @return true if this level is equal or higher the supplied level
         */
        public boolean isAtLeast(CompatibilityLevel level) {
            return level == null || this.ver >= level.ver; 
        }
        
        /**
         * Get whether this level is less than the specified level
         * 
         * @param level level to compare to
         * @return true if this level is less than the supplied level
         */
        public boolean isLessThan(CompatibilityLevel level) {
            return level == null || this.ver < level.ver; 
        }
        
        /**
         * Get whether this level can be elevated to the specified level
         * 
         * @param level desired level
         * @return true if this level supports elevation
         */
        public boolean canElevateTo(CompatibilityLevel level) {
            if (level == null || this.maxCompatibleLevel == null) {
                return true;
            }
            return level.ver <= this.maxCompatibleLevel.ver;
        }
        
        /**
         * True if this level can support the specified level
         * 
         * @param level desired level
         * @return true if the other level can be elevated to this level
         */
        public boolean canSupport(CompatibilityLevel level) {
            if (level == null) {
                return true;
            }
            
            return level.canElevateTo(this);
        }
        
        /**
         * Return the minimum language level required to support the specified
         * language feature(s). Returns <tt>null</tt> if no compatibility level
         * available can support the requested language features.
         * 
         * @param languageFeatures Language feature(s) to check for
         * @return Lowest compatibility level which supports the requested
         *      language feature, or null if no levels support the requested
         *      feature 
         */
        public static CompatibilityLevel requiredFor(int languageFeatures) {
            for (CompatibilityLevel level : CompatibilityLevel.values()) {
                if (level.supports(languageFeatures)) {
                    return level;
                }
            }
            return null;
        }

        static String getSupportedVersions() {
            StringBuilder sb = new StringBuilder();
            boolean comma = false;
            int rangeStart = 0, rangeEnd = 0;
            for (CompatibilityLevel level : CompatibilityLevel.values()) {
                if (level.isSupported()) {
                    if (level.ver == rangeEnd + 1) {
                        rangeEnd = level.ver;
                    } else {
                        if (rangeStart > 0) {
                            sb.append(comma ? "," : "").append(rangeStart);
                            if (rangeEnd > rangeStart) {
                                sb.append(rangeEnd > rangeStart + 1 ? '-' : ',').append(rangeEnd);
                            }
                            comma = true;
                            rangeStart = rangeEnd = level.ver;
                        }
                        rangeStart = rangeEnd = level.ver;
                    }
                }
            }
            if (rangeStart > 0) {
                sb.append(comma ? "," : "").append(rangeStart);
                if (rangeEnd > rangeStart) {
                    sb.append(rangeEnd > rangeStart + 1 ? '-' : ',').append(rangeEnd);
                }
            }
            return sb.toString();
        }
        
    }

    /**
     * Wrapper for providing a natural sorting order for providers
     */
    static class TokenProviderWrapper implements Comparable<TokenProviderWrapper> {
        
        private static int nextOrder = 0;
        
        private final int priority, order;
        
        private final IEnvironmentTokenProvider provider;

        private final MixinEnvironment environment;
        
        public TokenProviderWrapper(IEnvironmentTokenProvider provider, MixinEnvironment environment) {
            this.provider = provider;
            this.environment = environment;
            this.order = TokenProviderWrapper.nextOrder++;
            this.priority = provider.getPriority();
        }

        @Override
        public int compareTo(TokenProviderWrapper other) {
            if (other == null) {
                return 0;
            }
            if (other.priority == this.priority) {
                return other.order - this.order;
            }
            return (other.priority - this.priority);
        }
        
        public IEnvironmentTokenProvider getProvider() {
            return this.provider;
        }
        
        Integer getToken(String token) {
            return this.provider.getToken(token, this.environment);
        }

    }

    private CompatibilityLevel compatibility;

    private final IMixinService service;

    private final boolean[] options;

    private final Set<String> tokenProviderClasses = new HashSet<String>();

    private final List<TokenProviderWrapper> tokenProviders = new ArrayList<TokenProviderWrapper>();

    private final Map<String, Integer> internalTokens = new HashMap<String, Integer>();

    private final RemapperChain remappers = new RemapperChain();

    private final List<IMixinConfig> configs = new ArrayList<>();

    private final Set<String> errorHandlers = new LinkedHashSet<String>();

    private final ClassInfo.Cache classInfoCache = new ClassInfo.Cache(this);

    private final InjectionPoint.Registry injectionPointRegistry;

    private final InjectionInfo.Registry injectionInfoRegistry;

    private final TargetSelector.Registry targetSelectorRegistry;
    
    public MixinEnvironment(IMixinService service) {
        super(ProfilerSection.class);
        this.service = Objects.requireNonNull(service);
        this.injectionPointRegistry = new InjectionPoint.Registry(service);
        this.injectionInfoRegistry = new InjectionInfo.Registry(service);
        this.targetSelectorRegistry = new TargetSelector.Registry(service);
        
        this.options = new boolean[Option.values().length];
        for (Option option : Option.values()) {
            this.options[option.ordinal()] = option.getBooleanValue();
        }

        if (getOption(Option.DEBUG_PROFILER)) {
            this.attachDefaultProfiler();
        }
    }

    public void printHeader() {
        String version = getVersion();
        String codeSource = this.getCodeSource();
        String serviceName = this.service.getName();
        service.getLogger().info("SpongePowered MIXIN Subsystem Version={} Source={} Service={}", version, codeSource, serviceName);
        
        boolean verbose = this.getOption(Option.DEBUG_VERBOSE);
        if (verbose || this.getOption(Option.DEBUG_EXPORT) || this.getOption(Option.DEBUG_PROFILER)) {
            PrettyPrinter printer = new PrettyPrinter(32);
            printer.add("SpongePowered MIXIN%s", verbose ? " (Verbose debugging enabled)" : "").centre().hr();
            printer.kv("Code source", codeSource);
            printer.kv("Internal Version", version);
            printer.kv("Java Version", "%s (supports compatibility %s)", JavaVersion.current(), CompatibilityLevel.getSupportedVersions());
            printer.kv("Default Compatibility Level", getCompatibilityLevel());
            printer.kv("Detected ASM Version", ASM.getVersionString());
            printer.kv("Detected ASM Supports Java", ASM.getClassVersionString()).hr();
            printer.kv("Service Name", serviceName);
            printer.kv("Mixin Service Class", this.service.getClass().getName());
            printer.kv("Logger Type", service.getLogger().getClass().getSimpleName()).hr();
            for (Option option : Option.values()) {
                StringBuilder indent = new StringBuilder();
                for (int i = 0; i < option.depth; i++) {
                    indent.append("- ");
                }
                printer.kv(option.property, "%s<%s>", indent, option);
            }
            printer.print(System.err);
        }
    }

    private String getCodeSource() {
        try {
            return this.getClass().getProtectionDomain().getCodeSource().getLocation().toString();
        } catch (Throwable th) {
            return "Unknown";
        }
    }

    /**
     * Get logging level info/debug based on verbose setting
     */
    private Logger.Level getVerboseLoggingLevel() {
        return this.getOption(Option.DEBUG_VERBOSE) ? Logger.Level.INFO : Logger.Level.DEBUG;
    }

    public List<IMixinConfig> getMixinConfigs() {
        return List.copyOf(configs);
    }

    public void registerConfig(IMixinConfig config) {
        if (!configs.contains(Objects.requireNonNull(config))) {
            configs.add(config);
        }
    }

    /**
     * Add a new token provider class to this environment
     * 
     * @param providerName Class name of the token provider to add
     * @return fluent interface
     */
    public MixinEnvironment registerTokenProviderClass(String providerName) {
        if (!this.tokenProviderClasses.contains(providerName)) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends IEnvironmentTokenProvider> providerClass =
                        (Class<? extends IEnvironmentTokenProvider>)this.service.getClassProvider().findClass(providerName, true);
                IEnvironmentTokenProvider provider = providerClass.getDeclaredConstructor().newInstance();
                this.registerTokenProvider(provider);
            } catch (Throwable th) {
                service.getLogger().error("Error instantiating " + providerName, th);
            }
        }
        return this;
    }

    /**
     * Add a new token provider to this environment
     * 
     * @param provider Token provider to add
     * @return fluent interface
     */
    public MixinEnvironment registerTokenProvider(IEnvironmentTokenProvider provider) {
        if (provider != null && !this.tokenProviderClasses.contains(provider.getClass().getName())) {
            String providerName = provider.getClass().getName();
            TokenProviderWrapper wrapper = new TokenProviderWrapper(provider, this);
            service.getLogger().log(this.getVerboseLoggingLevel(), "Adding new token provider {} to {}", providerName, this);
            this.tokenProviders.add(wrapper);
            this.tokenProviderClasses.add(providerName);
            Collections.sort(this.tokenProviders);
        }
        
        return this;
    }
    
    /**
     * Get a token value from this environment
     * 
     * @param token Token to fetch
     * @return token value or null if the token is not present in the
     *      environment
     */
    @Override
    public Integer getToken(String token) {
        token = token.toUpperCase(Locale.ROOT);
        
        for (TokenProviderWrapper provider : this.tokenProviders) {
            Integer value = provider.getToken(token);
            if (value != null) {
                return value;
            }
        }
        
        return this.internalTokens.get(token);
    }
    
    /**
     * Get the current mixin subsystem version
     * 
     * @return current version
     */
    public String getVersion() {
        return VERSION;
    }

    /**
     * Get the specified option from the current environment
     * 
     * @param option Option to get
     * @return Option value
     */
    public boolean getOption(Option option) {
        return this.options[option.ordinal()];
    }
    
    /**
     * Set the specified option for this environment
     * 
     * @param option Option to set
     * @param value New option value
     */
    public void setOption(Option option, boolean value) {
        this.options[option.ordinal()] = value;
    }

    /**
     * Get the specified option from the current environment
     * 
     * @param option Option to get
     * @return Option value
     */
    public String getOptionValue(Option option) {
        return option.getStringValue();
    }
    
    /**
     * Get the specified option from the current environment
     * 
     * @param option Option to get
     * @param defaultValue value to use if the user-defined value is invalid
     * @param <E> enum type
     * @return Option value
     */
    public <E extends Enum<E>> E getOption(Option option, E defaultValue) {
        return option.getEnumValue(defaultValue);
    }

    public RemapperChain getRemappers() {
        return this.remappers;
    }

    @Override
    public String toString() {
        return String.format("%s", this.getClass().getSimpleName());
    }

    public CompatibilityLevel getCompatibilityLevel() {
        if (compatibility == null) {
            CompatibilityLevel minLevel = getMinCompatibilityLevel();
            CompatibilityLevel optionLevel = Option.DEFAULT_COMPATIBILITY_LEVEL.<CompatibilityLevel>getEnumValue(minLevel);
            compatibility = optionLevel.isAtLeast(minLevel) ? optionLevel : minLevel;
        }
        return compatibility;
    }
    
    /**
     * Get the minimum (default) compatibility level supported by the current
     * service
     */
    public CompatibilityLevel getMinCompatibilityLevel() {
        CompatibilityLevel minLevel = service.getMinCompatibilityLevel();
        return minLevel == null ? CompatibilityLevel.DEFAULT : minLevel;
    }
    
    /**
     * Set desired compatibility level for the entire environment
     * 
     * @param level Level to set, ignored if less than the current level
     * @throws IllegalArgumentException if the specified level is not supported
     */
    public void setCompatibilityLevel(CompatibilityLevel level) throws IllegalArgumentException {
        CompatibilityLevel currentLevel = getCompatibilityLevel();
        if (level != currentLevel && level.isAtLeast(currentLevel)) {
            if (!level.isSupported()) {
                throw new IllegalArgumentException(String.format(
                    "The requested compatibility level %s could not be set. Level is not supported by the active JRE or ASM version (Java %s, %s)",
                    level, JavaVersion.current(), ASM.getVersionString()
                ));
            }

            CompatibilityLevel maxLevel = service.getMaxCompatibilityLevel();
            if (maxLevel != null && maxLevel.isLessThan(level)) {
                service.getLogger().warn("The requested compatibility level {} is higher than the level supported by the active subsystem '{}'"
                        + " which supports {}. This is not a supported configuration and instability may occur.", level, service.getName(), maxLevel);
            }
            
            compatibility = level;
            service.getLogger().info("Compatibility level set to {}", level);
        }
    }

    public IMixinService getService() {
        return service;
    }

    public Logger getLogger() {
        return service.getLogger();
    }

    public void registerErrorHandlerClass(String handlerClassName) {
        if (handlerClassName != null) {
            errorHandlers.add(handlerClassName);
        }
    }

    public Set<String> getErrorHandlerClasses() {
        return Collections.<String>unmodifiableSet(errorHandlers);
    }

    public ClassInfo.Cache getClassInfoCache() {
        return classInfoCache;
    }

    public InjectionPoint.Registry getInjectionPointRegistry() {
        return injectionPointRegistry;
    }

    public InjectionInfo.Registry getInjectionInfoRegistry() {
        return injectionInfoRegistry;
    }

    public TargetSelector.Registry getTargetSelectorRegistry() {
        return targetSelectorRegistry;
    }

    @Override
    public SimpleProfilerGroup attachDefaultProfiler() {
        final var profiler = new FactoryProfilerGroup("mixin");
        this.attachProfiler(profiler);
        return profiler;
    }

    @Override
    protected ProfilerEntry findProfilerEntry(SimpleProfilerGroup profiler, ProfilerSection entry) {
        final var split = entry.name().toLowerCase(Locale.ROOT).split("_");
        ProfilerGroup group = profiler;
        for (int i = 0; i < split.length - 1; i++) group = group.getSubgroup(split[i]);
        return group.getEntry(split[split.length - 1]);
    }

}
