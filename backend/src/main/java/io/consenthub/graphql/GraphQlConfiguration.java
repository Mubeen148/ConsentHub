package io.consenthub.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.scalars.ExtendedScalars;
import graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Spring for GraphQL runtime configuration ({@code ADR-009}, {@code SEC-G06}):
 * <ul>
 *   <li>Registers {@code ExtendedScalars.Json} for {@code scalar JSON} in {@code schema.graphqls}.</li>
 *   <li>Enforces query depth ({@code <= 8}) and query complexity ({@code <= 200}) guards.</li>
 *   <li>Disables schema introspection in {@code prod} profile unless explicitly enabled.</li>
 * </ul>
 */
@Configuration
public class GraphQlConfiguration {

    public static final int MAX_QUERY_DEPTH = 8;
    public static final int MAX_QUERY_COMPLEXITY = 200;

    @Bean
    public RuntimeWiringConfigurer graphqlRuntimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder.scalar(ExtendedScalars.Json);
    }

    @Bean
    public MaxQueryDepthInstrumentation maxQueryDepthInstrumentation() {
        return new MaxQueryDepthInstrumentation(MAX_QUERY_DEPTH);
    }

    @Bean
    public MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation() {
        return new MaxQueryComplexityInstrumentation(MAX_QUERY_COMPLEXITY);
    }

    @Bean
    public GraphQlSourceBuilderCustomizer introspectionSecurityCustomizer(
            Environment environment,
            @Value("${spring.graphql.schema.introspection.enabled:true}") boolean introspectionProperty
    ) {
        boolean isProdProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        boolean effectiveIntrospectionEnabled = introspectionProperty && (!isProdProfile
                || Boolean.parseBoolean(environment.getProperty("GRAPHQL_INTROSPECTION_ENABLED", "false")));

        return builder -> {
            if (!effectiveIntrospectionEnabled) {
                builder.configureRuntimeWiring(wiring ->
                        wiring.fieldVisibility(NoIntrospectionGraphqlFieldVisibility.NO_INTROSPECTION_FIELD_VISIBILITY)
                );
            }
        };
    }
}
