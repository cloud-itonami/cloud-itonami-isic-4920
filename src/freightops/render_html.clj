(ns freightops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300):
  this repo previously shipped a HAND-WRITTEN 2,056-byte stub console
  (a single invented `SH1 Tokyo -> Osaka` row that appears nowhere in
  `freightops.store/demo-data`) and no generator at all. This namespace
  replaces it with a page every field of which is produced by actually
  running the REAL actor stack --

    `freightops.operation` (langgraph StateGraph, `interrupt-before
    #{:request-approval}`) -> `freightops.governor` (11 HARD checks)
    -> `freightops.phase` (rollout gate) -> `freightops.store` (SSoT)

  -- through `langgraph.graph/run*`, exactly the way `freightops.sim`
  (`clojure -M:dev:run`) drives it. Nothing on the page is hand-typed:
  shipment fields come from `store/all-shipments`, dispatch/settlement
  reference numbers from `store/dispatch-history`/`settlement-history`
  (i.e. from `freightops.registry`, which the actor invoked), hold
  reasons and their Japanese detail strings from the governor's own
  violation maps in the SSoT ledger, the action-gate table from
  `freightops.phase/phases` itself, and the approver join from the
  `:approval-granted` facts the graph actually emitted.

  The scenario was written only AFTER running this repo's own
  `clojure -M:dev:run` to confirm the seeded ids -- `freightops.sim`
  uses `shipment-1`..`shipment-6`, which DO match
  `freightops.store/demo-data` (some sibling repos' sims do not).
  The `:transport-leg/log` handoff fixtures (`trk-breach`/`trk-clean`,
  window 2.0-10.0 C) are this repo's OWN, lifted verbatim from
  `test/freightops/governor_contract_test.clj`, not invented here.

  Determinism: nothing in this stack carries a timestamp or a random
  value -- `freightops.registry` builds jurisdiction-scoped sequence
  numbers, the ledger facts carry no clock -- so two consecutive runs
  are byte identical. Verify with two runs into a `mktemp -d` scratch
  directory and `cmp`.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [freightops.store :as store]
            [freightops.registry :as registry]
            [freightops.phase :as phase]
            [freightops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  "The same operator context `freightops.sim` uses."
  {:actor-id "op-1" :actor-role :carrier-dispatcher :phase 3})

;; ----------------------------- scenario -----------------------------
;;
;; Every op below goes through `langgraph.graph/run*` against a real
;; compiled OperationActor. The governor is never bypassed: a HARD
;; violation returns `:disposition :hold` WITHOUT the graph ever
;; interrupting for a human, which is what `hard-hold?` below detects.

(defn- record!
  "Records one graph run so the page can report, per thread, whether a
  human was ever asked (`:interrupted?`) and what the actor finally
  decided. A resume updates the thread's existing entry in place."
  [runs tid request r]
  (swap! runs
         (fn [v]
           (let [idx (first (keep-indexed (fn [i m] (when (= (:thread m) tid) i)) v))
                 prev (when idx (nth v idx))
                 entry (merge {:thread tid}
                              (when request {:op (:op request) :subject (:subject request)})
                              {:status (:status r)
                               :interrupted? (or (:interrupted? prev)
                                                 (= :interrupted (:status r)))
                               :disposition (get-in r [:state :disposition])
                               :audit (vec (get-in r [:state :audit]))})]
             (if idx (assoc v idx (merge prev entry)) (conj v entry))))))

(defn- exec! [actor runs tid request]
  (let [r (g/run* actor {:request request :context operator} {:thread-id tid})]
    (record! runs tid request r)
    r))

(defn- approve! [actor runs tid]
  (let [r (g/run* actor {:approval {:status :approved :by (:actor-id operator)}}
                  {:thread-id tid :resume? true})]
    (record! runs tid nil r)
    r))

(defn run-demo!
  "Runs a fresh seeded store through a scenario that reaches EVERY
  disposition this actor can produce and exercises all eleven of the
  Freight Governor's HARD checks, each one directly:

    shipment-1  full clean lifecycle -- intake (auto-commits at phase 3,
                the only op in phase 3's `:auto` set), jurisdiction
                assessment (governor-clean but never auto-eligible ->
                escalates -> approved), shipment dispatch (ALWAYS
                escalates -- `:actuation/dispatch-shipment` -> approved)
                and consignment settlement (ALWAYS escalates --
                `:actuation/settle-consignment` -> approved); then a
                repeat dispatch and a repeat settlement, HARD-held by
                the double-actuation guards.
    shipment-2  jurisdiction assessment for a deliberately unregistered
                jurisdiction (ATL) -> HARD `:no-spec-basis`; a dispatch
                attempted with no assessment on file -> HARD
                `:evidence-incomplete`.
    shipment-3  assessment approved, then a dispatch whose seeded
                tracking number fails `kotoba.logistics/tracking-valid?`
                -> HARD `:tracking-number-invalid`.
    shipment-4  assessment approved, then a dispatch with the prior
                leg's proof-of-delivery unconfirmed -> HARD
                `:pod-chain-integrity-broken`.
    shipment-5  assessment approved, dispatch approved and committed,
                then a settlement with the cargo-liability disclosure
                unconfirmed -> HARD
                `:cargo-liability-disclosure-unconfirmed`.
    shipment-6  assessment approved, then a dispatch with an open,
                unresolved delivery exception -> HARD
                `:delivery-exception-unresolved`.
    transport   this actor's THIRD-PARTY carrier role (ADR-2800000700)
                over another actor's `:handoff`: a leg-log with no
                `:handoff/carrier-tracking-ref` -> HARD
                `:carrier-tracking-ref-missing`; a leg whose reported
                transport temperature left the handoff's declared
                cold-chain window -> HARD `:cold-chain-breach`; a clean
                leg (escalates, approved, committed); and the same
                carrier-tracking-ref logged twice -> HARD
                `:transport-leg-already-logged`.

  Returns {:db store :runs [..]} -- the SSoT plus the per-thread run
  record. Every field the renderer reads is real actor output."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        runs (atom [])
        ;; This repo's OWN handoff fixtures -- copied verbatim from
        ;; test/freightops/governor_contract_test.clj, not invented here.
        cold-window {:handoff/cold-chain-temp-min-c 2.0
                     :handoff/cold-chain-temp-max-c 10.0}
        breach-handoff (merge {:handoff/id "h1"
                               :handoff/source-actor "cloud-itonami-jsic-4721"
                               :handoff/carrier-tracking-ref "trk-breach"}
                              cold-window)
        clean-handoff (merge {:handoff/id "h3"
                              :handoff/source-actor "cloud-itonami-jsic-4721"
                              :handoff/carrier-tracking-ref "trk-clean"}
                             cold-window)]

    ;; --- shipment-1: the full clean lifecycle -------------------------
    (exec! actor runs "s1-intake" {:op :shipment/intake :subject "shipment-1"
                                   :patch {:id "shipment-1" :carrier "Local Freight Co"}})

    (exec! actor runs "s1-assess" {:op :jurisdiction/assess :subject "shipment-1"})
    (approve! actor runs "s1-assess")

    (exec! actor runs "s1-dispatch" {:op :shipment/dispatch :subject "shipment-1"})
    (approve! actor runs "s1-dispatch")

    (exec! actor runs "s1-settle" {:op :consignment/settle :subject "shipment-1"})
    (approve! actor runs "s1-settle")

    ;; --- shipment-2: no spec-basis, then no assessment on file --------
    (exec! actor runs "s2-assess" {:op :jurisdiction/assess :subject "shipment-2" :no-spec? true})
    (exec! actor runs "s2-dispatch" {:op :shipment/dispatch :subject "shipment-2"})

    ;; --- shipment-3: malformed tracking number ------------------------
    (exec! actor runs "s3-assess" {:op :jurisdiction/assess :subject "shipment-3"})
    (approve! actor runs "s3-assess")
    (exec! actor runs "s3-dispatch" {:op :shipment/dispatch :subject "shipment-3"})

    ;; --- shipment-4: prior-leg POD unconfirmed ------------------------
    (exec! actor runs "s4-assess" {:op :jurisdiction/assess :subject "shipment-4"})
    (approve! actor runs "s4-assess")
    (exec! actor runs "s4-dispatch" {:op :shipment/dispatch :subject "shipment-4"})

    ;; --- shipment-5: clean dispatch, then unconfirmed liability -------
    (exec! actor runs "s5-assess" {:op :jurisdiction/assess :subject "shipment-5"})
    (approve! actor runs "s5-assess")
    (exec! actor runs "s5-dispatch" {:op :shipment/dispatch :subject "shipment-5"})
    (approve! actor runs "s5-dispatch")
    (exec! actor runs "s5-settle" {:op :consignment/settle :subject "shipment-5"})

    ;; --- shipment-6: unresolved delivery exception --------------------
    (exec! actor runs "s6-assess" {:op :jurisdiction/assess :subject "shipment-6"})
    (approve! actor runs "s6-assess")
    (exec! actor runs "s6-dispatch" {:op :shipment/dispatch :subject "shipment-6"})

    ;; --- double-actuation guards --------------------------------------
    (exec! actor runs "s1-dispatch-again" {:op :shipment/dispatch :subject "shipment-1"})
    (exec! actor runs "s1-settle-again" {:op :consignment/settle :subject "shipment-1"})

    ;; --- third-party carrier confirmation (ADR-2800000700) ------------
    (exec! actor runs "leg-no-ref" {:op :transport-leg/log :subject "shipment-1"})

    (exec! actor runs "leg-breach" {:op :transport-leg/log :subject "shipment-1"
                                    :handoff breach-handoff
                                    :actual-temp-min-c 12.0 :actual-temp-max-c 14.0})

    (exec! actor runs "leg-clean" {:op :transport-leg/log :subject "shipment-1"
                                   :handoff clean-handoff
                                   :actual-temp-min-c 3.0 :actual-temp-max-c 6.0})
    (approve! actor runs "leg-clean")

    (exec! actor runs "leg-dup" {:op :transport-leg/log :subject "shipment-1"
                                 :handoff clean-handoff
                                 :actual-temp-min-c 3.0 :actual-temp-max-c 6.0})

    {:db db :runs @runs}))

;; ----------------------------- derived views -----------------------------

(defn- hard-hold?
  "A HARD hold is a governor hold the graph reached WITHOUT ever
  interrupting for a human -- it was never overridable."
  [{:keys [disposition interrupted?]}]
  (and (= :hold disposition) (not interrupted?)))

(defn hard-holds [runs] (filterv hard-hold? runs))

(defn hold-facts
  "The `:governor-hold` facts the actor actually appended to the SSoT."
  [db]
  (filterv #(= :governor-hold (:t %)) (store/ledger db)))

(defn approval-facts
  "Every `:approval-granted` fact the graph emitted, in run order.
  These live on the graph's `:audit` channel; see `approver-on-record?`
  for why they are the only place some of them survive."
  [runs]
  (vec (for [r runs
             f (:audit r)
             :when (= :approval-granted (:t f))]
         (assoc (select-keys f [:op :subject :by]) :thread (:thread r)))))

(defn approver-on-record?
  "Does the SSoT itself carry the approver for this committed op, or is
  the attribution only in the run's audit channel?

  `freightops.operation`'s `:request-approval` node attaches the
  approver under the record's `:payload` key. `store/commit-record!`
  reads `:payload` for `:assessment/set` ONLY -- `:shipment/upsert` and
  `:transport-leg/upsert` destructure `:value`, and
  `:shipment/mark-dispatched`/`:shipment/mark-settled` read only
  `:path`. So who approved a dispatch, a settlement or a transport leg
  never reaches the SSoT. This function probes the store rather than
  asserting that from the source, so the page stays honest if the
  scaffold is fixed."
  [db {:keys [op subject]}]
  (case op
    :jurisdiction/assess (some? (:approved-by (store/assessment-of db subject)))
    :transport-leg/log   (boolean
                          (some (fn [ref]
                                  (:approved-by (store/transport-leg db ref)))
                                ["trk-clean"]))
    false))

(defn- observed-dispositions
  "op -> the set of dispositions this run actually produced for it."
  [runs]
  (reduce (fn [m {:keys [op disposition]}]
            (update m op (fnil conj #{}) disposition))
          {} runs))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-str [v] (if (keyword? v) (name v) (str v)))

(defn- join-basis [basis]
  (str/join ", " (map kw-str basis)))

(defn- td [& cells] (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- last-fact-for [ledger subject]
  (last (filter #(= (:subject %) subject) ledger)))

(defn- status-cell [ledger subject]
  (let [f (last-fact-for ledger subject)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (join-basis (:basis f))) "</span>")
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      :else (str "<span class=\"muted\">" (esc (kw-str (:t f))) "</span>"))))

(defn- actuation-cell [{:keys [dispatched? settled? dispatch-number settlement-number]}]
  (cond
    settled? (str "<span class=\"ok\">settled</span> <code>" (esc settlement-number) "</code>")
    dispatched? (str "<span class=\"warn\">dispatched, unsettled</span> <code>"
                     (esc dispatch-number) "</code>")
    :else "<span class=\"muted\">not dispatched</span>"))

(defn- tracking-cell
  "The tracking number, marked by the SAME capability-library predicate
  the governor uses (`kotoba.logistics/tracking-valid?` via
  `freightops.registry/tracking-valid?`) -- not a copy of its verdict."
  [tracking]
  (if (registry/tracking-valid? tracking)
    (str "<code>" (esc tracking) "</code>")
    (str "<code>" (esc tracking) "</code> <span class=\"critical\">invalid</span>")))

(defn- shipment-row [ledger {:keys [id origin destination carrier jurisdiction
                                    tracking declared-value] :as sh}]
  (td (esc id)
      (str (esc origin) " &rarr; " (esc destination))
      (esc carrier)
      (esc jurisdiction)
      (tracking-cell tracking)
      (str "<span class=\"amt\">" (esc declared-value) "</span>")
      (actuation-cell sh)
      (status-cell ledger id)))

(defn- gate-row
  "One row of the action-gate table, derived from `freightops.phase`
  itself (not a hand-written description) plus what this run observed."
  [{:keys [writes auto]} observed o]
  (let [seen (get observed o)]
    (td (str "<code>" (esc o) "</code>")
        (if (contains? writes o)
          "<span class=\"ok\">enabled</span>"
          "<span class=\"muted\">disabled</span>")
        (if (contains? auto o)
          "<span class=\"ok\">auto-commit when governor-clean</span>"
          "<span class=\"warn\">human approval required &middot; never auto at any phase</span>")
        (if (seq seen)
          (esc (str/join ", " (sort (map kw-str seen))))
          "<span class=\"muted\">not exercised</span>"))))

(defn- hold-row [{:keys [op subject basis violations confidence]}]
  (td (str "<span class=\"critical\">" (esc (join-basis basis)) "</span>")
      (str "<code>" (esc op) "</code>")
      (esc subject)
      (esc (str/join " / " (map :detail violations)))
      (str "<span class=\"amt\">" (esc confidence) "</span>")))

(defn- leg-row
  "One committed third-party transport-leg confirmation, read back out
  of the SSoT by its carrier-tracking-ref."
  [db ref]
  (when-let [rec (store/transport-leg db ref)]
    (let [h (:handoff rec)]
      (td (str "<code>" (esc ref) "</code>")
          (esc (:handoff/source-actor h))
          ;; NB the entity is appended OUTSIDE `esc` -- passing "&deg;C"
          ;; through `esc` escapes its own ampersand and the cell renders
          ;; the literal text "&deg;C" instead of a degree sign.
          (str (esc (str (:handoff/cold-chain-temp-min-c h) " ~ "
                         (:handoff/cold-chain-temp-max-c h) " ")) "&deg;C")
          (str (esc (str (:transport/actual-temp-min-c rec) " ~ "
                         (:transport/actual-temp-max-c rec) " ")) "&deg;C")
          "<span class=\"ok\">leg confirmed</span>"))))

(defn- draft-row [rec]
  (td (str "<code>" (esc (get rec "record_id")) "</code>")
      (esc (get rec "kind"))
      (esc (get rec "shipment_id"))
      (esc (get rec "jurisdiction"))
      (if (get rec "immutable")
        "<span class=\"ok\">immutable</span>"
        "<span class=\"warn\">mutable</span>")))

(defn- approval-row [db {:keys [op subject by]}]
  (td (str "<code>" (esc op) "</code>")
      (esc subject)
      (str "<code>" (esc by) "</code>")
      (if (approver-on-record? db {:op op :subject subject})
        "<span class=\"ok\">on the SSoT record</span>"
        "<span class=\"warn\">not on record &middot; audit-channel only</span>")))

(defn- ledger-row [{:keys [t op subject basis summary]}]
  (td (esc (kw-str t))
      (str "<code>" (esc op) "</code>")
      (esc subject)
      (esc (or summary (join-basis basis)))))

(defn- section [title lead headers rows]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"
       "  </section>\n"))

(defn render
  "Renders the whole operator console from a completed `run-demo!`
  result. Reads the SSoT for everything it can, and the graph's audit
  channel only for approver attribution the SSoT does not keep."
  [{:keys [db runs]}]
  (let [ledger (vec (store/ledger db))
        holds (hold-facts db)
        hard (hard-holds runs)
        phase-n phase/default-phase
        phase-cfg (get phase/phases phase-n)
        observed (observed-dispositions runs)]
    (str
     "<html><head><meta charset=\"utf-8\">"
     "<title>cloud-itonami-isic-4920 &middot; community-freight-transport</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Community freight transport (ISIC 4920) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · shipment dispatch and consignment settlement are always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>This page is generated by running the actor</h2>\n"
     "    <p>Every row below was produced by <code>freightops.render-html</code> driving the real "
     "<code>freightops.operation</code> StateGraph through <code>langgraph.graph/run*</code> against a fresh "
     "<code>freightops.store/seed-db</code> — the same path <code>clojure -M:dev:run</code> takes. "
     "Regenerate with <code>clojure -M:dev:render-html</code>; the output is byte-identical across runs.</p>\n"
     "    <p class=\"muted\">Rollout phase " phase-n " (<code>" (esc (:label phase-cfg)) "</code>) · "
     (count ledger) " ledger facts · " (count holds) " governor holds, "
     (count hard) " of them HARD (never reached a human) · "
     (count (store/dispatch-history db)) " dispatch drafts · "
     (count (store/settlement-history db)) " settlement drafts.</p>\n"
     "  </section>\n"

     (section "Shipments"
              (str "Directory state after the run, read back from the SSoT (<code>store/all-shipments</code>). "
                   "Dispatch and settlement reference numbers come from <code>freightops.registry</code>, "
                   "which the actor invoked at commit time.")
              ["Shipment" "Route" "Carrier" "Jurisdiction" "Tracking" "Declared value"
               "Actuation" "Last op status"]
              (map (partial shipment-row ledger) (store/all-shipments db)))

     (section (str "Action gate (Freight Governor, phase " phase-n ")")
              (str "Derived from <code>freightops.phase/phases</code> itself. "
                   "<code>:shipment/dispatch</code> and <code>:consignment/settle</code> are members of "
                   "<code>write-ops</code> but of no phase's <code>:auto</code> set — a permanent structural "
                   "fact, enforced independently a second time by the governor's "
                   "<code>high-stakes</code> actuation gate.")
              ["Op" "Phase writes" "Auto-commit" "Dispositions observed this run"]
              (map (partial gate-row phase-cfg observed) (sort phase/write-ops)))

     (section "HARD holds this run"
              (str "Each row is a <code>:governor-hold</code> fact the actor appended to the SSoT ledger, "
                   "with the governor's own violation detail. A HARD hold is not overridable: the graph "
                   "never interrupted for a human on any of these.")
              ["Rule" "Op" "Shipment" "Governor detail" "Advisor confidence"]
              (map hold-row holds))

     (section "Third-party carrier confirmations (ADR-2800000700)"
              (str "This actor as the CARRIER in another actor's <code>:handoff</code>, keyed by "
                   "<code>:handoff/carrier-tracking-ref</code> — read back from the SSoT. "
                   "Legs whose reported transport temperature left the declared cold-chain window, "
                   "or that carry no resolvable tracking ref, appear in the HARD-hold table above and "
                   "wrote nothing here.")
              ["Carrier tracking ref" "Handoff source actor" "Declared window" "Reported actual" "Outcome"]
              (keep (partial leg-row db) ["trk-clean" "trk-breach" "trk-dup"]))

     (section "Registry drafts"
              (str "Append-only shipment-dispatch and consignment-settlement records built by "
                   "<code>freightops.registry</code>. Every certificate this actor produces is UNSIGNED — "
                   "signature is the carrier's act, not the actor's.")
              ["Record id" "Kind" "Shipment" "Jurisdiction" "Status"]
              (map draft-row (concat (store/dispatch-history db)
                                     (store/settlement-history db))))

     (section "Approvals this run"
              (str "Joined from the <code>:approval-granted</code> facts the graph emitted. "
                   "The last column is probed against the SSoT, not asserted: "
                   "<code>store/commit-record!</code> reads the approver-bearing <code>:payload</code> key "
                   "for <code>:assessment/set</code> only, so a dispatch, a settlement or a transport leg "
                   "keeps no approver on its record. That is reported as it is, not papered over.")
              ["Op" "Subject" "Approved by" "Approver attribution"]
              (map (partial approval-row db) (approval-facts runs)))

     (section "Audit ledger (this run)"
              (str "The append-only decision-fact log itself — every commit and every hold this scenario "
                   "produced, in order.")
              ["Fact" "Op" "Subject" "Summary / basis"]
              (map ledger-row ledger))

     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        holds (hold-facts db)
        hard (hard-holds runs)
        committed (filterv #(= :committed (:t %)) (store/ledger db))]
    ;; Build-time invariant, not a convention: a console that shows no
    ;; governor hold is not evidence that the governor works. If the
    ;; scenario ever stops producing one, this build FAILS rather than
    ;; quietly publishing a page implying everything auto-commits.
    ;; Checked against the SSoT ledger itself, not against the run
    ;; records -- the ledger is what the page renders.
    (when (empty? holds)
      (throw (ex-info "no :governor-hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count (store/ledger db))})))
    ;; ...and at least one of those holds must have been reached WITHOUT
    ;; the graph ever interrupting for a human, i.e. genuinely HARD.
    (when (empty? hard)
      (throw (ex-info "render-html: every governor hold this run passed through a human interrupt -- none was HARD, so the page cannot evidence a non-overridable hold"
                      {:governor-holds (count holds)
                       :hard-holds 0
                       :ledger-facts (count (store/ledger db))})))
    (when (zero? (count committed))
      (throw (ex-info "render-html: scenario produced 0 commits -- a console showing only holds cannot show the approval path"
                      {:committed 0 :ledger-facts (count (store/ledger db))})))
    (spit out (render result))
    (println "wrote" out
             "(" (count (store/ledger db)) "ledger facts,"
             (count holds) "governor holds," (count hard) "of them HARD,"
             (count committed) "commits,"
             (count (store/dispatch-history db)) "dispatch drafts,"
             (count (store/settlement-history db)) "settlement drafts )")))
