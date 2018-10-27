(ns status-im.desktop.core
  (:require [reagent.core :as reagent]
            [taoensso.timbre :as log]
            [re-frame.core :as re-frame]
            status-im.utils.db
            status-im.ui.screens.db
            status-im.ui.screens.events
            status-im.ui.screens.subs
            status-im.data-store.core

            [status-im.ui.screens.desktop.views :as views]
            [status-im.core :as core]
            [status-im.desktop.deep-links :as deep-links]))

(defn app-root []
  (reagent/create-class
   {:component-did-mount (fn [this]
                           (let [initial-props (reagent/props this)]
                             (log/debug "### initial-props: " initial-props)
                             (re-frame/dispatch [:set-initial-props initial-props])
                             (deep-links/add-event-listener)))
    :reagent-render      views/main}))

(defn init []
  (core/init app-root))
