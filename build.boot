(set-env!
 :source-paths #{"src" "test"}
 :dependencies '[[adzerk/boot-test "1.2.0" :scope "test"]
                 [org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/test.check "0.9.0" :scope "test"]])

(require '[adzerk.boot-test :as t])
(require '[clojure.pprint :as pp])
(require '[clojure.spec :as s])
(require '[clojure.spec.test :as stest])
(require '[clojure.string :as str])
(require '[music-lib.core :as m])
(require 'music-lib.spec-test)

(deftask run []
  (with-pass-thru _
    (m/-main)))

(deftask test
  []
  (t/test :namespaces #{'music-lib.core-test}))

(deftask gen-test
  [n num-tests NUM int "number of tests to generate"]
  (let [num-tests (or num-tests 20)]
    (println (format "Generating %s tests for `handle-input`" num-tests))
    (with-pass-thru _
      (prn (stest/summarize-results
            (stest/check `music-lib.core/handle-input
                         {:clojure.spec.test.check/opts {:num-tests num-tests}}))))))

(deftask gen-store
  []
  (with-pass-thru _
    (prn (ffirst (s/exercise :music-lib.spec-test/store)))))

(deftask gen-input
  [n num-inputs NUM int "number of inputs to generate"]
  (let [num-inputs (or num-inputs 10)]
    (with-pass-thru _
      (run! (comp println #(str/join " " %) first) (s/exercise :music-lib.core/expr num-inputs)))))
