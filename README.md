# cloud-itonami-isic-4920

Open Business Blueprint for **ISIC Rev.5 4920**: Community Freight
Transport -- shipment booking, tracking, multi-modal routing, and
consignment settlement for a local carrier.

This repository publishes a community-freight actor -- shipment
intake, per-jurisdiction carrier-safety/cargo-liability-disclosure
regulatory assessment, shipment dispatch and consignment settlement --
as an OSS business that any qualified operator can fork, deploy, run,
improve and sell, so a regional or last-mile carrier never surrenders
shipment and proof-of-delivery data to a closed TMS SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet (87 prior actors) -- here it is
**FreightOps-LLM ⊣ Freight Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:freight-governor`, is a
UNIQUE keyword fleet-wide (grep-verified: no other blueprint declares
it) -- a fresh, independent build.

**This is the SECOND vertical in this fleet built on top of a real,
pre-existing bespoke domain capability library**
([`kotoba-lang/logistics`](https://github.com/kotoba-lang/logistics)
-- shipment/tracking/route/consignment pure-data contracts, after
`retailops`/4711's own `kotoba-lang/retail` integration) rather than
self-contained domain logic: `freightops.registry` calls `kotoba.
logistics/tracking-valid?` directly, it does not reimplement the
tracking-number structural contract. This blueprint's own `docs/
business-model.md` already published a detailed `:freight-governor`
Decision Rule before this actor existed; `freightops.governor`
implements that published design faithfully.

> **Why an actor layer at all?** An LLM is great at drafting a
> shipment summary, normalizing records, and checking a tracking
> number's basic shape -- but it has **no notion of which
> jurisdiction's carrier-safety/cargo-liability-disclosure law is
> official, no license to dispatch a real shipment or settle a real
> consignment, and no way to know on its own whether a prior route
> leg's proof-of-delivery is actually on file or whether a carrier's
> cargo-liability limits have actually been disclosed**. Letting it
> dispatch a shipment or settle a consignment directly invites
> fabricated regulatory citations, freight moving onto a route with a
> broken chain of custody, and a settlement closing without the
> carrier's own liability limits ever being disclosed -- exposing the
> carrier to real cargo-liability exposure and the customer to
> undisclosed risk -- and liability, for whoever runs it. This project
> seals the FreightOps-LLM into a single node and wraps it with an
> independent **Freight Governor**, a human **approval workflow**, and
> an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers shipment intake through carrier-safety/cargo-
liability-disclosure regulatory assessment, shipment dispatch and
consignment settlement. It does **not**, by itself, hold any
operating authority required to run a freight-carrier business in a
given jurisdiction, and it does not claim to. It also does not
perform the actual physical movement of freight itself, or judge
routing quality -- multi-modal route optimization (the blueprint's own
`:optimization` technology) is a follow-up slice, not in this R0.
Whoever deploys and operates a live instance (a qualified carrier
dispatcher/operator) supplies any jurisdiction-specific operating
authority, the real vehicle/robot dispatch integration and the real
TMS-system integrations, and bears that jurisdiction's liability -- the
software supplies the governed, spec-cited, audited execution
scaffold so that operator does not have to build the compliance layer
from scratch.

### Actuation

**Dispatching a real shipment and settling a real consignment are
never autonomous, at any phase, by construction.** Two independent
layers enforce this (`freightops.governor`'s `:actuation/dispatch-
shipment`/`:actuation/settle-consignment` high-stakes gate and
`freightops.phase`'s phase table, which never puts either op in any
phase's `:auto` set) -- see `freightops.phase`'s docstring and
`test/freightops/phase_test.clj`'s `shipment-dispatch-never-auto-at-
any-phase`/`consignment-settle-never-auto-at-any-phase`. The actor may
draft, check and recommend; a human carrier dispatcher is always the
one who actually dispatches a shipment or settles a consignment.
Grounded directly in this blueprint's own `docs/business-model.md`
text ("Settlement-affecting actions require governor approval" /
"Approves dispatch of a shipment onto a route only when all of the
following hold...") -- a genuine DUAL-actuation shape, applied
SEQUENTIALLY to the SAME shipment (dispatch first, settlement later),
unlike `retailops`/4711's own `:kind`-distinguished alternative-action
shape.

## The core contract

```
shipment intake + jurisdiction facts (freightops.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ FreightOps-LLM        │ ─────────────▶ │ Freight Governor              │  (independent system)
   │ (sealed)              │  + citations    │ spec-basis · evidence-       │
   └───────────────────────┘                 │ incomplete · tracking-       │
          │                 commit ◀┼ number-invalid (NEW, cap-lib     │
          │                         │ reuse) · pod-chain-integrity            │
    record + ledger        escalate ┼ broken (NEW) · cargo-liability-          │
          │              (ALWAYS for│ disclosure-unconfirmed (FLAGSHIP          │
          │       :actuation/dispatch│ NEW) · delivery-exception-                │
          │       -shipment/         │ unresolved · already-dispatched ·         │
          │       :actuation/settle- │ already-settled                           │
          │       consignment)       │                                            │
          ▼                          └───────────────────────┘
      human approval
```

**The FreightOps-LLM never dispatches a shipment or settles a
consignment the Freight Governor would reject, and never does so
without a human sign-off.** Hard violations (fabricated regulatory
requirements; unsupported evidence; an invalid tracking number; a
broken POD chain; an unconfirmed cargo-liability disclosure; an
unresolved delivery exception; a double dispatch/settlement) force
**hold** and *cannot* be approved past; a clean dispatch/settlement
proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dispatch + settlement lifecycle, plus five HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here an autonomous delivery
vehicle or last-mile robot performs the physical movement of freight,
under the actor, gated by the independent **Freight Governor**. The
governor never dispatches hardware itself: a route-clearing action
must have cleared the same sign-off a human dispatcher would need.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Freight Governor, dispatch/settlement draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record. A companion playable prototype
lives in `gftdcojp/network-isekai` (`:itonami.blueprint/game`,
ADR-2607031000).

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4920`). This IS backed by a bespoke domain capability lib:
[`kotoba-lang/logistics`](https://github.com/kotoba-lang/logistics)
(shipment, tracking, route, consignment pure-data contracts) --
`freightops.*` calls its functions directly rather than reimplementing
them, on top of the generic robotics/identity/forms/dmn/bpmn/audit-
ledger stack.

## Layout

| File | Role |
|---|---|
| `src/freightops/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + dispatch AND settlement history (dual history). The double-actuation guard checks dedicated `:dispatched?`/`:settled?` booleans rather than a `:status` value |
| `src/freightops/registry.cljc` | Dispatch/settlement draft records, wrapping `kotoba.logistics`'s own `tracking-valid?` function rather than reimplementing it |
| `src/freightops/facts.cljc` | Per-jurisdiction carrier-safety AND cargo-liability-disclosure catalog with an official spec-basis citation per entry, honest coverage reporting -- ALL EIGHT seeded jurisdictions have a liability sub-citation here |
| `src/freightops/freightopsllm.cljc` | **FreightOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/jurisdiction-assessment/dispatch/settlement proposals |
| `src/freightops/governor.cljc` | **Freight Governor** -- 6 HARD checks (spec-basis · evidence-incomplete · tracking-number-invalid, capability-lib reuse, the 73rd unconditional-evaluation-discipline grounding · pod-chain-integrity-broken, the 74th grounding · cargo-liability-disclosure-unconfirmed, FLAGSHIP NEW, the 75th grounding · delivery-exception-unresolved) + 2 double-actuation guards + 3 HARD checks for this actor's own THIRD-PARTY carrier role over an OTHER actor's `:handoff` (carrier-tracking-ref-missing · cold-chain-breach · transport-leg-already-logged, ADR-2800000700) + 1 soft (confidence/actuation gate) |
| `src/freightops/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (dispatch/settlement always human; shipment intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/freightops/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/freightops/sim.cljc` | demo driver |
| `test/freightops/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers shipment intake through carrier-safety/cargo-
liability-disclosure regulatory assessment, shipment dispatch and
consignment settlement -- the core governed lifecycle this
blueprint's own `docs/business-model.md` names in its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Shipment intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:shipment/intake`/`:jurisdiction/assess`) | Real TMS/vehicle-dispatch-system integration, multi-modal route optimization (see `freightops.facts`'s docstring) |
| Shipment dispatch, HARD-gated on full evidence, a valid tracking number and an intact POD chain, plus a double-dispatch guard (`:actuation/dispatch-shipment`) | |
| Consignment settlement, HARD-gated on full evidence, a confirmed cargo-liability disclosure and no unresolved delivery exception, plus a double-settlement guard (`:actuation/settle-consignment`) | |
| Immutable audit ledger for every intake/assessment/dispatch/settlement decision | |
| Third-party carrier confirmation of an OTHER cross-actor `:handoff`'s transport leg, HARD-gated on a resolvable carrier-tracking-ref and (when the handoff declares one) a maintained cold-chain window, plus a double-log guard (`:transport-leg/log`, ADR-2800000700) | |

Extending coverage is additive: add the next gate (e.g. a driver
hours-of-service check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor re-
verifies against the actor's own records before any real-world act"
pattern this repo's flagship ops already establish.

### Cross-actor handoff carrier confirmation (`:transport-leg/log`)

This actor is not only an ISSUER of shipments -- it is also, from
several OTHER `cloud-itonami-isic-*`/`-jsic-*` actors' point of view,
the CARRIER that physically moves a handed-off batch/consignment
between them (isic-1075⇔jsic-4721, jsic-4721⇔isic-5610/isic-4711/
isic-4719, isic-2710⇔isic-2813 -- superproject ADR-2607177600/
ADR-2800000000/ADR-2800000500). Those actors' own shared `:handoff`
wire shape gained two OPTIONAL fields, `:handoff/carrier-actor`/
`:handoff/carrier-tracking-ref` (superproject ADR-2800000700) --
**no code change on any of their sides was needed**, since every
existing handoff issuer/receiver already ignores unknown map keys.

`:transport-leg/log` is THIS actor's own independent record that it
carried the leg a `:handoff/carrier-tracking-ref` identifies:

- `carrier-tracking-ref-missing` (HARD) -- no `:handoff` at all, or a
  `:handoff` with no `:handoff/carrier-tracking-ref`.
- `cold-chain-breach` (HARD) -- when the referenced handoff declares
  a cold-chain temperature window (`:handoff/cold-chain-temp-min-c`/
  `max-c`, optional per ADR-2607177600), this carrier's own reported
  `:transport/actual-temp-min-c`/`max-c` must have stayed within it
  (`freightops.facts/handoff-cold-chain-maintained?`) -- an omitted
  reading counts as a breach, never a pass. Ordinary (non-
  refrigerated) handoffs need no comparison.
- `transport-leg-already-logged` (double-log guard) -- refuses to log
  the SAME `:handoff/carrier-tracking-ref` twice.

Like `:jurisdiction/assess`, `:transport-leg/log` is governor-gated
and enabled at phase 3, but deliberately never a member of any
phase's `:auto` set -- it always routes to a human, even when clean.

## Jurisdiction coverage (honest)

`freightops.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `freightops.facts/catalog` --
currently 8 seeded (JPN, USA, GBR, DEU, IND, SAU, ARE, MEX) out of
~194 jurisdictions worldwide. This is a starting catalog to prove the
governor contract end-to-end, not a claim of global coverage. Adding a
jurisdiction is additive: one map entry in `freightops.facts/catalog`,
citing a real official source -- never fabricate a jurisdiction's
requirements to make coverage look bigger. Note that the cargo-
liability-disclosure sub-citation is FULL coverage rather than a gap:
ALL EIGHT seeded jurisdictions (JPN, USA, GBR, DEU, IND, SAU, ARE,
MEX) actually have a real cargo-liability-disclosure enforcement
regime, reported honestly -- including MEX, whose regime is codified
private/commercial law (Código de Comercio) enforced through ordinary
courts rather than a dedicated regulatory agency, reported as such
rather than inventing an agency that does not exist for this
function.

## Maturity

`:implemented` -- `FreightOps-LLM` + `Freight Governor` run as real,
tested code (see `Run` above), promoted from the originally-published
`:blueprint`-tier scaffold, following the SAME governed-actor
architecture as the 87 other prior actors across this fleet, with its
own distinct, independently-named governor and its own novel
integration with a real, pre-existing bespoke capability library. See
`docs/adr/0001-architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
