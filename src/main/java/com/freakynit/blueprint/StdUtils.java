package com.freakynit.blueprint;

import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.*;

public class StdUtils {
    public void registerAll(Blueprint engine) {
        new Functions().registerAll(engine);
        new Filters().registerAll(engine);
    }

    public static class Functions {
        public void registerAll(Blueprint engine) {
            registerLower("lower", engine);
            registerUpper("upper", engine);
            registerLength("length", engine);
            registerJoin("join", engine);
            registerDefault("default", engine);
            registerRandomInt("randomInt", engine);
            registerAbs("abs", engine);
            registerNowISO601("now", engine);
        }

        public void registerLower(String name, Blueprint engine) {
            engine.registerFunction(name, (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return "";
                }
                return argsList.get(0).toString().toLowerCase();
            });
        }

        public void registerUpper(String name, Blueprint engine) {
            engine.registerFunction(name, (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return "";
                }
                return argsList.get(0).toString().toUpperCase();
            });
        }

        public void registerLength(String name, Blueprint engine) {
            engine.registerFunction(name, (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return 0;
                }
                Object value = argsList.get(0);
                if (value instanceof String) {
                    return ((String) value).length();
                } else if (value instanceof Collection) {
                    return ((Collection<?>) value).size();
                } else if (value.getClass().isArray()) {
                    return Array.getLength(value);
                }
                return 0;
            });
        }

        public void registerJoin(String name, Blueprint engine) {
            engine.registerFilter(name, (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return "";
                }
                String delimiter = argsList.size() > 1 ? argsList.get(1).toString() : "";
                Object collection = argsList.get(0);
                List<String> items = new ArrayList<>();
                if (collection instanceof Iterable) {
                    for (Object item : (Iterable<?>) collection) {
                        items.add(item.toString());
                    }
                } else if (collection.getClass().isArray()) {
                    int len = Array.getLength(collection);
                    for (int i = 0; i < len; i++) {
                        Object item = Array.get(collection, i);
                        items.add(item.toString());
                    }
                } else {
                    return collection.toString();
                }
                return String.join(delimiter, items);
            });
        }

        public void registerDefault(String name, Blueprint engine) {
            engine.registerFunction(name, (context, argsList) -> {
                if (argsList.size() < 2) {
                    return argsList.get(0);
                }
                Object value = argsList.get(0);
                Object defaultValue = argsList.get(1);
                if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                    return defaultValue;
                }
                return value;
            });
        }

        public void registerRandomInt(String name, Blueprint engine) {
            engine.registerFunction(name, (context, argsList) -> {
                int min = argsList.size() > 0 ? ((Number) argsList.get(0)).intValue() : 0;
                int max = argsList.size() > 1 ? ((Number) argsList.get(1)).intValue() : 100;
                return new Random().nextInt(max - min + 1) + min;
            });
        }

        public void registerAbs(String name, Blueprint engine) {
            engine.registerFunction(name, (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return "";
                }

                Object value = argsList.get(0);
                if (!(value instanceof Number)) {
                    return value;
                }

                Number number = (Number) value;

                if (number instanceof Integer) {
                    return (number.intValue() < 0) ? -number.intValue() : number.intValue();
                } else if (number instanceof Long) {
                    return (number.longValue() < 0) ? -number.longValue() : number.longValue();
                } else if (number instanceof Double) {
                    return (number.doubleValue() < 0) ? -number.doubleValue() : number.doubleValue();
                } else if (number instanceof Float) {
                    return (number.floatValue() < 0) ? -number.floatValue() : number.floatValue();
                } else if (number instanceof Short) {
                    return (number.shortValue() < 0) ? -number.shortValue() : number.shortValue();
                } else if (number instanceof Byte) {
                    return (number.byteValue() < 0) ? -number.byteValue() : number.byteValue();
                }

                return number;
            });
        }

        public void registerNowISO601(String name, Blueprint engine) {
            engine.registerFunction(name, (context, argsList) -> {
                String format = argsList.size() > 0 ? argsList.get(0).toString() : "yyyy-MM-dd'T'HH:mm:ss'Z'";
                return new SimpleDateFormat(format).format(new Date());
            });
        }
    }

    public static class Filters {
        public void registerAll(Blueprint engine) {
            registerTruncate("truncate", engine);
            registerReverse("reverse", engine);
            registerReplace("replace", engine);
            registerSort("sort", engine);
            registerUnique("unique", engine);
            registerRound("round", engine);
        }

        public void registerTruncate(String name, Blueprint engine) {
            engine.registerFilter(name, (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return "";
                }
                String input = argsList.get(0).toString();
                int length = argsList.size() > 1 ? ((Number) argsList.get(1)).intValue() : 50;
                String suffix = argsList.size() > 2 ? argsList.get(2).toString() : "...";
                if (input.length() <= length) {
                    return input;
                }
                return input.substring(0, length) + suffix;
            });
        }

        public void registerReverse(String name, Blueprint engine) {
            engine.registerFilter(name, (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return "";
                }

                Object value = argsList.get(0);

                if (value instanceof String) {
                    return new StringBuilder((String) value).reverse().toString();
                } else if (value instanceof List<?>) {
                    List<?> list = (List<?>) value;
                    List<Object> reversed = new ArrayList<>(list);
                    Collections.reverse(reversed);
                    return reversed;
                } else if (value.getClass().isArray()) {
                    int length = Array.getLength(value);
                    Object reversedArray = Array.newInstance(value.getClass().getComponentType(), length);

                    for (int i = 0; i < length; i++) {
                        Array.set(reversedArray, i, Array.get(value, length - 1 - i));
                    }

                    return reversedArray;
                }

                return value;
            });
        }

        public void registerReplace(String name, Blueprint engine) {
            engine.registerFilter(name, (context, argsList) -> {
                if (argsList.size() < 3) {
                    return argsList.get(0);
                }
                String input = argsList.get(0).toString();
                String target = argsList.get(1).toString();
                String replacement = argsList.get(2).toString();
                return input.replace(target, replacement);
            });
            // A filter "capitalize": capitalizes the first character.
            engine.registerFilter("capitalize", (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return "";
                }
                String s = argsList.get(0).toString();
                if (s.isEmpty()) return s;
                return s.substring(0, 1).toUpperCase() + s.substring(1);
            });
        }

        public void registerSort(String name, Blueprint engine) {
            engine.registerFilter(name, (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return "";
                }

                Object value = argsList.get(0);

                Comparator<Object> comparator = (a, b) -> {
                    if (a instanceof Comparable && b instanceof Comparable) {
                        return ((Comparable) a).compareTo(b);
                    }
                    return a.toString().compareTo(b.toString()); // Fallback to string comparison
                };

                if (value instanceof List<?>) {
                    List<?> list = (List<?>) value;
                    List<Object> sorted = new ArrayList<>(list);
                    sorted.sort(comparator);
                    return sorted;
                } else if (value.getClass().isArray()) {
                    Object[] array = (Object[]) value;
                    Object[] sortedArray = Arrays.copyOf(array, array.length);
                    Arrays.sort(sortedArray, comparator);
                    return sortedArray;
                }

                return value;
            });
        }

        public void registerUnique(String name, Blueprint engine) {
            engine.registerFilter(name, (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return "";
                }
                Object value = argsList.get(0);
                if (value instanceof List) {
                    return new ArrayList<>(new LinkedHashSet<>((List<?>) value));
                } else if (value.getClass().isArray()) {
                    return Arrays.stream((Object[]) value).distinct().toArray();
                }
                return value;
            });
        }

        public void registerRound(String name, Blueprint engine) {
            engine.registerFilter(name, (context, argsList) -> {
                if (argsList.isEmpty() || argsList.get(0) == null) {
                    return "";
                }
                double num = ((Number) argsList.get(0)).doubleValue();
                int precision = argsList.size() > 1 ? ((Number) argsList.get(1)).intValue() : 0;
                double factor = Math.pow(10, precision);
                return Math.round(num * factor) / factor;
            });
        }
    }
}
