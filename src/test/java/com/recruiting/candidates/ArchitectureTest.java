package com.recruiting.candidates;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;
import org.springframework.transaction.annotation.Transactional;

/** Architecture rules enforced by the build, so the structure described in DESIGN.md cannot silently erode. */
@AnalyzeClasses(packages = "com.recruiting.candidates", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layersAreRespected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Api")
            .definedBy("..candidate.api..")
            .layer("Dto")
            .definedBy("..candidate.dto..")
            .layer("Service")
            .definedBy("..candidate.service..")
            .layer("Persistence")
            .definedBy("..candidate.persistence..")
            .layer("Domain")
            .definedBy("..candidate.domain..")
            .whereLayer("Api")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Service")
            .mayOnlyBeAccessedByLayers("Api")
            .whereLayer("Persistence")
            .mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Dto")
            .mayOnlyBeAccessedByLayers("Api", "Service")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Dto", "Service", "Persistence");

    @ArchTest
    static final ArchRule domainIsFreeOfWebAndApiTypes = noClasses()
            .that()
            .resideInAPackage("..candidate.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..candidate.dto..", "org.springframework.web..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule sharedInfrastructureDoesNotKnowFeatures = noClasses()
            .that()
            .resideInAPackage("..common..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..candidate..");

    @ArchTest
    static final ArchRule noPackageCycles =
            slices().matching("com.recruiting.candidates.(**)").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule entitiesLiveInTheDomain =
            classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage("..domain..");

    @ArchTest
    static final ArchRule transactionsAreOwnedByServices = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage("..service..")
            .should()
            .beAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
