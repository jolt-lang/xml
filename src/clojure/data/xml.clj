(ns clojure.data.xml
  "XML generation API matching data.xml 0.0.8: Element/CData records and
  emit-str serialization. Pure Clojure string building — no libxml2/FFI needed."
  (:require [clojure.string :as str]))

(defrecord Element [tag attrs content])
(defrecord CData [content])

(defn cdata
  "Returns a CData node with the given string content."
  [s]
  (CData. s))

(defn element
  "Returns an Element with the given tag, attribute map (nil becomes {}), and
  content. Nil content items are dropped (data.xml behavior); remaining items —
  strings, numbers, nested Elements/CData, and seqs — are kept as-is and
  emit-str flattens nested seqs during rendering."
  [tag attrs & content]
  (Element. tag (or attrs {}) (remove nil? content)))

;; --- emit helpers -------------------------------------------------------

(defn- escape-text
  "Escape &, <, > for text content."
  [s]
  (-> s
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- escape-attr
  "Escape &, <, \" for attribute values."
  [s]
  (-> s
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace "\"" "&quot;")))

(defn- render-attrs
  "Render a map of attributes to XML attribute string, preserving insertion order."
  [attrs]
  (apply str
         (for [[k v] attrs]
           (str " " (name k) "=\"" (escape-attr (str v)) "\""))))

(declare emit-el)

(defn- emit-content
  "Render content items into the StringBuilder. Flattens nested seqs."
  [sb content]
  (doseq [item content]
    (cond
      (instance? Element item) (emit-el sb item)
      (instance? CData item)   (.append sb (str "<![CDATA[" (:content item) "]]>"))
      (nil? item)              nil
      (coll? item)             (emit-content sb item)
      :else                    (.append sb (escape-text (str item))))))

(defn- emit-el
  "Render a single Element into the StringBuilder."
  [sb el]
  (let [tag (if (keyword? (:tag el)) (name (:tag el)) (:tag el))]
    (.append sb (str "<" tag))
    (.append sb (render-attrs (:attrs el)))
    (.append sb ">")
    (emit-content sb (:content el))
    (.append sb (str "</" tag ">"))))

(defn emit-str
  "Serialize an Element to an XML string. Includes the XML prolog, no newlines."
  [el]
  (let [sb (StringBuilder.)]
    (.append sb "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    (emit-el sb el)
    (.toString sb)))
