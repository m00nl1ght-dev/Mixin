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

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.ProfilerSection;
import org.spongepowered.asm.mixin.transformer.MixinCoprocessor.ProcessResult;

import java.util.ArrayList;

/**
 * Convenience list of coprocessors
 */
class MixinCoprocessors extends ArrayList<MixinCoprocessor> {

    private static final long serialVersionUID = 1L;

    /**
     * Process the supplied class using all registered coprocessors. If the
     * class is transformed, or should be passed through (rather than treated as
     * a mixin target) then this is indicated by the return value.
     * 
     * @param className Name of the target class
     * @param classNode Classnode of the target class
     * @return result indicating whether the class was transformed, and whether
     *      or not to passthrough instead of apply mixins
     */
    ProcessResult process(MixinEnvironment environment, String className, ClassNode classNode) {
        var timer = environment.profilerBegin();
        ProcessResult result = ProcessResult.NONE;
        for (MixinCoprocessor coprocessor : this) {
            result = coprocessor.process(className, classNode).with(result);
        }
        environment.profilerEnd(ProfilerSection.COPROCESSOR_PROCESS, timer);
        return result;
    }

    /**
     * Perform postprocessing actions on the supplied class using all registered
     * coprocessors.
     * 
     * @param className Name of the target class
     * @param classNode Classnode of the target class
     * @return true if the coprocessor applied any transformations
     */
    boolean postProcess(MixinEnvironment environment, String className, ClassNode classNode) {
        var timer = environment.profilerBegin();
        boolean transformed = false;
        for (MixinCoprocessor coprocessor : this) {
            transformed |= coprocessor.postProcess(className, classNode);
        }
        environment.profilerEnd(ProfilerSection.COPROCESSOR_POSTPROCESS, timer);
        return transformed;
    }
    
}
