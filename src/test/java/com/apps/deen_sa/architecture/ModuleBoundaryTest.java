package com.apps.deen_sa.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.apps.deen_sa", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {
    @ArchTest
    static final ArchRule CORE_MUST_NOT_DEPEND_ON_EXTENSIONS = noClasses()
            .that().resideInAnyPackage("..core..")
            .should().dependOnClassesThat().resideInAnyPackage("..finance..", "..saree..", "..grocery..");

    @ArchTest
    static final ArchRule CORE_MUST_NOT_DEPEND_ON_DELIVERY_OR_AI_ADAPTERS = noClasses()
            .that().resideInAnyPackage("..core..")
            .should().dependOnClassesThat().resideInAnyPackage("..conversation..", "..llm..", "..config..", "..dto..", "..exception..");

    @ArchTest
    static final ArchRule CONVERSATION_MUST_NOT_DEPEND_ON_EXTENSIONS = noClasses()
            .that().resideInAnyPackage("..conversation..")
            .should().dependOnClassesThat().resideInAnyPackage("..finance..", "..saree..", "..grocery..");

    @ArchTest
    static final ArchRule EXTENSION_API_MUST_NOT_DEPEND_ON_IMPLEMENTATIONS = noClasses()
            .that().resideInAnyPackage("..extension.api..")
            .should().dependOnClassesThat().resideInAnyPackage("..finance..", "..saree..", "..grocery..", "..conversation..", "..llm..");

    @ArchTest
    static final ArchRule FINANCE_MUST_NOT_DEPEND_ON_OTHER_EXTENSIONS = noClasses()
            .that().resideInAnyPackage("..finance..")
            .should().dependOnClassesThat().resideInAnyPackage("..saree..", "..grocery..");

    @ArchTest
    static final ArchRule SAREE_MUST_NOT_DEPEND_ON_OTHER_EXTENSIONS = noClasses()
            .that().resideInAnyPackage("..saree..")
            .should().dependOnClassesThat().resideInAnyPackage("..finance..", "..grocery..");

    @ArchTest
    static final ArchRule GROCERY_MUST_NOT_DEPEND_ON_OTHER_EXTENSIONS = noClasses()
            .that().resideInAnyPackage("..grocery..")
            .should().dependOnClassesThat().resideInAnyPackage("..finance..", "..saree..");
}
