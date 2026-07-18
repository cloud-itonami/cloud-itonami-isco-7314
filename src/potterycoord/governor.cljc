(ns potterycoord.governor
  "PotteryCoordGovernor — the independent safety/scope layer gating
  every pottery workshop scheduling/logistics proposal an advisor may
  make for a potters and related workers crew. The governor never
  dispatches hardware itself, never performs pottery-making, glazing
  or firing work itself, and never finalizes a firing/glazing-
  execution decision (e.g. deciding to proceed with a specific kiln
  firing) or overrides a workshop safety officer's judgment — those
  are permanently out of this actor's scope and remain a workshop
  safety officer's exclusive judgment (README's 'Robotics premise':
  this actor coordinates POTTERY WORKSHOP SCHEDULING/LOGISTICS ONLY —
  it never performs pottery-making, glazing or firing work itself).
  Modeled closely on cloud-itonami-isco-7211's foundrycoord.governor
  (same hot-process/heat-exposure workshop-safety coordination shape:
  kiln <-> furnace, glaze-chemical-exposure <-> fume-hazard, workshop
  safety officer <-> foundry safety officer).

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. potter provenance    — the crew member must be independently
                                verified/registered before any action.
    2. workshop provenance  — the workshop site must be independently
                                verified/registered before any action.
    3. no-actuation          — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never performs pottery-making, glazing
                                or firing work itself; it only gates
                                what the advisor may coordinate).
    4. closed op-allowlist   — only :log-work-record,
                                :schedule-crew-operation,
                                :flag-safety-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action — any proposal to directly finalize a
                                firing/glazing-execution decision (e.g.
                                deciding to proceed with a specific
                                kiln firing), or to override a
                                workshop safety officer's judgment, is
                                a hard, permanent block (checked both
                                against the proposed :op and,
                                defense-in-depth, against the
                                proposal's :rationale text — matched
                                as full finalization/execution ACTION
                                phrases such as \"proceed with the
                                kiln-firing operation\" / \"authorize
                                the kiln-firing operation\" /
                                \"override the workshop safety
                                officer's judgment\", never as bare
                                nouns like \"kiln\", \"firing\" or
                                \"glaze\", so the check can never
                                self-trip on the advisor's own routine
                                rationale text, e.g. \"logged work
                                record for potter …\" or \"scheduled
                                crew operation for kiln-schedule task
                                …\" or \"…routed for workshop safety
                                officer review\" — all three
                                legitimately contain those bare nouns
                                but none is a finalization action, and
                                all are exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-safety-concern (a burn-hazard /
                                glaze-chemical-exposure /
                                kiln-condition concern always
                                escalates to a human, never
                                auto-commits).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [potterycoord.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 2000)

(def allowed-ops
  #{:log-work-record :schedule-crew-operation
    :flag-safety-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops`
;; above, so they are already refused by the closed-allowlist check
;; below; they are named again here — as explicit finalization/
;; execution ACTIONS, never bare nouns — so a future allowlist edit
;; cannot silently re-open this specific out-of-scope path without
;; also touching this list. This actor coordinates workshop
;; scheduling/logistics ONLY: it NEVER finalizes a firing/glazing
;; decision or overrides a workshop safety officer's judgment, so no
;; op in this list may ever be auto-commit-eligible — every one of
;; them is either structurally absent from `allowed-ops` (hard
;; permanent block) or, if it were ever a bare noun-only rationale
;; match, still routed to a hard block, never an escalate-only path.
(def ^:private scope-excluded-ops
  #{:finalize-firing-decision :authorize-kiln-firing
    :proceed-with-kiln-firing :finalize-glazing-decision
    :authorize-glazing-decision :proceed-with-glazing
    :override-workshop-safety-officer-judgment
    :override-safety-officer-judgment})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("kiln", "firing", "glaze", "clay", "safety", "workshop",
;; "officer") — so this can never match inside the mock advisor's own
;; default rationale text (which legitimately contains those bare
;; nouns, e.g. "kiln-schedule task" / "workshop safety officer
;; review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["proceed with the kiln-firing operation" "proceed with the kiln firing"
   "proceed with the firing" "proceed with the glazing"
   "authorize the kiln-firing operation" "authorize the kiln firing"
   "authorize the firing" "authorize the glazing"
   "finalize the firing decision" "finalize the glazing decision"
   "override the workshop safety officer's judgment"
   "override the safety officer's judgment"
   "override workshop safety officer judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower-case (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal potter-record workshop-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? potter-record)
      (conj {:rule :no-potter
             :detail "未登録 potter への提案は不可（potter record は独立して検証・登録済みでなければならない）"})

      (nil? workshop-record)
      (conj {:rule :no-workshop
             :detail "未登録 workshop への提案は不可（workshop record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は施釉・焼成作業を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "焼成・施釉（キルンファイアリング/グレージング）実行判断の確定・workshop safety officer の判断の上書きは、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `potterycoord.store/Store`. Pure — never
  mutates the store, never dispatches a workshop operation."
  [request _context proposal store]
  (let [potter-record (store/potter store (:potter-id request))
        workshop-record (some->> (:workshop-id proposal) (store/workshop store))
        hard (hard-violations proposal potter-record workshop-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-safety-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
