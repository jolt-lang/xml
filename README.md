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
