/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
ruleset {
    ruleset('rulesets/junit.xml') {
        // Spock ...
        exclude 'JUnitPublicNonTestMethod'
    }

    ruleset('file:config/codenarc/rules.groovy') {
        // Spock encourages to violate this rule
        exclude 'MethodName'
        // OK for tests
        exclude 'Instanceof'
        // Spock ...
        exclude 'UnnecessaryBooleanExpression'
    }

    ruleset('rulesets/formatting.xml') {
        Indentation {
            spacesPerIndentLevel = 4
        }
    }
}
