package com.template.api.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(packages = "com.template.api", importOptions = ImportOption.DoNotIncludeTests.class)
class WebRulesArchTest {

    private static final DescribedPredicate<JavaClass> RESIDE_IN_DOMAIN_MODEL_EXCEPT_ENUMS =
            describe("residir en domain.model excluyendo enums",
                    javaClass -> javaClass.getPackage().getName().contains(".domain.model") &&
                            !javaClass.getPackage().getName().contains(".domain.model.enums"));

    @ArchTest
    static final ArchRule web_adapters_should_not_depend_on_domain_models_except_enums =
            noClasses().that().resideInAPackage("..adapter.in.web..")
                    .should().dependOnClassesThat(RESIDE_IN_DOMAIN_MODEL_EXCEPT_ENUMS)
                    .as("INP-01: Los adaptadores web no deben depender del modelo de dominio salvo enums de estado");

    @ArchTest
    static final ArchRule web_adapters_should_not_be_transactional =
            noMethods().that().areDeclaredInClassesThat().resideInAPackage("..adapter.in.web..")
                    .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                    .as("INP-02: Los adaptadores web nunca deben abrir transacciones (@Transactional)");
}
