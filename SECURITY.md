# Security Policy

## Supported Versions

This project is under active development.
Only the **latest `main` branch** is considered supported.

Old commits, forks, or modified deployments are **not supported** for security fixes.

---

## Reporting a Vulnerability

If you discover a security vulnerability, **DO NOT** open a public GitHub issue.

### How to report

Send a detailed report to the project maintainer via **private communication**:

- Describe the vulnerability clearly
- Include steps to reproduce
- Include affected components/services
- Include potential impact (data exposure, RCE, privilege escalation, etc.)

Public disclosure without prior coordination is strongly discouraged.

---

## Scope of Security Considerations

This project includes, but is not limited to:

- Telegram Bot integrations
- Web retrieval via Google Custom Search Engine (CSE)
- LLM orchestration (Gemini)
- Multi-service architecture (Go, Java, Python, Rust)
- Storage of user-generated content and metadata
- Network-exposed APIs

Any issue related to the following is considered **security-relevant**:
- Authentication / authorization bypass
- Chat allowlist or admin control bypass
- Token / API key leakage
- Remote code execution
- Prompt injection leading to policy bypass
- Data exfiltration or privacy violations
- Abuse of web search / crawling mechanisms

---

## Out of Scope

The following are **explicitly out of scope**:
- Vulnerabilities caused by misconfiguration of third-party services
- Issues in forked or heavily modified deployments
- Abuse scenarios violating Telegram, Google, or Gemini Terms of Service
- Social engineering or user behavior issues

---

## Responsible Disclosure

Please allow reasonable time for investigation and remediation
before any public discussion or disclosure.

Security research conducted in good faith is appreciated.
Malicious exploitation is not.

---

## Disclaimer

This software is provided **"AS IS"**, without warranty of any kind.
The maintainers are not responsible for deployments operated by third parties.
