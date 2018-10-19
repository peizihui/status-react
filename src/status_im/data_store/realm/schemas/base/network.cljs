(ns status-im.data-store.realm.schemas.base.network)

(def v1 {:name       :network
         :primaryKey :id
         :properties {:id      :string
                      :name    {:type     :string
                                :optional true}
                      :config  {:type     :string
                                :optional true}
                      :rpc-url {:type     :string
                                :optional true}}})
