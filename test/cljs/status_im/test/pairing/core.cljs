(ns status-im.test.pairing.core
  (:require [cljs.test :refer-macros [deftest is testing]]
            [status-im.transport.message.pairing :as transport.pairing]
            [status-im.utils.identicon :as identicon]
            [status-im.utils.config :as config]
            [status-im.pairing.core :as pairing]))

(deftest merge-contact-test
  (testing "vanilla contacts"
    (let [contact-1 {:pending? false
                     :this-should-be-kept true
                     :last-updated 1
                     :name "name-v1"
                     :photo-path "photo-v1"}
          contact-2 {:pending? false
                     :last-updated 2
                     :name "name-v2"
                     :photo-path "photo-v2"}
          expected  {:pending? false
                     :this-should-be-kept true
                     :last-updated 2
                     :name "name-v2"
                     :photo-path "photo-v2"}]
      (is (= expected (pairing/merge-contact contact-1 contact-2)))))
  (testing "without last-updated"
    (let [contact-1 {:pending? false
                     :name "name-v1"
                     :photo-path "photo-v1"}
          contact-2 {:pending? false
                     :last-updated 2
                     :name "name-v2"
                     :photo-path "photo-v2"}
          expected  {:pending? false
                     :last-updated 2
                     :name "name-v2"
                     :photo-path "photo-v2"}]
      (is (= expected (pairing/merge-contact contact-1 contact-2)))))
  (testing "nil contact"
    (let [contact-1 nil
          contact-2 {:pending? false
                     :last-updated 2
                     :name "name-v2"
                     :photo-path "photo-v2"}
          expected  {:pending? false
                     :last-updated 2
                     :name "name-v2"
                     :photo-path "photo-v2"}]
      (is (= expected (pairing/merge-contact contact-1 contact-2)))))
  (testing "not pending in one device"
    (let [contact-1 {:pending? false
                     :last-updated 1
                     :name "name-v1"
                     :photo-path "photo-v1"}
          contact-2 {:pending? true
                     :last-updated 2
                     :name "name-v2"
                     :photo-path "photo-v2"}
          expected  {:pending? false
                     :last-updated 2
                     :name "name-v2"
                     :photo-path "photo-v2"}]
      (is (= expected (pairing/merge-contact contact-1 contact-2)))))
  (testing "pending in one device and nil"
    (let [contact-1 nil
          contact-2 {:pending? true
                     :last-updated 2
                     :name "name-v2"
                     :photo-path "photo-v2"}
          expected  {:pending? true
                     :last-updated 2
                     :name "name-v2"
                     :photo-path "photo-v2"}]
      (is (= expected (pairing/merge-contact contact-1 contact-2))))))

(deftest handle-sync-installation-test
  (with-redefs [config/pairing-enabled? (constantly true)
                identicon/identicon (constantly "generated")]
    (let [old-contact-1   {:name "old-contact-one"
                           :public-key "contact-1"
                           :last-updated 0
                           :photo-path "old-contact-1"
                           :pending? true}
          new-contact-1   {:name "new-contact-one"
                           :public-key "contact-1"
                           :last-updated 1
                           :photo-path "new-contact-1"
                           :pending? false}
          old-contact-2   {:name "old-contact-2"
                           :public-key "contact-2"
                           :last-updated 0
                           :photo-path "old-contact-2"
                           :pending? false}
          new-contact-2   {:name "new-contact-2"
                           :public-key "contact-2"
                           :last-updated 1
                           :photo-path "new-contact-2"
                           :pending? false}
          contact-3        {:name "contact-3"
                            :public-key "contact-3"
                            :photo-path "contact-3"
                            :pending? false}
          contact-4        {:name "contact-4"
                            :public-key "contact-4"
                            :pending? true}
          local-contact-5  {:name "contact-5"
                            :photo-path "local"
                            :public-key "contact-5"
                            :pending? true
                            :last-updated 1}
          remote-contact-5 {:name "contact-5"
                            :public-key "contact-5"
                            :photo-path "remote"
                            :pending? true
                            :last-updated 1}
          cofx {:db {:account/account {:public-key "us"}
                     :contacts/contacts {"contact-1" old-contact-1
                                         "contact-2" new-contact-2
                                         "contact-3" contact-3
                                         "contact-5" local-contact-5}}}
          sync-message {:contacts {"contact-1" new-contact-1
                                   "contact-2" old-contact-2
                                   "contact-4" contact-4
                                   "contact-5" remote-contact-5}}
          expected {"contact-1" new-contact-1
                    "contact-2" new-contact-2
                    "contact-3" contact-3
                    "contact-4" (assoc contact-4
                                       :photo-path "generated")
                    "contact-5" local-contact-5}]
      (testing "not coming from us"
        (is (not (pairing/handle-sync-installation cofx sync-message "not-us"))))
      (testing "coming from us"
        (is (= expected (get-in
                         (pairing/handle-sync-installation cofx sync-message "us")
                         [:db :contacts/contacts])))))))

(deftest handle-pair-installation-test
  (with-redefs [config/pairing-enabled? (constantly true)]
    (let [cofx {:db {:account/account {:public-key "us"}
                     :pairing/installations {"1" {:has-bundle? true
                                                  :installation-id "1"}
                                             "2" {:has-bundle? false
                                                  :installation-id "2"}}}}
          pair-message {:device-type "ios"
                        :installation-id "1"}]
      (testing "not coming from us"
        (is (not (pairing/handle-pair-installation cofx pair-message 1 "not-us"))))
      (testing "coming from us"
        (is (= {"1" {:has-bundle? true
                     :installation-id "1"
                     :last-paired 1
                     :device-type "ios"}
                "2"  {:has-bundle? false
                      :installation-id "2"}}
               (get-in
                (pairing/handle-pair-installation cofx pair-message 1 "us")
                [:db :pairing/installations])))))))

(deftest sync-installation-messages-test
  (testing "it creates a sync installation message"
    (let [cofx {:db {:account/account {:public-key "us"}
                     :contacts/contacts {"contact-1" {:name "contact-1"
                                                      :public-key "contact-1"}
                                         "contact-2" {:name "contact-2"
                                                      :public-key "contact-2"}
                                         "contact-3" {:name "contact-3"
                                                      :public-key "contact-3"}
                                         "contact-4" {:name "contact-4"
                                                      :public-key "contact-4"}
                                         "contact-5" {:name "contact-5"
                                                      :public-key "contact-5"}}}}
          expected [(transport.pairing/SyncInstallation. {"contact-1" {:name "contact-1"
                                                                       :public-key "contact-1"}
                                                          "contact-2" {:name "contact-2"
                                                                       :public-key "contact-2"}
                                                          "contact-3" {:name "contact-3"
                                                                       :public-key "contact-3"}
                                                          "contact-4" {:name "contact-4"
                                                                       :public-key "contact-4"}})
                    (transport.pairing/SyncInstallation. {"contact-5" {:name "contact-5"
                                                                       :public-key "contact-5"}})]]
      (is (= expected (pairing/sync-installation-messages cofx))))))

(deftest handle-bundles-added-test
  (with-redefs [config/pairing-enabled? (constantly true)]
    (let [installation-1 {:has-bundle? false
                          :installation-id "installation-1"}
          cofx {:db {:account/account {:public-key "us"}
                     :pairing/installations {"installation-1" installation-1}}}]
      (testing "new installations"
        (let [new-installation {:identity "us" :installationID "installation-2"}
              expected {"installation-1" installation-1
                        "installation-2" {:has-bundle? true
                                          :installation-id "installation-2"}}]
          (is (= expected (get-in (pairing/handle-bundles-added cofx new-installation) [:db :pairing/installations])))))
      (testing "already existing installation"
        (let [old-installation {:identity "us" :installationID "installation-1"}]
          (is (not (pairing/handle-bundles-added cofx old-installation)))))
      (testing "not from us"
        (let [new-installation {:identity "not-us" :installationID "does-not-matter"}]
          (is (not (pairing/handle-bundles-added cofx new-installation))))))))

(deftest has-paired-installations-test
  (testing "no paired devices"
    (is (not (pairing/has-paired-installations? {:db {:pairing/installations {"1" {}
                                                                              "2" {}}}}))))
  (testing "has paired devices"
    (is (pairing/has-paired-installations? {:db {:pairing/installations {"1" {}
                                                                         "2" {:enabled? true}}}}))))
