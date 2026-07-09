(ns freightops.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [freightops.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/shipment s "shipment-1"))))
      (is (= "1Z999AA10123456784" (:tracking (store/shipment s "shipment-1"))))
      (is (true? (:prior-leg-pod-confirmed? (store/shipment s "shipment-1"))))
      (is (true? (:liability-disclosure-confirmed? (store/shipment s "shipment-1"))))
      (is (= "BAD#TRACK123" (:tracking (store/shipment s "shipment-3"))))
      (is (false? (:prior-leg-pod-confirmed? (store/shipment s "shipment-4"))))
      (is (false? (:liability-disclosure-confirmed? (store/shipment s "shipment-5"))))
      (is (true? (:exception-raised? (store/shipment s "shipment-6"))))
      (is (false? (:exception-resolved? (store/shipment s "shipment-6"))))
      (is (false? (:dispatched? (store/shipment s "shipment-1"))))
      (is (false? (:settled? (store/shipment s "shipment-1"))))
      (is (= ["shipment-1" "shipment-2" "shipment-3" "shipment-4" "shipment-5" "shipment-6"]
             (mapv :id (store/all-shipments s))))
      (is (nil? (store/assessment-of s "shipment-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/settlement-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-settlement-sequence s "JPN")))
      (is (false? (store/shipment-already-dispatched? s "shipment-1")))
      (is (false? (store/shipment-already-settled? s "shipment-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :shipment/upsert
                                 :value {:id "shipment-1" :carrier "Local Freight Co"}})
        (is (= "Local Freight Co" (:carrier (store/shipment s "shipment-1"))))
        (is (= "1Z999AA10123456784" (:tracking (store/shipment s "shipment-1"))) "unrelated field preserved"))
      (testing "assessment payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["shipment-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "shipment-1"))))
      (testing "shipment dispatch drafts a record and advances the dispatch sequence"
        (store/commit-record! s {:effect :shipment/mark-dispatched :path ["shipment-1"]})
        (is (= "JPN-DIS-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "shipment-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:dispatched? (store/shipment s "shipment-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/shipment-already-dispatched? s "shipment-1"))))
      (testing "consignment settlement drafts a record and advances the settlement sequence"
        (store/commit-record! s {:effect :shipment/mark-settled :path ["shipment-1"]})
        (is (= "JPN-SET-000000" (get (first (store/settlement-history s)) "record_id")))
        (is (= "consignment-settlement-draft" (get (first (store/settlement-history s)) "kind")))
        (is (true? (:settled? (store/shipment s "shipment-1"))))
        (is (= 1 (count (store/settlement-history s))))
        (is (= 1 (store/next-settlement-sequence s "JPN")))
        (is (true? (store/shipment-already-settled? s "shipment-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/shipment s "nope")))
    (is (= [] (store/all-shipments s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/settlement-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-settlement-sequence s "JPN")))
    (store/with-shipments s {"x" {:id "x" :tracking "1Z999AA10123456784"
                                 :origin "o" :destination "d" :carrier "c" :mode :road
                                 :declared-value 10.0
                                 :prior-leg-pod-confirmed? true :liability-disclosure-confirmed? true
                                 :exception-raised? false :exception-resolved? false
                                 :dispatched? false :settled? false
                                 :jurisdiction "JPN" :status :intake}})
    (is (= "c" (:carrier (store/shipment s "x"))))))
