(ns freightops.governor
  "Freight Governor -- the independent compliance layer that earns the
  FreightOps-LLM the right to commit. The LLM has no notion of
  jurisdictional carrier-safety/cargo-liability-disclosure law,
  whether a shipment's own tracking number is structurally valid,
  whether a shipment's prior route leg actually closed with a
  recorded proof-of-delivery before the next leg is authorized,
  whether a carrier's cargo-liability limits have actually been
  disclosed, whether an open delivery exception has actually been
  resolved, or when an act stops being a draft and becomes a real-
  world shipment dispatch or consignment settlement, so this MUST be
  a separate system able to *reject* a proposal and fall back to HOLD.

  Like `retailops`/4711's own `governor`, this is the SECOND vertical
  in this fleet built on TOP of a real, pre-existing bespoke domain
  capability library (`kotoba-lang/logistics`) rather than self-
  contained domain logic -- `freightops.registry` calls `kotoba.
  logistics/tracking-valid?` directly rather than reimplementing it.
  This blueprint's own `docs/business-model.md` already publishes a
  detailed `:freight-governor` Decision Rule (written before this
  actor existed) naming exactly the checks below; this governor
  implements that published design faithfully rather than inventing
  one from a generic template.

  `:itonami.blueprint/governor` is `:freight-governor`, grep-verified
  UNIQUE fleet-wide -- no naming-collision precedent question, a
  fresh independent build following the SAME governed-actor
  architecture (langgraph StateGraph + independent Governor + Phase
  0->3 rollout) established by `cloud-itonami-isic-6511`.

  Seven checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them. The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `freightops.phase`: for `:stake
  :actuation/dispatch-shipment`/`:actuation/settle-consignment` (a
  real dispatch or settlement) NO phase ever allows auto-commit
  either. Two independent layers agree that actuation is always a
  human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`freightops.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:shipment/dispatch`/
                                       `:consignment/settle`, has the
                                       jurisdiction actually been
                                       assessed with a full evidence
                                       checklist on file?
    3. Tracking number invalid     -- for `:shipment/dispatch`,
                                       INDEPENDENTLY verify the
                                       shipment's own tracking number
                                       passes the structural contract
                                       via `kotoba.logistics/tracking-
                                       valid?` (through `freightops.
                                       registry/tracking-valid?`) --
                                       the actor layer calls the
                                       CAPABILITY LIBRARY's own
                                       validated function rather than
                                       reimplementing it, the SAME
                                       genuinely new sub-category
                                       `retailops.governor/ean13-
                                       invalid-violations` establishes
                                       (reusing a capability library's
                                       own logic, not a sibling
                                       actor's check), the 73rd
                                       distinct application of the
                                       unconditional-evaluation
                                       discipline overall (most
                                       recently `retailops.governor/
                                       price-band-violation-
                                       violations` at 72nd). Evaluated
                                       UNCONDITIONALLY (every dispatch
                                       needs a valid tracking number).
    4. POD chain integrity broken  -- for `:shipment/dispatch`,
                                       INDEPENDENTLY verify the
                                       shipment's own `:prior-leg-pod-
                                       confirmed?` is true -- a
                                       genuinely new concept (grep-
                                       verified absent fleet-wide --
                                       zero hits for 'pod-chain'/
                                       'proof-of-delivery' as a
                                       governor check function name),
                                       the 74th distinct application
                                       overall. Grounded in real cargo-
                                       liability/chain-of-custody law:
                                       the US Carmack Amendment
                                       (49 U.S.C. §14706) and the CMR
                                       Convention (implemented in the
                                       UK via the Carriage of Goods by
                                       Road Act 1965 and in Germany via
                                       HGB §407 ff.) both condition
                                       carrier liability on an intact
                                       proof-of-delivery chain across
                                       route legs. Evaluated
                                       UNCONDITIONALLY (every dispatch
                                       needs its prior leg's POD on
                                       file, when a prior leg exists).
    5. Cargo liability disclosure
       unconfirmed                    -- for `:consignment/settle`,
                                       INDEPENDENTLY verify the
                                       shipment's own `:liability-
                                       disclosure-confirmed?` is true
                                       -- the FLAGSHIP genuinely new
                                       check this vertical adds (grep-
                                       verified absent fleet-wide --
                                       zero hits for 'cargo-liability'/
                                       'carmack'/'cmr-convention' as a
                                       governor check function name),
                                       the 75th distinct application
                                       overall. Grounded in real cargo-
                                       liability-disclosure law: the US
                                       Carmack Amendment (49 U.S.C.
                                       §14706), the UK's Carriage of
                                       Goods by Road Act 1965
                                       (implementing the CMR
                                       Convention), Germany's HGB §407
                                       ff. Frachtgeschäft (also CMR),
                                       and Japan's own 商法
                                       (Commercial Code) 運送営業
                                       provisions -- ALL FOUR seeded
                                       jurisdictions actually have a
                                       real regime here, reported
                                       honestly (matching
                                       `leathergoods`/9523's,
                                       `ictrepair`/9511's and
                                       `retailops`/4711's own full-
                                       coverage sub-citations).
                                       Evaluated UNCONDITIONALLY (every
                                       settlement needs a confirmed
                                       liability disclosure).
    6. Delivery exception
       unresolved                     -- reported by THIS proposal
                                       itself, or already on file for
                                       the shipment (`:exception-
                                       raised? true` AND `:exception-
                                       resolved? false`) -- is a HARD,
                                       un-overridable hold, matching
                                       this blueprint's own text
                                       ('delivery exceptions cannot be
                                       silently suppressed to force a
                                       dispatch or settlement
                                       through'). Evaluated
                                       UNCONDITIONALLY across both
                                       `:shipment/dispatch` and
                                       `:consignment/settle`.
    7. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:shipment/
                                       dispatch`/`:consignment/settle`
                                       (REAL acts) -> escalate.

  Two more guards, double-dispatch/double-settlement prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-dispatched-
  violations`/`already-settled-violations` refuse to dispatch/settle
  the SAME shipment twice, off dedicated `:dispatched?`/`:settled?`
  facts (never a `:status` value) -- the SAME 'check a dedicated
  boolean, not status' discipline every prior governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-lifecycle
  bug (ADR-2607071320)."
  (:require [freightops.facts :as facts]
            [freightops.registry :as registry]
            [freightops.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real shipment and settling a real consignment are the
  two real-world actuation events this actor performs -- a two-member
  set, matching every sibling's own dual-actuation shape."
  #{:actuation/dispatch-shipment :actuation/settle-consignment})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:shipment/dispatch`/`:consignment/
  settle`) proposal with no spec-basis citation is a HARD violation --
  never invent a jurisdiction's carrier-safety/cargo-liability-
  disclosure requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :shipment/dispatch :consignment/settle} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:shipment/dispatch`/`:consignment/settle`, the jurisdiction's
  required shipment-registration/carrier-authorization/tracking/
  cargo-liability evidence must actually be satisfied -- do not trust
  the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:shipment/dispatch :consignment/settle} op)
    (let [sh (store/shipment st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction sh) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(出荷登録記録/事業者認可記録/追跡記録/運送責任限度額開示記録等)が充足していない状態での提案"}]))))

(defn- tracking-number-invalid-violations
  "For `:shipment/dispatch`, INDEPENDENTLY verify the shipment's own
  tracking number passes `kotoba.logistics/tracking-valid?` (through
  `freightops.registry/tracking-valid?`) -- calls the capability
  library's own validated function rather than reimplementing it.
  Evaluated UNCONDITIONALLY (every dispatch needs a valid tracking
  number)."
  [{:keys [op subject]} st]
  (when (= op :shipment/dispatch)
    (let [sh (store/shipment st subject)]
      (when-not (registry/tracking-valid? (:tracking sh))
        [{:rule :tracking-number-invalid
          :detail (str subject " の追跡番号(" (:tracking sh) ")が構造検証に失敗")}]))))

(defn- pod-chain-integrity-violations
  "For `:shipment/dispatch`, INDEPENDENTLY verify the shipment's own
  `:prior-leg-pod-confirmed?` is true before authorizing dispatch onto
  the next leg -- a genuinely new concept. Evaluated UNCONDITIONALLY
  (every dispatch needs its prior leg's POD on file)."
  [{:keys [op subject]} st]
  (when (= op :shipment/dispatch)
    (let [sh (store/shipment st subject)]
      (when-not (true? (:prior-leg-pod-confirmed? sh))
        [{:rule :pod-chain-integrity-broken
          :detail (str subject " は前区間の配達証明(POD)が未確認 -- 次区間への配車提案は進められない")}]))))

(defn- cargo-liability-disclosure-violations
  "For `:consignment/settle`, INDEPENDENTLY verify the shipment's own
  `:liability-disclosure-confirmed?` is true -- the FLAGSHIP genuinely
  new check this vertical adds. Evaluated UNCONDITIONALLY (every
  settlement needs a confirmed liability disclosure)."
  [{:keys [op subject]} st]
  (when (= op :consignment/settle)
    (let [sh (store/shipment st subject)]
      (when-not (true? (:liability-disclosure-confirmed? sh))
        [{:rule :cargo-liability-disclosure-unconfirmed
          :detail (str subject " は運送責任限度額の開示が未確認 -- 精算提案は進められない")}]))))

(defn- delivery-exception-unresolved-violations
  "An unresolved delivery exception -- reported by THIS proposal
  itself, or already on file for the shipment -- is a HARD,
  un-overridable hold. Evaluated UNCONDITIONALLY across both
  `:shipment/dispatch` and `:consignment/settle` so neither op can
  proceed while an exception sits open."
  [{:keys [op subject]} st]
  (when (contains? #{:shipment/dispatch :consignment/settle} op)
    (let [sh (store/shipment st subject)]
      (when (and (true? (:exception-raised? sh)) (not (true? (:exception-resolved? sh))))
        [{:rule :delivery-exception-unresolved
          :detail (str subject " は未解決の配送例外がある -- 配車/精算提案は進められない")}]))))

(defn- already-dispatched-violations
  "For `:shipment/dispatch`, refuses to dispatch the SAME shipment
  twice, off a dedicated `:dispatched?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :shipment/dispatch)
    (when (store/shipment-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に配車済み")}])))

(defn- already-settled-violations
  "For `:consignment/settle`, refuses to settle the SAME shipment's
  consignment twice, off a dedicated `:settled?` fact (never a
  `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :consignment/settle)
    (when (store/shipment-already-settled? st subject)
      [{:rule :already-settled
        :detail (str subject " は既に精算済み")}])))

(defn check
  "Censors a FreightOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (tracking-number-invalid-violations request st)
                           (pod-chain-integrity-violations request st)
                           (cargo-liability-disclosure-violations request st)
                           (delivery-exception-unresolved-violations request st)
                           (already-dispatched-violations request st)
                           (already-settled-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
