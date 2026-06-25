(ns jolt.xml
  "XML pull parsing for Jolt, bound to the system libxml2 (its xmlTextReader, a
  pull parser) through jolt.ffi and exposed as the slice of `javax.xml.stream`
  that real Clojure libraries reach for:

    XMLInputFactory     newInstance / setProperty / createXMLStreamReader
    XMLStreamReader     hasNext / next / getLocalName / getText / isWhiteSpace /
                        getAttributeCount / getAttribute{LocalName,Value,Namespace}
    XMLStreamConstants  START_ELEMENT / END_ELEMENT / CHARACTERS / END_DOCUMENT

  This is enough for cognitect aws-api's cognitect.aws.util.xml, which walks an
  XMLStreamReader to parse AWS responses. Shim objects are host tagged-tables
  (jolt.host/tagged-table); everything registers through Jolt's host-shim hooks
  (__register-class-statics! / __register-class-methods!), the same seam
  jolt-lang/jolt-crypto uses for its javax.crypto shims.

  libxml2 is declared in deps.edn :jolt/native and loaded before this namespace."
  (:require [clojure.string :as str]
            [jolt.ffi :as ffi]))

;; --- libxml2 xmlTextReader bindings -----------------------------------------
(ffi/defcfn xml-reader-for-memory "xmlReaderForMemory"       [:pointer :int :string :string :int] :pointer)
(ffi/defcfn xml-reader-read       "xmlTextReaderRead"        [:pointer] :int)
(ffi/defcfn xml-node-type         "xmlTextReaderNodeType"    [:pointer] :int)
(ffi/defcfn xml-local-name        "xmlTextReaderConstLocalName" [:pointer] :string)
(ffi/defcfn xml-value             "xmlTextReaderConstValue"  [:pointer] :string)
(ffi/defcfn xml-is-empty          "xmlTextReaderIsEmptyElement" [:pointer] :int)
(ffi/defcfn xml-attr-count        "xmlTextReaderAttributeCount" [:pointer] :int)
(ffi/defcfn xml-move-to-attr-no   "xmlTextReaderMoveToAttributeNo" [:pointer :int] :int)
(ffi/defcfn xml-move-to-element   "xmlTextReaderMoveToElement" [:pointer] :int)
(ffi/defcfn xml-attr-ns           "xmlTextReaderConstNamespaceUri" [:pointer] :string)
(ffi/defcfn xml-free-reader       "xmlFreeTextReader"        [:pointer] :void)

;; --- javax.xml.stream.XMLStreamConstants event values -----------------------
(def START-ELEMENT 1)
(def END-ELEMENT   2)
(def CHARACTERS    4)
(def END-DOCUMENT  8)

;; libxml2 node types -> XMLStreamConstants. Element/end-element/text map across;
;; comment / PI / declaration map to 0, an event util.xml's `consts` table doesn't
;; recognize, so callers skip it (their reader-cond default re-reads).
(defn- node-type->event [t]
  (case (long t)
    1  START-ELEMENT          ; XML_READER_TYPE_ELEMENT
    15 END-ELEMENT            ; XML_READER_TYPE_END_ELEMENT
    3  CHARACTERS             ; XML_READER_TYPE_TEXT
    4  CHARACTERS             ; XML_READER_TYPE_CDATA
    13 CHARACTERS             ; XML_READER_TYPE_WHITESPACE
    14 CHARACTERS             ; XML_READER_TYPE_SIGNIFICANT_WHITESPACE
    0))

;; libxml2 returns a NULL char* (no name/value/namespace) as a non-string; map it
;; to nil so callers see Clojure's absence, not a raw false.
(defn- ffi-str [x] (when (string? x) x))

;; --- tagged-table helpers ---------------------------------------------------
(defn- tt [tag] (jolt.host/tagged-table tag))
(defn- tget [t k] (jolt.host/ref-get t k))
(defn- tput! [t k v] (jolt.host/ref-put! t k v))

(defn- free-reader! [rdr]
  (when-not (tget rdr :done)
    (xml-free-reader (tget rdr :reader))
    (ffi/free (tget rdr :buf))
    (tput! rdr :done true)))

;; Build an XMLStreamReader over an XML string. libxml2 references (does not copy)
;; the buffer, so it's kept alive in the reader and freed at end-of-document.
(defn- make-reader [^String s]
  (let [bytes (.getBytes s "UTF-8")
        n     (alength bytes)
        buf   (ffi/alloc (max 1 n))]
    (ffi/write-array buf bytes)
    (let [reader (xml-reader-for-memory buf n "" "UTF-8" 0)]
      (when (ffi/null? reader)
        (ffi/free buf)
        (throw (ex-info "could not create XML reader" {})))
      (doto (tt :jolt.xml/reader)
        (tput! :reader reader)
        (tput! :buf buf)
        (tput! :pending-end false)
        (tput! :done false)))))

(defn install! []
  ;; javax.xml.stream.XMLStreamConstants — the event-type fields.
  (doseq [nm ["XMLStreamConstants" "javax.xml.stream.XMLStreamConstants"]]
    (__register-class-statics! nm {"START_ELEMENT" START-ELEMENT
                                   "END_ELEMENT"   END-ELEMENT
                                   "CHARACTERS"    CHARACTERS
                                   "END_DOCUMENT"  END-DOCUMENT}))

  ;; javax.xml.stream.XMLInputFactory — newInstance + the property-name fields,
  ;; createXMLStreamReader over a Reader/String. setProperty is accepted and ignored
  ;; (libxml2 is already namespace-unaware-friendly and coalesces text).
  (doseq [nm ["XMLInputFactory" "javax.xml.stream.XMLInputFactory"]]
    (__register-class-statics! nm {"newInstance" (fn [& _] (tt :jolt.xml/factory))
                                   "IS_NAMESPACE_AWARE"             "javax.xml.stream.isNamespaceAware"
                                   "IS_SUPPORTING_EXTERNAL_ENTITIES" "javax.xml.stream.isSupportingExternalEntities"
                                   "IS_COALESCING"                  "javax.xml.stream.isCoalescing"}))
  (__register-class-methods! :jolt.xml/factory
    {"setProperty" (fn [factory k v] nil)
     "createXMLStreamReader" (fn [factory src & more]
                               (make-reader (if (string? src) src (slurp src))))})

  ;; javax.xml.stream.XMLStreamReader — the pull cursor.
  (__register-class-methods! :jolt.xml/reader
    {"hasNext" (fn [self] (not (tget self :done)))
     "next" (fn [self]
              (cond
                (tget self :done) END-DOCUMENT
                (tget self :pending-end)
                (do (tput! self :pending-end false) END-ELEMENT)
                :else
                (let [reader (tget self :reader)
                      r (xml-reader-read reader)]
                  (if (<= r 0)
                    (do (free-reader! self) END-DOCUMENT)
                    (let [t (xml-node-type reader)]
                      (when (and (= (long t) 1) (= 1 (xml-is-empty reader)))
                        (tput! self :pending-end true))
                      (node-type->event t))))))
     "getLocalName" (fn [self] (ffi-str (xml-local-name (tget self :reader))))
     "getText" (fn [self] (ffi-str (xml-value (tget self :reader))))
     "isWhiteSpace" (fn [self] (let [v (ffi-str (xml-value (tget self :reader)))]
                                 (or (nil? v) (str/blank? v))))
     "getAttributeCount" (fn [self] (xml-attr-count (tget self :reader)))
     "getAttributeLocalName" (fn [self i]
                               (let [rd (tget self :reader)]
                                 (xml-move-to-attr-no rd i)
                                 (let [v (ffi-str (xml-local-name rd))] (xml-move-to-element rd) v)))
     "getAttributeValue" (fn [self i]
                           (let [rd (tget self :reader)]
                             (xml-move-to-attr-no rd i)
                             (let [v (ffi-str (xml-value rd))] (xml-move-to-element rd) v)))
     "getAttributeNamespace" (fn [self i]
                               (let [rd (tget self :reader)]
                                 (xml-move-to-attr-no rd i)
                                 (let [v (or (ffi-str (xml-attr-ns rd)) "")] (xml-move-to-element rd) v)))
     "close" (fn [self] (free-reader! self) nil)})
  nil)

(install!)
