# ADR-0001: FreightOps-LLM ⊣ Freight Governor architecture

## Status

Accepted. `cloud-itonami-isic-4920` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-4920` publishes an OSS business blueprint for
community freight transport (shipment booking, tracking, multi-modal
routing, consignment settlement). Like every prior actor in this
fleet, the blueprint alone is not an implementation: this ADR records
the governed-actor architecture that promotes it to real, tested
code, following the same langgraph StateGraph + independent Governor
+ Phase 0→3 rollout pattern established by `cloud-itonami-isic-6511`
(life insurance) and applied across 87 prior siblings, most recently
`cloud-itonami-isic-4711` (community retail operations).

Like `cloud-itonami-isic-4711`, this blueprint's own `docs/business-
model.md` already published a fully detailed `:freight-governor`
Decision Rule -- approve/reject conditions, a required-technologies
table, and a tie to a companion playable prototype (`network-isekai`'s
"ITONAMI: Freight Dispatch", ADR-2607031000) -- BEFORE this actor's
code existed. This build implements that published design faithfully.

This is also the SECOND vertical in this fleet built on top of a
real, pre-existing bespoke domain capability library
([`kotoba-lang/logistics`](https://github.com/kotoba-lang/logistics)
-- shipment/tracking/route/consignment pure-data contracts, named in
this blueprint's own README `Capability layer` section), following the
pattern `cloud-itonami-isic-4711`'s own ADR-0001 established for
`kotoba-lang/retail`.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:freight-governor`, is grep-verified UNIQUE fleet-wide -- no naming-
collision precedent question, a fresh independent build.

## Decision

### Decision 1: implement the ALREADY-PUBLISHED Decision Rule faithfully

`freightops.governor` is a faithful implementation of the Decision
Rule `docs/business-model.md` already published: approve dispatch only
when a valid tracking number exists, consignment/POD chain integrity
is intact for the lane, and the sign-off is single-use; approve
settlement only when cargo-liability is disclosed and no unresolved
exception exists; reject/escalate on any violation of these.

### Decision 2: wrap `kotoba-lang/logistics`, don't reimplement it

`freightops.registry/tracking-valid?` delegates directly to `kotoba.
logistics/tracking-valid?`. The actor layer adds the governed
proposal/approval loop on top of this existing, independently-tested
domain library; it does not duplicate the tracking-number structural
contract. This is the SECOND vertical in this fleet to establish this
architectural pattern, after `cloud-itonami-isic-4711`'s own
`kotoba-lang/retail` integration.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `shipment` entity

Unlike `retailops`/4711's own `order` entity (distinguished by
`:kind`, alternative sale-or-reorder actions), this vertical's
`dispatch` and `settle` actuation events apply SEQUENTIALLY to the
SAME `shipment` -- dispatch happens first, settlement happens later,
matching the repair-shop cluster's own `ticket` shape more closely
(two real-world acts, in order, on one entity). `high-stakes` is
`#{:actuation/dispatch-shipment :actuation/settle-consignment}`.

### Decision 4: `tracking-number-invalid?` -- the 73rd unconditional-evaluation grounding, the SAME genuinely new sub-category `retailops`/4711 establishes

Before writing this check, every prior sibling's governor namespace
was grepped for any check function named `tracking-invalid`,
`tracking-number-invalid` or similar -- zero hits, confirming this is
a genuinely new concept, though structurally the SAME sub-category
`retailops.governor/ean13-invalid-violations` establishes (reusing a
CAPABILITY LIBRARY's own validated function rather than a sibling
actor's check). The 73rd distinct application of the unconditional-
evaluation-screening discipline overall (most recently `retailops.
governor/price-band-violation-violations` at 72nd). Evaluated
UNCONDITIONALLY on every `:shipment/dispatch`.

### Decision 5: `pod-chain-integrity-broken?` -- the 74th unconditional-evaluation grounding, a genuinely new concept

Grep-verified absent fleet-wide (zero hits for `pod-chain`, `proof-
of-delivery` as a governor check name). Grounded in real cargo-
liability/chain-of-custody law: the US Carmack Amendment (49 U.S.C.
§14706) and the CMR Convention (implemented in the UK via the
Carriage of Goods by Road Act 1965 and in Germany via HGB §407 ff.)
both condition carrier liability on an intact proof-of-delivery chain
across route legs. Evaluated UNCONDITIONALLY on every `:shipment/
dispatch` (every dispatch needs its prior leg's POD on file, when a
prior leg exists).

### Decision 6: `cargo-liability-disclosure-unconfirmed?` -- the 75th unconditional-evaluation grounding, the FLAGSHIP genuinely new check

Grep-verified absent fleet-wide (zero hits for `cargo-liability`,
`carmack`, `cmr-convention` as a governor check name). Grounded in real
cargo-liability-disclosure law: the US Carmack Amendment (49 U.S.C.
§14706), the UK's Carriage of Goods by Road Act 1965 (implementing
the CMR Convention), Germany's HGB §407 ff. Frachtgeschäft (also CMR),
and Japan's own 商法 (Commercial Code) 運送営業 provisions. Unlike some
prior repair-shop-cluster siblings' own honest single-jurisdiction
gap, ALL EIGHT seeded jurisdictions actually have a real regime here,
reported honestly (matching `leathergoods`/9523's own, `ictrepair`/
9511's own and `retailops`/4711's own full-coverage sub-citations).
Evaluated UNCONDITIONALLY on every `:consignment/settle`. See
"Addendum: IND/SAU/ARE/MEX jurisdiction extension" below for the four
jurisdictions seeded after this ADR was first written.

### Decision 7: `delivery-exception-unresolved?` -- explicit, unconditional across both actuation ops

Grounded directly in this blueprint's own README text ("No automated
advice can dispatch a shipment with an invalid tracking number,
suppress a delivery exception, or settle a consignment without
governor approval"). Evaluated UNCONDITIONALLY across BOTH
`:shipment/dispatch` and `:consignment/settle` -- an open exception
blocks either act, not just one.

### Decision 8: dedicated double-actuation-guard booleans

`:dispatched?`/`:settled?` are dedicated booleans on the `shipment`
record, never a single `:status` value -- the same discipline every
prior governor's guards establish, informed by `cloud-itonami-isic-
6492`'s real status-lifecycle bug (ADR-2607071320).

### Decision 9: Store protocol, MemStore + DatomicStore parity

`freightops.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.
db`-backed), proven to satisfy the same contract in
`test/freightops/store_contract_test.clj`.

### Decision 10: `blueprint.edn` field-sync fix, and scoped-down R0

Like `cloud-itonami-isic-4711`, this repo's `blueprint.edn` DID need a
field-sync fix: `:required-technologies` was missing `:robotics`
(present in the `kotoba-lang/industry` registry's own entry for
`"4920"`, which also correctly includes `:optimization` as required,
matching this blueprint's own original list) -- fixed by adding
`:robotics` to `:required-technologies`, matching registry's exact
list. (An earlier version of this fix incorrectly also moved
`:optimization` into `:optional-technologies`, based on a misread of
the registry entry; this was caught and corrected in a same-day
follow-up commit before promotion.) Separately, this R0 build
deliberately scopes DOWN from the full Decision Rule already
published: multi-modal route optimization is left as a follow-up
slice, even though its capability is already correctly marked
required.

## Alternatives considered

- **Reimplementing tracking-number validation in-repo.** Rejected:
  `kotoba-lang/logistics` already provides an independently-tested,
  pure-data function for exactly this.
- **A `:kind`-distinguished entity** (matching `retailops`/4711's own
  `order` shape). Rejected: dispatch and settlement happen
  SEQUENTIALLY on the SAME shipment in this domain, not as
  alternative actions -- the repair-shop cluster's own sequential
  `ticket` shape is the honest match here, not retail's own shape.
- **Building the FULL Decision Rule in one commit** (including
  multi-modal route optimization). Rejected in favor of a scoped R0
  slice, consistent with this fleet's own "extending coverage is
  additive" convention.

## Consequences

- 88th actor in this fleet (87 implemented before this build).
- SECOND vertical in this fleet to integrate a real, pre-existing
  bespoke domain capability library rather than self-contained logic.
- Establishes three genuinely NEW unconditional-evaluation-discipline
  checks: `tracking-number-invalid?` (73rd), `pod-chain-integrity-
  broken?` (74th), and `cargo-liability-disclosure-unconfirmed?`
  (FLAGSHIP, 75th).
- `MemStore` ‖ `DatomicStore` parity is proven by
  `test/freightops/store_contract_test.clj`.
- 37 tests / 180 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean dispatch + settlement
  lifecycle, plus five HARD-hold scenarios, end-to-end.
- `blueprint.edn` required a field-sync fix (`:robotics` missing from
  `:required-technologies`) in addition to the `:maturity` flip.

## Addendum: IND/SAU/ARE/MEX jurisdiction extension

`freightops.facts/catalog` grows from 4 to 8 seeded jurisdictions:
India, Saudi Arabia, UAE and Mexico added alongside the original
JPN/USA/GBR/DEU, each with a real carrier-safety/licensing AND a real,
SEPARATE cargo-liability-disclosure citation -- same schema, no new
fields. Two honesty notes worth recording:

- **ARE**: UAE road-freight carrier licensing is genuinely
  emirate-level operational detail under a thin federal framework
  (Federal Law No. 9 of 2011) -- Abu Dhabi Mobility's Resolution No.
  74 of 2021 is cited as the concrete, verified regime (each emirate
  issues its own equivalent; this is not claimed as a single uniform
  UAE-wide rule, the same honesty discipline `terminal`/5210's own
  jurisdiction-level citations already establish).
- **MEX**: Mexico's cargo-liability regime (Código de Comercio, Arts.
  576-604) is codified private/commercial law enforced through
  ordinary courts, not a dedicated regulatory agency the way MoRTH/
  TGA/UAE-MOJ are for the other three -- reported as such rather than
  inventing an agency that does not exist for this function.

Every new entry's `:provenance`/`:liability-provenance` URL resolves
to an official government domain (`morth.gov.in`, `tga.gov.sa`,
`misa.gov.sa`, `admobility.gov.ae`, `uaelegislation.gov.ae`,
`gob.mx`, `diputados.gob.mx`), verified by direct web research at
extension time, never carried over from training-data recall alone.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of
  the general governed-actor architecture pattern)
- `cloud-itonami-isic-4711/docs/adr/0001-architecture.md` (origin of
  the "wrap a real capability library" pattern this build follows)
- `kotoba-lang/logistics` (the capability library this build wraps)
- Carmack Amendment, 49 U.S.C. §14706 (US)
- Carriage of Goods by Road Act 1965 (CMR Convention) (UK)
- Handelsgesetzbuch (HGB) §407 ff. (CMR Convention) (Germany)
- 商法 (Commercial Code) 運送営業規定 (Japan)
- Motor Vehicles Act, 1988 + Carriage by Road Act, 2007 (India)
- TGA Regulation Organizing Freight Transport (Decision 1/1068);
  Civil Transactions Law, Royal Decree M/191, 2023 (Saudi Arabia)
- Abu Dhabi Mobility Resolution No. 74 of 2021; Federal Decree-Law
  No. 50 of 2022 (UAE)
- Ley de Caminos, Puentes y Autotransporte Federal; Código de
  Comercio Arts. 576-604 (Mexico)
