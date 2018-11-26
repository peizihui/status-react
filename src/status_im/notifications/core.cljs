(ns status-im.notifications.core
  (:require [goog.object :as object]
            [re-frame.core :as re-frame]
            [status-im.react-native.js-dependencies :as rn]
            [taoensso.timbre :as log]
            [status-im.accounts.db :as accounts.db]
            [status-im.chat.models :as chat-model]
            [status-im.utils.platform :as platform]
            [status-im.utils.fx :as fx]))

;; Work in progress namespace responsible for push notifications and interacting
;; with Firebase Cloud Messaging.

(when-not platform/desktop?

  (def firebase (object/get rn/react-native-firebase "default")))

;; NOTE: Only need to explicitly request permissions on iOS.
(defn request-permissions []
  (if platform/desktop?
    (re-frame/dispatch [:notifications.callback/request-notifications-permissions-granted {}])
    (-> (.requestPermission (.messaging firebase))
        (.then
         (fn [_]
           (log/debug "notifications-granted")
           (re-frame/dispatch [:notifications.callback/request-notifications-permissions-granted {}]))
         (fn [_]
           (log/debug "notifications-denied")
           (re-frame/dispatch [:notifications.callback/request-notifications-permissions-denied {}]))))))

(when-not platform/desktop?

  (defn get-fcm-token []
    (-> (.getToken (.messaging firebase))
        (.then (fn [x]
                 (log/debug "get-fcm-token: " x)
                 (re-frame/dispatch [:notifications.callback/get-fcm-token-success x])))))

  (defn on-refresh-fcm-token []
    (.onTokenRefresh (.messaging firebase)
                     (fn [x]
                       (log/debug "on-refresh-fcm-token: " x)
                       (re-frame/dispatch [:notifications.callback/get-fcm-token-success x]))))

  ;; TODO(oskarth): Only called in background on iOS right now.
  ;; NOTE(oskarth): Hardcoded data keys :sum and :msg in status-go right now.
  (defn on-notification []
    (.onNotification (.notifications firebase)
                     (fn [event-js]
                       (let [event (js->clj event-js :keywordize-keys true)
                             data (select-keys event [:sum :msg])
                             aps (:aps event)]
                         (log/debug "on-notification event: " (pr-str event))
                         (log/debug "on-notification aps: " (pr-str aps))
                         (log/debug "on-notification data: " (pr-str data))))))

  (def channel-id "status-im")
  (def channel-name "Status")
  (def sound-name "message.wav")
  (def group-id "im.status.ethereum.MESSAGE")
  (def icon "ic_stat_status_notification")

  (defn create-notification-channel []
    (let [channel (firebase.notifications.Android.Channel. channel-id
                                                           channel-name
                                                           firebase.notifications.Android.Importance.Max)]
      (.setSound channel sound-name)
      (.setShowBadge channel true)
      (.enableVibration channel true)
      (.. firebase
          notifications
          -android
          (createChannel channel)
          (then #(log/debug "Notification channel created:" channel-id)
                #(log/error "Notification channel creation error:" channel-id %)))))

  (fx/defn handle-push-notification
    [{:keys [db] :as cofx} {:keys [from to] :as event}]
    (let [current-public-key (accounts.db/current-public-key cofx)]
      (if (= to current-public-key)
        (fx/merge cofx
                  {:db (update db :push-notifications/stored dissoc to)}
                  (chat-model/navigate-to-chat from nil))
        {:db (assoc-in db [:push-notifications/stored to] from)})))

  (defn parse-notification-payload [s]
    (try
      (js/JSON.parse s)
      (catch :default _
        #js {})))

  (defn handle-notification-event [event]
    (let [msg (object/get (.. event -notification -data) "msg")
          data (parse-notification-payload msg)
          from (object/get data "from")
          to (object/get data "to")]
      (log/debug "on notification" (pr-str msg))
      (when (and from to)
        (re-frame/dispatch [:notifications/notification-event-received {:from from
                                                                        :to   to}]))))

  (defn handle-initial-push-notification
    []
    (.. firebase
        notifications
        getInitialNotification
        (then (fn [event]
                (when event
                  (handle-notification-event event))))))

  (defn on-notification-opened []
    (.. firebase
        notifications
        (onNotificationOpened handle-notification-event)))

  (defn init []
    (on-refresh-fcm-token)
    (on-notification)
    (on-notification-opened)
    (when platform/android?
      (create-notification-channel)))

  (defn display-notification [{:keys [title body from to]}]
    (let [notification (firebase.notifications.Notification.)]
      (.. notification
          (setTitle title)
          (setBody body)
          (setData (js/JSON.stringify #js {:from from
                                           :to   to}))
          (setSound sound-name)
          (-android.setChannelId channel-id)
          (-android.setAutoCancel true)
          (-android.setPriority firebase.notifications.Android.Priority.Max)
          (-android.setGroup group-id)
          (-android.setGroupSummary true)
          (-android.setSmallIcon icon))
      (.. firebase
          notifications
          (displayNotification notification)
          (then #(log/debug "Display Notification" title body))
          (then #(log/debug "Display Notification error" title body))))))

(fx/defn process-stored-event [cofx address]
  (when-not platform/desktop?
    (let [to (get-in cofx [:db :accounts/accounts address :public-key])
          from (get-in cofx [:db :push-notifications/stored to])]
      (when from
        (handle-push-notification cofx
                                  {:from from
                                   :to   to})))))

(re-frame/reg-fx
 :notifications/display-notification
 display-notification)

(re-frame/reg-fx
 :notifications/handle-initial-push-notification
 handle-initial-push-notification)

(re-frame/reg-fx
 :notifications/get-fcm-token
 (fn [_]
   (when platform/mobile?
     (get-fcm-token))))

(re-frame/reg-fx
 :notifications/request-notifications-permissions
 (fn [_]
   (request-permissions)))
