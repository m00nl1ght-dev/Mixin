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
package dev.m00nl1ght.mixin;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.mixin.transformer.MixinTransformer;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.transformers.MixinClassReader;
import org.spongepowered.asm.util.perf.Profiler;
import org.spongepowered.asm.util.perf.Profiler.Section;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Mixin service that can be used for non-Minecraft applications
 */
@SuppressWarnings({"DeprecatedIsStillUsed", "UnstableApiUsage"})
public class UniversalMixinService extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider, ITransformerProvider {

    public final File classSource = new File("./build/classes/java/universalTestExample/");
    public final File resDir = new File("./build/resources/universalTestExample/");

    private MixinEnvironment environment;
    private UniversalTestClassLoader classLoader;

    public void init() {
        environment = new MixinEnvironment(this);
        classLoader = new UniversalTestClassLoader(ClassLoader.getSystemClassLoader(), classSource, resDir);
        classLoader.setTransformer(new MixinTransformer(environment));
    }

    public UniversalTestClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public String getName() {
        return "Universal";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public CompatibilityLevel getMaxCompatibilityLevel() {
        return CompatibilityLevel.JAVA_8;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return null;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public Class<?> findClass(String name) {
        return classLoader.findClass(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, classLoader);
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, this.getClass().getClassLoader());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return classLoader.getResourceAsStream(name);
    }

    @Override
    @Deprecated
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Collection<ITransformer> getTransformers() {
        return Collections.emptyList();
    }

    /**
     * Returns (and generates if necessary) the transformer delegation list for
     * this environment.
     * 
     * @return current transformer delegation list (read-only)
     */
    @Override
    public List<ITransformer> getDelegatedTransformers() {
        return Collections.emptyList();
    }

    /**
     * Adds a transformer to the transformer exclusions list
     *
     * @param name Class transformer exclusion to add
     */
    @Override
    public void addTransformerExclusion(String name) {

    }

    /**
     * Retrieve class bytes using available classloaders, does not transform the
     * class
     * 
     * @param name class name
     * @param transformedName transformed class name
     * @return class bytes or null if not found
     * @deprecated Use {@link #getClassNode} instead
     */
    @Deprecated
    public byte[] getClassBytes(String name, String transformedName) {
        byte[] classBytes = classLoader.getClassBytes(name);
        if (classBytes != null) {
            return classBytes;
        }

        URLClassLoader appClassLoader;
        if (this.getClass().getClassLoader() instanceof URLClassLoader) {
            appClassLoader = (URLClassLoader) this.getClass().getClassLoader();
        } else {
            appClassLoader = new URLClassLoader(new URL[]{}, this.getClass().getClassLoader());
        }

        InputStream classStream = null;
        try {
            final String resourcePath = transformedName.replace('.', '/') + ".class";
            classStream = appClassLoader.getResourceAsStream(resourcePath);
            return ByteStreams.toByteArray(Objects.requireNonNull(classStream));
        } catch (Exception ex) {
            return null;
        } finally {
            Closeables.closeQuietly(classStream);
        }
    }
    
    /**
     * Loads class bytecode from the classpath
     * 
     * @param className Name of the class to load
     * @param runTransformers True to run the loaded bytecode through the
     *      delegate transformer chain
     * @return Transformed class bytecode for the specified class
     * @throws ClassNotFoundException if the specified class could not be loaded
     */
    @Deprecated
    public byte[] getClassBytes(String className, boolean runTransformers) throws ClassNotFoundException {
        String transformedName = className.replace('/', '.');
        String name = transformedName;
        
        Profiler profiler = environment.getProfiler();
        Section loadTime = profiler.begin(Profiler.ROOT, "class.load");
        byte[] classBytes = this.getClassBytes(name, transformedName);
        loadTime.end();

        if (classBytes == null) {
            throw new ClassNotFoundException(String.format("The specified class '%s' was not found", transformedName));
        }

        return classBytes;
    }

    @Override
    public ClassNode getClassNode(String className) throws ClassNotFoundException {
        return this.getClassNode(className, this.getClassBytes(className, true), ClassReader.EXPAND_FRAMES);
    }

    @Override
    public ClassNode getClassNode(String className, boolean runTransformers) throws ClassNotFoundException {
        return this.getClassNode(className, this.getClassBytes(className, true), ClassReader.EXPAND_FRAMES);
    }

    /**
     * Gets an ASM Tree for the supplied class bytecode
     * 
     * @param classBytes Class bytecode
     * @param flags ClassReader flags
     * @return ASM Tree view of the specified class 
     */
    private ClassNode getClassNode(String className, byte[] classBytes, int flags) {
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new MixinClassReader(classBytes, className);
        classReader.accept(classNode, flags);
        return classNode;
    }

    public MixinEnvironment getEnvironment() {
        return environment;
    }

}
