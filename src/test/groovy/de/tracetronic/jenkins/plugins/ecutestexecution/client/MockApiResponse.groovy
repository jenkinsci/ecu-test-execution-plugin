/*
 * Copyright (c) 2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.client

import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

class MockApiResponse {
    static Response getResponseUnauthorized(){
        return new Response.Builder()
                .request(new Request.Builder().url('http://example.com').build())
                .protocol(Protocol.HTTP_1_1)
                .code(401).message('unauthorized')
                .body(ResponseBody.create("{}", MediaType.parse('application/json; charset=utf-8')
                )).build()
    }

    static Response getResponseBusy(){
        return new Response.Builder()
                .request(new Request.Builder().url('http://example.com').build())
                .protocol(Protocol.HTTP_1_1)
                .code(409).message('ecu.test is busy')
                .body(ResponseBody.create("{}", MediaType.parse('application/json; charset=utf-8')
                )).build()
    }
}
