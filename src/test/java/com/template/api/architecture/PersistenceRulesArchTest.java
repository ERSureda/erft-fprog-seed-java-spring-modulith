package com.template.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.template.api", importOptions = ImportOption.DoNotIncludeTests.class)
class PersistenceRulesArchTest {

    @ArchTest
    static final ArchRule entities_should_reside_in_jpa_persistence =
            classes().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAPackage("..infrastructure.adapter.out.persistence..jpa..")
                    .as("OUT-01: Las entidades JPA (@Entity) deben residir exclusivamente en el adaptador de persistencia JPA");

    @ArchTest
    static final ArchRule jdbc_template_should_reside_in_jdbc_persistence =
            noClasses().that().resideOutsideOfPackages(
                            "..infrastructure.adapter.out.persistence..jdbc..",
                            "..shared.infrastructure.adapter.out.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
                    .as("OUT-05: El uso de NamedParameterJdbcTemplate debe confinarse al adaptador de persistencia JDBC o al outbox transversal");
}
