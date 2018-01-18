(ns music-lib.spec-test
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.walk :as walk]
            [music-lib.core :as m]))

;; The strategy here is to generate a random music store, which has a bunch
;; of random nonsense in the same {"artist" {"album" {"track" listen-count}}}
;; shape as a real store. Then, once we have that, we can use `clojure.spec`
;; to generate a random input string that uses the artists, albums, and tracks
;; that are in that store. We can then test a bunch of random stores in this
;; way, and see if it finds any input that causes errors.

;; In order to generate the inputs, we'll actually start with a random AST,
;; which `clojure.spec` can give us easily. Then, to make the input match the
;; store, we'll modify the AST to contain appropriate values from the random
;; store, so that everything matches.

;; Sometimes, we'll generate a store/input pair that doesn't work quite right.
;; For instance, it's possible to generate a store without any tracks in it,
;; along with an input like `listen to <track> on <album> by <artist>`. In this
;; case, we'll need to use these dummy values. Having bad input like this
;; still shouldn't cause the program to crash though, so testing this is still
;; worthwhile
(def dummy-artist [(s/conform ::m/name ["-artist"])])
(def dummy-album [(s/conform ::m/name ["-album"])])
(def dummy-track [(s/conform ::m/name ["-track"])])

(defn ast-names
  [names]
  (map #(s/conform ::m/name (str/split % #"\s+")) names))

;; These generators randomly select information from a store, so that these can
;; be inserted into an AST.
(defn existing-artist-gen
  [store]
  (gen/elements (if-let [ks (keys store)] (ast-names ks) dummy-artist)))

(defn existing-artist-album-gen
  [store]
  (gen/bind
   (gen/elements (if-let [ks (seq (filter #(pos? (count (keys (get store %))))
                                          (keys store)))]
                   (ast-names ks)
                   dummy-artist))
   (fn [artist]
     (gen/tuple
      (gen/return artist)
      (gen/elements (if-let [ks (keys (get store artist))]
                      (ast-names ks)
                      dummy-album))))))

(defn existing-artist-album-track-gen
  [store]
  (gen/bind
   (gen/elements (if-let [ks (seq (filter #(let [albums (get store %)]
                                             (and (pos? (count albums))
                                                  (pos? (count (mapcat identity
                                                                       (vals albums))))))
                                          (keys store)))]
                   (ast-names ks)
                   dummy-artist))
   (fn [artist]
     (gen/bind
      (gen/elements (if-let [ks (seq (filter #(pos? (count (keys (get artist %))))
                                             (keys (get store artist))))]
                      (ast-names ks)
                      dummy-album))
      (fn [album]
        (gen/tuple
         (gen/return artist)
         (gen/return album)
         (gen/elements (if-let [ks (some-> (get store artist)
                                           (get album)
                                           keys)]
                         (ast-names ks)
                         dummy-track))))))))

;; Here we walk the AST and replace the appropriate nodes
(defn replace-ast
  [ast key val]
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (contains? x key))
       (assoc x key val)
       x))
   ast))

;; Modifying (or, I guess, munging) the AST is a lot like parsing it,
;; so I used a multimethod in the same way as before. There's more detail
;; about how multimethods work in src/music_lib/core.clj, if you haven't
;; seen it yet.
(defmulti munge-ast (fn [_ ast] (first ast)))

(defmethod munge-ast :default
  [_ ast]
  ast)

(defmethod munge-ast :add
  [store ast]
  (case (first (:object (second ast)))
    :album (replace-ast ast :artist (gen/generate (existing-artist-gen store)))
    :track (let [[artist album] (gen/generate (existing-artist-album-gen store))]
             (-> ast
                 (replace-ast :artist artist)
                 (replace-ast :album album)))
    ast))

(defmethod munge-ast :list
  [store ast]
  (case (first (:object (second ast)))
    :albums (replace-ast ast :artist (gen/generate (existing-artist-gen store)))
    :tracks (let [[artist album] (gen/generate (existing-artist-album-gen store))]
              (-> ast
                  (replace-ast :artist artist)
                  (replace-ast :album album)))
    ast))

(defmethod munge-ast :listen-to
  [store ast]
  (case (first (:object (second ast)))
    :track (let [[artist album track] (gen/generate (existing-artist-album-track-gen store))]
             (-> ast
                 (replace-ast :artist artist)
                 (replace-ast :album album)
                 (replace-ast :track track)))
    ast))

;; Here's where we generate a random AST, and transform it so that it
;; applies to the store that's passed here.
(defn input-gen
  [store]
  (gen/fmap
   (fn [input]
     (->> input
          (s/conform ::m/expr)
          (munge-ast store)
          (s/unform ::m/expr)
          (str/join " ")))
   (s/gen ::m/expr)))

;; This part defines the shapes of the inputs and outputs of the `handle-input` function,
;; which is where all the input is handled. Defining them like this allows `clojure.spec`
;; to generate hundreds or thousands of tests for us, using inputs that we might not
;; have considered.
(s/def ::store (s/map-of m/non-empty-string?
                         (s/map-of m/non-empty-string?
                                   (s/map-of m/non-empty-string? nat-int?))))
(s/def ::store-input-pair (s/with-gen
                            (s/cat :store ::store :input string?)
                            (fn [] (gen/bind
                                    (s/gen ::store)
                                    #(gen/tuple
                                      (gen/return %)
                                      (gen/frequency [[1 (s/gen string?)]
                                                      [49 (input-gen %)]]))))))

(s/def ::new-store (s/or :store ::store :quit #{::m/quit}))
(s/def ::output string?)
(s/def ::error boolean?)
(s/fdef m/handle-input
        :args ::store-input-pair
        :ret (s/keys :req-un [::new-store ::output] :opt-un [::error]))
