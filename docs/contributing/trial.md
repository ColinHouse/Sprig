# Fresh contributor trial

Give a fresh human or coding agent only the public repository at a recorded
commit, one published scoped issue, normal public docs and normal tools.
No private implementation hints or prewritten patch. Ask it to understand the
issue, make a patch, run verification and produce a PR summary. Preserve the
actual transcript/log locally; redact secrets before sharing.

Use the same record for docs, regression, compiler, stdlib/tooling and Windows
trials. A fresh agent trial is an agent trial; it must not be described as
human feedback. Maintainers append measured outcomes to the milestone's
single validation record, including failures. This protocol itself records
no completed trial.

```text
Trial ID / date:
Participant: human or agent (tool/model if known)
Public commit / issue URL:
Environment / JDK / commands available:
Start → finish / elapsed:
Patch iterations / check failures / final verify exit:
Wrong assumptions or invented language behavior:
Unnecessary files changed:
Compiler queries used (capabilities/help/api/explain/etc.):
Outcome: accepted / needs revision / blocked (why)
Patch or PR URL / evidence log:
Contributor-doc or issue correction prompted by evidence:
```

Success means a reviewed patch meets the issue contract and passes the normal
gate. Record infrastructure/network failure separately from patch correctness.
Do not infer attraction from stars or agent completion alone: track external
builds, reproducible issues, first contributions and useful non-demo programs.
