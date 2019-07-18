/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.test.auth;

import org.ballerinalang.test.util.HttpResponse;
import org.ballerinalang.test.util.HttpsClientRequest;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Test cases for authentication with custom handlers.
 */
@Test(groups = "auth-test")
public class AuthnWithCustomHandlersTest extends AuthBaseTest {

    private final int servicePort = 20023;

    @Test(description = "Secured resource, secured service test case with valid auth headers")
    public void testNoAuthHeaders() throws Exception {
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put("Authorization", "Basic YWJjOjEyMw==");
        HttpResponse response = HttpsClientRequest.doGet(serverInstance.getServiceURLHttps(servicePort, "echo/test"),
                headersMap, serverInstance.getServerHome());
        assertUnauthorized(response);
    }

    public void testInvalidAuthHeaders() throws Exception {
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put("Authorization", "Basic YWJjOjEyMw==");
        HttpResponse response = HttpsClientRequest.doGet(serverInstance.getServiceURLHttps(servicePort, "echo/test"),
                headersMap, serverInstance.getServerHome());
        assertUnauthorized(response);
    }

    public void testValidAuthHeaders() throws Exception {
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put("Authorization", "Custom YWJjOjEyMw==");
        HttpResponse response = HttpsClientRequest.doGet(serverInstance.getServiceURLHttps(servicePort, "echo/test"),
                headersMap, serverInstance.getServerHome());
        assertOK(response);
    }
}
