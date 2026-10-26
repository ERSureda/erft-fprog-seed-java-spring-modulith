package com.template.api.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(packages = "com.template.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ApplicationRulesArchTest {

    private static final DescribedPredicate<JavaClass> IMPLEMENT_EXACTLY_ONE_USE_CASE =
            describe("implementar exactamente una interfaz UseCase",
                    javaClass -> javaClass.getRawInterfaces().size() == 1 &&
                            javaClass.getRawInterfaces().iterator().next().getSimpleName().endsWith("UseCase"));

    private static final DescribedPredicate<JavaClass> BE_DOMAIN_MODEL_OR_JPA_ENTITY =
            describe("ser entidad de dominio (excepto enums) o entidad JPA",
                    javaClass -> (javaClass.getPackageName().contains(".domain.model") &&
                            !javaClass.getPackageName().contains(".domain.model.enums")) ||
                            javaClass.isAnnotatedWith(jakarta.persistence.Entity.class));

    @ArchTest
    static final ArchRule application_services_should_implement_one_use_case =
            classes().that().resideInAPackage("..application.service..")
                    .should().haveSimpleNameEndingWith("Service")
                    .andShould(ArchCondition.from(IMPLEMENT_EXACTLY_ONE_USE_CASE))
                    .as("APP-01: Cada servicio de aplicación debe implementar exactamente una interfaz UseCase y terminar en Service");

    @ArchTest
    static final ArchRule use_cases_should_not_return_domain_models_or_entities =
            noMethods().that().areDeclaredInClassesThat().resideInAnyPackage("..application.port.in..", "..application.service..")
                    .and().arePublic()
                    .should().haveRawReturnType(BE_DOMAIN_MODEL_OR_JPA_ENTITY)
                    .as("APP-04: Los casos de uso deben retornar proyecciones inmutables (*Result) o void, nunca entidades de dominio ni @Entity");

    @ArchTest
    static final ArchRule transactional_should_only_be_in_application =
            methods().that().areAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                    .should().beDeclaredInClassesThat().resideInAnyPackage(
                            "..application..",
                            "..shared.infrastructure.adapter.out.."
                    )
                    .as("TRX-01: La demarcación transaccional debe residir exclusivamente en la capa de aplicación (salvo el relay transversal en shared)");
}
