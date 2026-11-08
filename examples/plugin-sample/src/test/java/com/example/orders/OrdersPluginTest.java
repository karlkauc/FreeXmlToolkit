package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class OrdersPluginTest {

    @Test
    void validatorFindsAWrongTotal() {
        var validator = new OrdersPlugin.OrderValidator();
        assertTrue(validator.accepts(OrdersPlugin.SAMPLE, null));
        assertEquals(0, validator.validate(OrdersPlugin.SAMPLE, null).size());
        var problems = validator.validate(OrdersPlugin.SAMPLE.replace("7.00", "8.00"), null);
        assertEquals("The total is 8.00, the lines add up to 7.00.", problems.getFirst().message());
    }

    @Test
    void registersItsContributions() {
        PluginManager manager = PluginManager.getInstance();
        manager.registerForTesting(new OrdersPlugin());
        assertTrue(manager.activities().stream().anyMatch(a -> a.id().equals("orders")));
        assertTrue(manager.validators().stream().anyMatch(v -> v.name().equals("Orders")));
    }
}
