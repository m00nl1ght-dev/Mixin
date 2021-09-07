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

import org.spongepowered.asm.mixin.transformer.MixinConfig;
import org.spongepowered.asm.service.MixinService;

import java.util.Collections;

/**
 * Test runner for universal mixin example.
 */
public final class UniversalTest {

    private static final String TEST_CLASS_NAME = "dev.m00nl1ght.mixin.example.TestClass";
    private static final String CONFIG_FILE = "testconfig.json";

    private UniversalTest() {}

    /**
     * Test runner for universal mixin example.
     * @param args cmd line args
     */
    public static void main(String[] args) throws Exception {

        final UniversalMixinService service = (UniversalMixinService) MixinService.getService();
        service.init();

        service.getClassLoader().addExcludedPackage("dev.m00nl1ght.mixin.example.mixins");

        final MixinConfig config = new MixinConfig("test");
        config.setMixinPackage("dev.m00nl1ght.mixin.example.mixins");
        config.setMixinClasses(Collections.singletonList("TestClassMixin"));
        config.setMinVersion("0.8.3");
        config.setVerboseLogging(true);
        config.setRequired(true);

        config.init(service.getEnvironment());
        service.getEnvironment().registerConfig(config);

        final Class<?> testClass = service.getClassLoader().loadClass(TEST_CLASS_NAME);
        final TestInterface testInstance = (TestInterface) testClass.getConstructor().newInstance();

        System.out.println("Message: " + testInstance.someMethod());

    }

}
