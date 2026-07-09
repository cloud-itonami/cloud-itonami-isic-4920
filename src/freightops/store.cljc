(ns freightops.store
  "SSoT for the community-freight actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/freightops/store_contract_test.clj), which is the whole point:
  the actor, the Freight Governor and the audit ledger never know
  which SSoT they run on.

  Unlike `retailops`/4711's own `order` entity (distinguished by
  `:kind`), this vertical's `dispatch` and `settle` actuation events
  apply SEQUENTIALLY to the SAME `shipment` -- dispatch happens first,
  settlement happens later, on the same shipment record. This matches
  the repair-shop cluster's own `ticket` shape more closely (two real-
  world acts, in order, on one entity), with dedicated double-
  actuation-guard booleans (`:dispatched?`/`:settled?`, never a
  `:status` value).

  The ledger stays append-only on every backend: 'which shipment was
  screened for an invalid tracking number, a broken POD chain, an
  unconfirmed cargo-liability disclosure, or an unresolved delivery
  exception, which shipment was dispatched, which consignment was
  settled, on what jurisdictional basis, approved by whom' is always a
  query over an immutable log -- the audit trail a customer or
  carrier trusting a freight operator needs, and the evidence an
  operator needs if a dispatch or a settlement is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [freightops.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (shipment [s id])
  (all-shipments [s])
  (assessment-of [s shipment-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only shipment-dispatch history (freightops.registry drafts)")
  (settlement-history [s] "the append-only consignment-settlement history (freightops.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-settlement-sequence [s jurisdiction] "next settlement-number sequence for a jurisdiction")
  (shipment-already-dispatched? [s shipment-id] "has this shipment already been dispatched?")
  (shipment-already-settled? [s shipment-id] "has this shipment's consignment already been settled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-shipments [s shipments] "replace/seed the shipment directory (map id->shipment)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained shipment set covering both actuation
  lifecycles (dispatch, settlement) plus the governor's own new
  checks, so the actor + tests run offline."
  []
  {:shipments
   {"shipment-1" {:id "shipment-1" :tracking "1Z999AA10123456784"
                   :origin "Tokyo DC" :destination "Osaka Store" :carrier "Local Freight Co"
                   :mode :road :declared-value 500.0
                   :prior-leg-pod-confirmed? true :liability-disclosure-confirmed? true
                   :exception-raised? false :exception-resolved? false
                   :dispatched? false :settled? false
                   :jurisdiction "JPN" :status :intake}
    "shipment-2" {:id "shipment-2" :tracking "1Z999AA10123456784"
                   :origin "Atlantis Port" :destination "Atlantis City" :carrier "Atlantis Freight"
                   :mode :road :declared-value 200.0
                   :prior-leg-pod-confirmed? true :liability-disclosure-confirmed? true
                   :exception-raised? false :exception-resolved? false
                   :dispatched? false :settled? false
                   :jurisdiction "ATL" :status :intake}
    "shipment-3" {:id "shipment-3" :tracking "BAD#TRACK123"
                   :origin "Tokyo DC" :destination "Nagoya Store" :carrier "Local Freight Co"
                   :mode :road :declared-value 300.0
                   :prior-leg-pod-confirmed? true :liability-disclosure-confirmed? true
                   :exception-raised? false :exception-resolved? false
                   :dispatched? false :settled? false
                   :jurisdiction "JPN" :status :intake}
    "shipment-4" {:id "shipment-4" :tracking "1Z999AA10123456784"
                   :origin "Tokyo DC" :destination "Sapporo Store" :carrier "Local Freight Co"
                   :mode :road :declared-value 400.0
                   :prior-leg-pod-confirmed? false :liability-disclosure-confirmed? true
                   :exception-raised? false :exception-resolved? false
                   :dispatched? false :settled? false
                   :jurisdiction "JPN" :status :intake}
    "shipment-5" {:id "shipment-5" :tracking "1Z999AA10123456784"
                   :origin "Tokyo DC" :destination "Fukuoka Store" :carrier "Local Freight Co"
                   :mode :road :declared-value 600.0
                   :prior-leg-pod-confirmed? true :liability-disclosure-confirmed? false
                   :exception-raised? false :exception-resolved? false
                   :dispatched? false :settled? false
                   :jurisdiction "JPN" :status :intake}
    "shipment-6" {:id "shipment-6" :tracking "1Z999AA10123456784"
                   :origin "Tokyo DC" :destination "Sendai Store" :carrier "Local Freight Co"
                   :mode :road :declared-value 350.0
                   :prior-leg-pod-confirmed? true :liability-disclosure-confirmed? true
                   :exception-raised? true :exception-resolved? false
                   :dispatched? false :settled? false
                   :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-shipment!
  "Backend-agnostic `:shipment/mark-dispatched` -- looks up the
  shipment via the protocol and drafts the dispatch record, and
  returns {:result .. :shipment-patch ..} for the caller to persist."
  [s shipment-id]
  (let [sh (shipment s shipment-id)
        seq-n (next-dispatch-sequence s (:jurisdiction sh))
        result (registry/register-shipment-dispatch shipment-id (:jurisdiction sh) seq-n)]
    {:result result
     :shipment-patch {:dispatched? true
                      :dispatch-number (get result "dispatch_number")}}))

(defn- settle-consignment!
  "Backend-agnostic `:shipment/mark-settled` -- looks up the shipment
  via the protocol and drafts the settlement record, and returns
  {:result .. :shipment-patch ..} for the caller to persist."
  [s shipment-id]
  (let [sh (shipment s shipment-id)
        seq-n (next-settlement-sequence s (:jurisdiction sh))
        result (registry/register-consignment-settlement shipment-id (:jurisdiction sh) seq-n)]
    {:result result
     :shipment-patch {:settled? true
                      :settlement-number (get result "settlement_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (shipment [_ id] (get-in @a [:shipments id]))
  (all-shipments [_] (sort-by :id (vals (:shipments @a))))
  (assessment-of [_ shipment-id] (get-in @a [:assessments shipment-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (settlement-history [_] (:settlements @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-settlement-sequence [_ jurisdiction] (get-in @a [:settlement-sequences jurisdiction] 0))
  (shipment-already-dispatched? [_ shipment-id] (boolean (get-in @a [:shipments shipment-id :dispatched?])))
  (shipment-already-settled? [_ shipment-id] (boolean (get-in @a [:shipments shipment-id :settled?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :shipment/upsert
      (swap! a update-in [:shipments (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :shipment/mark-dispatched
      (let [shipment-id (first path)
            {:keys [result shipment-patch]} (dispatch-shipment! s shipment-id)
            jurisdiction (:jurisdiction (shipment s shipment-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:shipments shipment-id] merge shipment-patch)
                       (update :dispatches registry/append result))))
        result)

      :shipment/mark-settled
      (let [shipment-id (first path)
            {:keys [result shipment-patch]} (settle-consignment! s shipment-id)
            jurisdiction (:jurisdiction (shipment s shipment-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:settlement-sequences jurisdiction] (fnil inc 0))
                       (update-in [:shipments shipment-id] merge shipment-patch)
                       (update :settlements registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-shipments [s shipments] (when (seq shipments) (swap! a assoc :shipments shipments)) s))

(defn seed-db
  "A MemStore seeded with the demo shipment set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :dispatch-sequences {} :dispatches []
                           :settlement-sequences {} :settlements []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts,
  dispatch/settlement records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:shipment/id                {:db/unique :db.unique/identity}
   :assessment/shipment-id     {:db/unique :db.unique/identity}
   :ledger/seq                 {:db/unique :db.unique/identity}
   :dispatch/seq                {:db/unique :db.unique/identity}
   :settlement/seq              {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :settlement-sequence/jurisdiction  {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- shipment->tx [{:keys [id tracking origin destination carrier mode declared-value
                            prior-leg-pod-confirmed? liability-disclosure-confirmed?
                            exception-raised? exception-resolved?
                            dispatched? settled?
                            jurisdiction status dispatch-number settlement-number]}]
  (cond-> {:shipment/id id}
    tracking                                          (assoc :shipment/tracking tracking)
    origin                                              (assoc :shipment/origin origin)
    destination                                           (assoc :shipment/destination destination)
    carrier                                                 (assoc :shipment/carrier carrier)
    mode                                                       (assoc :shipment/mode mode)
    declared-value                                               (assoc :shipment/declared-value declared-value)
    (some? prior-leg-pod-confirmed?)                               (assoc :shipment/prior-leg-pod-confirmed? prior-leg-pod-confirmed?)
    (some? liability-disclosure-confirmed?)                          (assoc :shipment/liability-disclosure-confirmed? liability-disclosure-confirmed?)
    (some? exception-raised?)                                          (assoc :shipment/exception-raised? exception-raised?)
    (some? exception-resolved?)                                          (assoc :shipment/exception-resolved? exception-resolved?)
    (some? dispatched?)                                                    (assoc :shipment/dispatched? dispatched?)
    (some? settled?)                                                         (assoc :shipment/settled? settled?)
    jurisdiction                                                               (assoc :shipment/jurisdiction jurisdiction)
    status                                                                       (assoc :shipment/status status)
    dispatch-number                                                              (assoc :shipment/dispatch-number dispatch-number)
    settlement-number                                                              (assoc :shipment/settlement-number settlement-number)))

(def ^:private shipment-pull
  [:shipment/id :shipment/tracking :shipment/origin :shipment/destination :shipment/carrier
   :shipment/mode :shipment/declared-value :shipment/prior-leg-pod-confirmed?
   :shipment/liability-disclosure-confirmed? :shipment/exception-raised? :shipment/exception-resolved?
   :shipment/dispatched? :shipment/settled?
   :shipment/jurisdiction :shipment/status :shipment/dispatch-number :shipment/settlement-number])

(defn- pull->shipment [m]
  (when (:shipment/id m)
    {:id (:shipment/id m) :tracking (:shipment/tracking m) :origin (:shipment/origin m)
     :destination (:shipment/destination m) :carrier (:shipment/carrier m) :mode (:shipment/mode m)
     :declared-value (:shipment/declared-value m)
     :prior-leg-pod-confirmed? (boolean (:shipment/prior-leg-pod-confirmed? m))
     :liability-disclosure-confirmed? (boolean (:shipment/liability-disclosure-confirmed? m))
     :exception-raised? (boolean (:shipment/exception-raised? m))
     :exception-resolved? (boolean (:shipment/exception-resolved? m))
     :dispatched? (boolean (:shipment/dispatched? m))
     :settled? (boolean (:shipment/settled? m))
     :jurisdiction (:shipment/jurisdiction m) :status (:shipment/status m)
     :dispatch-number (:shipment/dispatch-number m) :settlement-number (:shipment/settlement-number m)}))

(defrecord DatomicStore [conn]
  Store
  (shipment [_ id]
    (pull->shipment (d/pull (d/db conn) shipment-pull [:shipment/id id])))
  (all-shipments [_]
    (->> (d/q '[:find [?id ...] :where [?e :shipment/id ?id]] (d/db conn))
         (map #(pull->shipment (d/pull (d/db conn) shipment-pull [:shipment/id %])))
         (sort-by :id)))
  (assessment-of [_ shipment-id]
    (dec* (d/q '[:find ?p . :in $ ?sid
                :where [?a :assessment/shipment-id ?sid] [?a :assessment/payload ?p]]
              (d/db conn) shipment-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (settlement-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :settlement/seq ?s] [?e :settlement/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-settlement-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :settlement-sequence/jurisdiction ?j] [?e :settlement-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (shipment-already-dispatched? [s shipment-id]
    (boolean (:dispatched? (shipment s shipment-id))))
  (shipment-already-settled? [s shipment-id]
    (boolean (:settled? (shipment s shipment-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :shipment/upsert
      (d/transact! conn [(shipment->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/shipment-id (first path) :assessment/payload (enc payload)}])

      :shipment/mark-dispatched
      (let [shipment-id (first path)
            {:keys [result shipment-patch]} (dispatch-shipment! s shipment-id)
            jurisdiction (:jurisdiction (shipment s shipment-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(shipment->tx (assoc shipment-patch :id shipment-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :shipment/mark-settled
      (let [shipment-id (first path)
            {:keys [result shipment-patch]} (settle-consignment! s shipment-id)
            jurisdiction (:jurisdiction (shipment s shipment-id))
            next-n (inc (next-settlement-sequence s jurisdiction))]
        (d/transact! conn
                     [(shipment->tx (assoc shipment-patch :id shipment-id))
                      {:settlement-sequence/jurisdiction jurisdiction :settlement-sequence/next next-n}
                      {:settlement/seq (count (settlement-history s)) :settlement/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-shipments [s shipments]
    (when (seq shipments) (d/transact! conn (mapv shipment->tx (vals shipments)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:shipments ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [shipments]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-shipments s shipments))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo shipment set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
