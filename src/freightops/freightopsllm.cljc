(ns freightops.freightopsllm
  "FreightOps-LLM client -- the *contained intelligence node* for the
  community-freight actor.

  It normalizes shipment intake, drafts a per-jurisdiction carrier-
  safety/cargo-liability-disclosure evidence checklist, drafts the
  shipment-dispatch action, and drafts the consignment-settlement
  action. CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real dispatch/settlement. Every output is
  censored downstream by `freightops.governor` before anything touches
  the SSoT, and `:shipment/dispatch`/`:consignment/settle` proposals
  NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/dispatch-shipment | :actuation/settle-consignment | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [freightops.facts :as facts]
            [freightops.registry :as registry]
            [freightops.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the tracking number, origin/destination or
  jurisdiction. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "出荷記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :shipment/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction carrier-safety/cargo-liability-disclosure evidence
  checklist draft. `:no-spec?` injects the failure mode we must defend
  against: proposing a checklist for a jurisdiction with NO official
  spec-basis in `freightops.facts` -- the Freight Governor must reject
  this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [sh (store/shipment db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction sh))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "freightops.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-shipment-dispatch
  "Draft the actual SHIPMENT-DISPATCH action -- dispatching a real
  shipment onto a route. ALWAYS `:stake :actuation/dispatch-shipment`
  -- this is a REAL-WORLD act (a vehicle/robot physically moves
  freight), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`freightops.phase`); the governor also always escalates on
  `:actuation/dispatch-shipment`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [sh (store/shipment db subject)
        tracking-ok? (and sh (registry/tracking-valid? (:tracking sh)))
        pod-ok? (and sh (:prior-leg-pod-confirmed? sh))
        no-exception? (and sh (or (not (:exception-raised? sh)) (:exception-resolved? sh)))]
    {:summary    (str subject " 向け配車提案"
                      (when sh (str " (carrier=" (:carrier sh) ")")))
     :rationale  (if sh
                   (str "tracking-valid?=" tracking-ok?
                        " prior-leg-pod-confirmed?=" pod-ok?
                        " exception-clear?=" no-exception?)
                   "shipmentが見つかりません")
     :cites      (if sh [subject] [])
     :effect     :shipment/mark-dispatched
     :value      {:shipment-id subject}
     :stake      :actuation/dispatch-shipment
     :confidence (if (and tracking-ok? pod-ok? no-exception?) 0.9 0.3)}))

(defn- propose-consignment-settlement
  "Draft the actual CONSIGNMENT-SETTLEMENT action -- settling a real
  consignment. ALWAYS `:stake :actuation/settle-consignment` -- this
  is a REAL-WORLD act (real money moves between carrier and
  customer), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`freightops.phase`); the governor also always escalates on
  `:actuation/settle-consignment`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [sh (store/shipment db subject)
        liability-ok? (and sh (:liability-disclosure-confirmed? sh))
        no-exception? (and sh (or (not (:exception-raised? sh)) (:exception-resolved? sh)))]
    {:summary    (str subject " 向け精算提案"
                      (when sh (str " (carrier=" (:carrier sh) ")")))
     :rationale  (if sh
                   (str "liability-disclosure-confirmed?=" liability-ok?
                        " exception-clear?=" no-exception?)
                   "shipmentが見つかりません")
     :cites      (if sh [subject] [])
     :effect     :shipment/mark-settled
     :value      {:shipment-id subject}
     :stake      :actuation/settle-consignment
     :confidence (if (and liability-ok? no-exception?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :shipment/intake             (normalize-intake db request)
    :jurisdiction/assess             (assess-jurisdiction db request)
    :shipment/dispatch                   (propose-shipment-dispatch db request)
    :consignment/settle                      (propose-consignment-settlement db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは地域貨物輸送事業者の配車・精算エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:shipment/upsert|:assessment/set|:shipment/mark-dispatched|"
       ":shipment/mark-settled) "
       ":stake(:actuation/dispatch-shipment か :actuation/settle-consignment か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"
       "配達証明(POD)の確認状況や運送責任限度額の開示状況を偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess    {:shipment (store/shipment st subject)}
    :shipment/dispatch      {:shipment (store/shipment st subject)}
    :consignment/settle     {:shipment (store/shipment st subject)}
    {:shipment (store/shipment st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Freight Governor escalates/
  holds -- an LLM hiccup can never auto-dispatch a shipment or auto-
  settle a consignment."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :freightopsllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
