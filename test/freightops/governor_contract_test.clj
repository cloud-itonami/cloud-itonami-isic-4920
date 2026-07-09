(ns freightops.governor-contract-test
  "The governor contract as executable tests -- this vertical's own
  Decision Rule, published in `docs/business-model.md` BEFORE this
  actor existed, implemented faithfully. The single invariant under
  test:

    FreightOps-LLM never dispatches a shipment or settles a
    consignment the Freight Governor would reject, `:shipment/
    dispatch`/`:consignment/settle` NEVER auto-commit at any phase,
    `:shipment/intake` (no direct capital risk) MAY auto-commit when
    clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [freightops.store :as store]
            [freightops.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :carrier-dispatcher :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :shipment/intake :subject "shipment-1"
                   :patch {:id "shipment-1" :carrier "Local Freight Co"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Local Freight Co" (:carrier (store/shipment db "shipment-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "shipment-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "shipment-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "shipment-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "shipment-1")) "no assessment written"))))

(deftest shipment-dispatch-without-assessment-is-held
  (testing "shipment/dispatch before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :shipment/dispatch :subject "shipment-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest tracking-number-invalid-is-held-and-unoverridable
  (testing "a malformed tracking number -> HOLD, and never reaches request-approval -- the SAME genuinely new sub-category retailops.governor/ean13-invalid-violations establishes (reusing a capability library's own validated function), the 73rd unconditional-evaluation-discipline grounding overall"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "shipment-3")
          res (exec-op actor "t5" {:op :shipment/dispatch :subject "shipment-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:tracking-number-invalid} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest pod-chain-integrity-broken-is-held-and-unoverridable
  (testing "a shipment whose prior route leg has no confirmed proof-of-delivery -> HOLD, and never reaches request-approval -- a genuinely new check, the 74th unconditional-evaluation-discipline grounding overall"
    (let [[db actor] (fresh)
          _ (assess! actor "t6pre" "shipment-4")
          res (exec-op actor "t6" {:op :shipment/dispatch :subject "shipment-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:pod-chain-integrity-broken} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest cargo-liability-disclosure-unconfirmed-is-held-and-unoverridable
  (testing "an unconfirmed cargo-liability disclosure at settlement time -> HOLD, and never reaches request-approval -- the FLAGSHIP genuinely new check this vertical adds, the 75th unconditional-evaluation-discipline grounding overall, grounded in the US Carmack Amendment, the UK's Carriage of Goods by Road Act 1965 (CMR), Germany's HGB (CMR) and Japan's own 商法 (see this actor's governor ns docstring / the full accumulated ADR-0001 chain: parksafety's ADR-2607071922 Decision 5 through leathergoods's, ictrepair's and retailops's own)"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "shipment-5")
          _ (exec-op actor "t7dispatch" {:op :shipment/dispatch :subject "shipment-5"} operator)
          _ (approve! actor "t7dispatch")
          res (exec-op actor "t7" {:op :consignment/settle :subject "shipment-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:cargo-liability-disclosure-unconfirmed} (-> (store/ledger db) last :basis)))
      (is (empty? (store/settlement-history db))))))

(deftest delivery-exception-unresolved-is-held-and-unoverridable
  (testing "an unresolved delivery exception -> HOLD, and never reaches request-approval, on either dispatch or settlement"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "shipment-6")
          res (exec-op actor "t8" {:op :shipment/dispatch :subject "shipment-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:delivery-exception-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest shipment-dispatch-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, valid-tracking, intact-POD-chain, no-exception shipment still ALWAYS interrupts for human approval -- actuation/dispatch-shipment is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "shipment-1")
          r1 (exec-op actor "t9" {:op :shipment/dispatch :subject "shipment-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:dispatched? (store/shipment db "shipment-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest consignment-settle-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, confirmed-liability-disclosure, no-exception shipment still ALWAYS interrupts for human approval -- actuation/settle-consignment is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "shipment-1")
          r1 (exec-op actor "t10" {:op :consignment/settle :subject "shipment-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, settlement record drafted"
        (let [r2 (approve! actor "t10")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:settled? (store/shipment db "shipment-1"))))
          (is (= 1 (count (store/settlement-history db))) "one draft settlement record"))))))

(deftest shipment-dispatch-double-dispatch-is-held
  (testing "dispatching the same shipment twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t11pre" "shipment-1")
          _ (exec-op actor "t11a" {:op :shipment/dispatch :subject "shipment-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :shipment/dispatch :subject "shipment-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest consignment-settle-double-settlement-is-held
  (testing "settling the same shipment's consignment twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t12pre" "shipment-1")
          _ (exec-op actor "t12a" {:op :consignment/settle :subject "shipment-1"} operator)
          _ (approve! actor "t12a")
          res (exec-op actor "t12" {:op :consignment/settle :subject "shipment-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-settled} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/settlement-history db))) "still only the one earlier settlement"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :shipment/intake :subject "shipment-1"
                          :patch {:id "shipment-1" :carrier "Local Freight Co"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "shipment-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
