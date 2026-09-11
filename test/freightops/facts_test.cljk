(ns freightops.facts-test
  (:require [clojure.test :refer [deftest is]]
            [freightops.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest newly-seeded-jurisdictions-have-a-spec-basis
  (doseq [iso3 ["IND" "SAU" "ARE" "MEX"]]
    (is (some? (facts/spec-basis iso3)) (str iso3 " spec-basis"))
    (is (string? (:provenance (facts/spec-basis iso3))) (str iso3 " provenance"))))

(deftest all-eight-seeded-jurisdictions-have-a-liability-spec-basis
  ;; unlike some prior repair-shop-cluster siblings' own honest single-
  ;; jurisdiction gap, ALL EIGHT seeded jurisdictions actually have a
  ;; real cargo-liability-disclosure enforcement regime here --
  ;; reported honestly, not forced narrower
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU" "IND" "SAU" "ARE" "MEX"]]
    (is (some? (facts/liability-spec-basis iso3)) (str iso3 " liability-spec-basis"))
    (is (string? (:liability-provenance (facts/liability-spec-basis iso3))) (str iso3 " liability-provenance"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest unknown-jurisdiction-has-no-liability-spec-basis
  (is (nil? (facts/liability-spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

;; ----------------- Cross-Actor Handoff carrier confirmation (ADR-2800000700) -----------------

(deftest handoff-declares-cold-chain-window-only-when-both-bounds-present
  (is (facts/handoff-declares-cold-chain-window?
       {:handoff/cold-chain-temp-min-c 2.0 :handoff/cold-chain-temp-max-c 10.0}))
  (is (not (facts/handoff-declares-cold-chain-window? {})))
  (is (not (facts/handoff-declares-cold-chain-window? {:handoff/cold-chain-temp-min-c 2.0})))
  (is (not (facts/handoff-declares-cold-chain-window? nil))))

(deftest handoff-cold-chain-maintained-with-no-declared-window-is-trivially-true
  (is (facts/handoff-cold-chain-maintained? {} nil nil))
  (is (facts/handoff-cold-chain-maintained? nil nil nil)))

(deftest handoff-cold-chain-maintained-checks-declared-window-inclusively
  (let [handoff {:handoff/cold-chain-temp-min-c 2.0 :handoff/cold-chain-temp-max-c 10.0}]
    (is (facts/handoff-cold-chain-maintained? handoff 2.0 10.0) "bounds themselves are within range")
    (is (facts/handoff-cold-chain-maintained? handoff 3.0 6.0))
    (is (not (facts/handoff-cold-chain-maintained? handoff 1.9 10.0)))
    (is (not (facts/handoff-cold-chain-maintained? handoff 2.0 10.1)))))

(deftest handoff-cold-chain-maintained-missing-actual-reading-is-not-maintained
  (let [handoff {:handoff/cold-chain-temp-min-c 2.0 :handoff/cold-chain-temp-max-c 10.0}]
    (is (not (facts/handoff-cold-chain-maintained? handoff nil nil)))
    (is (not (facts/handoff-cold-chain-maintained? handoff 3.0 nil)))
    (is (not (facts/handoff-cold-chain-maintained? handoff nil 6.0)))))
