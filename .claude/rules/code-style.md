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

## Comments — "why", not "what"

- Comment only to explain **why** (a non-obvious reason, constraint, or trade-off),
  never **what** the code does — the code says that itself.
- Short: one line where possible.
- Timeless: no ticket numbers, PR links, URLs, dates, or history
  ("changed from X", "added for VR-NN") — that belongs in git.
- No section dividers, no javadoc that restates the method or parameter names.

```java
// good — the reason is not visible in the code
// insurance before finance: "insurance" texts often mention "financial" too
INSURANCE(...),

// avoid — restates the code / not timeless
/** Returns the domain of the industry. */
// VR-42: added per review, see https://...
```

## Blank lines — separate the steps of a method

A method body reads as steps: guard → prepare → process → result. Put one blank line
between steps, so each step is seen at a glance.

- After a guard clause (`if (...) { return/throw }`) at the top.
- After declarations/setup, before the loop or stream that uses them.
- After a loop or multi-line block, before what follows.
- Before the final `return` when something precedes it.
- Never two blank lines in a row; inside a method, no blank line right after `{` or before `}`.
- A short method of one step (2–3 lines) stays without blank lines.
- If a method needs many blank lines, it probably needs splitting or simplifying instead.

```java
// good
private double remotePercentage(List<RawJobOffer> offers, int total) {
  if (total == 0) {
    return 0.0;
  }

  var remote = offers.stream()
      .filter(o -> Boolean.TRUE.equals(o.remote()))
      .count();

  return remote * 100.0 / total;
}

// good — one step, no blank lines needed
private String hash(String text) throws NoSuchAlgorithmException {
  var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8));
  return HexFormat.of().formatHex(digest);
}
```
