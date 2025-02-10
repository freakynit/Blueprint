package com.freakynit.blueprint;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Character.isDigit;

public class Blueprint {
    private final Map<String, TemplateFunction> functions = new HashMap<>();
    private final Map<String, TemplateFunction> filters = new HashMap<>();

    public void registerFunction(String name, TemplateFunction func) {
        functions.put(name, func);
    }
    public TemplateFunction getFunction(String name) {
        return functions.get(name);
    }

    //filters receive the “input” as the first argument.
    public void registerFilter(String name, TemplateFunction filter) {
        filters.put(name, filter);
    }

    // Get a registered filter. If no filter is registered, try to fall back to functions.
    public TemplateFunction getFilter(String name) {
        TemplateFunction f = filters.get(name);
        if (f != null) {
            return f;
        }
        return getFunction(name);
    }

    public Template compile(String templateSource) {
        Parser parser = new Parser(templateSource, this);
        List<Node> nodes = parser.parseNodes(Collections.emptySet());
        return new Template(nodes, this);
    }

    public String render(String templateSource, Map<String, Object> context) {
        Template template = compile(templateSource);
        return template.render(context);
    }

    // an interface for custom functions that can be called from within templates.
    public interface TemplateFunction {
        Object execute(RenderContext context, List<Object> args);
    }

    // --------------------------------------------------------------------------------
    // Template and RenderContext classes
    // --------------------------------------------------------------------------------

    // holds compiled template
    public static class Template {
        private final List<Node> nodes;
        private final Blueprint engine;

        // local cache for getter methods, tied to the Template instance to optimize for faster lookups for recurring renderings
        private final Map<Class<?>, Map<String, java.lang.reflect.Method>> getterCache = new ConcurrentHashMap<>();

        public Template(List<Node> nodes, Blueprint engine) {
            this.nodes = nodes;
            this.engine = engine;
        }

        public String render(Map<String, Object> context) {
            StringBuilder sb = new StringBuilder();
            // We wrap the context in a RenderContext (which gives variable lookup and function/filter access)
            RenderContext renderContext = new RenderContext(context, engine, getterCache);
            for (Node node : nodes) {
                node.render(renderContext, sb);
            }
            return sb.toString();
        }
    }

    // holds the current context and a pointer to the engine (so that custom functions and filters may be looked up).
    // Also holds a registry of macros defined in the template.
    public static class RenderContext {
        public final Map<String, Object> context;
        public final Map<String, TemplateFunction> macros = new HashMap<>();
        private final Blueprint engine;

        private final Map<Class<?>, Map<String, java.lang.reflect.Method>> getterCache;

        public RenderContext(Map<String, Object> context, Blueprint engine, Map<Class<?>, Map<String, java.lang.reflect.Method>> getterCache) {
            this.context = context;
            this.engine = engine;
            this.getterCache = getterCache;
        }

        // resolve a variable name from the context. Supports “dot–notation” and array access using bracket–notation.
        // Examples: "user.name" or "user.colors[0]" or "matrix[1][2]".
        public Object resolve(String variable) {
            // Check if the variable has a dot to separate into tokens.
            int dotIndex = variable.indexOf('.');
            String firstToken = (dotIndex == -1) ? variable : variable.substring(0, dotIndex);
            Object value;

            // If the first token contains a bracket, extract the base variable name.
            int bracketIndex = firstToken.indexOf('[');
            if (bracketIndex != -1) {
                String baseVar = firstToken.substring(0, bracketIndex);
                value = context.get(baseVar);
                // Process the remaining bracket parts (e.g. "[1]").
                String remaining = firstToken.substring(bracketIndex);
                value = resolvePart(value, remaining);
            } else {
                value = context.get(firstToken);
            }

            // If there are additional dot-separated parts, process them.
            if (dotIndex != -1) {
                String[] parts = variable.substring(dotIndex + 1).split("\\.");
                for (String part : parts) {
                    value = resolvePart(value, part);
                    if (value == null) {
                        break;
                    }
                }
            }
            return value;
        }

        // helper to support index lookups (e.g. "colors[0]")
        private Object resolvePart(Object value, String part) {
            int bracketIndex = part.indexOf('[');
            if (bracketIndex == -1) {
                return getProperty(value, part);
            } else {
                // first, resolve the property (if any) before the first '['
                String propName = part.substring(0, bracketIndex);
                if (!propName.isEmpty()) {
                    value = getProperty(value, propName);
                }
                // process one or more indices (e.g. [0][1])
                while (bracketIndex != -1) {
                    int endBracket = part.indexOf(']', bracketIndex);
                    if (endBracket == -1) {
                        return null; // malformed
                    }
                    String indexStr = part.substring(bracketIndex + 1, endBracket).trim();
                    int index;
                    try {
                        index = Integer.parseInt(indexStr);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    value = getIndexedValue(value, index);
                    if (value == null) {
                        return null;
                    }
                    bracketIndex = part.indexOf('[', endBracket);
                }
                return value;
            }
        }

        // helper to retrieve a property value from an object. If the object is a map, then the property is
        // fetched directly. Otherwise, we try to call a getter method with the camelCase name.

        // For example, a property "name" is looked up as:
        // - If value is a Map: map.get("name")
        // - Otherwise: value.getClass().getMethod("getName") or, for booleans, value.getClass().getMethod("isName")

        // This method uses the cache stored in the Template
        private Object getProperty(Object value, String property) {
            if (value instanceof Map) {
                return ((Map<?, ?>) value).get(property);
            }
            if (value == null || property == null || property.isEmpty()) {
                return null;
            }

            Class<?> cls = value.getClass();
            Map<String, Method> classCache = getterCache.computeIfAbsent(cls, k -> new ConcurrentHashMap<>());

            if (classCache.containsKey(property)) {
                Method cachedMethod = classCache.get(property);
                return (cachedMethod != null) ? invokeGetter(value, cachedMethod) : null;
            }

            // build the expected getter method name using camelCase conversion
            String getterName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            Method method = null;
            try {
                method = cls.getMethod(getterName);
            } catch (NoSuchMethodException e) {
                // if not found, try the boolean "is" style getter
                String isGetterName = "is" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
                try {
                    method = cls.getMethod(isGetterName);
                } catch (NoSuchMethodException e2) {
                    method = null;
                }
            }
            // cache the result (even if null) so we don't repeat expensive reflection calls
            classCache.put(property, method);
            return (method != null) ? invokeGetter(value, method) : null;
        }

        private Object invokeGetter(Object value, Method method) {
            try {
                return method.invoke(value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                return null;
            }
        }

        // helper to retrieve an element from a List or array
        private Object getIndexedValue(Object value, int index) {
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (index >= 0 && index < list.size()) {
                    return list.get(index);
                }
            } else if (value != null && value.getClass().isArray()) {
                int len = Array.getLength(value);
                if (index >= 0 && index < len) {
                    return Array.get(value, index);
                }
            }
            return null;
        }

        // look up a custom function or macro.
        // Macros (defined in the template) take precedence over engine–registered functions
        public TemplateFunction getFunction(String name) {
            if (macros.containsKey(name)) {
                return macros.get(name);
            }
            return engine.getFunction(name);
        }

        // look up a custom filter
        public TemplateFunction getFilter(String name) {
            TemplateFunction f = engine.getFilter(name);
            if (f != null) {
                return f;
            }
            return getFunction(name);
        }
    }

    // --------------------------------------------------------------------------------
    // Node types (AST)
    // --------------------------------------------------------------------------------

    public static abstract class Node {
        public abstract void render(RenderContext context, StringBuilder sb);
    }

    // literal text node
    public static class TextNode extends Node {
        private final String text;

        public TextNode(String text) {
            this.text = text;
        }

        @Override
        public void render(RenderContext context, StringBuilder sb) {
            sb.append(text);
        }
    }

    // variable expression (e.g. {{ name }}) node
    public static class VariableNode extends Node {
        private final Expression expression;

        public VariableNode(Expression expression) {
            this.expression = expression;
        }

        @Override
        public void render(RenderContext context, StringBuilder sb) {
            Object value = expression.evaluate(context);
            if (value != null) {
                sb.append(value.toString());
            }
        }
    }

    // if/else block node
    public static class IfNode extends Node {
        private final Expression condition;
        private final List<Node> trueNodes;
        private final List<Node> falseNodes;

        public IfNode(Expression condition, List<Node> trueNodes, List<Node> falseNodes) {
            this.condition = condition;
            this.trueNodes = trueNodes;
            this.falseNodes = falseNodes;
        }

        @Override
        public void render(RenderContext context, StringBuilder sb) {
            Object cond = condition.evaluate(context);
            boolean truth = isTrue(cond);
            List<Node> nodesToRender = truth ? trueNodes : falseNodes;
            if (nodesToRender != null) {
                for (Node node : nodesToRender) {
                    node.render(context, sb);
                }
            }
        }

        private boolean isTrue(Object cond) {
            if (cond == null) return false;
            if (cond instanceof Boolean) return (Boolean) cond;
            if (cond instanceof Number) return ((Number) cond).doubleValue() != 0;
            if (cond instanceof String) return !((String) cond).isEmpty();
            return true;
        }
    }

    // for–loop node
    public static class ForNode extends Node {
        private final String loopVar;
        private final Expression listExpression;
        private final List<Node> bodyNodes;

        public ForNode(String loopVar, Expression listExpression, List<Node> bodyNodes) {
            this.loopVar = loopVar;
            this.listExpression = listExpression;
            this.bodyNodes = bodyNodes;
        }

        @Override
        public void render(RenderContext context, StringBuilder sb) {
            Object listVal = listExpression.evaluate(context);
            if (listVal instanceof Iterable) {
                // save any existing values for the "loop" meta key *and* the loop variable itself
                Object originalLoopMeta = context.context.get("loop");
                Object originalLoopVar = context.context.get(loopVar);

                int index = 0;
                for (Object item : (Iterable<?>) listVal) {
                    // set the loop variable for the current iteration
                    context.context.put(loopVar, item);

                    // create a "loop" map with the current index and other meta info if needed
                    Map<String, Object> loopInfo = new HashMap<>();
                    loopInfo.put("index", index);
                    context.context.put("loop", loopInfo);

                    for (Node node : bodyNodes) {
                        node.render(context, sb);
                    }
                    index++;
                }
                // restore the original loop variable if it existed; otherwise, remove it
                if (originalLoopVar != null) {
                    context.context.put(loopVar, originalLoopVar);
                } else {
                    context.context.remove(loopVar);
                }
                // restore the original "loop" meta key
                if (originalLoopMeta != null) {
                    context.context.put("loop", originalLoopMeta);
                } else {
                    context.context.remove("loop");
                }
            }
        }
    }

    // set–assignment (e.g. {% set foo = "bar" %}) node
    public static class SetNode extends Node {
        private final String variableName;
        private final Expression expression;

        public SetNode(String variableName, Expression expression) {
            this.variableName = variableName;
            this.expression = expression;
        }

        @Override
        public void render(RenderContext context, StringBuilder sb) {
            Object value = expression.evaluate(context);
            context.context.put(variableName, value);
        }
    }

    /**
     * A node representing a macro definition.
     * Example:
     * {% macro shout(text) %}
     *   {{ upper(text) }}!!!
     * {% endmacro %}
     *
     * When rendered, the macro is registered in the RenderContext and produces no output.
     */
    public static class MacroNode extends Node {
        private final String name;
        private final List<String> parameters;
        private final List<Node> body;

        public MacroNode(String name, List<String> parameters, List<Node> body) {
            this.name = name;
            this.parameters = parameters;
            this.body = body;
        }

        @Override
        public void render(RenderContext context, StringBuilder sb) {
            MacroFunction macroFunc = new MacroFunction(parameters, body, context);
            context.macros.put(name, macroFunc);
            // macro definitions produce no output... they are just holders
        }
    }

    // --------------------------------------------------------------------------------
    // Expression classes
    // --------------------------------------------------------------------------------

    /**
     * An expression can be evaluated to produce a value.
     */
    public static abstract class Expression {
        public abstract Object evaluate(RenderContext context);
    }

    // literal value expression (number or string, e.g. {{ 5 }} or {{ "hello" }})
    public static class LiteralExpression extends Expression {
        private final Object value;

        public LiteralExpression(Object value) {
            this.value = value;
        }

        @Override
        public Object evaluate(RenderContext context) {
            return value;
        }
    }

    // object literal expression (e.g. { firstName: "alice", lastName: "smith", age: 30 })
    public static class ObjectLiteralExpression extends Expression {
        private final Map<String, Expression> entries;

        public ObjectLiteralExpression(Map<String, Expression> entries) {
            this.entries = entries;
        }

        @Override
        public Object evaluate(RenderContext context) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, Expression> entry : entries.entrySet()) {
                result.put(entry.getKey(), entry.getValue().evaluate(context));
            }
            return result;
        }
    }

    // array literal expression (e.g. [ { name: "Bob", age: 22 }, { name: "Charlie", age: 27 } ])
    public static class ArrayLiteralExpression extends Expression {
        private final List<Expression> elements;

        public ArrayLiteralExpression(List<Expression> elements) {
            this.elements = elements;
        }

        @Override
        public Object evaluate(RenderContext context) {
            List<Object> list = new ArrayList<>();
            for (Expression expr : elements) {
                list.add(expr.evaluate(context));
            }
            return list;
        }
    }

    // variable expression (e.g. “user.name” or “user.colors[0]”)
    public static class VariableExpression extends Expression {
        private final String name;

        public VariableExpression(String name) {
            this.name = name;
        }

        @Override
        public Object evaluate(RenderContext context) {
            return context.resolve(name);
        }
    }

    // function–call expression (e.g. {{ upper(name) }})
    public static class FunctionCallExpression extends Expression {
        private final String functionName;
        private final List<Expression> arguments;

        public FunctionCallExpression(String functionName, List<Expression> arguments) {
            this.functionName = functionName;
            this.arguments = arguments;
        }

        @Override
        public Object evaluate(RenderContext context) {
            TemplateFunction func = context.getFunction(functionName);
            if (func == null) {
                throw new RuntimeException("Function not found: " + functionName);
            }
            List<Object> args = new ArrayList<>();
            for (Expression argExp : arguments) {
                args.add(argExp.evaluate(context));
            }
            return func.execute(context, args);
        }
    }

    // binary expression (e.g. a >= 18, foo and bar)
    public static class BinaryExpression extends Expression {
        private final Expression left;
        private final Expression right;
        private final String operator;

        public BinaryExpression(Expression left, String operator, Expression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public Object evaluate(RenderContext context) {
            Object leftVal = left.evaluate(context);
            Object rightVal = right.evaluate(context);
            switch (operator) {
                case "or":
                    return isTrue(leftVal) || isTrue(rightVal);
                case "and":
                    return isTrue(leftVal) && isTrue(rightVal);
                case "==":
                    return (leftVal == null && rightVal == null) || (leftVal != null && leftVal.equals(rightVal));
                case "!=":
                    return !((leftVal == null && rightVal == null) || (leftVal != null && leftVal.equals(rightVal)));
                case ">":
                    return compare(leftVal, rightVal) > 0;
                case ">=":
                    return compare(leftVal, rightVal) >= 0;
                case "<":
                    return compare(leftVal, rightVal) < 0;
                case "<=":
                    return compare(leftVal, rightVal) <= 0;
                case "+":
                    if (leftVal instanceof Number && rightVal instanceof Number) {
                        if (leftVal instanceof Integer && rightVal instanceof Integer) {
                            return ((Integer) leftVal) + ((Integer) rightVal);
                        } else {
                            return ((Number) leftVal).doubleValue() + ((Number) rightVal).doubleValue();
                        }
                    }
                    return String.valueOf(leftVal) + String.valueOf(rightVal);
                case "-":
                    if (leftVal instanceof Number && rightVal instanceof Number) {
                        if (leftVal instanceof Integer && rightVal instanceof Integer) {
                            return ((Integer) leftVal) - ((Integer) rightVal);
                        } else {
                            return ((Number) leftVal).doubleValue() - ((Number) rightVal).doubleValue();
                        }
                    }
                    throw new RuntimeException(String.format("Cannot subtract non-numeric values. Left value: %s right value: %s", leftVal, rightVal));
                case "*":
                    if (leftVal instanceof Number && rightVal instanceof Number) {
                        if (leftVal instanceof Integer && rightVal instanceof Integer) {
                            return ((Integer) leftVal) * ((Integer) rightVal);
                        } else {
                            return ((Number) leftVal).doubleValue() * ((Number) rightVal).doubleValue();
                        }
                    }
                    throw new RuntimeException(String.format("Cannot multiply non-numeric values. Left value: %s right value: %s", leftVal, rightVal));
                case "/":
                    if (leftVal instanceof Number && rightVal instanceof Number) {
                        if (leftVal instanceof Integer && rightVal instanceof Integer) {
                            int divisor = (Integer) rightVal;
                            if (divisor == 0) {
                                throw new RuntimeException("Division by zero");
                            }
                            return ((Integer) leftVal) / divisor;
                        } else {
                            double divisor = ((Number) rightVal).doubleValue();
                            if (divisor == 0.0) {
                                throw new RuntimeException("Division by zero");
                            }
                            return ((Number) leftVal).doubleValue() / divisor;
                        }
                    }
                    throw new RuntimeException(String.format("Cannot divide non-numeric values. Left value: %s right value: %s", leftVal, rightVal));
                case "%":
                    if (leftVal instanceof Number && rightVal instanceof Number) {
                        if (leftVal instanceof Integer && rightVal instanceof Integer) {
                            return ((Integer) leftVal) % ((Integer) rightVal);
                        } else {
                            return ((Number) leftVal).doubleValue() % ((Number) rightVal).doubleValue();
                        }
                    }
                    throw new RuntimeException(String.format("Cannot apply modulo to non-numeric values. Left value: %s right value: %s", leftVal, rightVal));
                case "**":
                    if (leftVal instanceof Number && rightVal instanceof Number) {
                        if (leftVal instanceof Integer && rightVal instanceof Integer) {
                            int base = (Integer) leftVal;
                            int exponent = (Integer) rightVal;
                            // for negative exponents, fall back to double arithmetic
                            if (exponent < 0) {
                                return Math.pow(base, exponent);
                            } else {
                                int result = 1;
                                for (int i = 0; i < exponent; i++) {
                                    result *= base;
                                }
                                return result;
                            }
                        } else {
                            return Math.pow(((Number) leftVal).doubleValue(), ((Number) rightVal).doubleValue());
                        }
                    }
                    throw new RuntimeException(String.format("Cannot apply power operator to non-numeric values. Left value: %s right value: %s", leftVal, rightVal));
                default:
                    throw new RuntimeException(String.format("Unknown operator: %s", operator));
            }
        }

        private boolean isTrue(Object val) {
            if (val == null) return false;
            if (val instanceof Boolean) return (Boolean) val;
            if (val instanceof Number) return ((Number) val).doubleValue() != 0;
            if (val instanceof String) return !((String) val).isEmpty();
            return true;
        }

        private int compare(Object leftVal, Object rightVal) {
            if (leftVal instanceof Number && rightVal instanceof Number) {
                // when both are integers, use integer arithmetic; otherwise, use double arithmetic
                if (leftVal instanceof Integer && rightVal instanceof Integer) {
                    return ((Integer) leftVal).compareTo((Integer) rightVal);
                }
                double diff = ((Number) leftVal).doubleValue() - ((Number) rightVal).doubleValue();
                return Double.compare(diff, 0);
            }
            if (leftVal instanceof Comparable && rightVal instanceof Comparable) {
                return ((Comparable) leftVal).compareTo(rightVal);
            }

            throw new RuntimeException(String.format("Cannot compare non-numeric or non-comparable values. Left value: %s right value: %s", leftVal, rightVal));
        }
    }

    // unary expression (e.g. "not foo" or "-x").
    public static class UnaryExpression extends Expression {
        private final String operator;
        private final Expression operand;

        public UnaryExpression(String operator, Expression operand) {
            this.operator = operator;
            this.operand = operand;
        }

        @Override
        public Object evaluate(RenderContext context) {
            Object val = operand.evaluate(context);
            switch (operator) {
                case "not":
                    return !isTrue(val);
                case "-":
                    if (val instanceof Number) {
                        return -((Number) val).doubleValue();
                    }
                    throw new RuntimeException(String.format("Cannot negate non-numeric value. Value: %s", val));
                default:
                    throw new RuntimeException(String.format("Unknown unary operator: %s", operator));
            }
        }

        private boolean isTrue(Object val) {
            if (val == null) return false;
            if (val instanceof Boolean) return (Boolean) val;
            if (val instanceof Number) return ((Number) val).doubleValue() != 0;
            if (val instanceof String) return !((String) val).isEmpty();
            return true;
        }
    }

    // expression that applies one or more filters to a base expression (e.g. {{ foo | replace("foo", "bar") | capitalize }})
    public static class FilteredExpression extends Expression {
        private final Expression base;
        private final List<Filter> filters;

        public FilteredExpression(Expression base, List<Filter> filters) {
            this.base = base;
            this.filters = filters;
        }

        @Override
        public Object evaluate(RenderContext context) {
            Object value = base.evaluate(context);
            for (Filter filter : filters) {
                TemplateFunction func = context.getFilter(filter.filterName);
                if (func == null) {
                    throw new RuntimeException("Filter not found: " + filter.filterName);
                }
                List<Object> args = new ArrayList<>();
                // The current value is passed as the first argument.
                args.add(value);
                for (Expression argExp : filter.arguments) {
                    args.add(argExp.evaluate(context));
                }
                value = func.execute(context, args);
            }
            return value;
        }
    }

    public static class Filter {
        public final String filterName;
        public final List<Expression> arguments;

        public Filter(String filterName, List<Expression> arguments) {
            this.filterName = filterName;
            this.arguments = arguments;
        }
    }

    // TemplateFunction implementation for macros.
    // When executed, the macro renders its body using a new local context with its parameters set.
    public static class MacroFunction implements TemplateFunction {
        private final List<String> parameters;
        private final List<Node> body;
        // (optionally) we can capture the definition context if needed.
        private final RenderContext definitionContext;

        public MacroFunction(List<String> parameters, List<Node> body, RenderContext definitionContext) {
            this.parameters = parameters;
            this.body = body;
            this.definitionContext = definitionContext;
        }

        @Override
        public Object execute(RenderContext context, List<Object> args) {
            // create a new local context that starts as a copy of the caller's context
            Map<String, Object> localVars = new HashMap<>(context.context);
            // bind macro parameters
            for (int i = 0; i < parameters.size(); i++) {
                Object argVal = i < args.size() ? args.get(i) : null;
                localVars.put(parameters.get(i), argVal);
            }
            RenderContext localContext = new RenderContext(localVars, context.engine, context.getterCache);
            // optionally, propagate macro definitions from the caller
            localContext.macros.putAll(context.macros);
            StringBuilder sb = new StringBuilder();
            for (Node node : body) {
                node.render(localContext, sb);
            }
            return sb.toString();
        }
    }

    // --------------------------------------------------------------------------------
    // Parser
    // --------------------------------------------------------------------------------

    /**
     * A very simple parser that tokenizes the template and builds an AST.
     *
     * Supported syntax:
     * - Variable interpolation: {{ expression }}
     * - If-else blocks: {% if condition %} ... {% else %} ... {% endif %}
     * - For loops: {% for item in list %} ... {% endfor %}
     * - Set assignments: {% set variable = expression %}
     * - Raw blocks: {% raw %} ... {% endraw %}
     * - Macro definitions: {% macro name(params) %} ... {% endmacro %}
     *
     * This parser supports numbers, quoted strings, variable names (with dot and
     * bracket notation), function calls, filters (using the pipe | operator), logical/arithmetic expressions,
     * object literals (using { key: value, ... }), and array literals (using [ ... ]).
     */
    public static class Parser {
        private final String input;
        private final int length;
        private int pos;
        private final Blueprint engine;

        public Parser(String input, Blueprint engine) {
            this.input = input;
            this.engine = engine;
            this.length = input.length();
            this.pos = 0;
        }

        /**
         * Parse nodes until one of the stopTags is encountered.
         *
         * @param stopTags a set of tag names that will cause the parser to return (without consuming the tag)
         * @return a list of nodes parsed
         */
        public List<Node> parseNodes(Set<String> stopTags) {
            List<Node> nodes = new ArrayList<>();
            while (pos < length) {
                if (peek("{{")) {
                    nodes.add(parseVariable());
                } else if (peek("{%")) {
                    // look ahead to check if this tag is a stop–tag
                    int tagEnd = input.indexOf("%}", pos);
                    if (tagEnd == -1) {
                        throw error("Tag not closed");
                    }
                    String tagContent = input.substring(pos + 2, tagEnd).trim();
                    String[] tagParts = tagContent.split("\\s+", 2);
                    String tagName = tagParts[0];
                    if (stopTags.contains(tagName)) {
                        // do not consume the stop tag; break out of this block
                        break;
                    }
                    nodes.add(parseTag());
                } else {
                    nodes.add(parseText());
                }
            }
            return nodes;
        }

        private boolean peek(String s) {
            return input.startsWith(s, pos);
        }

        // parse a text node until the next tag is encountered
        private Node parseText() {
            int start = pos;
            while (pos < length && !peek("{{") && !peek("{%")) {
                pos++;
            }
            String text = input.substring(start, pos);
            return new TextNode(text);
        }

        // parse a variable node. Assumes that pos is at "{{"
        private Node parseVariable() {
            int startPos = pos;
            pos += 2; // Skip {{
            skipWhitespace();
            int end = input.indexOf("}}", pos);
            if (end == -1) {
                throw error("Variable tag not closed starting at position " + startPos);
            }
            String exprStr = input.substring(pos, end).trim();
            pos = end + 2; // skip "}}" characters
            Expression expr = parseExpression(exprStr);
            return new VariableNode(expr);
        }

        // parse a tag node. Assumes that pos is at "{%".
        private Node parseTag() {
            int startPos = pos;
            pos += 2; // skip "{%" characters
            skipWhitespace();
            int tagEnd = input.indexOf("%}", pos);
            if (tagEnd == -1) {
                throw error("Tag not closed starting at position " + startPos);
            }
            String tagContent = input.substring(pos, tagEnd).trim();
            pos = tagEnd + 2; // skip "%}" characters
            String[] parts = tagContent.split("\\s+", 2);
            String tagName = parts[0];
            if ("if".equals(tagName)) {
                String conditionStr = (parts.length > 1 ? parts[1] : "");
                Expression condition = parseExpression(conditionStr);
                // parse the “true” branch until {% else %} or {% endif %}
                List<Node> trueNodes = parseNodes(new HashSet<>(Arrays.asList("else", "endif")));
                List<Node> falseNodes = null;
                if (matchTag("else")) {
                    falseNodes = parseNodes(Collections.singleton("endif"));
                }
                expectTag("endif");
                return new IfNode(condition, trueNodes, falseNodes);
            } else if ("for".equals(tagName)) {
                if (parts.length < 2) {
                    throw error("For tag missing arguments. Expected format: 'for item in list'");
                }
                // expected format: "for item in list"
                String[] tokens = parts[1].split("\\s+");
                if (tokens.length != 3 || !"in".equals(tokens[1])) {
                    throw error("Invalid for tag syntax. Expected format: 'for item in list'");
                }
                String loopVar = tokens[0];
                Expression listExpr = parseExpression(tokens[2]);
                List<Node> bodyNodes = parseNodes(Collections.singleton("endfor"));
                expectTag("endfor");
                return new ForNode(loopVar, listExpr, bodyNodes);
            } else if ("set".equals(tagName)) {
                if (parts.length < 2) {
                    throw error("Set tag missing arguments. Expected format: 'set var = expression'");
                }
                // expected format: "set var = expression"
                String[] assignmentParts = parts[1].split("=", 2);
                if (assignmentParts.length < 2) {
                    throw error("Invalid set tag syntax. Expected format: 'set var = expression'");
                }
                String varName = assignmentParts[0].trim();
                String exprStr = assignmentParts[1].trim();
                Expression expr = parseExpression(exprStr);
                return new SetNode(varName, expr);
            } else if ("raw".equals(tagName)) {
                // everything until the next "{% endraw %}" is treated as raw text.
                int endRawIndex = input.indexOf("{% endraw %}", pos);
                if (endRawIndex == -1) {
                    throw error("Raw tag not closed with {% endraw %}");
                }
                String rawContent = input.substring(pos, endRawIndex);
                pos = endRawIndex + "{% endraw %}".length();
                return new TextNode(rawContent);
            } else if ("macro".equals(tagName)) {
                if (parts.length < 2) {
                    throw error("Macro tag missing arguments. Expected format: 'macro macroName(param1, param2, ...)'");
                }
                // expected format: "macro macroName(param1, param2, ...)"
                String macroDef = parts[1].trim();
                int parenIndex = macroDef.indexOf('(');
                if (parenIndex == -1 || !macroDef.endsWith(")")) {
                    throw error("Invalid macro definition syntax. Expected format: 'macro macroName(param1, param2, ...)'");
                }
                String macroName = macroDef.substring(0, parenIndex).trim();
                String paramsStr = macroDef.substring(parenIndex + 1, macroDef.length() - 1).trim();
                List<String> parameters = new ArrayList<>();
                if (!paramsStr.isEmpty()) {
                    String[] paramTokens = paramsStr.split(",");
                    for (String p : paramTokens) {
                        parameters.add(p.trim());
                    }
                }
                List<Node> bodyNodes = parseNodes(Collections.singleton("endmacro"));
                expectTag("endmacro");
                return new MacroNode(macroName, parameters, bodyNodes);
            } else if ("else".equals(tagName) || "endif".equals(tagName) || "endfor".equals(tagName) || "endmacro".equals(tagName)) {
                throw error("Unexpected tag: " + tagName);
            } else {
                throw error("Unknown tag: " + tagName);
            }
        }

        // if the next tag (i.e. the next "{% ... %}") matches the expected tagName,
        // consume it and return true, otherwise, return false (and make sure to not consume)
        private boolean matchTag(String tagName) {
            int savedPos = pos;
            if (!peek("{%")) {
                return false;
            }
            int tagEnd = input.indexOf("%}", pos);
            if (tagEnd == -1) {
                return false;
            }
            String tagContent = input.substring(pos + 2, tagEnd).trim();
            String[] parts = tagContent.split("\\s+", 2);
            if (parts.length > 0 && parts[0].equals(tagName)) {
                pos = tagEnd + 2; // consume the tag.
                return true;
            }
            pos = savedPos;
            return false;
        }

        private void expectTag(String tagName) {
            if (!matchTag(tagName)) {
                throw error("Expected tag: " + tagName);
            }
        }

        private void skipWhitespace() {
            while (pos < length && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private RuntimeException error(String message) {
            String snippet = "..." + input.substring(Math.max(0, pos - 30), Math.min(length, pos + 30));
            return new RuntimeException(message + " (pos " + pos + ", near: \"" + snippet + "\")");
        }

        private List<String> splitArguments(String argsStr) {
            List<String> args = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            char quoteChar = '\0';

            for (int i = 0; i < argsStr.length(); i++) {
                char c = argsStr.charAt(i);
                if (inQuotes) {
                    if (c == quoteChar) {
                        inQuotes = false;
                    }
                    current.append(c);
                } else {
                    if (c == '\'' || c == '"') {
                        inQuotes = true;
                        quoteChar = c;
                        current.append(c);
                    } else if (c == ',') {
                        args.add(current.toString().trim());
                        current.setLength(0);
                    } else {
                        current.append(c);
                    }
                }
            }
            if (current.length() > 0) {
                args.add(current.toString().trim());
            }
            return args;
        }

        // parse an expression from a string.
        // Supports filters (using |) and logical/arithmetic expressions.
        private Expression parseExpression(String exprStr) {
            // first, split on the pipe character for filters.
            String[] parts = exprStr.split("\\|");
            Expression base = parseLogicalExpression(parts[0].trim());
            if (parts.length > 1) {
                List<Filter> filterList = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) {
                    String filterPart = parts[i].trim();
                    int parenIndex = filterPart.indexOf('(');
                    if (parenIndex != -1 && filterPart.endsWith(")")) {
                        String filterName = filterPart.substring(0, parenIndex).trim();
                        String argsStr = filterPart.substring(parenIndex + 1, filterPart.length() - 1).trim();
                        List<Expression> filterArgs = new ArrayList<>();
                        if (!argsStr.isEmpty()) {
                            List<String> argTokens = splitArguments(argsStr);
                            for (String token : argTokens) {
                                filterArgs.add(parseExpression(token));
                            }
                        }
                        filterList.add(new Filter(filterName, filterArgs));
                    } else {
                        filterList.add(new Filter(filterPart, Collections.emptyList()));
                    }
                }
                return new FilteredExpression(base, filterList);
            } else {
                return base;
            }
        }

        // parse a logical/arithmetic expression (with support for "or", "and", "not", comparisons, etc.)
        // using a recursive descent parser.
        private Expression parseLogicalExpression(String expr) {
            ExpressionParser parser = new ExpressionParser(expr);
            Expression result = parser.parseExpression();
            parser.skipWhitespace();
            if (!parser.isEnd()) {
                throw new RuntimeException("Unexpected characters in expression: " + expr.substring(parser.pos));
            }
            return result;
        }

        // a simple recursive descent parser for expressions
        private class ExpressionParser {
            private final String input;
            private int pos;

            public ExpressionParser(String input) {
                this.input = input;
                this.pos = 0;
            }

            public Expression parseExpression() {
                return parseOr();
            }

            // or-expression: andExpr ('or' andExpr)*
            private Expression parseOr() {
                Expression expr = parseAnd();
                while (true) {
                    skipWhitespace();
                    if (matchKeyword("or")) {
                        Expression right = parseAnd();
                        expr = new BinaryExpression(expr, "or", right);
                    } else {
                        break;
                    }
                }
                return expr;
            }

            // and-expression: equalityExpr ('and' equalityExpr)*
            private Expression parseAnd() {
                Expression expr = parseEquality();
                while (true) {
                    skipWhitespace();
                    if (matchKeyword("and")) {
                        Expression right = parseEquality();
                        expr = new BinaryExpression(expr, "and", right);
                    } else {
                        break;
                    }
                }
                return expr;
            }

            // equality-expression: relationalExpr (("==" | "!=") relationalExpr)*
            private Expression parseEquality() {
                Expression expr = parseRelational();
                while (true) {
                    skipWhitespace();
                    if (match("==")) {
                        Expression right = parseRelational();
                        expr = new BinaryExpression(expr, "==", right);
                    } else if (match("!=")) {
                        Expression right = parseRelational();
                        expr = new BinaryExpression(expr, "!=", right);
                    } else {
                        break;
                    }
                }
                return expr;
            }

            // relational-expression: additiveExpr ((">" | ">=" | "<" | "<=") additiveExpr)*
            private Expression parseRelational() {
                Expression expr = parseAdditive();
                while (true) {
                    skipWhitespace();
                    if (match(">=")) {
                        Expression right = parseAdditive();
                        expr = new BinaryExpression(expr, ">=", right);
                    } else if (match("<=")) {
                        Expression right = parseAdditive();
                        expr = new BinaryExpression(expr, "<=", right);
                    } else if (match(">")) {
                        Expression right = parseAdditive();
                        expr = new BinaryExpression(expr, ">", right);
                    } else if (match("<")) {
                        Expression right = parseAdditive();
                        expr = new BinaryExpression(expr, "<", right);
                    } else {
                        break;
                    }
                }
                return expr;
            }

            // additive-expression: multiplicativeExpr (("+" | "-") multiplicativeExpr)*
            private Expression parseAdditive() {
                Expression expr = parseMultiplicative();
                while (true) {
                    skipWhitespace();
                    if (match("+")) {
                        Expression right = parseMultiplicative();
                        expr = new BinaryExpression(expr, "+", right);
                    } else if (match("-")) {
                        Expression right = parseMultiplicative();
                        expr = new BinaryExpression(expr, "-", right);
                    } else {
                        break;
                    }
                }
                return expr;
            }

            // multiplicative-expression: powerExpr (("*" | "/" | "%") powerExpr)*
            private Expression parseMultiplicative() {
                Expression expr = parsePower();
                while (true) {
                    skipWhitespace();
                    if (match("*")) {
                        Expression right = parsePower();
                        expr = new BinaryExpression(expr, "*", right);
                    } else if (match("/")) {
                        Expression right = parsePower();
                        expr = new BinaryExpression(expr, "/", right);
                    } else if (match("%")) {
                        Expression right = parsePower();
                        expr = new BinaryExpression(expr, "%", right);
                    } else {
                        break;
                    }
                }
                return expr;
            }

            // power-expression: unaryExpr ("**" powerExpr)?
            // Note: power operator is right–associative.
            private Expression parsePower() {
                Expression expr = parseUnary();
                skipWhitespace();
                if (match("**")) {
                    Expression right = parsePower();
                    expr = new BinaryExpression(expr, "**", right);
                }
                return expr;
            }

            // unary-expression: ("not" | "-")? primary
            private Expression parseUnary() {
                skipWhitespace();
                if (matchKeyword("not")) {
                    Expression operand = parseUnary();
                    return new UnaryExpression("not", operand);
                } else if (match("-")) {
                    Expression operand = parseUnary();
                    return new UnaryExpression("-", operand);
                } else {
                    return parsePrimary();
                }
            }

            // primary: object literal, array literal, number, string, variable, function call, or parenthesized expression
            private Expression parsePrimary() {
                skipWhitespace();
                if (isEnd()) {
                    throw error("Unexpected end of expression");
                }
                char ch = peek();
                // object literal
                if (ch == '{') {
                    return parseObject();
                }
                // array literal
                if (ch == '[') {
                    return parseArray();
                }
                if (match("(")) {
                    Expression expr = parseExpression();
                    skipWhitespace();
                    if (!match(")")) {
                        throw error("Expected ')' after expression");
                    }
                    return expr;
                }
                if (ch == '"' || ch == '\'') {
                    return parseString();
                }
                if (isDigit(ch)) {
                    return parseNumber();
                }
                // parse an identifier (for variable or function name)
                String ident = parseIdentifier();
                skipWhitespace();
                if (match("(")) {
                    // function call.
                    List<Expression> args = new ArrayList<>();
                    skipWhitespace();
                    if (!match(")")) {
                        do {
                            Expression arg = parseExpression();
                            args.add(arg);
                            skipWhitespace();
                        } while (match(","));
                        if (!match(")")) {
                            throw error("Expected ')' in function call");
                        }
                    }
                    return new FunctionCallExpression(ident, args);
                } else {
                    // variable expression. To support bracket based indexing "user.colors[0]" we handle any dot or bracket parts
                    StringBuilder varBuilder = new StringBuilder(ident);
                    while (true) {
                        skipWhitespace();
                        if (match(".")) {
                            varBuilder.append(".");
                            varBuilder.append(parseIdentifier());
                        } else if (peek() == '[') {
                            int start = pos;
                            int bracketCount = 0;
                            while (pos < input.length()) {
                                char c = consume();
                                if (c == '[') {
                                    bracketCount++;
                                } else if (c == ']') {
                                    bracketCount--;
                                    if (bracketCount == 0) {
                                        break;
                                    }
                                }
                            }
                            String bracketPart = input.substring(start, pos);
                            varBuilder.append(bracketPart);
                        } else {
                            break;
                        }
                    }
                    return new VariableExpression(varBuilder.toString());
                }
            }

            private Expression parseString() {
                char quote = consume();
                StringBuilder sb = new StringBuilder();
                while (pos < input.length() && peek() != quote) {
                    char c = consume();
                    if (c == '\\' && pos < input.length()) {
                        c = consume();
                    }
                    sb.append(c);
                }
                if (!match(String.valueOf(quote))) {
                    throw error("Unterminated string literal");
                }
                return new LiteralExpression(sb.toString());
            }

            private Expression parseNumber() {
                int start = pos;
                while (pos < input.length() && (isDigit(peek()) || peek() == '.')) {
                    consume();
                }
                String numStr = input.substring(start, pos);
                try {
                    if (numStr.contains(".")) {
                        return new LiteralExpression(Double.parseDouble(numStr));
                    } else {
                        return new LiteralExpression(Integer.parseInt(numStr));
                    }
                } catch (NumberFormatException e) {
                    throw error("Invalid number format: " + numStr);
                }
            }

            private String parseIdentifier() {
                skipWhitespace();
                int start = pos;
                while (pos < input.length() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
                    consume();
                }
                if (start == pos) {
                    throw error("Expected identifier");
                }
                return input.substring(start, pos);
            }

            // parse an object literal: { key: value, ... }
            private Expression parseObject() {
                // consume '{'
                consume();
                skipWhitespace();
                Map<String, Expression> map = new HashMap<>();
                if (peek() == '}') {
                    consume();
                    return new ObjectLiteralExpression(map);
                }
                while (true) {
                    skipWhitespace();
                    String key;
                    if (peek() == '"' || peek() == '\'') {
                        LiteralExpression keyExpr = (LiteralExpression) parseString();
                        key = keyExpr.evaluate(null).toString();
                    } else {
                        key = parseIdentifier();
                    }
                    skipWhitespace();
                    if (!match(":")) {
                        throw error("Expected ':' in object literal");
                    }
                    skipWhitespace();
                    Expression valueExpr = parseExpression();
                    map.put(key, valueExpr);
                    skipWhitespace();
                    if (match("}")) {
                        break;
                    }
                    if (!match(",")) {
                        throw error("Expected ',' in object literal");
                    }
                    skipWhitespace();
                }
                return new ObjectLiteralExpression(map);
            }

            // parse an array literal: [ expr, expr, ... ]
            private Expression parseArray() {
                // consume '['
                consume();
                skipWhitespace();
                List<Expression> elements = new ArrayList<>();
                if (peek() == ']') {
                    consume();
                    return new ArrayLiteralExpression(elements);
                }
                while (true) {
                    skipWhitespace();
                    Expression element = parseExpression();
                    elements.add(element);
                    skipWhitespace();
                    if (match("]")) {
                        break;
                    }
                    if (!match(",")) {
                        throw error("Expected ',' in array literal");
                    }
                    skipWhitespace();
                }
                return new ArrayLiteralExpression(elements);
            }

            private void skipWhitespace() {
                while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                    pos++;
                }
            }

            private boolean match(String s) {
                skipWhitespace();
                if (input.startsWith(s, pos)) {
                    pos += s.length();
                    return true;
                }
                return false;
            }

            private boolean matchKeyword(String kw) {
                skipWhitespace();
                int end = pos + kw.length();
                if (end <= input.length() && input.substring(pos, end).equals(kw)) {
                    if (end == input.length() || !Character.isLetterOrDigit(input.charAt(end))) {
                        pos = end;
                        return true;
                    }
                }
                return false;
            }

            private char peek() {
                if (pos < input.length()) {
                    return input.charAt(pos);
                }
                return '\0';
            }

            private char consume() {
                return input.charAt(pos++);
            }

            public boolean isEnd() {
                return pos >= input.length();
            }
        }
    }
}
