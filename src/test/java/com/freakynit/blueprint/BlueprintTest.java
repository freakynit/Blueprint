package com.freakynit.blueprint;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class BlueprintTest {

    private static Blueprint engine;

    @BeforeAll
    public static void setUpBeforeClass() {
        engine = new Blueprint();
    }

    @Test
    public void testVariableInterpolation() {
        String template = "Hello, {{ name }}!";
        Map<String, Object> context = new HashMap<>();
        context.put("name", "Alice");

        String output = engine.render(template, context);
        assertEquals("Hello, Alice!", output);
    }

    public static class OrderDetails {
        private String id;
        private Float total;
        private boolean shipped;

        public OrderDetails(String id, Float total, boolean shipped) {
            this.id = id;
            this.total = total;
            this.shipped = shipped;
        }

        public String getId() {
            return id;
        }

        public Float getTotal() {
            return total;
        }

        public boolean isShipped() {
            return shipped;
        }
    }

    @Test
    public void testObjectBasedVariableInterpolation() {
        String template = "Your order: {{ orderDetails.id }} total is {{ orderDetails.total}}";

        OrderDetails orderDetails = new OrderDetails("order-1", 123.4f, true);
        Map<String, Object> context = new HashMap<>();
        context.put("orderDetails", orderDetails);

        String output = engine.render(template, context);
        assertEquals("Your order: order-1 total is 123.4", output);
    }

    @Test
    public void testObjectBasedBooleanGetterTypes() {
        String template = "Your order: {{ orderDetails.id }} total is {{ orderDetails.total}} and is shipped: {{ orderDetails.shipped }}";

        OrderDetails orderDetails = new OrderDetails("order-1", 123.4f, true);
        Map<String, Object> context = new HashMap<>();
        context.put("orderDetails", orderDetails);

        String output = engine.render(template, context);
        assertEquals("Your order: order-1 total is 123.4 and is shipped: true", output);
    }

    @Test
    public void testObjectBasedCached() {
        String template = "Orders: {{ orderDetails1.id }}: {{ orderDetails1.total}}\n{{ orderDetails2.id }}: {{ orderDetails2.total}}";

        OrderDetails orderDetails1 = new OrderDetails("order-1", 123.4f, true);
        OrderDetails orderDetails2 = new OrderDetails("order-2", 234.5f, true);

        Map<String, Object> context = new HashMap<>();
        context.put("orderDetails1", orderDetails1);
        context.put("orderDetails2", orderDetails2);

        String output = engine.render(template, context);
        output = engine.render(template, context);
        output = engine.render(template, context);
        assertEquals("Orders: order-1: 123.4\norder-2: 234.5", output);
    }

    @Test
    public void testConditionalRendering() {
        String template = "{% if age >= 18 %}Adult{% else %}Minor{% endif %}";
        Map<String, Object> context1 = new HashMap<>();
        context1.put("age", 20);
        assertEquals("Adult", engine.render(template, context1));

        Map<String, Object> context2 = new HashMap<>();
        context2.put("age", 16);
        assertEquals("Minor", engine.render(template, context2));
    }

    @Test
    public void testLoopRendering() {
        String template = "{% for color in colors %}{{ color }} {% endfor %}";
        Map<String, Object> context = new HashMap<>();
        context.put("colors", Arrays.asList("red", "green", "blue"));

        String output = engine.render(template, context);
        assertEquals("red green blue ", output);
    }

    @Test
    public void testLoopWithIndex() {
        String template = "{% for color in colors %}{{ loop.index }}: {{ color }} {% endfor %}";
        Map<String, Object> context = new HashMap<>();
        context.put("colors", Arrays.asList("red", "green", "blue"));

        String output = engine.render(template, context);
        assertEquals("0: red 1: green 2: blue ", output);
    }

    @Test
    public void testSetVariable() {
        String template = "{% set greeting = 'Hello' %}{{ greeting }}, World!";
        String output = engine.render(template, new HashMap<>());
        assertEquals("Hello, World!", output);
    }

    @Test
    public void testMacroDefinitionAndUsage() {
        String template = "{% macro greet(name) %}Hello, {{ name }}!{% endmacro %}{{ greet('Alice') }}";
        String output = engine.render(template, new HashMap<>());
        assertEquals("Hello, Alice!", output);
    }

    @Test
    public void testArithmeticExpressions() {
        String template = "{{ 5 + 3 * 2 }}";
        String output = engine.render(template, new HashMap<>());
        assertEquals("11", output);
    }

    @Test
    public void testObjectPropertyResolution() {
        String template = "{{ user.name }}";
        Map<String, Object> user = new HashMap<>();
        user.put("name", "Bob");

        Map<String, Object> context = new HashMap<>();
        context.put("user", user);

        String output = engine.render(template, context);
        assertEquals("Bob", output);
    }

    @Test
    public void testArrayIndexing() {
        String template = "{{ colors[1] }}";
        Map<String, Object> context = new HashMap<>();
        context.put("colors", Arrays.asList("red", "green", "blue"));

        String output = engine.render(template, context);
        assertEquals("green", output);
    }

    @Test
    public void testNestedObjectResolution() {
        String template = "{{ user.address.city }}";
        Map<String, Object> address = new HashMap<>();
        address.put("city", "New York");

        Map<String, Object> user = new HashMap<>();
        user.put("address", address);

        Map<String, Object> context = new HashMap<>();
        context.put("user", user);

        String output = engine.render(template, context);
        assertEquals("New York", output);
    }

    @Test
    public void testRawBlockRendering() {
        String template = "{% raw %}This is raw: {{ not processed }}{% endraw %}";
        String output = engine.render(template, new HashMap<>());
        assertEquals("This is raw: {{ not processed }}", output);
    }

    @Test
    public void testCustomFunction() {
        engine.registerFunction("upper", (context, args) -> args.get(0).toString().toUpperCase());

        String template = "Uppercased: {{ upper(name) }}";
        Map<String, Object> context = new HashMap<>();
        context.put("name", "alice");

        String output = engine.render(template, context);
        assertEquals("Uppercased: ALICE", output);
    }

    @Test
    public void testCustomFilter() {
        engine.registerFilter("reverse", (context, args) -> new StringBuilder(args.get(0).toString()).reverse().toString());

        String template = "{{ name | reverse }}";
        Map<String, Object> context = new HashMap<>();
        context.put("name", "Alice");

        String output = engine.render(template, context);
        assertEquals("ecilA", output);
    }

    @Test
    public void testFilterToFunctionFallback() {
        engine.registerFunction("upper", (context, args) -> args.get(0).toString().toUpperCase());

        String template = "Uppercased: {{ upper(name) }}";
        Map<String, Object> context = new HashMap<>();
        context.put("name", "alice");

        String output = engine.render(template, context);
        assertEquals("Uppercased: ALICE", output);
    }

    @Test
    public void testNestedLoops() {
        String template = "{% for row in matrix %}{% for item in row %}{{ item }} {% endfor %}| {% endfor %}";
        List<List<Integer>> matrix = Arrays.asList(
                Arrays.asList(1, 2, 3),
                Arrays.asList(4, 5, 6),
                Arrays.asList(7, 8, 9)
        );

        Map<String, Object> context = new HashMap<>();
        context.put("matrix", matrix);

        String output = engine.render(template, context);
        assertEquals("1 2 3 | 4 5 6 | 7 8 9 | ", output);
    }

    @Test
    public void testComplexExpressions() {
        String template = "{% if user.age > 18 and user.active %}Allowed{% else %}Denied{% endif %}";
        Map<String, Object> user = new HashMap<>();
        user.put("age", 20);
        user.put("active", true);

        Map<String, Object> context = new HashMap<>();
        context.put("user", user);

        assertEquals("Allowed", engine.render(template, context));

        user.put("active", false);
        assertEquals("Denied", engine.render(template, context));
    }

    @Test
    public void testInvalidFunctionCall() {
        String template = "{{ unknownFunction() }}";
        Exception exception = assertThrows(RuntimeException.class, () -> engine.render(template, new HashMap<>()));
        assertTrue(exception.getMessage().contains("Function not found"));
    }
}
