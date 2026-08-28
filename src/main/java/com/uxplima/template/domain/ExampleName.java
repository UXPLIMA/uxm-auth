package com.uxplima.template.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The name of an example, as the domain models it. A name is lower case, it holds only letters, digits and
 * a hyphen, and it is between one and 32 characters long. The type exists so that an invalid name cannot
 * reach a use case: the constructor is the only way to make one, and it refuses anything else.
 *
 * <p>This is the shape that every value object in a plugin takes. Replace it with the real one.
 */
public record ExampleName(String value) {

    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9-]{1,32}$");

    public ExampleName {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        value = value.toLowerCase(Locale.ROOT);
        if (!ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException("name must match " + ALLOWED.pattern() + ", got: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
