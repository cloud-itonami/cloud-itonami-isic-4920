(ns freightops.facts-test
  (:require [clojure.test :refer [deftest is]]
            [freightops.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest all-four-seeded-jurisdictions-have-a-liability-spec-basis
  ;; unlike some prior repair-shop-cluster siblings' own honest single-
  ;; jurisdiction gap, ALL FOUR seeded jurisdictions actually have a
  ;; real cargo-liability-disclosure enforcement regime here --
  ;; reported honestly, not forced narrower
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]]
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
