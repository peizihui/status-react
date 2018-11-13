(ns status-im.hardwallet.core
  (:require [re-frame.core :as re-frame]
            [status-im.react-native.js-dependencies :as js-dependencies]
            [status-im.ui.screens.navigation :as navigation]
            [status-im.utils.config :as config]
            [status-im.utils.fx :as fx]
            [status-im.utils.platform :as platform]
            [taoensso.timbre :as log]))

(defn check-nfc-support []
  (when config/hardwallet-enabled?
    (.. js-dependencies/nfc-manager
        -default
        isSupported
        (then #(re-frame/dispatch [:hardwallet.callback/check-nfc-support-success %])))))

(defn check-nfc-enabled []
  (when platform/android?
    (.. js-dependencies/nfc-manager
        -default
        isEnabled
        (then #(re-frame/dispatch [:hardwallet.callback/check-nfc-enabled-success %])))))

(fx/defn set-nfc-support
  [{:keys [db]} supported?]
  {:db (assoc-in db [:hardwallet :nfc-supported?] supported?)})

(fx/defn set-nfc-enabled
  [{:keys [db]} enabled?]
  {:db (assoc-in db [:hardwallet :nfc-enabled?] enabled?)})

(defn open-nfc-settings []
  (when platform/android?
    (.. js-dependencies/nfc-manager
        -default
        goToNfcSetting)))

(fx/defn navigate-to-connect-screen [cofx]
  (fx/merge cofx
            {:hardwallet/check-nfc-enabled  nil
             :hardwallet/register-tag-event nil}
            (navigation/navigate-to-cofx :hardwallet-connect nil)))

(defn hardwallet-supported? [db]
  (and config/hardwallet-enabled?
       platform/android?
       (get-in db [:hardwallet :nfc-supported?])))

(fx/defn return-back-from-nfc-settings [{:keys [db]}]
  (when (= :hardwallet-connect (:view-id db))
    {:hardwallet/check-nfc-enabled nil}))

(defn- proceed-to-pin-confirmation [fx]
  (assoc-in fx [:db :hardwallet :pin :enter-step] :confirmation))

(defn- pin-match [fx]
  (assoc-in fx [:db :hardwallet :pin :status] :validating))

(defn- pin-mismatch [fx]
  (assoc-in fx [:db :hardwallet :pin] {:status       :error
                                       :error        :t/pin-mismatch
                                       :original     []
                                       :confirmation []
                                       :enter-step   :original}))

(fx/defn process-pin-input
  [{:keys [db]} number enter-step]
  (let [db' (update-in db [:hardwallet :pin enter-step] conj number)
        numbers-entered (count (get-in db' [:hardwallet :pin enter-step]))]
    (cond-> {:db (assoc-in db' [:hardwallet :pin :status] nil)}
      (and (= enter-step :original)
           (= 6 numbers-entered))
      (proceed-to-pin-confirmation)

      (and (= enter-step :confirmation)
           (= (get-in db' [:hardwallet :pin :original])
              (get-in db' [:hardwallet :pin :confirmation])))
      (pin-match)

      (and (= enter-step :confirmation)
           (= 6 numbers-entered)
           (not= (get-in db' [:hardwallet :pin :original])
                 (get-in db' [:hardwallet :pin :confirmation])))
      (pin-mismatch))))

(defn- register-tag-event []
  (when config/hardwallet-enabled?
    (.. js-dependencies/nfc-manager
        -default
        (registerTagEvent #(re-frame/dispatch [:hardwallet.callback/on-tag-discovered %]))
        (then #(log/debug "[hardwallet] register tag event")))))

(defn- unregister-tag-event []
  (when config/hardwallet-enabled?
    (.. js-dependencies/nfc-manager
        -default
        (unregisterTagEvent)
        (then #(log/debug "[hardwallet] unregister tag event")))))

(fx/defn on-tag-discovered [{:keys [db] :as cofx} data]
  (let [data' (js->clj data :keywordize-keys true)
        payload (get-in data' [:ndefMessage 0 :payload])]
    (log/debug "[hardwallet] on tag discovered" data')
    (log/debug "[hardwallet] " (str "tag payload: " (clojure.string/join
                                                     (map js/String.fromCharCode payload))))
    (fx/merge cofx
              {:db (assoc-in db [:hardwallet :setup-step] :begin)}
              (navigation/navigate-to-cofx :hardwallet-setup nil))))

(fx/defn on-initialization-completed [{:keys [db]}]
  {:db (assoc-in db [:hardwallet :setup-step] :secret-keys)})

(fx/defn on-pairing-completed [{:keys [db]}]
  {:db (assoc-in db [:hardwallet :setup-step] :card-ready)})

(re-frame/reg-fx
 :hardwallet/check-nfc-support
 check-nfc-support)

(re-frame/reg-fx
 :hardwallet/check-nfc-enabled
 check-nfc-enabled)

(re-frame/reg-fx
 :hardwallet/open-nfc-settings
 open-nfc-settings)

(re-frame/reg-fx
 :hardwallet/register-tag-event
 register-tag-event)

(re-frame/reg-fx
 :hardwallet/unregister-tag-event
 unregister-tag-event)
