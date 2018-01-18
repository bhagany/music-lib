(ns music-lib.core
  (:require [clojure.pprint :as pp]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.string :as str]))

;;;; Parser

;; clojure.spec can be used to define and parse a context-free grammar,
;; which seemed like a good fit for the mini-language in this exercise.
;; If you're familiar with CFG's this should look familiar. If you're not,
;; the basic idea for this parser is that starts with valid expressions
;; (::expr), which can be one of four things: :add, :list, :listen-to,
;; or :quit. If the expression is an :add, then it's the string "add"
;; followed by one of three things: an :artist, :album, or :track. It
;; follows this decision tree until all the tokens match and it produces
;; an abstract syntax tree for the expression, or else the input is invalid.
;; I don't want to go into any more detail on this grammar here, but I'd
;; be more than happy to walk through it in detail in-person.

(s/def ::expr (s/or :add ::add
                    :list ::list
                    :listen-to ::listen-to
                    :quit ::quit
                    :help ::help))
(s/def ::add (s/cat :keyword #{"add"}
                    :object (s/alt :artist (s/cat :keyword #{"artist"}
                                                  :artist ::name)
                                   :album (s/cat :keyword #{"album"}
                                                 :album ::name
                                                 :by #{"by"}
                                                 :artist ::name)
                                   :track (s/cat :keyword #{"track"}
                                                 :track ::name
                                                 :on #{"on"}
                                                 :album ::name
                                                 :by #{"by"}
                                                 :artist ::name))))
(s/def ::list (s/cat :keyword #{"list"}
                     :object (s/alt :albums (s/cat :albums #{"albums"}
                                                   :by #{"by"}
                                                   :artist ::name)
                                    :tracks (s/cat :albums #{"tracks"}
                                                   :on #{"on"}
                                                   :album ::name
                                                   :by #{"by"}
                                                   :artist ::name)
                                    :top-tracks (s/cat :keyword #{"top"}
                                                       :num (s/with-gen
                                                              (s/and string? #(re-matches #"\d+" %))
                                                              #(gen/fmap str (s/gen nat-int?)))
                                                       :tracks #{"tracks"})
                                    :top-artists (s/cat :keyword #{"top"}
                                                        :num (s/with-gen
                                                               (s/and string? #(re-matches #"\d+" %))
                                                               #(gen/fmap str (s/gen nat-int?)))
                                                        :artists #{"artists"}))))
(s/def ::listen-to (s/cat :keyword (s/cat :key1 #{"listen"} :key2 #{"to"})
                          :object (s/alt :track (s/cat :track ::name
                                                       :on #{"on"}
                                                       :album ::name
                                                       :by #{"by"}
                                                       :artist ::name))))
(s/def ::quit (s/cat :keyword #{"quit"}))
(s/def ::help (s/cat :keyword #{"help"}))

(def non-empty-string? (s/and string? #(not (empty? %))))
(s/def ::name (s/alt :quoted-single-token
                     (s/with-gen
                       (s/and non-empty-string? #(str/starts-with? % "\"") #(str/ends-with? % "\""))
                       (fn [] (gen/fmap #(str "\"" % "\"") (s/gen non-empty-string?))))
                     :quoted-multi-token
                     (s/cat :start (s/with-gen
                                     (s/and non-empty-string? #(str/starts-with? % "\""))
                                     (fn [] (gen/fmap #(str "\"" %) (s/gen non-empty-string?))))
                            :middle (s/* non-empty-string?)
                            :end (s/with-gen
                                   (s/and non-empty-string? #(str/ends-with? % "\""))
                                   (fn [] (gen/fmap #(str % "\"") (s/gen non-empty-string?)))))
                     :unquoted (s/+ non-empty-string?)))

;;;; Acting on the AST

;; Once the input is parsed and we have our AST, we need to use it to
;; decide what to do, which in this case means either modifying the music
;; store, and/or producing some output. The following section uses Clojure's
;; multimethods to decide what to do with particular AST nodes. Multimethods
;; are a way that Clojure implements polymorphism. I'll use name parsing as
;; an example to explain how they work.

;;; Parsing names

;; In our mini language, names of artists, albums, or tracks can be quoted
;; or unquoted, which means we need three different ways of parsing them.
;; For an unquoted name, the AST node looks like this:
;; `[:unquoted ["these" "are" "name" "parts"]]`. A quoted name is a bit more
;; complicated, because it could consist of either one or multiple tokens.
;; : `[:quoted {:start "\"Beginning", :middle ["mid"], :end "end\""}]`.
;; The `parsed-name` multimethod knows how to take each of these nodes and
;; return a normalized name, without the beginning and ending quotes

;; Here we declare the `parsed-name` multimethod and give it the dispatch
;; function `first`. This means it will look at the first part of the AST
;; node (aka, either `:quoted` or `:unquoted`), and dispatch to one of the
;; methods we defined based on this value.
(defmulti parsed-name first)

;; For `:unquoted` AST nodes, `parsed-name` dispatches to this method. It
;; joins the parts of the name into a single string.
(defmethod parsed-name :unquoted
  ;; This is the argument list for this method. It looks odd because
  ;; it's destructuring the AST node that was passed to it. It's a bit like
  ;; destructuring assignment in ES6, and it's probably enough to understand
  ;; that it's reaching in and pulling out the values that are named.
  [[_ parts]]
  (str/join " " parts))

;; Similarly, `:quoted-single-token` AST nodes get dispatched here. Beginning
;; and ending quotation marks are removed from the string
(defmethod parsed-name :quoted-single-token
  [[_ name]]
  (str/replace name #"^\"|\"$" ""))

;; Finally, `:quoted-multi-token` removes beginning and end quotes from the
;; first and last name tokens, and then joins them into a single string.
(defmethod parsed-name :quoted-multi-token
  [[_ {:keys [start middle end]}]]
  (let [start* (str/replace start #"^\"" "")
        end* (str/replace end #"\"$" "")]
    (str/join " " (into [start*] (into middle [end*])))))

;; The rest of the parsing multimethods use the same dispatch function
;; so I defined it here.
(defn common-dispatch
  [_ parsed]
  (first parsed))

;;; Parsing :add expressions

;; Artists, albums, and tracks are added to a single nested map that looks like
;; `{artist {album {track listen-count}}`, so each of these methods adds a key
;; at the appropriate level of the store map.

(defmulti add common-dispatch)

(defmethod add :artist
  [store [_ {:keys [artist]}]]
  (let [artist* (parsed-name artist)]
    {:new-store (merge-with merge store {artist* {}})
     :output (format "Adding artist \"%s\"" artist*)}))

(defmethod add :album
  [store [_ {:keys [artist album]}]]
  (let [artist* (parsed-name artist)
        album* (parsed-name album)]
    (if (contains? store artist*)
      {:new-store (update-in store [artist*] #(merge-with merge % {album* {}}))
       :output (format "Adding album \"%s\" by \"%s\"" album* artist*)}
      {:new-store store
       :output (format "Unknown artist \"%s\"" artist*)
       :error true})))

(defmethod add :track
  [store [_ {:keys [artist album track]}]]
  (let [artist* (parsed-name artist)
        album* (parsed-name album)
        track* (parsed-name track)]
    (if-let [artist-entry (get store artist*)]
      (if (contains? artist-entry album*)
        {:new-store (assoc-in store [artist* album* track*] 0)
         :output (format "Adding track \"%s\" on album \"%s\" by \"%s\"" track* album* artist*)}
        {:new-store store
         :output (format "Unknown album \"%s\" by \"%s\"" album* artist*)
         :error true})
      {:new-store store
       :output (format "Unknown artist \"%s\"" artist*)
       :error true})))

;;; Parsing :list expressions

;; All of the `list_` methods return the store unmodified, and produce more complex
;; output than `add` or `listen-to`.

(defmulti list_ common-dispatch)

(defmethod list_ :albums
  [store [_ {:keys [artist]}]]
  (let [artist* (parsed-name artist)]
    (if-let [artist-entry (get store artist*)]
      (let [albums (keys artist-entry)]
        (if (empty? albums)
          {:new-store store
           :output "No albums found"}
          {:new-store store
           :output (with-out-str
                     (pp/print-table (map (fn [album]
                                            {(format "albums by \"%s\"" artist*) album})
                                          albums)))}))
      {:new-store store
       :output (format "Unknown artist \"%s\"" artist*)
       :error true})))

(defmethod list_ :tracks
  [store [_ {:keys [artist album]}]]
  (let [artist* (parsed-name artist)
        album* (parsed-name album)]
    (if-let [artist-entry (get store artist*)]
      (if-let [album-entry (get artist-entry album*)]
        (let [tracks (-> store
                         (get artist*)
                         (get album*)
                         keys)]
          (if (empty? tracks)
            {:new-store store
             :output "No tracks found"}
            {:new-store store
             :output (with-out-str
                       (pp/print-table (map (fn [track]
                                              {(format "tracks on \"%s\" by \"%s\"" album* artist*) track})
                                            tracks)))}))
        {:new-store store
         :output (format "Unknown album \"%s\" by \"%s\"" album* artist*)
         :error true})
      {:new-store store
       :output (format "Unknown artist \"%s\"" artist*)
       :error true})))

;; This is a helper function - it returns a list of tracks that
;; is suitable for printing in a table
(defn flatten-store-tracks
  [store]
  (mapcat (fn [[artist albums]]
            (mapcat (fn [[album tracks]]
                      (map (fn [[track listens]]
                             {"artist" artist
                              "album" album
                              "track" track
                              "listens" listens})
                           tracks))
                    albums))
          store))

(defmethod list_ :top-tracks
  [store [_ {:keys [num]}]]
  (let [track-listens (flatten-store-tracks store)
        tracks (take (BigInteger. num)
                     (sort-by #(get % "listens") > track-listens))]
    {:new-store store
     :output (if (empty? tracks)
               "No tracks found"
               (with-out-str (pp/print-table tracks)))}))

;; This is a helper function - it returns a list of artists that
;; is suitable for printing in a table
(defn count-artist-listens
  [store]
  (map (fn [[artist albums]]
         {"artist" artist
          "listens" (apply + (mapcat (comp vals second) albums))})
       store))

(defmethod list_ :top-artists
  [store [_ {:keys [num]}]]
  (let [artist-listens (count-artist-listens store)
        artists (take (BigInteger. num)
                      (sort-by #(get % "listens") > artist-listens))]
    {:new-store store
     :output (if (empty? artists)
               "No artists found"
               (with-out-str (pp/print-table artists)))}))

;;; Parsing :listen-to expressions

(defmulti listen-to common-dispatch)

;; `:track`s are the only kind of thing that can be listened to in our
;; language, but I still made it a multimethod to allow for future
;; enhancements. For instance, we could add another method to listen
;; to an entire `:album`.
(defmethod listen-to :track
  [store [_ {:keys [artist album track]}]]
  (let [artist* (parsed-name artist)
        album* (parsed-name album)
        track* (parsed-name track)]
    (if-let [artist-entry (get store artist*)]
      (if-let [album-entry (get artist-entry album*)]
        (if (contains? album-entry track*)
          {:new-store (update-in store [artist* album* track*] inc)
           :output (format "Incrementing listen count for track \"%s\" on album \"%s\" by \"%s\""
                           track* album* artist*)}
          {:new-store store
           :output (format "Unknown track \"%s\" on album \"%s\" by \"%s\"" track* album* artist*)
           :error true})
        {:new-store store
         :output (format "Unknown album \"%s\" by \"%s\"" album* artist*)
         :error true})
      {:new-store store
       :output (format "Unknown artist \"%s\"" artist*)
       :error true})))

;;; Parsing top-level expressions

;; The `expr` multimethod provides a single parsing entry point,
;; calling into the more specific parsing functions above

(defmulti expr common-dispatch)

(defmethod expr :add
  [store [_ & [data]]]
  (add store (:object data)))

(defmethod expr :list
  [store [_ & [data]]]
  (list_ store (:object data)))

(defmethod expr :listen-to
  [store [_ & [data]]]
  (listen-to store (:object data)))

(defmethod expr :quit
  [_ _]
  {:new-store ::quit
   :output "Quitting..."})

(defmethod expr :help
  [store _]
  (let [output ["Add an artist: `add artist <artist>`"
                "Add an album: `add album <album> by <artist>`"
                "Add a track: `add track <track> on <album> by <artist>`"
                "Show albums by artist: `list albums by <artist>`"
                "Show tracks by album: `list tracks on <album> by <artist>`"
                "Listen to a track (increase its play count): `listen to <track> on <album> by <artist>`"
                "List the N most popular tracks by play count: `list top <N> tracks`"
                "List the N most popular artists by play count: `list top <N> artists`"
                "Quit: `quit`"
                "Display this help text: `help`"]]
    {:new-store store
     :output (str/join "\n" output)}))

;;; Bringing it all together

;; This little function uses everything defined above to parse the
;; input its given and then act on the AST, which results in a possibly
;; modified store, plus optional output to show the the user. If the
;; input is invalid, we don't change the store and output an error message.
(defn handle-input
  [store input]
  ;; `s/conform` is clojure.spec's function for normalizing data based on
  ;; a spec. Here, we're using it in a specialized way, to transform our
  ;; input into an AST.
  (let [parsed (s/conform ::expr (str/split input #"\s+"))]
    (if (= parsed ::s/invalid)
      {:new-store store :output "Unrecognized input" :error true}
      (expr store parsed))))

;;; The main control loop

;; Here, we control the prompt and output any strings that come back from
;; `handle-input`. The loop exits when the quit command is issued
(defn -main
  []
  (println "Welcome to your music library!")
  (println "Type commands at the prompt, or \"help\" for assistance")
  (loop [store {}]
    (when (not= ::quit store)
      (print "\n> ")
      (let [{:keys [new-store output]} (handle-input store (str/trim (read-line)))]
        (when output
          (println output))
        (recur new-store)))))
