# Recording the compiler-feedback loop

Rehearse from a built repository or extracted SDK:

```bash
python3 scripts/record-demo.py
python3 scripts/record-demo.py --pause --sdk /path/to/extracted-sdk
# Windows: py -3 scripts/record-demo.py --pause --sdk C:\path\to\sdk
```

The script copies the real multi-module repository auditor and standard modules
into a temporary workspace, queries capabilities/help, resolves and checks it,
introduces a visible String→Int mistake, shows the real JSON diagnostic,
restores the original code and runs against a fixed fixture tree. The report
is produced by the application. This is an **automated recording rehearsal**;
it is not evidence of a fresh agent learning or contributing.

## Short recording sequence (about 3–5 minutes)

1. Show version, manifest, modules and fixture input. Explain: small JVM tooling language.
2. Show `capabilities --json` and `help generics --json`: the compiler supplies the rules.
3. Show the actual typed counter; visibly make the deliberate mistake.
4. Read the error's code and location; repair and recheck.
5. Run the auditor and inspect its JSON output; show the separate Maven showcase
   README for third-party JVM resolve/API/offline commands.
6. End with the contribution call to action below.

To demonstrate a **fresh agent**, start a new agent using only the public docs,
SDK/repository and scoped issue. Record its actual queries, edits and failures;
use the trial record in [`trial.md`](trial.md). Do not splice the replay into a claim of
agent success. Do not publish the video until several reviewed, genuinely
mergeable `good first issue` / `agent-friendly` issues are open.

## Closing call to action

> Want to contribute with Codex / Claude / ChatGPT? Install JDK, clone Sprig,
> pick an agent-friendly issue, let your agent read AGENTS.md, run verify,
> review the patch, and open a PR. You can contribute useful docs, regression
> tests or tooling without redesigning a language.

Links for the description: [repository](https://github.com/ColinHouse/Sprig),
[contribution guide](https://github.com/ColinHouse/Sprig/blob/main/CONTRIBUTING.md),
[agent-friendly issues](https://github.com/ColinHouse/Sprig/issues?q=is%3Aissue+is%3Aopen+label%3Aagent-friendly).
