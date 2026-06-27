(ns clojure.xml
  "A parse-only clojure.xml shim over jolt-lang/xml's javax.xml.stream pull parser
  (libxml2). (parse source) returns the standard {:tag :attrs :content} element
  tree, so clojure.zip / clojure.data.zip code reads it exactly like clojure.xml
  on the JVM. Whitespace-only text between elements is dropped, matching the JDK
  SAX parser clojure.xml uses (it reports such runs as ignorable whitespace)."
  (:require [jolt.xml]))

(def ^:private START javax.xml.stream.XMLStreamConstants/START_ELEMENT)
(def ^:private END   javax.xml.stream.XMLStreamConstants/END_ELEMENT)
(def ^:private CHARS javax.xml.stream.XMLStreamConstants/CHARACTERS)
(def ^:private EOD   javax.xml.stream.XMLStreamConstants/END_DOCUMENT)

(defn- source->string
  "An XML source as a string: a String as-is, else the character stream of an
  org.xml.sax.InputSource, else slurp the source (a Reader)."
  [source]
  (cond
    (string? source) source
    :else (slurp (or (try (.getCharacterStream source) (catch Throwable _ nil))
                     source))))

(defn- attrs [rdr]
  (let [n (.getAttributeCount rdr)]
    (when (pos? n)
      (persistent!
       (reduce (fn [m i]
                 (assoc! m (keyword (.getAttributeLocalName rdr i)) (.getAttributeValue rdr i)))
               (transient {})
               (range n))))))

(defn- parse-element
  "Reads one element (the reader is positioned on its START_ELEMENT) into a
  {:tag :attrs :content} map, recursively."
  [rdr]
  (let [tag (keyword (.getLocalName rdr))
        a   (attrs rdr)
        content (loop [acc []]
                  (let [ev (.next rdr)]
                    (cond
                      (= ev START) (recur (conj acc (parse-element rdr)))
                      ;; keep text, but drop whitespace-only runs between elements
                      ;; (clojure.xml's SAX parser reports them as ignorable).
                      (= ev CHARS) (recur (if (.isWhiteSpace rdr) acc (conj acc (.getText rdr))))
                      (= ev END)   acc
                      (= ev EOD)   acc
                      :else        (recur acc))))]
    {:tag tag :attrs a :content (seq content)}))

(defn parse
  "Parses an XML source (a String, a Reader, or an org.xml.sax.InputSource) into
  the {:tag :attrs :content} element tree. The optional second argument (a SAX
  content handler in clojure.xml) is ignored."
  ([source]
   (let [f   (javax.xml.stream.XMLInputFactory/newInstance)
         rdr (.createXMLStreamReader f (source->string source))]
     (loop []
       (let [ev (.next rdr)]
         (cond
           (= ev START) (parse-element rdr)
           (= ev EOD)   nil
           :else        (recur))))))
  ([source _handler] (parse source)))
