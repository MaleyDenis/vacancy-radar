---
paths:
  - "src/**/service/**"
---

# Claude API Rules

- Model: `claude-haiku-5-5`, `output_config.effort = low` (adaptive thinking is on by default and billed as output; low keeps it small). ~3.6K in / ~1K out tokens per extraction ≈ $0.001/offer
- Haiku 5.5 beat Haiku 4.5 on 10 offers (2026-11-08): fewer invented values (4.5 made up English levels absent from the text), correct companyType, nulls where the text is silent
- Input: plain text description (~1500 tokens) + resume text (scoring only)
- Output: strictly typed JSON (structured output via Anthropic Java SDK)
- NEVER send raw HTML to Claude — plain text only
- Do NOT switch to Sonnet/Opus without approval — cost scales with offer volume
