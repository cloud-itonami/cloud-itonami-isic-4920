(ns freightops.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:shipment/dispatch`/`:consignment/settle` must NEVER
  be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [freightops.phase :as phase]))

(deftest shipment-dispatch-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real shipment dispatch"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :shipment/dispatch))
          (str "phase " n " must not auto-commit :shipment/dispatch")))))

(deftest consignment-settle-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real consignment settlement"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :consignment/settle))
          (str "phase " n " must not auto-commit :consignment/settle")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":shipment/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:shipment/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :shipment/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :shipment/dispatch} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :consignment/settle} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :shipment/intake} :commit)))))

;; ----------------- :transport-leg/log (ADR-2800000700) -----------------

(deftest transport-leg-log-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in future entries, auto-commits a carrier's own transport-leg confirmation"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :transport-leg/log))
          (str "phase " n " must not auto-commit :transport-leg/log")))))

(deftest transport-leg-log-enabled-at-phase-3-only
  (is (contains? (:writes (get phase/phases 3)) :transport-leg/log))
  (is (not (contains? (:writes (get phase/phases 0)) :transport-leg/log)))
  (is (not (contains? (:writes (get phase/phases 1)) :transport-leg/log)))
  (is (not (contains? (:writes (get phase/phases 2)) :transport-leg/log))))

(deftest gate-escalates-a-clean-transport-leg-log
  (is (= :escalate (:disposition (phase/gate 3 {:op :transport-leg/log} :commit)))))
