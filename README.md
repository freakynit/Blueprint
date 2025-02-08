# Blueprint Templating Engine

1. Blueprint is a single class, lightweight, easily extensible, zero external dependencies, and java-8 compatible templating engine for Java with same easily readable syntax as [Nunjucks](https://mozilla.github.io/nunjucks/).
2. Blueprint is pretty fast too. Following results are on a M1 Mac 8GB:
    - Single core: `163_767 ops/s`, 4-core: `572_487 ops/s` for a large template using all available capabilities ([full.blu](src/main/resources/full.blu)).
    - Single core: `3_147_166 ops/s`(3+Million) , 4-core: `10_278_819 ops/s`(10+Million) for a small template using variables and if-else conditions only ([small.blu](src/main/resources/small.blu)).
    - See [`Performance`](#Performance) section for details.

---

## Features

- **Variable Interpolation**  
  Embed variables directly in your templates using the `{{ ... }}` syntax.

- **Conditional Statements**  
  Use `{% if %}`, `{% else %}`, and `{% endif %}` to control what content is rendered based on dynamic conditions.

- **Loops**  
  Iterate over collections with `{% for item in list %} ... {% endfor %}`. A special `loop` variable is automatically injected, offering iteration details such as the current index (`loop.index`.. starts from 0).

- **Set Assignment**  
  Assign values to new or existing variables within your template using `{% set variable = expression %}`.

- **Macros**  
  Define reusable template snippets with parameters using `{% macro name(params) %} ... {% endmacro %}`. Macros allow you to encapsulate logic and rendering blocks that you can reuse elsewhere in your template.

- **Raw Blocks**  
  Prevent processing of content (e.g., when you need to output template syntax literally) with `{% raw %} ... {% endraw %}`.

- **Custom Functions and Filters**  
  Extend Blueprint by registering your own functions and filters. Filters are applied via the pipe operator (`|`) on expressions.

- **Rich Expression Support**  
  Blueprint’s expression parser supports:
    - **Arithmetic Operators:** `+`, `-`, `*`, `/`, `%`, `**` (power)
    - **Comparison Operators:** `==`, `!=`, `>`, `>=`, `<`, `<=`
    - **Logical Operators:** `and`, `or`, `not`
    - **Unary Operators:** `-` (negation)
    - **Parentheses** for grouping
    - **Object & Array Literals:** e.g., `{ firstName: "Alice", age: 30 }` and `[1, 2, 3]`
    - **Function Calls:** e.g., `upper(name)`
    - **Filters:** Chain transformations such as `{{ name | capitalize }}`

- **Dynamic Property Resolution**  
  Supports dot–notation (`user.name`) and bracket–notation (`matrix[1][2]`) to access nested object properties and array elements. Reflection-based property lookup (when using class instances instead of Map for context data) is cached for improved performance.

- **separate compile and render phases**
  For repeated usage of same template (with different data/context), compile the template once and re-use the template for different data sets. See [`SampleUsage.java`](src/main/java/com/freakynit/usage/SampleUsage.java) or [`BenchmarkRunner.java`](src/main/java/com/freakynit/benchmark/BenchmarkRunner.java).

---

## Getting Started

### 1. Add Blueprint to Your Project

1. Just include one java class: [`Blueprint.java`](src/main/java/com/freakynit/blueprint/Blueprint.java) source file in your Java project (Not published on maven yet). No other dependencies are there.
2. In case you need a few utility functions and filters too, like, `upper`, `length`, `join`, `truncate`, `reverse`, `replace`, etc., you can add [StdUtils.java](src/main/java/com/freakynit/blueprint/StdUtils.java) too. This is not needed otherwise. Check out [StdUtils](#StdUtils) section for details.

### 2. Basic Usage Example

Below is a quick example showing how to register a custom function, compile a template, and render it with a context:

```java
import com.freakynit.blueprint.Blueprint;
import com.freakynit.blueprint.Blueprint.TemplateFunction;

import java.util.HashMap;
import java.util.Map;

public class BlueprintExample {
    public static void main(String[] args) {
        // Create a new Blueprint engine instance.
        Blueprint engine = new Blueprint();

        // Register a custom function "upper" to convert text to uppercase.
        engine.registerFunction("upper", (context, argsList) -> {
            String input = argsList.get(0).toString();
            return input.toUpperCase();
        });

        // Define a simple template with variable interpolation and filter usage.
        String templateSource = "Hello, {{ upper(name) }}!";

        // Create a context with variables.
        Map<String, Object> context = new HashMap<>();
        context.put("name", "world");

        // Render the template.
        String output = engine.render(templateSource, context);
        System.out.println(output);  // Output: "Hello, WORLD!"
    }
}
```

**Note:** You can also load your template from resources directory or local file system. See [`BenchmarkRunner.java`](src/main/java/com/freakynit/benchmark/BenchmarkRunner.java) for reference. 

The official extension for `Blueprint` templating engine is `blu`. 

---

## Template Syntax Examples

### Variable Interpolation

Embed variables using double curly braces:

```jinja
Hello, {{ user.name }}!
```

If `user.name` is `"Alice"`, the rendered output will be:

```
Hello, Alice!
```

### Conditional Statements

Render content conditionally using `{% if %}` blocks:

```jinja
{% if user.age >= 18 %}
  Welcome, adult user!
{% else %}
  Sorry, you are too young.
{% endif %}
```

Depending on the value of `user.age`, the appropriate branch is rendered.

### Loops

Iterate over collections with a `for` loop:

```jinja
{% for color in user.colors %}
  Color: {{ color }}
{% endfor %}
```

For `user.colors = ["red", "green", "blue"]`, the rendered output will be:

```
Color: red
Color: green
Color: blue
```

A special `loop` variable is available to provide iteration details (e.g., `loop.index`).

### Set Assignment

Assign values to variables within the template:

```jinja
{% set greeting = "Hello" %}
{{ greeting }}, World!
```

This produces:

```
Hello, World!
```

### Macros

Define reusable template blocks with macros:

```jinja
{% macro shout(text) %}
  {{ upper(text) }}!!!
{% endmacro %}

{{ shout("hello") }}
```

In this example:
- A macro named `shout` is defined to take a parameter `text`.
- It calls the custom `upper` function (registered in your Java code) to convert text to uppercase.
- Rendering produces: `HELLO!!!`

### Raw Blocks

Output content verbatim without processing:

```jinja
{% raw %}
This will not be processed: {{ this is raw }}
{% endraw %}
```

Everything inside the raw block is output exactly as written.

### Expressions & Operations

Blueprint supports complex expressions, including arithmetic and logical operations:

```jinja
{{ 5 + 3 * 2 }}          {# Evaluates to 11 #}
{{ (a + b) * c }}        {# Uses parenthesized grouping #}
{{ user.age >= 18 and user.active }}
```

You can also define object and array literals:

```jinja
{% set person = { firstName: "Alice", lastName: "Smith", age: 30 } %}
{% set colors = ["red", "green", "blue"] %}
```

---

## Extending Blueprint with Custom Functions & Filters

Blueprint allows you to register your own functions and filters to extend template capabilities. The only difference is that filters automatically receive the current value as the first argument.

### Registering a Custom Filter

For example, to register a filter that capitalizes text:

```java
engine.registerFilter("capitalize", (context, argsList) -> {
    String input = argsList.get(0).toString();
    if (input.isEmpty()) return input;
    return input.substring(0, 1).toUpperCase() + input.substring(1);
});
```

You can then use it in your template like so:

```jinja
{{ name | capitalize }}
```

If `name` is `"alice"`, the rendered output will be `"Alice"`.

---

## Under the Hood

Blueprint parses your template into an Abstract Syntax Tree (AST) composed of various node types:

- **TextNode:** Represents plain text.
- **VariableNode:** Handles variable interpolation.
- **IfNode:** Manages conditional blocks.
- **ForNode:** Iterates over collections.
- **SetNode:** Represents variable assignments.
- **MacroNode:** Holds macro definitions.

Each node implements a `render` method that outputs content based on the provided context. The engine’s expression parser further supports literals, variable references, function calls, filters, and both binary and unary operators.

The engine also uses reflection to resolve properties from objects (supporting dot–notation and bracket–notation) and caches getter method lookups for optimal performance.

---

## StdUtils
Contains definitions for many commonly used functions and filters. You can refer these for understanding if needed. To register all these:
```java
new StdUtils().registerAll(engine);
```
Or register only selective ones, for example:
```java
// Function
new StdUtils.Functions().registerUpper("myUpper", engine);
// Usage example: Hello, {{ myUpper(name) }}

// Filter
new StdUtils.Filters().registerReverse("myReverse", engine);
// Usage example: Reversed: {{ name | myReverse }}
```
Usage is same as defined above for functions and filters

---

## Performance
1. JMH benchmarks are integrated. Check out [`BenchmarkRunner.java`](src/main/java/com/freakynit/benchmark/BenchmarkRunner.java).
2. Numbers: 
    - Single core: `163_767 ops/s`, 4-core: `572_487 ops/s` for a large template using all available capabilities ([full.blu](src/main/resources/full.blu)). 
    - Single core: `3_147_166 ops/s`(3+Million) , 4-core: `10_278_819 ops/s`(10+Million) for a small template using variables and if-else conditions only ([small.blu](src/main/resources/small.blu)).
3. Detailed results available in [`jmh_report_template_full.txt`]('jmh_report_template_full.txt') and [`jmh_report_template_small.txt`]('jmh_report_template_small.txt').
4. Tested on M1 Mac, 8GB
5. Running benchmark with demo template (`full.blu`)
> You can adjust the template in `BenchmarkRunner.java` by adjusting just this single line: `String templateFileName = "full.blu";  // or small.blu`
```shell
mvn clean package
java -jar target/blueprint-1.0.1.jar
```

---

## Contributing

Contributions, feedback, and feature requests are welcome! Feel free to fork the repository, submit pull requests, or open issues to help improve Blueprint.

---

## License

Blueprint is released under an open-source license. See the [LICENSE](LICENSE) file for details.

