/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
// `buildPluginWithGradle` step provided by: https://github.com/jenkins-infra/pipeline-library
@Library('pipeline-library@0bfb338')
buildPluginWithGradle(
        configurations: [
            [platform: 'linux', jdk: 17],
            [platform: 'windows', jdk: 17]
        ],
        failFast: false,
)
