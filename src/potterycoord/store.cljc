(ns potterycoord.store
  "SSoT for the ISCO-08 7314 potters and related workers pottery
  workshop scheduling/logistics coordination actor (itonami actor
  pattern, ADR-2607121000 / CLAUDE.md Actors section; README's
  'Robotics premise' — a pottery workshop scheduling/logistics
  coordination robot performs crew scheduling,
  clay-batch/glaze-materials-usage/kiln-load progress-record logging
  and clay/glaze-materials supply-order coordination for a potters
  crew under this advisor/governor pair, which never dispatches
  hardware itself, never performs pottery-making, glazing or firing
  work itself, and never finalizes a firing/glazing-execution
  decision or overrides a workshop safety officer's judgment — those
  remain the workshop safety officer's exclusive judgment). Modeled
  closely on cloud-itonami-isco-7211's foundrycoord.store (same
  hot-process/heat-exposure workshop-safety coordination shape:
  kiln <-> furnace, glaze-chemical-exposure <-> fume-hazard,
  workshop safety officer <-> foundry safety officer).

  Domain:

    potter  — a registered pottery-workshop crew member (:potter-id,
              :name)
    workshop — a registered pottery workshop site {:workshop-id :name
              :max-supply-cost number}. `:max-supply-cost` is an
              informational registered ceiling used only to decide
              whether a `:coordinate-supply-order` proposal escalates
              to human sign-off (the governor never blocks a
              within-threshold order outright; it only decides
              commit vs. escalate).
    record  — a committed operating record (a logged clay-batch/
              glaze-materials-usage/kiln-load progress entry, a
              scheduled crew/kiln-schedule operation, a flagged
              safety concern, or a coordinated clay/glaze-materials
              supply order) — written ONLY via commit-record!.
    ledger  — append-only audit trail, commit or hold.")

(defprotocol Store
  (potter [s potter-id])
  (workshop [s workshop-id])
  (records-of [s potter-id])
  (ledger [s])
  (register-potter! [s potter])
  (register-workshop! [s workshop])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (potter [_ potter-id] (get-in @a [:potters potter-id]))
  (workshop [_ workshop-id] (get-in @a [:workshops workshop-id]))
  (records-of [_ potter-id] (filter #(= potter-id (:potter-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-potter! [s p]
    (swap! a assoc-in [:potters (:potter-id p)] p) s)
  (register-workshop! [s w]
    (swap! a assoc-in [:workshops (:workshop-id w)] w) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:potters {} :workshops {} :records [] :ledger []}
                                    seed)))))
