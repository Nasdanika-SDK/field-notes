# .mvn

This directory marks the root of the field notes' multi-module build. Maven sets
`maven.multiModuleProjectDirectory` to the nearest ancestor that has a `.mvn` directory, so a note
built from its own directory (`cd emf/cross-resource-containment && mvn verify`) still finds `shared`
at the repository root.
