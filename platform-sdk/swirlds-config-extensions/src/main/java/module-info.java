module com.swirlds.config.extensions {
    exports com.swirlds.config.extensions.export;
    exports com.swirlds.config.extensions.reflection;
    exports com.swirlds.config.extensions.sources;
    exports com.swirlds.config.extensions.validators;
    exports com.swirlds.config.extensions.converters;

    requires transitive com.swirlds.config.api;
    requires com.swirlds.base;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
