# AI Security

Last updated: 2026-03-04

## Basic concepts

### AI red teaming

Red teaming means looking at the system from an attacker’s perspective, validating real attack paths, and providing practical remediation recommendations.

Three things to do:
1. Identify vulnerabilities(technical, process, access control, etc.)
2. Measure risk level(likelihood * impact)
3. Drive fixes and re-test

Apart from the traditional red teaming, AI red teaming also considers vulnerabilities introduced by AI, such as:

1. Prompt injection, Jailbreak: Attempt to override the system prompt or security guardrails to make the AI system behave in unintended ways.
2. Data leakage: Attempt to extract sensitive information from the AI system.
3. Tool/API abuse: Attempt to use the AI system to perform actions that it should not be able to perform.
4. Harmful content generation: Attempt to make the AI system generate harmful content.
5. Reliability and business logic risks: Illusions, hallucinations, and other reliability issues that can lead to incorrect decisions or actions.

### Common methodologies

1. Start with Risk & Threat Frameworks (Define Scope, Avoid Blind Spots)

Before testing, teams use established frameworks to **map the risk surface** and create a coverage checklist:

- **NIST AI Risk Management Framework (AI RMF)**  
  A lifecycle approach (Govern / Map / Measure / Manage). Red teaming typically sits in **Measure**, but **Map/Govern** determines what to test and how deep to go.  
  https://www.nist.gov/itl/ai-risk-management-framework

- **OWASP Top 10 for LLM Applications**  
  A pragmatic “app-sec style” checklist for common LLM app risks (e.g., prompt injection, insecure output handling, data poisoning). Excellent for defining test categories and coverage goals.  
  https://owasp.org/www-project-top-10-for-large-language-model-applications/

- **MITRE ATLAS**  
  A knowledge base of adversarial tactics and techniques for AI-enabled systems (similar in spirit to MITRE ATT&CK). Useful for turning attacker goals into concrete scenarios.  
  https://atlas.mitre.org/

2. Threat Modeling the AI System (Model + Data + Tools + Humans)

AI vulnerabilities often emerge from **system interactions**, not just model behavior. Threat modeling typically covers:

- **Entry surfaces**: user input, multi-turn chat, file uploads, webpages/emails (indirect injection), multimodal inputs
- **Context surfaces**: system prompt, memory, RAG retrieved passages, tool outputs
- **Action surfaces**: plugins/tools (tickets, email, DB, payments, deployments), permission boundaries
- **Assets**: PII, trade secrets, policies/system prompts, internal knowledge bases, credentials/tokens
- **Attacker goals**: data exfiltration, unauthorized actions, business-impacting wrong decisions, availability attacks (token burn), poisoning for persistence

Deliverables often include an **asset inventory + attack surface map + abuse case library + prioritization**.

3. Testing Methodology: Attack Libraries + Scenario Scripts

- Coverage-Driven Attack Suites (Category-Based): Use OWASP/ATLAS categories to build standardized test templates per risk type, such as:

  - Prompt injection & **indirect injection** (via tools/RAG content)
  - Data leakage (PII, secrets, system prompts, internal policies)
  - Insecure output handling (downstream systems executing unsafe outputs)
  - Poisoning (RAG sources, knowledge base, memory contamination)
  - Resource abuse (DoS / token burn / prompt bombs)
  - Authorization failures (tool permission scope, tenant isolation, cross-user data access)

Teams often package these as a **repeatable red-team test suite** and run them in CI for regression coverage.

- Scenario-Driven / Kill-Chain Testing (Impact-Focused)
Instead of single prompts, design **end-to-end adversarial chains** that resemble realistic attacks and demonstrate business impact. Example:

  - attacker plants hidden instructions in a document/webpage  
  - RAG retrieves the malicious content  
  - model treats it as instructions  
  - model triggers tool calls (e.g., queries sensitive data / sends email)  
  - data exfiltration or unauthorized action occurs

This “chain validation” is a major source of red team value because it tests **system-level risk**.

Microsoft guidance also emphasizes jailbreak types like indirect jailbreaks and multi-turn strategies.  
https://learn.microsoft.com/en-us/azure/foundry/concepts/ai-red-teaming-agent

- Automated Adversarial Generation (Fuzzing / Mutation)
To scale beyond handcrafted prompts, teams use:

  - **Prompt fuzzing**: same intent with different phrasing, tone, languages, role-play, encoding, formatting
  - **Mutation**: transform successful attacks via paraphrases, noise insertion, structure wrapping, markdown tricks, base64/obfuscation
  - **Multi-turn planners / attack agents**: automated strategies that plan multi-step conversations to achieve a goal (closer to real adversaries)

4. Evaluation: Defining What “Counts” as a Vulnerability

To avoid subjective judgments, mature programs define measurable criteria:

- **Policy violation**: safety rules bypassed / disallowed output produced
- **Data exfiltration**: sensitive fields disclosed (with explicit detection rules)
- **Unauthorized action**: tool invoked outside authorization, or unsafe write actions executed
- **Reliability harm**: demonstrably wrong decisions in high-stakes tasks
- **Detectability**: whether monitoring/SOC/blue team alerting triggers, and whether incidents are traceable

NIST’s testing and evaluation guidance (and related docs like NIST AI 600-1) reinforces the importance of robust evaluation and context.  
https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.600-1.pdf

5. Remediation Loop: Turn Findings into Engineering Controls + Regression Tests

Effective red teaming doesn’t end at findings—it closes the loop:

Common remediation patterns:
- **Least-privilege tools**: narrow scopes, approval gates, strong confirmation for write actions
- **Instruction/data separation**: treat RAG/tool outputs as data, not executable instructions
- **Output constraints**: structured outputs + validators + allowlists for actions
- **Anti-poisoning measures**: trusted sources, content scanning, versioning/rollback for knowledge bases
- **Monitoring & auditability**: prompt/tool call logging, anomaly detection, rate limits

And critically: convert each finding into a **regression test** that runs continuously.

## First-Principles Thinking

The common practice above only set a general direction of identifying and mitigating AI security vulnerabilities. However, every AI system is different, and every application is different, which means:

- attack surface depends heavily on your exact context flow, tools, RBAC, and business actions;
- generic checklists miss product-specific failure modes;
- mitigations are only useful if tied to your real architecture.

Overall we are doing three things:
1. Identify vulnerabilities(technical, process, access control, etc.)
2. Measure risk level(likelihood * impact)
3. Drive fixes and re-test

So a more effective way is to use a LLM to help us identify vulnerabilities, measure risk level, and drive fixes project by project.

Two prompts for red teaming:

1. `docs/prompts/ai-red-team-customized-prompt.md`
2. `docs/prompts/ai-red-team-standard-prompt.md`
