(ns jolt.xml-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [jolt.xml])
  (:import (javax.xml.stream XMLInputFactory XMLStreamConstants)
           (java.io StringReader)))

;; Walk an XMLStreamReader into a simple {:tag :attrs :content} tree — the same
;; shape cognitect.aws.util.xml builds — to exercise the shim end to end.
(defn- parse [s]
  (let [f   (XMLInputFactory/newInstance)
        rdr (.createXMLStreamReader f (StringReader. s))]
    (loop [stack nil content []]
      (if (.hasNext rdr)
        (let [tok (.next rdr)]
          (cond
            (= tok XMLStreamConstants/START_ELEMENT)
            (let [tag   (keyword (.getLocalName rdr))
                  attrs (into {} (for [i (range (.getAttributeCount rdr))]
                                   [(keyword (.getAttributeLocalName rdr i))
                                    (.getAttributeValue rdr i)]))]
              (recur (conj stack content {:tag tag :attrs attrs}) []))
            (= tok XMLStreamConstants/END_ELEMENT)
            (let [[el pcontent & stack] stack]
              (recur stack (conj pcontent (assoc el :content content))))
            (= tok XMLStreamConstants/CHARACTERS)
            (recur stack (if (.isWhiteSpace rdr) content (conj content (.getText rdr))))
            (= tok XMLStreamConstants/END_DOCUMENT)
            (nth content 0)
            :else (recur stack content)))
        (throw (ex-info "unexpected EOF" {}))))))

(deftest parse-nested
  (is (= {:tag :a :attrs {} :content [{:tag :b :attrs {} :content ["hi"]}]}
         (parse "<a><b>hi</b></a>"))))

(deftest parse-attributes
  (is (= {:tag :a :attrs {:x "1" :y "2"} :content []}
         (parse "<a x=\"1\" y=\"2\"></a>"))))

(deftest parse-empty-element
  (is (= {:tag :a :attrs {} :content [{:tag :b :attrs {} :content []}]}
         (parse "<a><b/></a>"))))

(deftest parse-mixed
  (is (= {:tag :r :attrs {} :content [{:tag :x :attrs {} :content ["1"]}
                                      {:tag :y :attrs {} :content ["2"]}]}
         (parse "<r>\n  <x>1</x>\n  <y>2</y>\n</r>"))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'jolt.xml-test)]
    (when (pos? (+ fail error)) (System/exit 1))))
