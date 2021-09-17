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

import com.google.common.base.Strings;
import dev.m00nl1ght.clockwork.utils.logger.Logger;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.MixinEnvironment.Option;
import org.spongepowered.asm.mixin.ProfilerSection;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler.ErrorAction;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelectorDynamic;
import org.spongepowered.asm.mixin.throwables.ClassAlreadyLoadedException;
import org.spongepowered.asm.mixin.throwables.MixinApplyError;
import org.spongepowered.asm.mixin.throwables.MixinException;
import org.spongepowered.asm.mixin.throwables.MixinPrepareError;
import org.spongepowered.asm.mixin.transformer.MixinInfo.Variant;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IHotSwap;
import org.spongepowered.asm.mixin.transformer.ext.extensions.ExtensionClassExporter;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;
import org.spongepowered.asm.mixin.transformer.throwables.IllegalClassLoadError;
import org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException;
import org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError;
import org.spongepowered.asm.mixin.transformer.throwables.ReEntrantTransformerError;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.asm.util.ReEntranceLock;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Heart of the Mixin pipeline 
 */
class MixinProcessor {

    /**
     * Phase during which an error occurred,
     * delegates to functionality in available handler
     */
    static enum ErrorPhase {
        /**
         * Error during initialisation of a MixinConfig
         */
        PREPARE {
            @Override
            ErrorAction onError(IMixinErrorHandler handler, String context, InvalidMixinException ex, IMixinInfo mixin, ErrorAction action) {
                try {
                    return handler.onPrepareError(mixin.getConfig(), ex, mixin, action);
                } catch (AbstractMethodError ame) {
                    // Catch if error handler is pre-0.5.4
                    return action;
                }
            }
            
            @Override
            protected String getContext(IMixinInfo mixin, String context) {
                return String.format("preparing %s in %s", mixin.getName(), context);
            }
        },
        /**
         * Error during application of a mixin to a target class
         */
        APPLY {
            @Override
            ErrorAction onError(IMixinErrorHandler handler, String context, InvalidMixinException ex, IMixinInfo mixin, ErrorAction action) {
                try {
                    return handler.onApplyError(context, ex, mixin, action);
                } catch (AbstractMethodError ame) {
                    // Catch if error handler is pre-0.5.4
                    return action;
                }
            }
            
            @Override
            protected String getContext(IMixinInfo mixin, String context) {
                return String.format("%s -> %s", mixin, context);
            }
        };
        
        /**
         * Human-readable name
         */
        private final String text;
        
        private ErrorPhase() {
            this.text = this.name().toLowerCase(Locale.ROOT);
        }
        
        abstract ErrorAction onError(IMixinErrorHandler handler, String context, InvalidMixinException ex, IMixinInfo mixin, ErrorAction action);

        protected abstract String getContext(IMixinInfo mixin, String context);

        public String getLogMessage(String context, InvalidMixinException ex, IMixinInfo mixin) {
            return String.format("Mixin %s failed %s: %s %s", this.text, this.getContext(mixin, context), ex.getClass().getName(), ex.getMessage());
        }

        public String getErrorMessage(IMixinInfo mixin, IMixinConfig config) {
            return String.format("Mixin [%s] in config [%s] FAILED during %s", mixin, config, this.name());
        }
        
    }

    static class ConfigWrapper implements Comparable<ConfigWrapper> {

        /**
         * Global order of mixin configs,
         * used to determine ordering between configs with equivalent priority
         */
        private static int configOrder = 0;

        /**
         * Intrinsic order (for sorting configurations with identical priority)
         */
        private final transient int order = configOrder++;

        /**
         * Map of mixin target classes to mixin infos
         */
        private final transient Map<String, List<MixinInfo>> mixinMapping = new HashMap<String, List<MixinInfo>>();

        /**
         * Mixins which have been parsed but not yet prepared
         */
        private final transient List<MixinInfo> pendingMixins = new ArrayList<MixinInfo>();

        /**
         * All mixins loaded by this config
         */
        private final transient List<MixinInfo> mixins = new ArrayList<MixinInfo>();

        /**
         * Keep track of initialisation state
         */
        private transient boolean prepared = false;

        private final MixinProcessor processor;

        private final IMixinConfig config;

        ConfigWrapper(MixinProcessor processor, IMixinConfig config) {
            this.processor = Objects.requireNonNull(processor);
            this.config = Objects.requireNonNull(config);
        }

        /**
         * Get mixins for the specified target class
         *
         * @param targetClass target class
         * @return mixins for the specified target
         */
        public List<MixinInfo> getMixinsFor(String targetClass) {
            return this.mixinsFor(targetClass);
        }

        private List<MixinInfo> mixinsFor(String targetClass) {
            List<MixinInfo> mixins = this.mixinMapping.get(targetClass);
            if (mixins == null) {
                mixins = new ArrayList<MixinInfo>();
                this.mixinMapping.put(targetClass, mixins);
            }
            return mixins;
        }

        /**
         * Check whether this configuration bundle has a mixin for the specified class
         *
         * @param targetClass target class
         * @return true if this bundle contains any mixins for the specified target
         */
        public boolean hasMixinsFor(String targetClass) {
            return this.mixinMapping.containsKey(targetClass);
        }

        public boolean hasPendingMixinsFor(String targetClass) {
            if (config.packageMatch(targetClass)) {
                return false;
            }
            for (MixinInfo pendingMixin : this.pendingMixins) {
                if (pendingMixin.hasDeclaredTarget(targetClass)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * <p>Initialisation routine. It's important that we call this routine as
         * late as possible. In general we want to call it on the first call to
         * transform() in the parent transformer. At the very least we want to be
         * called <em>after</em> all the transformers for the current environment
         * have been spawned, because we will run the mixin bytecode through the
         * transformer chain and naturally we want this to happen at a point when we
         * can be reasonably sure that all transfomers have loaded.</p>
         *
         * <p>For this reason we will invoke the initialisation on the first call to
         * either the <em>hasMixinsFor()</em> or <em>getMixinsFor()</em> methods.
         * </p>
         */
        public void prepare() {
            if (this.prepared) {
                return;
            }
            this.prepared = true;

            this.prepareMixins("mixins", this.config.getClasses(), false);
        }

        public void postInitialise() {
            if (this.getConfig().getPlugin() != null) {
                List<String> pluginMixins = this.getConfig().getPlugin().getMixins();
                this.prepareMixins("companion plugin", pluginMixins, true);
            }

            for (Iterator<MixinInfo> iter = this.mixins.iterator(); iter.hasNext();) {
                MixinInfo mixin = iter.next();
                try {
                    mixin.validate();
                    for (IMixinConfig.IListener listener : this.getConfig().getListeners()) {
                        listener.onInit(mixin);
                    }
                } catch (InvalidMixinException ex) {
                    processor.logger.error(ex.getMixin() + ": " + ex.getMessage(), ex);
                    this.removeMixin(mixin);
                    iter.remove();
                } catch (Exception ex) {
                    processor.logger.error(ex.getMessage(), ex);
                    this.removeMixin(mixin);
                    iter.remove();
                }
            }
        }

        private void removeMixin(MixinInfo remove) {
            for (List<MixinInfo> mixinsFor : this.mixinMapping.values()) {
                for (Iterator<MixinInfo> iter = mixinsFor.iterator(); iter.hasNext();) {
                    if (remove == iter.next()) {
                        iter.remove();
                    }
                }
            }
        }

        private void prepareMixins(String collectionName, List<String> mixinClasses, boolean ignorePlugin) {
            if (mixinClasses == null) {
                return;
            }

            if (Strings.isNullOrEmpty(this.config.getMixinPackage())) {
                if (mixinClasses.size() > 0) {
                    processor.logger.error("{} declares mixin classes in {} but does not specify a package, {} orphaned mixins will not be loaded: {}",
                            this, collectionName, mixinClasses.size(), mixinClasses);
                }
                return;
            }

            for (String mixinClass : mixinClasses) {
                if (mixinClass == null || processor.globalMixinList.contains(mixinClass)) {
                    continue;
                }

                MixinInfo mixin = null;

                try {
                    this.pendingMixins.add(mixin = new MixinInfo(processor.environment, config, mixinClass, this.config.getPlugin(), ignorePlugin, processor.extensions));
                    processor.globalMixinList.add(mixinClass);
                } catch (InvalidMixinException ex) {
                    if (this.config.isRequired()) {
                        throw ex;
                    }
                    processor.logger.error(ex.getMessage(), ex);
                } catch (Exception ex) {
                    if (this.config.isRequired()) {
                        throw new InvalidMixinException(mixin, "Error initialising mixin " + mixin + " - " + ex.getClass() + ": " + ex.getMessage(), ex);
                    }
                    processor.logger.error(ex.getMessage(), ex);
                }
            }

            for (MixinInfo mixin : this.pendingMixins) {
                try {
                    mixin.parseTargets();
                    if (mixin.getTargetClasses().size() > 0) {
                        for (String targetClass : mixin.getTargetClasses()) {
                            String targetClassName = targetClass.replace('/', '.');
                            this.mixinsFor(targetClassName).add(mixin);
                            processor.unhandledTargets.add(targetClassName);
                        }
                        for (IMixinConfig.IListener listener : this.config.getListeners()) {
                            listener.onPrepare(mixin);
                        }
                        this.mixins.add(mixin);
                    }
                } catch (InvalidMixinException ex) {
                    if (this.config.isRequired()) {
                        throw ex;
                    }
                    processor.logger.error(ex.getMessage(), ex);
                } catch (Exception ex) {
                    if (this.config.isRequired()) {
                        throw new InvalidMixinException(mixin, "Error initialising mixin " + mixin + " - " + ex.getClass() + ": " + ex.getMessage(), ex);
                    }
                    processor.logger.error(ex.getMessage(), ex);
                }
            }

            this.pendingMixins.clear();
        }

        /**
         * Updates a mixin with new bytecode
         *
         * @param mixinClass Name of the mixin class
         * @param classNode New class
         * @return List of classes that need to be updated
         */
        public List<String> reloadMixin(String mixinClass, ClassNode classNode) {
            for (Iterator<MixinInfo> iter = this.mixins.iterator(); iter.hasNext();) {
                MixinInfo mixin = iter.next();
                if (mixin.getClassName().equals(mixinClass)) {
                    mixin.reloadMixin(classNode);
                    return mixin.getTargetClasses();
                }
            }
            return Collections.<String>emptyList();
        }

        public IMixinConfig getConfig() {
            return config;
        }

        @Override
        public int compareTo(ConfigWrapper other) {
            if (other == null) return 0;
            if (other.config.getPriority() == this.config.getPriority()) {
                return this.order - other.order;
            } else {
                return this.config.getPriority() - other.config.getPriority();
            }
        }

        @Override
        public String toString() {
            return this.config.getName();
        }

    }

    private final Logger logger;
    
    private final IMixinService service;

    private final List<ConfigWrapper> configs = new ArrayList<ConfigWrapper>();

    private final List<ConfigWrapper> pendingConfigs = new ArrayList<ConfigWrapper>();

    /**
     * Global list of mixin classes, so we can skip any duplicates
     */
    private final Set<String> globalMixinList = new HashSet<String>();

    /**
     * Targets for which haven't been mixed yet
     */
    private final Set<String> unhandledTargets = new HashSet<String>();

    private final ReEntranceLock lock;
    
    /**
     * Session ID, used as a check when parsing {@link MixinMerged} annotations
     * to prevent them being applied at compile time by people trying to
     * circumvent mixin application
     */
    private final String sessionId = UUID.randomUUID().toString();

    private final Extensions extensions;

    private final IHotSwap hotSwapper;
    
    private final MixinCoprocessors coprocessors = new MixinCoprocessors();

    private final IMixinAuditTrail auditTrail;

    private final MixinEnvironment environment;

    private Logger.Level verboseLoggingLevel = Logger.Level.DEBUG;

    /**
     * Handling an error state, do not process further mixins
     */
    private boolean errorState = false;
    
    /**
     * Number of classes transformed in the current phase
     */
    private int transformedCount = 0;

    MixinProcessor(MixinEnvironment environment, Extensions extensions, IHotSwap hotSwapper, MixinCoprocessorNestHost nestHostCoprocessor) {
        this.service = environment.getService();
        this.logger = environment.getLogger();
        this.lock = this.service.getReEntranceLock();

        this.environment = environment;
        this.extensions = extensions;
        this.hotSwapper = hotSwapper;
        
        this.coprocessors.add(new MixinCoprocessorPassthrough(environment));
        this.coprocessors.add(new MixinCoprocessorSyntheticInner(environment));
        this.coprocessors.add(new MixinCoprocessorAccessor(environment, this.sessionId));
        this.coprocessors.add(nestHostCoprocessor);

        this.auditTrail = this.service.getAuditTrail();
    }

    /**
     * Force-load all classes targetted by mixins but not yet applied
     * 
     * @param environment current environment
     */
    public void audit(MixinEnvironment environment) {
        Logger auditLogger = service.getLogger("mixin.audit");

        for (String target : unhandledTargets) {
            try {
                auditLogger.info("Force-loading class {}", target);
                this.service.getClassProvider().findClass(target, true);
            } catch (ClassNotFoundException ex) {
                auditLogger.error("Could not force-load " + target, ex);
            }
        }

        for (String target : unhandledTargets) {
            ClassAlreadyLoadedException ex = new ClassAlreadyLoadedException(target + " was already classloaded");
            auditLogger.error("Could not force-load " + target, ex);
        }
    }

    synchronized boolean applyMixins(String name, ClassNode targetClassNode) {
        if (name == null || this.errorState) {
            return false;
        }
        
        boolean locked = this.lock.push().check();
        var mixinTimer = environment.profilerBegin();

        if (locked) {
            for (ConfigWrapper config : this.pendingConfigs) {
                if (config.hasPendingMixinsFor(name)) {
                    ReEntrantTransformerError error = new ReEntrantTransformerError("Re-entrance error.");
                    logger.warn("Re-entrance detected during prepare phase, this will cause serious problems.", error);
                    throw error;
                }
            }
        } else {
            try {
                this.setup();
            } catch (Exception ex) {
                this.lock.pop();
                throw new MixinException(ex);
            }
        }
        
        boolean transformed = false;
        
        try {
            MixinCoprocessor.ProcessResult result = this.coprocessors.process(environment, name, targetClassNode);
            transformed |= result.isTransformed();
            
            if (result.isPassthrough()) {
                for (MixinCoprocessor coprocessor : this.coprocessors) {
                    transformed |= coprocessor.postProcess(name, targetClassNode);
                }
                if (this.auditTrail != null) {
                    this.auditTrail.onPostProcess(name);
                }
                this.extensions.export(environment, name, false, targetClassNode);
                return transformed;
            }

            ConfigWrapper packageOwnedByConfig = null;
            
            for (ConfigWrapper config : this.configs) {
                if (config.getConfig().packageMatch(name)) {
                    int packageLen = packageOwnedByConfig != null ? packageOwnedByConfig.getConfig().getMixinPackage().length() : 0;
                    if (config.getConfig().getMixinPackage().length() > packageLen) {
                        packageOwnedByConfig = config;
                    }
                    continue;
                }
            }                

            if (packageOwnedByConfig != null) {
                // AMS - Temp passthrough for injection points and dynamic selectors. Moving to service in 0.9
                ClassInfo targetInfo = ClassInfo.fromClassNode(environment, targetClassNode);
                if (targetInfo.hasSuperClass(InjectionPoint.class) || targetInfo.hasSuperClass(ITargetSelectorDynamic.class)) {
                    return transformed;
                }
                
                throw new IllegalClassLoadError(this.getInvalidClassError(name, targetClassNode, packageOwnedByConfig));
            }

            SortedSet<MixinInfo> mixins = null;
            for (ConfigWrapper config : this.configs) {
                if (config.hasMixinsFor(name)) {
                    if (mixins == null) {
                        mixins = new TreeSet<MixinInfo>();
                    }
                    
                    // Get and sort mixins for the class
                    mixins.addAll(config.getMixinsFor(name));
                }
            }
            
            if (mixins != null) {
                // Re-entrance is "safe" as long as we don't need to apply any mixins, if there are mixins then we need to panic now
                if (locked) {
                    ReEntrantTransformerError error = new ReEntrantTransformerError("Re-entrance error.");
                    logger.warn("Re-entrance detected, this will cause serious problems.", error);
                    throw error;
                }

                if (this.hotSwapper != null) {
                    this.hotSwapper.registerTargetClass(name, targetClassNode);
                }

                try {
                    TargetClassContext context = new TargetClassContext(environment, this.extensions, this.sessionId, name, targetClassNode, mixins);
                    context.applyMixins();
                    
                    transformed |= this.coprocessors.postProcess(environment, name, targetClassNode);

                    if (context.isExported()) {
                        this.extensions.export(environment, context.getClassName(), context.isExportForced(), context.getClassNode());
                    }
                    
                    for (InvalidMixinException suppressed : context.getSuppressedExceptions()) {
                        this.handleMixinApplyError(context.getClassName(), suppressed, environment);
                    }

                    this.transformedCount++;
                    transformed = true;
                } catch (InvalidMixinException th) {
                    this.dumpClassOnFailure(name, targetClassNode, environment);
                    this.handleMixinApplyError(name, th, environment);
                }
            } else {
                // No mixins, but still need to run postProcess stage of coprocessors
                if (this.coprocessors.postProcess(environment, name, targetClassNode)) {
                    transformed = true;
                    this.extensions.export(environment, name, false, targetClassNode);
                }
            }
        } catch (MixinTransformerError er) {
            throw er;
        } catch (Throwable th) {
            this.dumpClassOnFailure(name, targetClassNode, environment);
            throw new MixinTransformerError("An unexpected critical error was encountered", th);
        } finally {
            this.lock.pop();
            environment.profilerEnd(ProfilerSection.MIXIN, mixinTimer);
        }
        return transformed;
    }

    private String getInvalidClassError(String name, ClassNode targetClassNode, ConfigWrapper ownedByConfig) {
        if (ownedByConfig.getConfig().getClasses().contains(name)) {
            return String.format("Illegal classload request for %s. Mixin is defined in %s and cannot be referenced directly", name, ownedByConfig);
        }

        AnnotationNode mixin = Annotations.getInvisible(targetClassNode, Mixin.class);
        if (mixin != null) {
            Variant variant = MixinInfo.getVariant(environment, targetClassNode);
            if (variant == Variant.ACCESSOR) {
                return String.format("Illegal classload request for accessor mixin %s. The mixin is missing from %s which owns "
                        + "package %s* and the mixin has not been applied.", name, ownedByConfig, ownedByConfig.getConfig().getMixinPackage());
            }
        }

        return String.format("%s is in a defined mixin package %s* owned by %s and cannot be referenced directly",
                name, ownedByConfig.getConfig().getMixinPackage(), ownedByConfig);
    }
    
    /**
     * Update a mixin class with new bytecode.
     *
     * @param mixinClass Name of the mixin
     * @param classNode New class
     * @return List of classes that need to be updated
     */
    public List<String> reload(String mixinClass, ClassNode classNode) {
        if (this.lock.getDepth() > 0) {
            throw new MixinApplyError("Cannot reload mixin if re-entrant lock entered");
        }
        List<String> targets = new ArrayList<String>();
        for (ConfigWrapper config : this.configs) {
            targets.addAll(config.reloadMixin(mixinClass, classNode));
        }
        return targets;
    }

    private void setup() {
        this.verboseLoggingLevel = (environment.getOption(Option.DEBUG_VERBOSE)) ? Logger.Level.INFO : Logger.Level.DEBUG;

        var timer = environment.profilerBegin();
        
        this.setupConfigs();
        this.extensions.select(environment);
        int totalMixins = this.prepareConfigs();
        this.transformedCount = 0;

        environment.profilerEnd(ProfilerSection.PROCESSOR_PREPARE, timer);

        logger.log(this.verboseLoggingLevel, "Prepared {} mixins", totalMixins);
    }

    /**
     * Add configurations from the supplied mixin environment to the configs set
     *
     * @param environment Environment to query
     */
    private void setupConfigs() {
        for (IMixinConfig config : environment.getMixinConfigs()) {
            logger.log(this.verboseLoggingLevel, "Selecting config {}", config);
            this.pendingConfigs.add(new ConfigWrapper(this, config));
        }

        Collections.sort(this.pendingConfigs);
    }

    /**
     * Prepare mixin configs
     * 
     * @param environment Environment
     * @return total number of mixins initialised
     */
    private int prepareConfigs() {
        int totalMixins = 0;
        
        final IHotSwap hotSwapper = this.hotSwapper;
        for (ConfigWrapper config : this.pendingConfigs) {
            for (MixinCoprocessor coprocessor : this.coprocessors) {
                config.getConfig().addListener(coprocessor);
            }
            if (hotSwapper != null) {
                config.getConfig().addListener(new IMixinConfig.IListener() {
                    @Override
                    public void onPrepare(MixinInfo mixin) {
                        hotSwapper.registerMixinClass(mixin.getClassName());
                    }
                    @Override
                    public void onInit(MixinInfo mixin) {
                    }
                });
            }
        }
        
        for (ConfigWrapper config : this.pendingConfigs) {
            try {
                logger.log(this.verboseLoggingLevel, "Preparing {} ({})", config, config.getConfig().getClasses().size());
                config.prepare();
                totalMixins += config.mixins.size();
            } catch (InvalidMixinException ex) {
                this.handleMixinPrepareError(config, ex, environment);
            } catch (Exception ex) {
                String message = ex.getMessage();
                logger.error("Error encountered whilst initialising mixin config '" + config.getConfig().getName() + "': " + message, ex);
            }
        }
        
        for (ConfigWrapper config : this.pendingConfigs) {
            IMixinConfigPlugin plugin = config.getConfig().getPlugin();
            if (plugin == null) {
                continue;
            }
            
            Set<String> otherTargets = new HashSet<String>();
            for (ConfigWrapper otherConfig : this.pendingConfigs) {
                if (!otherConfig.equals(config)) {
                    otherTargets.addAll(otherConfig.mixinMapping.keySet());
                }
            }
            
            plugin.acceptTargets(config.mixinMapping.keySet(), Collections.<String>unmodifiableSet(otherTargets));
        }

        for (ConfigWrapper config : this.pendingConfigs) {
            try {
                config.postInitialise();
            } catch (InvalidMixinException ex) {
                this.handleMixinPrepareError(config, ex, environment);
            } catch (Exception ex) {
                String message = ex.getMessage();
                logger.error("Error encountered during mixin config postInit step'" + config.getConfig().getName() + "': " + message, ex);
            }
        }
        
        this.configs.addAll(this.pendingConfigs);
        Collections.sort(this.configs);
        this.pendingConfigs.clear();
        
        return totalMixins;
    }

    private void handleMixinPrepareError(ConfigWrapper config, InvalidMixinException ex, MixinEnvironment environment) throws MixinPrepareError {
        this.handleMixinError(config.getConfig().getName(), ex, environment, ErrorPhase.PREPARE);
    }
    
    private void handleMixinApplyError(String targetClass, InvalidMixinException ex, MixinEnvironment environment) throws MixinApplyError {
        this.handleMixinError(targetClass, ex, environment, ErrorPhase.APPLY);
    }

    private void handleMixinError(String context, InvalidMixinException ex, MixinEnvironment environment, ErrorPhase errorPhase) throws Error {
        this.errorState = true;
        
        IMixinInfo mixin = ex.getMixin();
        
        if (mixin == null) {
            logger.error("InvalidMixinException has no mixin!", ex);
            throw ex;
        }
        
        IMixinConfig config = mixin.getConfig();
        ErrorAction action = config.isRequired() ? ErrorAction.ERROR : ErrorAction.WARN;
        
        if (environment.getOption(Option.DEBUG_VERBOSE)) {
            new PrettyPrinter()
                .wrapTo(160)
                .add("Invalid Mixin").centre()
                .hr('-')
                .kvWidth(10)
                .kv("Action", errorPhase.name())
                .kv("Mixin", mixin.getClassName())
                .kv("Config", config.getName())
                .hr('-')
                .add("    %s", ex.getClass().getName())
                .hr('-')
                .addWrapped("    %s", ex.getMessage())
                .hr('-')
                .add(ex, 8)
                .log(logger, action.logLevel);
        }
    
        for (IMixinErrorHandler handler : this.getErrorHandlers()) {
            ErrorAction newAction = errorPhase.onError(handler, context, ex, mixin, action);
            if (newAction != null) {
                action = newAction;
            }
        }
        
        logger.log(action.logLevel, errorPhase.getLogMessage(context, ex, mixin), ex);
        
        this.errorState = false;

        if (action == ErrorAction.ERROR) {
            throw new MixinApplyError(errorPhase.getErrorMessage(mixin, config), ex);
        }
    }

    private List<IMixinErrorHandler> getErrorHandlers() {
        List<IMixinErrorHandler> handlers = new ArrayList<IMixinErrorHandler>();
        
        for (String handlerClassName : environment.getErrorHandlerClasses()) {
            try {
                logger.info("Instancing error handler class {}", handlerClassName);
                Class<?> handlerClass = this.service.getClassProvider().findClass(handlerClassName, true);
                IMixinErrorHandler handler = (IMixinErrorHandler)handlerClass.getDeclaredConstructor().newInstance();
                if (handler != null) {
                    handlers.add(handler);
                }
            } catch (Throwable th) {
                // skip bad handlers
            }
        }
        
        return handlers;
    }

    private void dumpClassOnFailure(String className, ClassNode classNode, MixinEnvironment env) {
        if (env.getOption(Option.DUMP_TARGET_ON_FAILURE)) {
            ExtensionClassExporter exporter = this.extensions.<ExtensionClassExporter>getExtension(ExtensionClassExporter.class);
            exporter.dumpClass(className.replace('.', '/') + ".target", classNode);
        }
    }

}
