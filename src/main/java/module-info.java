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

module org.spongepowered.mixin {

    requires transitive org.objectweb.asm;
    requires transitive org.objectweb.asm.commons;
    requires transitive org.objectweb.asm.tree;
    requires transitive org.objectweb.asm.tree.analysis;
    requires transitive org.objectweb.asm.util;

    requires dev.m00nl1ght.clockwork.utils.logger;
    requires dev.m00nl1ght.clockwork.utils.profiler;
    requires com.google.common;

    exports org.spongepowered.asm.mixin;
    exports org.spongepowered.asm.mixin.connect;
    exports org.spongepowered.asm.mixin.extensibility;
    exports org.spongepowered.asm.mixin.gen;
    exports org.spongepowered.asm.mixin.gen.throwables;
    exports org.spongepowered.asm.mixin.injection;
    exports org.spongepowered.asm.mixin.injection.callback;
    exports org.spongepowered.asm.mixin.injection.code;
    exports org.spongepowered.asm.mixin.injection.invoke.arg;
    exports org.spongepowered.asm.mixin.injection.points;
    exports org.spongepowered.asm.mixin.injection.selectors;
    exports org.spongepowered.asm.mixin.injection.selectors.dynamic;
    exports org.spongepowered.asm.mixin.injection.selectors.throwables;
    exports org.spongepowered.asm.mixin.injection.struct;
    exports org.spongepowered.asm.mixin.injection.throwables;
    exports org.spongepowered.asm.mixin.refmap;
    exports org.spongepowered.asm.mixin.throwables;
    exports org.spongepowered.asm.mixin.transformer.ext;
    exports org.spongepowered.asm.mixin.transformer.throwables;
    exports org.spongepowered.asm.obfuscation;
    exports org.spongepowered.asm.obfuscation.mapping;
    exports org.spongepowered.asm.obfuscation.mapping.common;
    exports org.spongepowered.asm.service;
    exports org.spongepowered.asm.util;
    exports org.spongepowered.asm.util.asm;

}
