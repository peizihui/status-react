(ns status-im.accounts.login.core
  (:require [re-frame.core :as re-frame]
            [status-im.accounts.db :as accounts.db]
            [status-im.data-store.core :as data-store]
            [status-im.native-module.core :as status]
            [status-im.node.core :as node]
            [status-im.ui.screens.navigation :as navigation]
            [status-im.utils.fx :as fx]
            [status-im.utils.keychain.core :as keychain]
            [status-im.utils.types :as types]
            [taoensso.timbre :as log]
            [status-im.utils.security :as security]
            [status-im.utils.platform :as platform]
            [status-im.protocol.core :as protocol]
            [status-im.models.wallet :as models.wallet]
            [status-im.models.transactions :as transactions]))

;; login flow:
;;
;; - event `:ui/login` is dispatched
;; - node is initialized with user config or default config
;; - `node.started` signal is received, applying `:login` fx
;; - `:callback/login` event is dispatched, account is changed in datastore, web-data is cleared
;; - `:init.callback/account-change-success` event is dispatched

(defn login! [address password save-password?]
  (status/login address password #(re-frame/dispatch [:accounts.login.callback/login-success %])))

(defn clear-web-data! []
  (status/clear-web-data))

(defn change-account! [address password]
  ;; No matter what is the keychain we use, as checks are done on decrypting base
  (.. (keychain/safe-get-encryption-key)
      (then #(data-store/change-account address password %))
      (then (fn [] (re-frame/dispatch [:init.callback/account-change-success address])))
      (catch (fn [error]
               (log/warn "Could not change account" error)
               ;; If all else fails we fallback to showing initial error
               (re-frame/dispatch [:init.callback/account-change-error error])))))

;;;; Handlers
(fx/defn login [cofx]
  (let [{:keys [address password save-password?]} (accounts.db/credentials cofx)]
    {:accounts.login/login [address password save-password?]}))

#_((fx/defn user-login [{:keys [db] :as cofx}]
     (fx/merge cofx
               {:db (assoc-in db [:accounts/login :processing] true)}
               (node/initialize (get-in db [:accounts/login :address]))))

   (fx/defn user-login-callback
     [{db :db :as cofx} login-result]
     (let [data    (types/json->clj login-result)
           error   (:error data)
           success (empty? error)]
       (if success
         (let [{:keys [address password save-password?]} (accounts.db/credentials cofx)]
           (merge {:accounts.login/clear-web-data nil
                   :data-store/change-account     [address password]}
                  (when save-password?
                    {:keychain/save-user-password [address password]})))
         {:db (update db :accounts/login assoc
                      :error error
                      :processing false)}))))

(defn initialize-wallet [cofx]
  (fx/merge cofx
            (models.wallet/initialize-tokens)
            (models.wallet/update-wallet)
            (transactions/start-sync)))

(fx/defn user-login [{:keys [db] :as cofx}]
  (let [{:keys [address password save-password?]} (accounts.db/credentials cofx)]
    (fx/merge
     cofx
     (merge
      {:db                            (assoc-in db [:accounts/login :processing] true)
       :accounts.login/clear-web-data nil
       :data-store/change-account     [address password]}
      (when save-password?
        {:keychain/save-user-password [address password]})))))

(fx/defn user-login-callback
  [{:keys [db web3] :as cofx} login-result]
  (let [data    (types/json->clj login-result)
        error   (:error data)
        success (empty? error)
        address (get-in db [:account/account :address])]
    (if success
      (fx/merge
       cofx
       {:web3/set-default-account [web3 address]
        :web3/fetch-node-version  [web3
                                   #(re-frame/dispatch
                                     [:web3/fetch-node-version-callback %])]}
       (protocol/initialize-protocol address)
       #(when-not platform/desktop?
          (initialize-wallet %)))
      {:db (update db :accounts/login assoc
                   :error error
                   :processing false)})))

(fx/defn open-login [{:keys [db]} address photo-path name]
  {:db (-> db
           (update :accounts/login assoc
                   :address address
                   :photo-path photo-path
                   :name name)
           (update :accounts/login dissoc
                   :error
                   :password))
   :keychain/can-save-user-password? nil
   :keychain/get-user-password [address
                                #(re-frame/dispatch [:accounts.login.callback/get-user-password-success %])]})

(fx/defn open-login-callback
  [{:keys [db] :as cofx} password]
  (if password
    (fx/merge cofx
              {:db (assoc-in db [:accounts/login :password] password)}
              (navigation/navigate-to-cofx :progress nil)
              (user-login))
    (navigation/navigate-to-clean cofx :login nil)))

(re-frame/reg-fx
 :accounts.login/login
 (fn [[address password save-password?]]
   (login! address (security/safe-unmask-data password) save-password?)))

(re-frame/reg-fx
 :accounts.login/clear-web-data
 clear-web-data!)

(re-frame/reg-fx
 :data-store/change-account
 (fn [[address password]]
   (change-account! address (security/safe-unmask-data password))))
