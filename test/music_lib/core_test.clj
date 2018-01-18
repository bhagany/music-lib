(ns music-lib.core-test
  (:require [clojure.test :refer [deftest is]]
            [music-lib.core :as m]))

(deftest add-valid
  (is (= (:new-store (m/handle-input {} "add artist bob"))
         {"bob" {}}))
  (is (= (:new-store (m/handle-input {"bob" {}} "add album okie dokie by bob"))
         {"bob" {"okie dokie" {}}}))
  (is (= (:new-store (m/handle-input {"bob" {"okie dokie" {}}}
                                     "add track infusion on \"okie dokie\" by bob"))
         {"bob" {"okie dokie" {"infusion" 0}}})))

(deftest add-invalid
  (is (:error (m/handle-input {} "add artist")))
  (is (:error (m/handle-input {} "add album okie dokie by bob")))
  (is (:error (m/handle-input {} "add track infusion on \"okie dokie\" by bob"))))

(deftest list-valid
  (let [store {"bob" {"okie dokie" {}}}]
    (let [output (m/handle-input store "list albums by bob")]
      (is (not (:error output)))
      (is (= store (:new-store output))))
    (let [output (m/handle-input store "list tracks on okie dokie by bob")]
      (is (not (:error output)))
      (is (= store (:new-store output)))))
  (let [store {}]
    (let [output (m/handle-input store "list top 10 tracks")]
      (is (not (:error output)))
      (is (= store (:new-store output))))
    (let [output (m/handle-input store "list top 10 artists")]
      (is (not (:error output)))
      (is (= store (:new-store output)))))
  (let [store {"bob" {"okie dokie" {"infusion" 18
                                    "prosthetic 22" 78}}}]
    (let [output (m/handle-input store "list top 10 tracks")]
      (is (not (:error output)))
      (is (= store (:new-store output))))
    (let [output (m/handle-input store "list top 10 artists")]
      (is (not (:error output)))
      (is (= store (:new-store output))))))

(deftest list-invalid
  (is (:error (m/handle-input {"bob" {"okie dokie" {}}} "list albums")))
  (is (:error (m/handle-input {"bob" {"okie dokie" {}}} "list albums by not bob")))
  (is (:error (m/handle-input {"bob" {"okie dokie" {}}} "list tracks by bob")))
  (is (:error (m/handle-input {"bob" {"okie dokie" {}}} "list tracks on not okie dokie by bob")))
  (is (:error (m/handle-input {} "list top 10")))
  (is (:error (m/handle-input {} "list top artists")))
  (is (:error (m/handle-input {} "list top q tracks"))))

(deftest listen-to-valid
  (is (= (:new-store (m/handle-input
                      {"bob" {"okie dokie" {"infusion" 18
                                            "prosthetic 22" 78}}}
                      "listen to infusion on okie dokie by bob"))
         {"bob" {"okie dokie" {"infusion" 19
                               "prosthetic 22" 78}}})))

(deftest listen-to-invalid
  (let [store {"bob" {"okie dokie" {"infusion" 18
                                    "prosthetic 22" 78}}}]
    (is (:error (m/handle-input store "listen to prosthetic")))
    (is (:error (m/handle-input store "listen to fake song")))
    (is (:error (m/handle-input store "listen to fake song on okie dokie")))
    (is (:error (m/handle-input store "listen to prosthetic on not okie dokie")))
    (is (:error (m/handle-input store "listen to prosthetic on okie dokie by not bob")))))

(deftest quit
  (is (= (:new-store (m/handle-input {} "quit")) ::m/quit))
  (is (:error (m/handle-input {} "quit add nonsense help"))))

(deftest help
  (is (= (:new-store (m/handle-input {"bob" {}} "help")) {"bob" {}}))
  (is (:error (m/handle-input {} "help add quit nonsense"))))

(deftest sample-in
  (let [inputs ["add artist bob"
                "add album foo by bob"
                "add album \"bar by bar\" by bob"
                "add track \"dancing man\" on foo by bob"
                "add track \"rock on\" on foo by bob"
                "add track \"sunday blues\" on foo by bob"
                "add track \"crushed heart\" on foo by bob"
                "add track \"dancing man\" on foo by bob"
                "list albums by bob"
                "list tracks on foo by bob"
                "list tracks on \"bar by bar\" by bob"
                "add artist \"Smiling Lemurs\""
                "add album \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "add track \"White Noise\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "add track \"Shades Alert\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "add track \"Five bucks per driveway\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "list albums by \"Smiling Lemurs\""
                "list tracks on \"Not a real album\" by \"Smiling Lemurs\""
                "list tracks on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "add track \"Boots, Gaiters, and a Bag of Raisins\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "add track Moonbeam on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "add track \"Clydesdales Never Get Me Down\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "list tracks on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "list top 10 tracks"
                "listen to \"Clydesdales Never Get Me Down\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"Boots, Gaiters, and a Bag of Raisins\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"Boots, Gaiters, and a Bag of Raisins\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"Clydesdales Never Get Me Down\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"crushed heart\" on foo by bob"
                "listen to \"dancing man\" on foo by bob"
                "listen to \"crushed heart\" on foo by bob"
                "listen to \"Boots, Gaiters, and a Bag of Raisins\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"Boots, Gaiters, and a Bag of Raisins\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"Clydesdales Never Get Me Down\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"Boots, Gaiters, and a Bag of Raisins\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"Boots, Gaiters, and a Bag of Raisins\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"Clydesdales Never Get Me Down\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"Clydesdales Never Get Me Down\" on \"Sunbeams and Snowdrifts\" by \"Smiling Lemurs\""
                "listen to \"Clydesdales Never Get Me Down\" on \"Sunbeams and Snowdrifts\" by bob"
                "list top q tracks"
                "list top 4 tracks"
                "list top 2 artists"
                "list top 10 artists"]
        final-store (loop [store {}
                           prev-store nil
                           inputs inputs]
                      (let [{:keys [new-store output]} (m/handle-input store (first inputs))
                            rest-inputs (rest inputs)]
                        (if-not (seq rest-inputs)
                          prev-store
                          (recur new-store store rest-inputs))))]
    (is (= final-store {"bob"
                        {"foo"
                         {"dancing man" 1
                          "rock on" 0
                          "sunday blues" 0
                          "crushed heart" 2}
                         "bar by bar" {}}
                        "Smiling Lemurs"
                        {"Sunbeams and Snowdrifts"
                         {"White Noise" 0
                          "Shades Alert" 0
                          "Five bucks per driveway" 0
                          "Boots, Gaiters, and a Bag of Raisins" 6
                          "Moonbeam" 0
                          "Clydesdales Never Get Me Down" 5}}}))))
