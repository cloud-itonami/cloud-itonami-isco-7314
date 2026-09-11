# cloud-itonami-isco-7314

Open Occupation Blueprint for **ISCO-08 7314**: Potters and Related Workers.

This repository designs a forkable OSS business for a pottery workshop scheduling and logistics coordination practice: a pottery workshop scheduling and supply-coordination robot manages crew/task records under a governor-gated actor, so a potters crew keeps its own operating records instead of renting a closed workforce-management SaaS.

**Maturity: `:implemented`.** `src/potterycoord/` implements the
`PotteryCoordActor` as a `langgraph.graph/state-graph`
(`potterycoord.actor`) wired to a `Pottery Coordination Advisor`
(`potterycoord.advisor`) and an independent `PotteryCoordGovernor`
(`potterycoord.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 22 tests / 47 assertions green (`kbb -M:test`).
HARD invariants (always hold, never overridable): potter provenance,
workshop provenance, no-actuation (`:effect` must be `:propose`), a closed
op-allowlist (`:log-work-record`, `:schedule-crew-operation`,
`:flag-safety-concern`, `:coordinate-supply-order` — nothing else may
ever be proposed), and a permanent, unconditional block on any
proposal that would directly finalize a firing/glazing-execution
decision (e.g. deciding to proceed with a specific kiln firing) or
override a workshop safety officer's judgment. Always-escalate paths
(human sign-off regardless of confidence, mapping this repo's Trust
Controls in [`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (always) and `:coordinate-supply-order` above
the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a pottery workshop scheduling/logistics coordination robot performs crew scheduling, clay-batch/glaze-materials-usage/kiln-load progress-record logging and clay/glaze-materials supply-order coordination for a potters crew, under an actor that proposes actions and an independent **Pottery Coordination Governor** that gates them. The governor never
dispatches hardware itself, never performs pottery-making, glazing or firing work on the workshop floor, and never finalizes a firing/glazing-execution decision or overrides a workshop safety officer's judgment; `:high`/`:safety-critical` actions (such as a flagged burn-hazard/glaze-chemical-exposure/kiln-condition concern, or an above-threshold supply order) require human sign-off. **This actor coordinates pottery workshop scheduling/logistics only — it never performs pottery-making, glazing or firing work itself.**

## Core Contract

```text
crew roster + workshop registration + safety-reporting policy
        |
        v
Pottery Coordination Advisor -> Pottery Coordination Governor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, finalize
a firing/glazing-execution decision, override a workshop safety officer's
judgment, suppress an operating record, or disclose sensitive data
without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `7314`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
