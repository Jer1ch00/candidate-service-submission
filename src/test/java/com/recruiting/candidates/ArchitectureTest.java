package com.recruiting.candidates;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Architecture rules enforced by the build, so the structure described in DESIGN.md cannot silently erode. */
@AnalyzeClasses(packages = "com.recruiting.candidates", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String SERVICE_INTERFACES = "com.recruiting.candidates.service";

    /**
     * Controller → service → repository. Mappers serve the service; entities never reach the controller because the
     * service maps them to DTOs inside its transaction.
     */
    @ArchTest
    static final ArchRule layersAreRespected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller")
            .definedBy("..controller..")
            .layer("Service")
            .definedBy("..service..")
            .layer("Mapper")
            .definedBy("..mapper..")
            .layer("Repository")
            .definedBy("..repository..")
            .layer("Entity")
            .definedBy("..entity..")
            .layer("Dto")
            .definedBy("..dto..")
            .whereLayer("Controller")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Service")
            .mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Mapper")
            .mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Repository")
            .mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Entity")
            .mayOnlyBeAccessedByLayers("Service", "Mapper", "Repository")
            .whereLayer("Dto")
            .mayOnlyBeAccessedByLayers("Controller", "Service", "Mapper");

    /** Dependency inversion: the controller knows the service contract, never its implementation. */
    @ArchTest
    static final ArchRule controllersDependOnServiceInterfacesOnly = noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..service.impl..");

    @ArchTest
    static final ArchRule servicePackageHoldsOnlyInterfaces =
            classes().that().resideInAPackage(SERVICE_INTERFACES).should().beInterfaces();

    @ArchTest
    static final ArchRule serviceImplementationsFollowTheConvention = classes()
            .that()
            .resideInAPackage("..service.impl..")
            .should()
            .haveSimpleNameEndingWith("Impl")
            .andShould()
            .beAnnotatedWith(Service.class)
            .andShould()
            .implement(resideInAPackage(SERVICE_INTERFACES));

    @ArchTest
    static final ArchRule entitiesAndModelAreFreeOfWebAndApiTypes = noClasses()
            .that()
            .resideInAnyPackage("..entity..", "..model..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..controller..",
                    "..service..",
                    "..mapper..",
                    "..dto..",
                    "org.springframework.web..",
                    "jakarta.servlet..");

    /** Cross-cutting code (configuration, filters, errors, utilities) stays generic and never reaches into features. */
    @ArchTest
    static final ArchRule infrastructureDoesNotDependOnBusinessLayers = noClasses()
            .that()
            .resideInAnyPackage("..config..", "..filter..", "..exception..", "..util..", "..validation..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..service..", "..mapper..", "..repository..", "..entity..");

    @ArchTest
    static final ArchRule noPackageCycles =
            slices().matching("com.recruiting.candidates.(**)").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule entitiesLiveInTheEntityPackage =
            classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage("..entity..");

    @ArchTest
    static final ArchRule transactionsAreOwnedByServiceImplementations = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage("..service.impl..")
            .should()
            .beAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
