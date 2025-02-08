package com.freakynit.benchmark;

import java.util.*;

public class SampleTemplateData {
    // sample data... uses Map and Class, both kind of data for demonstration
    public static Map<String, Object> getContextForFullTemplate() {
        Map<String, Object> context = new HashMap<>();

        Map<String, Object> customer = new HashMap<>();
        customer.put("name", "john doe");

        Map<String, Object> order = new HashMap<>();
        order.put("number", "A123456");
        order.put("subtotal", 75.0);

        // using class instances for this.. not necessity, just for demonstration
        List<OrderItem> items = new ArrayList<>();
        OrderItem item1 = new OrderItem("laptop", 999);
        OrderItem item2 = new OrderItem("mouse", 50);
        OrderItem item3 = new OrderItem("keyboard", 80);

        items.add(item1);
        items.add(item2);
        items.add(item3);

        order.put("items", items);

        // list of order IDs to demonstrate the join filter
        List<String> ids = Arrays.asList("ID1", "ID2", "ID3");
        order.put("ids", ids);

        customer.put("order", order);

        Map<String, Object> address = new HashMap<>();
        address.put("street", "123 main st");
        address.put("city", "springfield");
        address.put("zip", "12345");

        customer.put("address", address);

        context.put("customer", customer);

        return context;
    }

    public static Map<String, Object> getContextForSmallTemplate() {
        Map<String, Object> order = new HashMap<>();
        order.put("number", "12345");
        order.put("total", 70);

        Map<String, Object> customer = new HashMap<>();
        customer.put("name", "John Doe");

        Map<String, Object> context = new HashMap<>();
        context.put("order", order);
        context.put("customer", customer);

        return context;
    }

    public static class OrderItem {
        private String name;
        private Integer price;

        public OrderItem(String name, Integer price) {
            this.name = name;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public Integer getPrice() {
            return price;
        }
    }
}
