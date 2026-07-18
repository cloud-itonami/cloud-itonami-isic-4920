(ns freightops.phase
  "Phase 0->3 staged rollout for the community-freight actor.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- shipment intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-assess  -- adds jurisdiction assessment writes,
                                 still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:shipment/intake` (no capital risk
                                 yet) may auto-commit. `:shipment/
                                 dispatch`/`:consignment/settle` NEVER
                                 auto-commit, at any phase.

  `:shipment/dispatch`/`:consignment/settle` are deliberately ABSENT
  from every phase's `:auto` set, including phase 3 -- a permanent
  structural fact, not a rollout milestone still to come. Dispatching
  a real shipment and settling a real consignment are the two real-
  world legal/financial acts this actor performs; both are always a
  human carrier operator's call. `freightops.governor`'s `:actuation/
  dispatch-shipment`/`:actuation/settle-consignment` high-stakes gate
  enforces the same invariant independently -- two layers, not one,
  agree on this. Like every prior sibling's phase 3 `:auto` set, this
  domain has only ONE member (`:shipment/intake`) -- no separate
  no-capital-risk 'file' lifecycle distinct from the shipment itself.

  `:transport-leg/log` (ADR-2800000700, this actor's own THIRD-PARTY
  carrier-confirmation op over some OTHER actor's `:handoff`) is a
  gated `write-ops` member like `:jurisdiction/assess` -- governor-
  gated, enabled at phase 3, but likewise deliberately ABSENT from
  every phase's `:auto` set: it always needs human approval when
  governor-clean, same as `:jurisdiction/assess`. Do not add it to any
  `:auto` set.")

(def read-ops  #{})
(def write-ops #{:shipment/intake :jurisdiction/assess :shipment/dispatch :consignment/settle
                 :transport-leg/log})

;; NOTE the invariant: `:shipment/dispatch`/`:consignment/settle` are
;; members of `write-ops` (governor-gated like any write) but are
;; NEVER members of any phase's `:auto` set below. Do not add them
;; there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"       :writes #{}                                                                :auto #{}}
   1 {:label "assisted-intake" :writes #{:shipment/intake}                                                :auto #{}}
   2 {:label "assisted-assess" :writes #{:shipment/intake :jurisdiction/assess}                             :auto #{}}
   3 {:label "supervised-auto" :writes write-ops
      :auto #{:shipment/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:shipment/dispatch`/`:consignment/settle` are never auto-eligible
    at any phase, so they always escalate once the governor clears
    them (or hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Freight Governor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
