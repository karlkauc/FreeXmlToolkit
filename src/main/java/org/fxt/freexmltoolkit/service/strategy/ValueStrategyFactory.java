/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service.strategy;

import java.util.EnumMap;
import java.util.Map;

import org.fxt.freexmltoolkit.domain.GenerationStrategy;
import org.fxt.freexmltoolkit.service.XsdSampleDataGenerator;

/**
 * Factory for creating and caching {@link ValueStrategy} instances.
 * Stateless strategies are cached; stateful ones (like AUTO which holds
 * a reference to XsdSampleDataGenerator) are created per factory instance.
 */
public class ValueStrategyFactory {

    private final Map<GenerationStrategy, ValueStrategy> cache = new EnumMap<>(GenerationStrategy.class);

    public ValueStrategyFactory(XsdSampleDataGenerator generator) {
        cache.put(GenerationStrategy.AUTO, new AutoValueStrategy(generator));
        cache.put(GenerationStrategy.FIXED, new FixedValueStrategy());
        cache.put(GenerationStrategy.OMIT, new OmitValueStrategy());
        cache.put(GenerationStrategy.EMPTY, new EmptyValueStrategy());
        cache.put(GenerationStrategy.XSD_EXAMPLE, new XsdExampleValueStrategy());
        cache.put(GenerationStrategy.ENUM_CYCLE, new EnumCycleValueStrategy());
        cache.put(GenerationStrategy.SEQUENCE, new SequenceValueStrategy());
        cache.put(GenerationStrategy.XPATH_REF, new XPathRefValueStrategy());
        cache.put(GenerationStrategy.RANDOM_FROM_LIST, new RandomFromListValueStrategy());
        cache.put(GenerationStrategy.TEMPLATE, new TemplateValueStrategy());
        cache.put(GenerationStrategy.NULL, new NullValueStrategy());
    }

    /**
     * Returns the strategy implementation for the given generation strategy.
     *
     * @param strategy the generation strategy
     * @return the corresponding value strategy, never null
     * @throws IllegalArgumentException if no strategy is registered for the given type
     */
    public ValueStrategy forStrategy(GenerationStrategy strategy) {
        ValueStrategy result = cache.get(strategy);
        if (result == null) {
            throw new IllegalArgumentException("No strategy registered for: " + strategy);
        }
        return result;
    }
}
