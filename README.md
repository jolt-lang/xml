# jolt-xml

XML pull parsing for [Jolt](https://github.com/jolt-lang/jolt), bound to the
system **libxml2** through `jolt.ffi` and exposed as the slice of
`javax.xml.stream` that Clojure libraries reach for. libxml2's `xmlTextReader`
is itself a pull parser, so it maps almost 1:1 to `XMLStreamReader`.

Requiring the namespace installs the shims:

```clojure
(require 'jolt.xml)

(let [f   (javax.xml.stream.XMLInputFactory/newInstance)
      rdr (.createXMLStreamReader f (java.io.StringReader. "<a x=\"1\">hi</a>"))]
  (loop []
    (when (.hasNext rdr)
      (case (.next rdr)
        1 (println :start (.getLocalName rdr))   ; START_ELEMENT
        4 (println :text  (.getText rdr))         ; CHARACTERS
        2 (println :end)                          ; END_ELEMENT
        nil)
      (recur))))
```

## What's shimmed

- **`javax.xml.stream.XMLInputFactory`** — `newInstance`, `setProperty` (accepted,
  ignored — libxml2 is namespace-unaware-friendly and coalesces text),
  `createXMLStreamReader` over a `Reader` or `String`, and the
  `IS_NAMESPACE_AWARE` / `IS_SUPPORTING_EXTERNAL_ENTITIES` / `IS_COALESCING`
  property-name fields.
- **`javax.xml.stream.XMLStreamReader`** — `hasNext`, `next`, `getLocalName`,
  `getText`, `isWhiteSpace`, `getAttributeCount`, `getAttributeLocalName`,
  `getAttributeValue`, `getAttributeNamespace`, `close`. Empty elements (`<a/>`)
  report a `START_ELEMENT` followed by a synthetic `END_ELEMENT`, like StAX.
- **`javax.xml.stream.XMLStreamConstants`** — `START_ELEMENT` `END_ELEMENT`
  `CHARACTERS` `END_DOCUMENT`.

This is enough for [cognitect aws-api](https://github.com/cognitect-labs/aws-api)'s
`cognitect.aws.util.xml`, which walks an `XMLStreamReader` to parse AWS responses.

## Native dependency

libxml2 is declared `:jolt/native` in `deps.edn` and loaded before the namespace.
It ships with macOS (the dyld shared cache) and is ubiquitous on Linux
(`libxml2.so.2`).

## Use it

```clojure
;; deps.edn
{:deps {io.github.jolt-lang/xml {:git/sha "..."}}}
```

```
joltc -M:test   # run the conformance tests
```

## clojure.xml

Requiring `jolt.xml` also makes `clojure.xml/parse` available (it ships in this
library since it has no `clojure.xml` of its own):

```clojure
(require 'clojure.xml)
(clojure.xml/parse "<a x=\"1\"><b>hi</b></a>")
;; => {:tag :a, :attrs {:x "1"}, :content [{:tag :b, :attrs nil, :content ["hi"]}]}
```

It accepts a String, a Reader, or an `org.xml.sax.InputSource`, and returns the
standard `{:tag :attrs :content}` element tree — whitespace-only text between
elements dropped, like the JVM. That's enough for zipper code such as
[clojure.data.zip](https://github.com/clojure/data.zip).

## clojure.data.xml

An XML generation API matching [data.xml 0.0.8](https://github.com/clojure/data.xml):
Element and CData records plus `emit-str` serialization. Pure Clojure string
building — no libxml2/FFI needed, so `jolt.xml` is not required.

```clojure
(require '[clojure.data.xml :refer [element cdata emit-str]])

(emit-str (element :rss {:version "2.0"}
                   (element :channel nil
                            (element :title nil "Hi"))))
;; => <?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>Hi</title></channel></rss>

(emit-str (element :a nil (cdata "<b>hi</b>")))
;; => <?xml version="1.0" encoding="UTF-8"?><a><![CDATA[<b>hi</b>]]></a>

(pr-str (element :a {} (list "foo")))
;; => #clojure.data.xml.Element{:tag :a, :attrs {}, :content (("foo"))}
```

- `(element tag attrs & content)` — Element record. `tag` is a keyword or string;
  `attrs` is a map (nil becomes `{}`); content items are stored as the rest-args
  list and flattened during serialization.
- `(cdata s)` — CData record for unescaped character data.
- `(emit-str element)` — serializes to the XML string with prolog, no newlines.
  Empty elements render as `<tag></tag>` (not self-closing). Text content escapes
  `&`, `<`, `>`. Attribute values escape `&`, `<`, `"`.

```sh
joltc -M:test-data-xml   # run the data.xml conformance tests
```
