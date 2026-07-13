(ns clojure.data.xml-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.data.xml :as dx]))

(deftest emit-basic
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>Hi</title></channel></rss>"
         (dx/emit-str (dx/element :rss {:version "2.0"}
                                  (dx/element :channel nil
                                              (dx/element :title nil "Hi")))))))

(deftest emit-empty-element
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><empty></empty>"
         (dx/emit-str (dx/element :empty nil)))))

(deftest emit-empty-string-content
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a></a>"
         (dx/emit-str (dx/element :a nil "")))))

(deftest emit-cdata
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a><![CDATA[<b>hi</b>]]></a>"
         (dx/emit-str (dx/element :a nil (dx/cdata "<b>hi</b>"))))))

(deftest emit-text-escaping
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a>x &amp; y &lt; z &gt; w</a>"
         (dx/emit-str (dx/element :a nil "x & y < z > w")))))

(deftest emit-colon-keyword-tag
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><atom:link href=\"u\" rel=\"self\"></atom:link>"
         (dx/emit-str (dx/element (keyword "atom:link") {:href "u" :rel "self"} nil)))))

(deftest emit-numeric-content
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a>42</a>"
         (dx/emit-str (dx/element :a nil 42)))))

(deftest emit-nested-seq
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a><b>1</b><c>2</c></a>"
         (dx/emit-str (dx/element :a nil (list (dx/element :b nil "1")
                                                (dx/element :c nil "2")))))))

(deftest emit-string-tag
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><content:encoded>LONG CONTENT</content:encoded>"
         (dx/emit-str (dx/element "content:encoded" nil "LONG CONTENT")))))

(deftest emit-attr-escaping
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a href=\"a&amp;b&lt;c&quot;d\"></a>"
         (dx/emit-str (dx/element :a {:href "a&b<c\"d"} nil)))))

(deftest pr-str-element
  (is (= "#clojure.data.xml.Element{:tag :a, :attrs {}, :content ((\"foo\"))}"
         (pr-str (dx/element :a {} (list "foo"))))))

(deftest pr-str-cdata
  (is (= "#clojure.data.xml.CData{:content \"hi\"}"
         (pr-str (dx/cdata "hi")))))

(deftest element-uses-nil-attrs-as-empty-map
  (is (= {} (:attrs (dx/element :channel nil)))))

(deftest cdata-record
  (let [c (dx/cdata "raw")]
    (is (instance? clojure.data.xml.CData c))
    (is (= "raw" (:content c)))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'clojure.data.xml-test)]
    (when (pos? (+ fail error)) (System/exit 1))))
