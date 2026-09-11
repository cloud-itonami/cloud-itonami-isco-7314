(ns potterycoord.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [potterycoord.store :as store]
            [potterycoord.advisor :as advisor]
            [potterycoord.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-potter! st {:potter-id "potter-1" :name "Kobo Yamada"})
    (store/register-workshop! st {:workshop-id "W-1" :name "Kobo Pottery Workshop" :max-supply-cost 2000})
    st))

(defn- op [op-kw & {:as extra}]
  (merge {:op op-kw :effect :propose :workshop-id "W-1"
          :confidence 0.9 :stake :low}
         extra))

(def ^:private req {:potter-id "potter-1"})

(deftest ok-log-work-record
  (let [st (fresh-store)
        v (governor/check req {} (op :log-work-record) st)]
    (is (:ok? v))))

(deftest ok-schedule-crew-operation
  (let [st (fresh-store)
        v (governor/check req {} (op :schedule-crew-operation) st)]
    (is (:ok? v))))

(deftest ok-supply-order-at-threshold-boundary
  (testing "the supply-cost threshold escalate boundary is exclusive (over, not at)"
    (let [st (fresh-store)
          v (governor/check req {} (op :coordinate-supply-order :cost 2000) st)]
      (is (:ok? v)))))

(deftest hard-on-unregistered-potter
  (let [st (fresh-store)
        v (governor/check {:potter-id "nobody"} {} (op :log-work-record) st)]
    (is (:hard? v))
    (is (some #(= :no-potter (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-workshop
  (let [st (fresh-store)
        v (governor/check req {} (op :log-work-record :workshop-id "W-ghost") st)]
    (is (:hard? v))
    (is (some #(= :no-workshop (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :log-work-record) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-op-outside-closed-allowlist
  (let [st (fresh-store)
        v (governor/check req {} (op :dispatch-equipment) st)]
    (is (:hard? v))
    (is (some #(= :unknown-op (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-op-finalize-firing-decision
  (testing "finalizing a kiln-firing execution decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :finalize-firing-decision) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-authorize-kiln-firing
  (testing "authorizing a kiln firing is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :authorize-kiln-firing) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-proceed-with-kiln-firing
  (testing "proceeding with a kiln firing is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :proceed-with-kiln-firing) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-finalize-glazing-decision
  (testing "finalizing a glazing-execution decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :finalize-glazing-decision) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-override-workshop-safety-officer-judgment
  (testing "overriding a workshop safety officer's judgment is a workshop safety officer's exclusive judgment, never this actor's"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :override-workshop-safety-officer-judgment) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-even-with-allowlisted-op
  (testing "defense-in-depth: a rationale that itself attempts to proceed with the kiln firing is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :schedule-crew-operation)
                                           :rationale "recommend we proceed with the kiln firing now")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-override-workshop-safety-officer
  (testing "defense-in-depth: a rationale attempting to override the workshop safety officer's judgment is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :log-work-record)
                                           :rationale "override the workshop safety officer's judgment and proceed")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest always-escalates-safety-concern-even-at-high-confidence
  (testing "a burn-hazard/glaze-chemical-exposure/kiln-condition concern always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :flag-safety-concern :hazard-type :burn-exposure-risk)
                                           :confidence 0.99)
                             st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-supply-order-above-threshold
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :coordinate-supply-order :cost 5000) :confidence 0.99) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :log-work-record) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest default-mock-advisor-proposals-never-self-trip-on-scope-exclusion
  (testing "the governor's scope-exclusion term list must never match the mock advisor's own default rationale text for any allowlisted op — CLAUDE.md's known self-tripping bug pattern (rationale legitimately contains bare nouns like 'kiln'/'glaze'/'workshop'/'safety officer', but never the full finalization-action phrases)"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          ops [:log-work-record :schedule-crew-operation
               :flag-safety-concern :coordinate-supply-order]]
      (doseq [o ops]
        (let [request {:potter-id "potter-1" :op o :workshop-id "W-1"
                        :stake :low :task "routine kiln-schedule task"
                        :hazard-type :burn-exposure-risk :cost 500}
              proposal (advisor/-advise adv st request)
              v (governor/check request {} proposal st)]
          (is (not (:hard? v))
              (str o " proposal unexpectedly hard-blocked: " (:violations v))))))))
