/*
 * Copyright (C) 2015 Sebastian Daschner, sebastian-daschner.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sebastian_daschner.jaxrs_analyzer.analysis.classes.testclasses.resource.response;

import com.sebastian_daschner.jaxrs_analyzer.model.elements.HttpResponse;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

public class TestClass28 {

    @javax.ws.rs.GET public Response method(final String id) {
        Supplier<Response> responseSupplier = TestClass28::response;
        return responseSupplier.get();
    }

    private static Response response() {
        final Response.ResponseBuilder builder = Response.status(Response.Status.ACCEPTED);
        builder.header("X-Test", "Hello World");
        return builder.build();
    }

    public static Set<HttpResponse> getResult() {
        final HttpResponse result = new HttpResponse();

        result.getStatuses().add(202);
        result.getHeaders().add("X-Test");

        return Collections.singleton(result);
    }

}
