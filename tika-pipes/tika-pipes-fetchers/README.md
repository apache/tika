# Tika Pipes Fetchers

Tika Pipes has the ability to pull binary data from a variety of different sources.

Tika Pipes reads from these input sources using the Pipe Iterators library.

Each Tika Pipes fetcher is implemented as a pf4j plugin.

There are two Pf4j extensions for Fetchers: One for the Fetcher code, and one for the FetcherConfig.

Fetchers are Maven Java Jar modules that contain the following key files:

* `tika-pipes-fetchers/tika-fetcher-YOURFETCHER/src/main/assembly.xml` - maven assembly. Tells maven how to build the package.
* `tika-pipes-fetchers/tika-fetcher-YOURFETCHER/src/main/java/org/apache/tika/pipes/fetchers/YOURFETCHER/config/YOURFETCHERFetcherConfig.java` - Custom configuration properties for the fetcher.
* `tika-pipes-fetchers/tika-fetcher-YOURFETCHER/src/main/java/org/apache/tika/pipes/fetchers/YOURFETCHER/FETCHERFetcher.java` - The fetcher code.
* `tika-pipes-fetchers/tika-fetcher-YOURFETCHER/src/main/java/org/apache/tika/pipes/fetchers/YOURFETCHER/FETCHERPlugin.java` - The pf4j plugin with start/stop event handler at the pf4j plugin level.
* `tika-pipes-fetchers/tika-fetcher-YOURFETCHER/src/main/resources/plugin.properties` - pf4j plugin properties file (see https://pf4j.org/doc/plugins.html)

When packaged, they will be built to a `.zip` file format.

# Tika Fetchers are PF4J Plugins

Each Fetcher in Tika Pipes is a PF4J plugin.

Each Fetcher has a `plugin.properties` file that describes the plugin. See more info here: https://pf4j.org/doc/plugins.html

But most importantly, each Plugin has an ID that is defined in this file:

```
plugin.id=microsoft-graph-fetcher
plugin.class=org.apache.tika.pipes.fetchers.microsoftgraph.MicrosoftGraphPlugin
```

When you refer to a fetcher in the Tika Pipes service, you refer to it with the plugin ID.

# How to add a new Fetcher to the project

* Copy one of the existing folders in tika-pipes-fetchers to `tika-pipes-fetchers/tika-fetcher-YOURFETCHER` that most closely matches your new Fetcher.

* Update `tika-pipes-fetchers/tika-fetcher-YOURFETCHER/pom.xml`

* Update groupId, artifactId, to match your project.

* Update the Maven project dependencies:

    * Remove the dependencies from the fetcher you copied from that you do not need.
    * Add the dependency your project needs as you need them.

* All the java classes for the FetcherConfig, fetcher and plugin need to be refactored to your fetcher's name.

* Update `tika-pipes-fetchers/pom.xml` to include your new fetcher module in the `<modules>` section.

* Add a CLI to `tika-pipes-cli/src/main/java/pipes`
