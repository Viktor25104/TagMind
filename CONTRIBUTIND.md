# Contributing to TagMind

Thank you for your interest in contributing.

This project is intentionally strict about contributions to ensure
security, legal clarity, and long-term maintainability.

---

## License Agreement

By submitting any contribution (code, documentation, configuration, ideas),
you **explicitly agree** that:

- Your contribution is licensed under **GNU Affero General Public License v3.0**
- You have the legal right to submit the contribution
- You do not introduce code with incompatible licenses

No exceptions.

---

## Contribution Rules

### 1. No License Pollution
Do NOT submit code that:
- Is copied from proprietary sources
- Is licensed under incompatible licenses
- Introduces unclear copyright ownership

All code must be clean, original, or AGPL-compatible.

---

### 2. Security First
Contributions must NOT:
- Weaken access controls
- Bypass admin or allowlist logic
- Introduce hidden telemetry
- Log sensitive user data
- Store secrets, tokens, or credentials in code

Security-sensitive changes may be rejected without explanation.

---

### 3. No Abuse-Oriented Features
The following will be rejected immediately:
- Doxxing or surveillance features
- Stalking or deanonymization logic
- Private data harvesting
- Mass scraping or crawling abuse
- Telegram ToS or Google CSE ToS violations

This project is for **lawful, ethical use only**.

---

## Coding Standards

- Keep services **single-responsibility**
- Respect service boundaries (do not blur microservices)
- No hardcoded secrets
- Clear error handling and timeouts
- Observability-friendly code (logs, metrics where applicable)

Languages used in this project:
- Go (Telegram gateway)
- Java (orchestration & policy)
- Python (web retrieval)
- Rust (LLM gateway & safety)

Stick to idiomatic style for each language.

---

## Pull Request Process

1. Fork the repository
2. Create a clear, focused branch
3. Open a Pull Request with:
   - What was changed
   - Why it was changed
   - Any security or behavior implications

PRs may be closed if they:
- Are too broad
- Reduce security posture
- Conflict with project philosophy
- Introduce legal ambiguity

---

## Final Note

This is a **personal-first project**, not a generic SaaS template.

Quality, security, and control matter more than feature count.

If you disagree with these principles, this project is likely not for you.
