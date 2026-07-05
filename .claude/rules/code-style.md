# Code Style

## Local variables — always `var`

Java 25. Use `var` for **every local variable** where the compiler allows it —
never write the explicit type when `var` works.

```java
// good
var pool = new LinkedHashMap<String, StoredRawOffer>();
var offers = connector.fetchPage(page);
for (var offer : offers) { ... }

// avoid
LinkedHashMap<String, StoredRawOffer> pool = new LinkedHashMap<>();
List<RawJobOffer> offers = ...;
```

- Plain `var`, **not** `final var`.
- Applies to locals with an initializer and to enhanced-for loop variables.
- `var` is **not** legal for fields, method parameters, method return types,
  record components, or locals without an initializer — keep explicit types there.
- Keep an explicit type only when `var` genuinely won't compile (e.g. the
  initializer is `null`, an array initializer `{...}`, or a lambda/method-ref
  that needs a target type).
