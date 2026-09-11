(ns freightops.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean shipment through
  intake -> jurisdiction assessment -> shipment dispatch (escalate/
  approve/commit) -> consignment settlement (escalate/approve/commit),
  then shows HARD-hold scenarios: a jurisdiction with no spec-basis, an
  invalid tracking number, a broken POD chain, an unconfirmed cargo-
  liability disclosure, an unresolved delivery exception, a double
  dispatch, and a double settlement.

  Like `retailops`/4711's own new checks, this actor's new checks
  (`tracking-number-invalid?`, `pod-chain-integrity-broken?`, `cargo-
  liability-disclosure-unconfirmed?`, `delivery-exception-
  unresolved?`) are evaluated directly at `:shipment/dispatch`/
  `:consignment/settle` time rather than via a separate screening op
  -- a real dispatch/settlement decision validates a tracking number,
  a POD chain, a liability disclosure and an open exception at the
  point of the act itself, not as a discrete pre-screening ceremony.
  Each check is still exercised directly and independently below, one
  shipment per HARD-hold scenario, following the SAME 'exercise the
  failure mode directly, never only via a happy-path actuation'
  discipline `parksafety`'s ADR-2607071922 Decision 5 and every
  sibling since establish."
  (:require [langgraph.graph :as g]
            [freightops.store :as store]
            [freightops.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :carrier-dispatcher :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== shipment/intake shipment-1 (JPN, clean) ==")
    (println (exec-op actor "t1" {:op :shipment/intake :subject "shipment-1"
                                  :patch {:id "shipment-1" :carrier "Local Freight Co"}} operator))

    (println "== jurisdiction/assess shipment-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :jurisdiction/assess :subject "shipment-1"} operator))
    (println (approve! actor "t2"))

    (println "== shipment/dispatch shipment-1 (always escalates -- actuation/dispatch-shipment) ==")
    (let [r (exec-op actor "t3" {:op :shipment/dispatch :subject "shipment-1"} operator)]
      (println r)
      (println "-- human carrier dispatcher approves --")
      (println (approve! actor "t3")))

    (println "== consignment/settle shipment-1 (always escalates -- actuation/settle-consignment) ==")
    (let [r (exec-op actor "t4" {:op :consignment/settle :subject "shipment-1"} operator)]
      (println r)
      (println "-- human carrier dispatcher approves --")
      (println (approve! actor "t4")))

    (println "== jurisdiction/assess shipment-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :jurisdiction/assess :subject "shipment-2" :no-spec? true} operator))

    (println "== jurisdiction/assess shipment-3 (escalates -- human approves; sets up the tracking-number-invalid test) ==")
    (println (exec-op actor "t6" {:op :jurisdiction/assess :subject "shipment-3"} operator))
    (println (approve! actor "t6"))

    (println "== shipment/dispatch shipment-3 (malformed tracking number -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :shipment/dispatch :subject "shipment-3"} operator))

    (println "== jurisdiction/assess shipment-4 (escalates -- human approves; sets up the pod-chain-integrity test) ==")
    (println (exec-op actor "t8" {:op :jurisdiction/assess :subject "shipment-4"} operator))
    (println (approve! actor "t8"))

    (println "== shipment/dispatch shipment-4 (prior-leg POD unconfirmed -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :shipment/dispatch :subject "shipment-4"} operator))

    (println "== jurisdiction/assess shipment-5 (escalates -- human approves; sets up the cargo-liability-disclosure test) ==")
    (println (exec-op actor "t10" {:op :jurisdiction/assess :subject "shipment-5"} operator))
    (println (approve! actor "t10"))

    (println "== shipment/dispatch shipment-5 (clean dispatch -- escalates -- human approves) ==")
    (println (exec-op actor "t11" {:op :shipment/dispatch :subject "shipment-5"} operator))
    (println (approve! actor "t11"))

    (println "== consignment/settle shipment-5 (liability disclosure unconfirmed -> HARD hold) ==")
    (println (exec-op actor "t12" {:op :consignment/settle :subject "shipment-5"} operator))

    (println "== jurisdiction/assess shipment-6 (escalates -- human approves; sets up the delivery-exception test) ==")
    (println (exec-op actor "t13" {:op :jurisdiction/assess :subject "shipment-6"} operator))
    (println (approve! actor "t13"))

    (println "== shipment/dispatch shipment-6 (unresolved delivery exception -> HARD hold) ==")
    (println (exec-op actor "t14" {:op :shipment/dispatch :subject "shipment-6"} operator))

    (println "== shipment/dispatch shipment-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec-op actor "t15" {:op :shipment/dispatch :subject "shipment-1"} operator))

    (println "== consignment/settle shipment-1 AGAIN (double-settlement -> HARD hold) ==")
    (println (exec-op actor "t16" {:op :consignment/settle :subject "shipment-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft shipment-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft consignment-settlement records ==")
    (doseq [r (store/settlement-history db)] (println r))))
