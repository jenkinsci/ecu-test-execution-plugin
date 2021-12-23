/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
ruleset {
    ruleset('rulesets/basic.xml')

    ruleset('rulesets/braces.xml')

    ruleset('rulesets/concurrency.xml')

    ruleset('rulesets/convention.xml') {
        exclude 'CompileStatic'
        exclude 'FieldTypeRequired'
        exclude 'MethodParameterTypeRequired'
        exclude 'MethodReturnTypeRequired'
        exclude 'NoDef'
        exclude 'VariableTypeRequired'
    }

    ruleset('rulesets/design.xml')

    ruleset('rulesets/dry.xml') {
        exclude 'DuplicateListLiteral'
        exclude 'DuplicateMapLiteral'
        exclude 'DuplicateNumberLiteral'
        exclude 'DuplicateStringLiteral'
    }

    ruleset('rulesets/enhanced.xml')

    ruleset('rulesets/exceptions.xml')

    ruleset('rulesets/formatting.xml') {
        // enforce at least one space after map entry colon
        SpaceAroundMapEntryColon {
            characterAfterColonRegex = /\s/
            characterBeforeColonRegex = /./
        }

        exclude 'ClassJavadoc'
        exclude 'ClassEndsWithBlankLine'
    }

    ruleset('rulesets/generic.xml')

    ruleset('rulesets/groovyism.xml') {
        exclude 'GetterMethodCouldBeProperty'
    }

    ruleset('rulesets/imports.xml') {
        // we order static imports after other imports because that's the default style in IDEA
        MisorderedStaticImports {
            comesBefore = false
        }
    }

    ruleset('rulesets/logging.xml')

    ruleset('rulesets/naming.xml') {
        // Gradle encourages violations of this rule
        exclude 'ConfusingMethodName'
    }

    ruleset('rulesets/security.xml') {
        // we don't care for the Enterprise Java Bean specification here
        exclude 'JavaIoPackageAccess'
    }

    ruleset('rulesets/serialization.xml')

    ruleset('rulesets/size.xml') {
        NestedBlockDepth {
            maxNestedBlockDepth = 6
        }

        exclude 'AbcMetric'
        exclude 'CrapMetric'
        exclude 'MethodSize'
    }

    ruleset('rulesets/unnecessary.xml') {
        exclude 'UnnecessaryReturnKeyword'
        exclude 'UnnecessaryDotClass'
    }

    ruleset('rulesets/unused.xml')
}
