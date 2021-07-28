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

import org.spongepowered.asm.mixin.transformer.MixinTransformer;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class UniversalTestClassLoader extends ClassLoader {

    private final File baseDir;
    private final File resDir;

    private MixinTransformer transformer;
    private Set<String> excludedPackages = new HashSet<>();

    public UniversalTestClassLoader(ClassLoader parent, File baseDir, File resDir) {
        super(parent);
        this.baseDir = baseDir;
        this.resDir = resDir;
    }

    @Override
    public Class<?> findClass(String name) {
        byte[] bytes = getClassBytes(name);

        if (transformer != null && !isExcluded(name)) {
            bytes = transformer.transformClassBytes(name, name, bytes);
        }

        return defineClass(name, bytes, 0, bytes.length);
    }

    public byte[] getClassBytes(String name)  {
        String fileName = name.replace('.', File.separatorChar) + ".class";
        try {
            return Files.readAllBytes(new File(baseDir, fileName).toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isExcluded(String name) {
        return excludedPackages.stream().anyMatch(name::startsWith);
    }

    public void addExcludedPackage(String pn) {
        excludedPackages.add(pn);
    }

    @Override
    protected URL findResource(String name) {
        try {
            return new File(resDir, name).toURI().toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setTransformer(MixinTransformer transformer) {
        this.transformer = transformer;
    }

}
