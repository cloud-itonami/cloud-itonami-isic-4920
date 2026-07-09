(ns freightops.facts
  "Per-jurisdiction carrier-safety AND cargo-liability-disclosure
  regulatory catalog -- the G2-style spec-basis table the Freight
  Governor checks every `:jurisdiction/assess` proposal against ('did
  the advisor cite an OFFICIAL public source for this jurisdiction's
  requirements, or did it invent one?').

  This blueprint's own text (docs/business-model.md's already-written
  `:freight-governor` Decision Rule) requires 'settlement-affecting
  actions require governor approval' -- a real, distinct regulatory
  concern underlies this: most jurisdictions require a carrier to
  disclose its cargo-liability limits (how much the carrier owes if
  freight is lost/damaged) before a consignment can be legally
  settled, with a dedicated legal regime independent of general
  carrier-safety law. Each jurisdiction entry below therefore cites
  BOTH the general carrier-safety law AND a SEPARATE cargo-liability-
  disclosure law.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries. As with
  `leathergoods`/9523's own brand-authenticity sub-citation,
  `ictrepair`/9511's own media-sanitization sub-citation, and
  `retailops`/4711's own unit-pricing sub-citation, ALL FOUR seeded
  jurisdictions actually have a real cargo-liability-disclosure regime
  here, reported honestly rather than forcing an artificial gap.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  shipment-registration/carrier-authorization/tracking-record evidence
  set (PLUS a cargo-liability-disclosure record for every seeded
  jurisdiction); `:legal-basis` / `:owner-authority` / `:provenance`
  are the G2 citation the governor requires before any
  `:jurisdiction/assess` proposal can commit.
  `:liability-owner-authority` / `:liability-legal-basis` /
  `:liability-provenance` are the SEPARATE cargo-liability-disclosure
  citation the governor's `cargo-liability-disclosure-violation?`
  check is grounded in."
  {"JPN" {:name "Japan"
          :owner-authority "国土交通省 (Ministry of Land, Infrastructure, Transport and Tourism, MLIT)"
          :legal-basis "貨物自動車運送事業法 (Motor Truck Transportation Business Act)"
          :national-spec "貨物運送事業の運行安全に関する一般基準"
          :provenance "https://www.mlit.go.jp/jidosha/jidosha_tk10_000012.html"
          :required-evidence ["出荷登録記録 (shipment-registration record)"
                              "事業者認可記録 (carrier-authorization record)"
                              "追跡記録 (tracking record)"
                              "運送責任限度額開示記録 (cargo-liability-disclosure record)"]
          :liability-owner-authority "国土交通省 (MLIT)"
          :liability-legal-basis "商法 (Commercial Code) 第570条以下 運送営業規定"
          :liability-provenance "https://www.mlit.go.jp/jidosha/jidosha_tk10_000012.html"}
   "USA" {:name "United States"
          :owner-authority "Federal Motor Carrier Safety Administration (FMCSA)"
          :legal-basis "Motor Carrier Safety Regulations (49 C.F.R. Parts 300-399)"
          :national-spec "FMCSA operating-authority/safety-fitness standards for motor carriers"
          :provenance "https://www.fmcsa.dot.gov/regulations"
          :required-evidence ["Shipment-registration record"
                              "Carrier-authorization record"
                              "Tracking record"
                              "Cargo-liability-disclosure record"]
          :liability-owner-authority "Surface Transportation Board (STB) / FMCSA"
          :liability-legal-basis "Carmack Amendment (49 U.S.C. §14706, motor carrier cargo liability)"
          :liability-provenance "https://www.fmcsa.dot.gov/regulations/title49/section/14706"}
   "GBR" {:name "United Kingdom"
          :owner-authority "Driver and Vehicle Standards Agency (DVSA)"
          :legal-basis "Road Traffic Act 1988 / Goods Vehicles (Licensing of Operators) Act 1995"
          :national-spec "DVSA operator-licensing/road-safety enforcement standards"
          :provenance "https://www.gov.uk/government/organisations/driver-and-vehicle-standards-agency"
          :required-evidence ["Shipment-registration record"
                              "Carrier-authorization record"
                              "Tracking record"
                              "Cargo-liability-disclosure record"]
          :liability-owner-authority "Department for Transport"
          :liability-legal-basis "Carriage of Goods by Road Act 1965 (implementing the CMR Convention)"
          :liability-provenance "https://www.legislation.gov.uk/ukpga/1965/37"}
   "DEU" {:name "Germany"
          :owner-authority "Bundesamt für Güterverkehr (BAG)"
          :legal-basis "Güterkraftverkehrsgesetz (GüKG, Goods Transport by Road Act)"
          :national-spec "GüKG Betriebs- und Sicherheitsanforderungen für Güterkraftverkehrsunternehmen"
          :provenance "https://www.gesetze-im-internet.de/gukg/"
          :required-evidence ["Sendungsregistrierungsnachweis (shipment-registration record)"
                              "Frachtführerzulassungsnachweis (carrier-authorization record)"
                              "Sendungsverfolgungsnachweis (tracking record)"
                              "Haftungsoffenlegungsnachweis (cargo-liability-disclosure record)"]
          :liability-owner-authority "Bundesamt für Güterverkehr (BAG)"
          :liability-legal-basis "Handelsgesetzbuch (HGB) §407 ff. Frachtgeschäft; CMR-Übereinkommen"
          :liability-provenance "https://www.gesetze-im-internet.de/hgb/"}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch a
  shipment or settle a consignment on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4920 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `freightops.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

(defn liability-spec-basis
  "The jurisdiction's cargo-liability-disclosure requirement map, or
  nil -- nil means this jurisdiction has NO formal statutory cargo-
  liability-disclosure regime this catalog is aware of. In this R0
  catalog all four seeded jurisdictions actually have one (unlike some
  prior siblings' own honest single-jurisdiction gap), reported
  honestly."
  [iso3]
  (when-let [sb (spec-basis iso3)]
    (when (:liability-owner-authority sb)
      (select-keys sb [:liability-owner-authority :liability-legal-basis :liability-provenance]))))
