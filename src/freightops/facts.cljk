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
  `retailops`/4711's own unit-pricing sub-citation, ALL EIGHT seeded
  jurisdictions actually have a real cargo-liability-disclosure regime
  here, reported honestly rather than forcing an artificial gap --
  including MEX, whose cargo-liability regime is codified private/
  commercial law (Código de Comercio) enforced through ordinary courts
  rather than a dedicated regulatory agency, reported as such rather
  than inventing an agency that does not exist for this function.")

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
          :liability-provenance "https://www.gesetze-im-internet.de/hgb/"}
   "IND" {:name "India"
          :owner-authority "Ministry of Road Transport and Highways (MoRTH)"
          :legal-basis "Motor Vehicles Act, 1988, as amended by the Motor Vehicles (Amendment) Act, 2019 -- National Permit scheme"
          :national-spec "A commercial goods-transport vehicle must hold a valid registration + National/State Permit and comply with fitness/driver-licensing rules to lawfully operate as a carrier"
          :provenance "https://morth.gov.in/en/national-permit"
          :required-evidence ["Shipment-registration record"
                              "Carrier-authorization record"
                              "Tracking record"
                              "Cargo-liability-disclosure record"]
          :liability-owner-authority "Ministry of Road Transport and Highways (MoRTH)"
          :liability-legal-basis "Carriage by Road Act, 2007 (replaced the colonial-era Carriers Act, 1865)"
          :liability-provenance "https://morth.gov.in/en/carriage-road-act-2007"}
   "SAU" {:name "Saudi Arabia"
          :owner-authority "الهيئة العامة للنقل (Transport General Authority, TGA)"
          :legal-basis "اللائحة المنظمة لنشاط نقل البضائع ووسطاء الشحن وتأجير الشاحنات على الطرق البرية (Regulation Organizing Freight Transport, Freight Forwarding and Truck-Rental Activity on Land Roads, TGA Decision 1/1068)"
          :national-spec "Carriers must hold commercial registration for freight activity, a current vehicle technical-inspection certificate and civil-liability vehicle insurance, and log operations on the 'Logisti' platform"
          :provenance "https://www.tga.gov.sa/en/Regulations"
          :required-evidence ["Shipment-registration record"
                              "Carrier-authorization record"
                              "Tracking record"
                              "Cargo-liability-disclosure record"]
          :liability-owner-authority "وزارة العدل / المحاكم التجارية (Ministry of Justice / Commercial Courts)"
          :liability-legal-basis "نظام المعاملات المدنية (Civil Transactions Law, Royal Decree M/191, 2023) -- contract-of-carriage (عقد النقل) provisions"
          :liability-provenance "https://misa.gov.sa/app/uploads/2025/07/Civil-Transactions-Law.pdf"}
   "ARE" {:name "United Arab Emirates"
          :owner-authority "Abu Dhabi Mobility (formerly Department of Municipalities and Transport), under the federal Law No. 9 of 2011 on Land Transportation"
          :legal-basis "Resolution No. 74 of 2021, Regulation of Transport of Goods in the Emirate of Abu Dhabi"
          :national-spec "Carriers must hold an Activity Permit, drivers a Vocational Permit, and comply with GPS/electronic-tracking, axle-weight and 3-year maintenance-record requirements -- an emirate-level regime under the federal land-transport framework (each emirate issues its own equivalent; Abu Dhabi's is the one cited here)"
          :provenance "https://admobility.gov.ae/-/media/feature/itc-revamp/generic-content/related-documents/asateel/related-doc/freight-regulations-en.pdf"
          :required-evidence ["Shipment-registration record"
                              "Carrier-authorization record"
                              "Tracking record"
                              "Cargo-liability-disclosure record"]
          :liability-owner-authority "UAE Ministry of Justice / federal courts, under the Commercial Transactions Law"
          :liability-legal-basis "Federal Decree-Law No. 50 of 2022 (Commercial Transactions Law), Arts. 296-308 (Contract of Carriage)"
          :liability-provenance "https://uaelegislation.gov.ae/en/legislations/1610"}
   "MEX" {:name "Mexico"
          :owner-authority "Secretaría de Infraestructura, Comunicaciones y Transportes (SICT)"
          :legal-basis "Ley de Caminos, Puentes y Autotransporte Federal (LCPAF) + Reglamento de Autotransporte Federal y Servicios Auxiliares (RAFSA); NOM-012-SCT-2-2017 (weight/dimension limits)"
          :national-spec "A carrier must hold an SICT-issued permiso de autotransporte federal de carga general (vehicle registration, liability insurance, RFC/tax status) to operate on federal routes"
          :provenance "https://www.gob.mx/tramites/ficha/expedicion-del-permiso-para-el-servicio-de-autotransporte-federal-de-carga-general/SCT1699"
          :required-evidence ["Registro de embarque (shipment-registration record)"
                              "Autorización de transportista (carrier-authorization record)"
                              "Registro de rastreo (tracking record)"
                              "Divulgación de responsabilidad de carga (cargo-liability-disclosure record)"]
          :liability-owner-authority "Poder Judicial (ordinary commercial courts) -- codified private/commercial law, not a dedicated regulatory agency"
          :liability-legal-basis "Código de Comercio, Libro Segundo, Título Décimo, Cap. I, Arts. 576-604 (Del Contrato Mercantil de Transporte Terrestre)"
          :liability-provenance "https://www.diputados.gob.mx/LeyesBiblio/pdf/CCom.pdf"}})

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
  catalog all eight seeded jurisdictions actually have one (unlike some
  prior siblings' own honest single-jurisdiction gap), reported
  honestly."
  [iso3]
  (when-let [sb (spec-basis iso3)]
    (when (:liability-owner-authority sb)
      (select-keys sb [:liability-owner-authority :liability-legal-basis :liability-provenance]))))

;; ─────────────── Cross-Actor Handoff (isic-1075/jsic-4721/isic-5610/
;; isic-4711/isic-4719/isic-2710/isic-2813 -> isic-4920, THIS actor as
;; independent carrier) ───────────────
;;
;; A `:transport-leg/log` proposal's `:value` MAY carry a `:handoff`
;; record -- the SAME wire shape documented in cloud-itonami-jsic-4721's
;; `coldchain.facts`/superproject ADR-2607177600, extended by
;; superproject ADR-2800000700 with two OPTIONAL fields
;; `:handoff/carrier-actor`/`:handoff/carrier-tracking-ref`. Every
;; existing handoff issuer/receiver (isic-1075, jsic-4721, isic-5610,
;; isic-4711, isic-4719, isic-2710, isic-2813) ignores unknown map
;; keys, so this extension required ZERO code changes on any of their
;; sides -- see ADR-2800000700 for why:
;;
;;   {:handoff/id "..."
;;    :handoff/source-actor "cloud-itonami-jsic-4721"
;;    :handoff/batch-id "..."
;;    :handoff/product-type-id :coldchain/c3-chilled
;;    :handoff/cold-chain-temp-min-c 2.0
;;    :handoff/cold-chain-temp-max-c 10.0
;;    :handoff/quantity-kg 120.5
;;    :handoff/dispatched-at-iso "..."
;;    :handoff/carrier-actor "cloud-itonami-isic-4920"    ; NEW, OPTIONAL
;;    :handoff/carrier-tracking-ref "..."}                ; NEW, OPTIONAL
;;
;; This actor validates its own INDEPENDENT half of the contract (no
;; shared code, no shared store, the SAME asymmetric-optional design
;; every cross-actor reference in this fleet uses): did THIS carrier's
;; own REPORTED actual-transport temperature range
;; (`:transport/actual-temp-min-c`/`max-c`, a `:transport-leg/log`-only
;; proposal field, never invented by the handoff's issuer/receiver)
;; stay within the handoff's DECLARED cold-chain window? A handoff
;; carrying no declared window (ordinary, non-refrigerated freight)
;; makes no cold-chain claim to keep, so no comparison is meaningful.

(defn handoff-declares-cold-chain-window?
  "Does `handoff` declare a cold-chain temperature window at all
  (`:handoff/cold-chain-temp-min-c`/`max-c` both present)? False for
  ordinary, non-refrigerated freight -- these OPTIONAL fields are
  absent on purpose for a handoff with no cold-chain claim."
  [handoff]
  (boolean (and (some? (:handoff/cold-chain-temp-min-c handoff))
                (some? (:handoff/cold-chain-temp-max-c handoff)))))

(defn handoff-cold-chain-maintained?
  "Positive-sense convenience predicate: did `actual-min-c`/
  `actual-max-c` (THIS carrier's own reported transport temperature
  range) stay WITHIN `handoff`'s declared `:handoff/cold-chain-temp-
  min-c`/`max-c` window (inclusive)? A handoff with no declared window
  is defined as trivially maintained -- nothing to violate. A declared
  window with a MISSING actual reading is NOT trivially maintained --
  an omitted reading is not evidence of a maintained cold chain, the
  same 'never trust a self-report alone' discipline every governor
  check in this actor already applies."
  [handoff actual-min-c actual-max-c]
  (or (not (handoff-declares-cold-chain-window? handoff))
      (boolean (and (some? actual-min-c) (some? actual-max-c)
                    (<= (:handoff/cold-chain-temp-min-c handoff) actual-min-c)
                    (<= actual-max-c (:handoff/cold-chain-temp-max-c handoff))))))
