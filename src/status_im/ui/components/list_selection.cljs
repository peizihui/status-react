(ns status-im.ui.components.list-selection
  (:require [re-frame.core :as re-frame]
            [status-im.i18n :as i18n]
            [status-im.ui.components.action-sheet :as action-sheet]
            [status-im.ui.components.dialog :as dialog]
            [status-im.ui.components.react :as react]
            [status-im.utils.platform :as platform]
            [status-im.utils.http :as http]
            [status-im.utils.utils :as utils]))

(defn- open-share [content]
  (when (or (:message content)
            (:url content))
    (.share react/sharing (clj->js content))))

(defn- message-options [message-id text]
  [{:label  (i18n/label :t/message-reply)
    :action #(re-frame/dispatch [:chat.ui/reply-to-message message-id])}
   {:label  (i18n/label :t/sharing-copy-to-clipboard)
    :action #(react/copy-to-clipboard text)}
   {:label  (i18n/label :t/sharing-share)
    :action #(open-share {:message text})}])

(defn show [options]
  (cond platform/ios? (action-sheet/show options)
        platform/android? (dialog/show options)
        :else (utils/show-options-dialog options)))

(defn chat-message [message-id text dialog-title]
  (show {:title       dialog-title
         :options     (message-options message-id text)
         :cancel-text (i18n/label :t/message-options-cancel)}))

(defn browse [link]
  (show {:title       (i18n/label :t/browsing-title)
         :options     [{:label  (i18n/label :t/browsing-open-in-status)
                        :action #(re-frame/dispatch [:browser.ui/open-in-status-option-selected link])}
                       {:label  (i18n/label :t/browsing-open-in-web-browser)
                        :action #(.openURL react/linking (http/normalize-url link))}]
         :cancel-text (i18n/label :t/browsing-cancel)}))

(defn browse-dapp [link]
  (show {:title       (i18n/label :t/browsing-title)
         :options     [{:label  (i18n/label :t/browsing-open-in-status)
                        :action #(re-frame/dispatch [:browser.ui/open-in-status-option-selected link])}]
         :cancel-text (i18n/label :t/browsing-cancel)}))
