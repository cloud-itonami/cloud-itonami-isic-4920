(ns freightops.registry
  "Pure-function shipment-dispatch + consignment-settlement record
  construction -- an append-only freight book-of-record draft.

  Like `retailops`/4711's own `registry` for `kotoba-lang/retail`,
  this is the SECOND vertical in this fleet to wrap a REAL, pre-
  existing bespoke domain capability library (`kotoba-lang/logistics`,
  this blueprint's own README-named implementation for 'shipment,
  tracking, route, consignment') rather than building self-contained
  domain logic from scratch. `kotoba.logistics/tracking-valid?` is
  called directly, not reimplemented -- the actor layer adds the
  governed proposal/approval loop on top, it does not duplicate the
  capability library's own validated logic.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a shipment-dispatch or consignment-
  settlement record -- every carrier/jurisdiction assigns its own
  reference format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `freightops.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real TMS/dispatch system. It builds the RECORD a carrier
  would keep, not the act of dispatching a shipment or settling a
  consignment itself (that is `freightops.operation`'s `:shipment/
  dispatch`/`:consignment/settle`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]
            [kotoba.logistics :as logistics]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the carrier's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn tracking-valid?
  "Delegates to `kotoba.logistics/tracking-valid?` -- the actor layer
  never reimplements the tracking-number structural contract, it
  calls the capability library's own validated function."
  [tracking]
  (logistics/tracking-valid? tracking))

(defn register-shipment-dispatch
  "Validate + construct the SHIPMENT-DISPATCH registration DRAFT --
  the carrier's own legal act of dispatching a real shipment onto a
  route. Pure function -- does not touch any real TMS system; it
  builds the RECORD a carrier would keep. `freightops.governor`
  independently re-verifies the shipment's own tracking-number and
  POD-chain-integrity ground truth, and blocks a double-dispatch of
  the same shipment, before this is ever allowed to commit."
  [shipment-id jurisdiction sequence]
  (when-not (and shipment-id (not= shipment-id ""))
    (throw (ex-info "shipment-dispatch: shipment_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "shipment-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "shipment-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-DIS-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "shipment-dispatch-draft"
                "shipment_id" shipment-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "ShipmentDispatch" dispatch-number dispatch-number)}))

(defn register-consignment-settlement
  "Validate + construct the CONSIGNMENT-SETTLEMENT registration DRAFT
  -- the carrier's own legal act of settling a real consignment. Pure
  function -- does not touch any real settlement system; it builds
  the RECORD a carrier would keep. `freightops.governor` independently
  re-verifies the shipment's own cargo-liability-disclosure and
  unresolved-exception ground truth, and blocks a double-settlement of
  the same shipment, before this is ever allowed to commit."
  [shipment-id jurisdiction sequence]
  (when-not (and shipment-id (not= shipment-id ""))
    (throw (ex-info "consignment-settlement: shipment_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "consignment-settlement: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "consignment-settlement: sequence must be >= 0" {})))
  (let [settlement-number (str (str/upper-case jurisdiction) "-SET-" (zero-pad sequence 6))
        record {"record_id" settlement-number
                "kind" "consignment-settlement-draft"
                "shipment_id" shipment-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "settlement_number" settlement-number
     "certificate" (unsigned-certificate "ConsignmentSettlement" settlement-number settlement-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
