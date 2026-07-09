(ns freightops.registry-test
  (:require [clojure.test :refer [deftest is]]
            [freightops.registry :as r]))

;; ----------------------------- tracking-valid? -----------------------------

(deftest tracking-valid-delegates-to-the-capability-library
  (is (r/tracking-valid? "1Z999AA10123456784"))
  (is (not (r/tracking-valid? "BAD#TRACK123")))
  (is (not (r/tracking-valid? "SHORT"))))

;; ----------------------------- register-shipment-dispatch -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-shipment-dispatch "shipment-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-shipment-dispatch "shipment-1" "JPN" 7)]
    (is (= (get result "dispatch_number") "JPN-DIS-000007"))
    (is (= (get-in result ["record" "shipment_id"]) "shipment-1"))
    (is (= (get-in result ["record" "kind"]) "shipment-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-shipment-dispatch "" "JPN" 0)))
  (is (thrown? Exception (r/register-shipment-dispatch "shipment-1" "" 0)))
  (is (thrown? Exception (r/register-shipment-dispatch "shipment-1" "JPN" -1))))

;; ----------------------------- register-consignment-settlement -----------------------------

(deftest settlement-is-a-draft-not-a-real-settlement
  (let [result (r/register-consignment-settlement "shipment-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest settlement-assigns-settlement-number
  (let [result (r/register-consignment-settlement "shipment-1" "JPN" 7)]
    (is (= (get result "settlement_number") "JPN-SET-000007"))
    (is (= (get-in result ["record" "shipment_id"]) "shipment-1"))
    (is (= (get-in result ["record" "kind"]) "consignment-settlement-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest settlement-validation-rules
  (is (thrown? Exception (r/register-consignment-settlement "" "JPN" 0)))
  (is (thrown? Exception (r/register-consignment-settlement "shipment-1" "" 0)))
  (is (thrown? Exception (r/register-consignment-settlement "shipment-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-shipment-dispatch "shipment-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-shipment-dispatch "shipment-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-DIS-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-DIS-000001" (get-in hist2 [1 "record_id"])))))
