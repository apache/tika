# Tika's use of pf4j -- Informal Notes

As of the initial `pf4j` contribution, we aren't really using the plugin functionality. We only
need the Extension functionality for now. We can add the plugin lifecycle items later if needed.

At a high level, we needed to be able to inject configurations into Extensions, and we needed to be able to
create multiple instances of the same Extension with different configurations.

We tried to get this to work with subclassing the PluginManager, but it was cleaner to
create Extensions that were factories for the Extensions that we wanted, rather than Extensions that
were directly the Fetchers, etc. This adds a bit to code bloat, but it is much, much cleaner.

We do have a custom TikaPluginManager that performs the plugin unzipping in a multithreaded/multiprocess safe way.
Given that we're heading towards avoiding fat jars in 4.x, it felt like we should prefer the zip approach
to our own plugins. When we did that though, in the pipes framework, we ran into race conditions where
different PipesServers were all trying to unzip the plugins at the same time, or perhaps read from a 
plugin directory that was only half-unzipped.

And, we added `buildConfiguredExtensions(...)`, which is the main functionality that we needed.


## Thoughts for the future...
We didn't have much luck building unit tests for the plugins within the plugin modules. So,
we opted for integration tests, which are critical for making sure that the plugin zip contains
all that it needs to, etc. There are probably better ways to manage this.

If this all works out, this is a really elegant way of modularizing and packaging plugins and extensions.
It would be great to do the same for detectors and parsers.

We should add filters so that we're only loading the plugins we need based on the extensions that
are configured in the json config file.

We may want to move to a resource based organization for pipes so that we'd have, say, a `tika-pipes-s3`
plugin that has a fetcher, emitter and iterator. Some plugins would only have a fetcher or an emitter, but
some would have all three. If we did this, the heavy s3 dependencies for all three components would be 
packaged only once in a zip.

## Configuration generally