(ns status-im.notifications.background
  (:require [goog.object :as object]
            [re-frame.core :as re-frame]
            [status-im.react-native.js-dependencies :as rn]
            [taoensso.timbre :as log]
            [status-im.utils.platform :as platform]))

(when-not platform/desktop?
  (def firebase (object/get rn/react-native-firebase "default")))

;firebase.messaging.RemoteMessage ; https://github.com/invertase/react-native-firebase-docs/blob/master/docs/messaging/reference/RemoteMessage.md
(defn handle-message [message]
  ; handle your message
  (log/debug "handling background message" message))
;         // @flow
; import firebase from 'react-native-firebase';
; // Optional flow type
; import type { RemoteMessage } from 'react-native-firebase';

; export default async (message: RemoteMessage) => {
;     // handle your message

;     return Promise.resolve();
; }