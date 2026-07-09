# Business Model: Community Freight Transport

## Classification
- Repository: `cloud-itonami-4920`
- ISIC Rev.5: `4920` — freight transport by road
- Domain: `commerce/freight-transport`
- Social impact: supply resilience, rural access, decarbonization
- Governor: `:freight-governor`
- License: AGPL-3.0-or-later

## Customer
- regional and last-mile carriers
- cooperative haulers and farmers' logistics groups
- rural access programs
- merchants leaving closed TMS SaaS

## Offer
- shipment booking and tracking
- multi-modal route planning
- consignment and proof-of-delivery records
- exception and claims workflows
- settlement and reconciliation
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per carrier
- support retainer with SLA
- tracking and settlement integration

## The `:freight-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:freight-governor`. It is the single
authority that stands between "a shipment could move" and "a shipment is allowed to move," and
every rule it enforces is traceable to the domain (Community Freight Transport, ISIC 4920) and
the three `:social-impact` tags in `blueprint.edn`.

**Approves dispatch of a shipment onto a route only when all of the following hold:**
1. **A valid tracking number exists for the shipment** — the governor will not authorize
   dispatch against a shipment record that lacks a validated tracking number (see Trust
   Controls below). This is the direct enforcement mechanism for `:supply-resilience`: a
   shipment that cannot be tracked cannot be recovered if a lane fails, so it never leaves the
   depot.
2. **Consignment and proof-of-delivery (POD) chain integrity is intact for the lane** —
   the governor checks that the prior leg on that route closed with a recorded POD before
   authorizing the next leg. This protects `:rural-access`: rural/last-mile lanes are the ones
   most likely to have a single carrier and no fallback, so a broken POD chain there is treated
   as a hard stop rather than a warning.
3. **The sign-off is single-use, not a standing permit** — a carrier or driver must check in
   for authorization immediately before each dispatch; an earlier approval does not carry over
   to the next route. This keeps the audit ledger honest (one sign-off event per shipment, not
   a blanket credential) and is the rule the companion game (below) makes literally playable.
4. **Settlement-affecting actions require governor approval** — reconciliation and payout
   runs are held for governor sign-off before they post, so a dispute or claim can be caught
   before money moves, not after.

**Rejects (and routes to human/exception handling) when:**
- the shipment has no valid tracking number,
- the route/consignment integrity is broken (missing prior POD, mismatched lane),
- a settlement or claim has not been reconciled,
- a delivery exception has been raised and not yet resolved — exceptions cannot be silently
  suppressed to force a dispatch or settlement through.

The `:decarbonization` tag is enforced upstream of the governor, in the route-planning step
(see `:optimization` below) — the governor's job is authorization integrity, not route choice,
but it will not approve dispatch onto a route that bypasses the multi-modal plan the
optimizer produced, since that plan is the vehicle for lower-emission lane and mode selection.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business, and what each
one is actually load-bearing for here (not a generic capability list):

| Technology | What it is FOR in Community Freight Transport |
|---|---|
| `:logistics` | The shipment/consignment domain model itself — booking, lane assignment, tracking-number issuance, and multi-modal route state that every other technology in this table reads or writes. |
| `:identity` | Carrier, driver, and vehicle identity plus role-based access, so the governor's sign-off can be tied to *who* checked in for a route, not just *that* someone did. |
| `:forms` | Structured intake for shipment booking, consignment/POD capture, and exception and claims submission — the data the governor rule actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:freight-governor` decision rule itself (tracking-number validity, POD-chain integrity, single-use sign-off, settlement hold) as an evaluable decision table rather than code buried in application logic — this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake → propose → approve → execute → audit loop end-to-end (see `docs/operator-guide.md`) across booking, dispatch, delivery, and settlement, including the exception-escalation gate. |
| `:audit-ledger` | The immutable record of every sign-off, dispatch, POD, exception, and settlement — this is what "auditable for every consignment" (Trust Controls, below) actually means in practice. |
| `:optimization` | Multi-modal route planning — selects lanes/modes for a shipment, which is both the "multi-modal route planning" line in Offer and the mechanism for the `:decarbonization` social-impact tag (lower-emission mode/lane selection). |

`:itonami.blueprint/optional-technologies` adds `:telemetry` — vehicle/shipment position and
condition feeds for carriers who want live tracking beyond the tracking-number/POD minimum; it
is not required for the governor rule to function.

## Trust Controls
- shipments with invalid tracking numbers cannot dispatch
- delivery exceptions cannot be silently suppressed
- settlements require governor approval
- proof-of-delivery is auditable for every consignment
- customer and route data stays outside Git

## Implementation notes (`:implemented`)

The Decision Rule above was published before this actor existed; the actual
`freightops.governor` implements it faithfully, calling `kotoba-lang/logistics`'s own validated
`tracking-valid?` function rather than reimplementing it (the SECOND vertical in this fleet to
wrap a real, pre-existing bespoke capability library, after `retailops`/4711's own
`kotoba-lang/retail` integration):

- `tracking-number-invalid-violations` — the tracking-number-validity check above, delegating
  to the capability library, evaluated unconditionally on every `:shipment/dispatch`.
- `pod-chain-integrity-violations` — the POD-chain-integrity check above, a genuinely new
  concept grounded in the US Carmack Amendment and the CMR Convention (UK/Germany).
- `cargo-liability-disclosure-violations` — the FLAGSHIP genuinely new check this vertical
  adds, grounded in a real 4-jurisdiction cargo-liability-disclosure catalog
  (`freightops.facts`): the US Carmack Amendment, the UK's Carriage of Goods by Road Act 1965
  (CMR), Germany's HGB §407 ff. (CMR), and Japan's own 商法 (Commercial Code).
- `delivery-exception-unresolved-violations` — the exception-suppression check above,
  evaluated unconditionally across both dispatch and settlement.
- `already-dispatched-violations` / `already-settled-violations` — the double-actuation guards
  every sibling actor in this fleet uses.

`:shipment/dispatch` and `:consignment/settle` are the two real-world actuation events
(`#{:actuation/dispatch-shipment :actuation/settle-consignment}`), applied SEQUENTIALLY to the
SAME shipment (dispatch first, settlement later) rather than `retailops`/4711's own `:kind`-
distinguished alternative-action shape — neither ever auto-commits at any phase. Multi-modal
route optimization (the `:optimization` line above) is a follow-up slice, not in this R0 build
-- see README `Business-process coverage`.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. In this domain that models a fleet
whose route-clearing behavior is itself an operating-state machine gated by the same
freight-governor sign-off described above — not a human dispatcher acting as the sole
approval path, but an authorization state (`equipped`/not-equipped) that must be true before a
route can be legitimately cleared, and is consumed on use. The companion prototype at
network-isekai turns exactly this operating-state machine into a playable loop (see
`docs/operator-guide.md`).
