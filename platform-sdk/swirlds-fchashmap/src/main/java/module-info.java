/**
 * A HashMap-like structure that implements the FastCopyable interface.
 */
module com.swirlds.fchashmap {
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires static com.github.spotbugs.annotations;

    exports com.swirlds.fchashmap;
    exports com.swirlds.fchashmap.config;
}
