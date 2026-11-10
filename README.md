# Nasdanika Field Notes

Short articles about the technologies and services Nasdanika is built and published with, such as
EMF, OpenTelemetry and the JVM, each one verified by a test where it can be. A note states a belief
about how a technology behaves, gives the answer, and shows the evidence: its test, the listings, and
the files the test wrote. Then it lists the consequences and the versions the test ran against.

Recipes, in `recipes/`, are notes about how parts fit together, such as publishing a book. Each
service documents itself; a recipe documents the connections. It says which steps the build checks
and which are done by hand.

The notes are part of the Nasdanika Body of Knowledge. Their code depends on the technologies they
describe, not on Nasdanika, so they are useful to anyone who uses those technologies.

## Notes

| Note | Technology | What it answers |
|---|---|---|
| [Cross-Resource Containment](emf/cross-resource-containment) | EMF | Can a contained object live in a resource of its own, and what happens on saving, loading, moving and iterating? |
| [Publishing a Book](recipes/publishing) (draft) | Recipes | How do a Maven build, GitHub, Zenodo, Leanpub and Lulu fit together to publish one book in every form readers want? |

## How the notes are kept honest

- **The versions are Nasdanika's.** The parent POM imports the BOM of the Nasdanika SDK Runtime, so
  every third-party version comes from the runtime. The notes' version is the runtime release they
  are tested against.
- **Every runtime release runs them.** Before a release, build the notes against the runtime under
  development (installed in the local repository), the canary build:

  ```
  mvn verify -Dnasdanika.runtime.version=<the runtime's version>
  ```

  A failing note means that an upgrade changed a behavior the runtime may rely on.
- **The versions are measured.** `TestedWith` in `support` records the versions of the jars the
  tests actually loaded, and each note includes that record.
- **No Nasdanika code.** An enforcer rule bans `org.nasdanika*` dependencies other than this
  repository's own.

## Layout

```
pom.xml                              the parent: the runtime's BOM, the enforcer rule, the look
support/                             what the notes' tests share: TestedWith
<technology>/pom.xml                 one aggregator per technology, such as emf/
<technology>/<note>/
  pom.xml                            the note's name, description and third-party dependencies
  src/test/java/                     the evidence: the tests, the source of every listing
  src/docs/asciidoc/article.adoc     the note's text
  src/docs/asciidoc/index.adoc       the note printed on its own, as an article
book/                                all the notes in one book, a part per technology
shared/about-note.adoc               the last section of every note printed on its own
shared/field-notes.css               the series name at the top left of the HTML
```

`mvn verify` runs the tests and renders each note and the collection as PDF and HTML into
`target/book`. The tests run first because a note includes what they write to `target`.
Asciidoctor warnings fail the build.

The look and the NBOK appendix, which ends the collection, come from the runtime release the notes
are built with: the build unpacks `org.nasdanika.sdk.runtime.doc:zephyr` and
`org.nasdanika.sdk.runtime.doc:nbok` into `target/nasdanika`. For a runtime version that is not on
Maven Central, run `mvn install` in the runtime's `doc/zephyr` and `doc/nbok` first.

## Adding a note

1. Create `<technology>/<note>/`, with a `pom.xml` whose parent is the technology's aggregator and
   whose group is `org.nasdanika.sdk.fieldnotes.<technology>`, and add it to the aggregator's
   modules. Declare the third-party dependencies without versions: the runtime's BOM manages them.
   A technology the runtime does not use is the exception, and its note declares the version.
2. Write the tests in `src/test/java`. Mark listings with `tag::name[]` and `end::name[]`, write the
   files a reader should see to `target/evidence`, and call `TestedWith.write(...)` with one class
   from each artifact the note is about, in an `@AfterAll` method.
3. Write `article.adoc`: the belief, the answer, the evidence, the consequences, the versions tested
   (include `target/tested-with.adoc`), and the sources. Say which statements come from reading
   rather than from a test. Use paths relative to `article.adoc`, and prefix the attributes it
   defines with the note's initials, because the collection includes every note in one document.
4. Write `index.adoc`, copying the existing note's and changing its title, description, keywords and
   `note-path`.
5. Include `article.adoc` in `book/src/docs/asciidoc/index.adoc` under its technology's part, add
   the note as a dependency of `book/pom.xml`, and add it to the table above.

## License

The code is licensed under the [Eclipse Public License 2.0](LICENSE). The text of the notes is
licensed under the [Creative Commons Attribution 4.0 International License](https://creativecommons.org/licenses/by/4.0/).
