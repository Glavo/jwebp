# Java Code Style Requirements

These rules apply to all Java code written or modified in this repository.

## Nullability

- Annotate every class with JetBrains Annotations `@NotNullByDefault`.
- Any type, field, parameter, return value, local variable, or generic type argument that may be `null` must be explicitly annotated with `@Nullable`.
- Nullability must never be implicit.

## Optional Values

- Do not use Java `Optional`.
- Represent optional or absent values with `@Nullable` instead.
- Do not introduce APIs that require callers to unwrap `Optional`.

## Java Types

- Use Java `record` types when they fit the data model.

## Immutability Annotations

- Annotate immutable collections and arrays with JetBrains Annotations `@Unmodifiable`.
- Annotate immutable collection views with JetBrains Annotations `@UnmodifiableView`.
- Annotate immutable NIO buffers such as `ByteBuffer`, `IntBuffer`, `LongBuffer`, and other `Buffer`
  subclasses with `@Unmodifiable`.
- Annotate read-only or immutable views of NIO buffers with `@UnmodifiableView`.
- For arrays, place the annotation on the array dimension, for example `String @Unmodifiable []`.
- For multidimensional immutable arrays, annotate every immutable dimension, for example
  `int @Unmodifiable [] @Unmodifiable []`.

## Runtime Dependencies

- Except for tests, all code in this repository must run with no runtime dependencies other than the `java.base` module.
- Do not introduce runtime dependencies on external libraries, Maven artifacts, or JDK modules other than `java.base`.
- If a feature would require another dependency at runtime, redesign it, remove it, or confine it strictly to tests.

## Documentation

- Every class, field, and method must have documentation.
- Documentation must use `///` Markdown-style Javadoc comments.
- Keep documentation accurate and specific to the actual behavior, constraints, and side effects.
- Add concise implementation comments inside complex logic whenever they materially improve readability or explain non-obvious behavior.

