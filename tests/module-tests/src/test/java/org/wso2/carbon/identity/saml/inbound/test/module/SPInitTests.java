/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.saml.inbound.test.module;

import com.google.common.net.HttpHeaders;
import org.apache.commons.io.Charsets;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.Response;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.testng.listener.PaxExam;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.auth.saml2.common.SAML2AuthConstants;
import org.wso2.carbon.identity.auth.saml2.common.SAML2AuthUtils;
import org.wso2.carbon.identity.gateway.common.model.sp.ServiceProviderConfig;
import org.wso2.carbon.identity.saml.exception.SAML2SSOServerException;
import org.wso2.carbon.kernel.utils.CarbonServerInfo;

import javax.inject.Inject;
import javax.ws.rs.HttpMethod;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/**
 * General tests for SAML inbound SP Init.
 */
@Listeners(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SPInitTests {

    private static final Logger log = LoggerFactory.getLogger(SPInitTests.class);

    @Inject
    private BundleContext bundleContext;

    @Inject
    private CarbonServerInfo carbonServerInfo;


    @Configuration
    public Option[] createConfiguration() {

        List<Option> optionList = OSGiTestUtils.getDefaultSecurityPAXOptions();

        optionList.add(CoreOptions.systemProperty("java.security.auth.login.config")
                .value(Paths.get(OSGiTestUtils.getCarbonHome(), "conf", "security", "carbon-jaas.config")
                        .toString()));

        return optionList.toArray(new Option[optionList.size()]);
    }

    /**
     * Test inbound authentication and successful statement on assertion.
     */
    @Test
    public void testSAMLInboundAuthentication() {
        try {
            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, TestConstants.SAMPLE_ISSUER_NAME, TestConstants.ACS_URL);
            String samlRequestString = SAML2AuthUtils.encodeForRedirect(samlRequest);
            SAML2AuthUtils.encodeForPost(SAML2AuthUtils.marshall(samlRequest));

            StringBuilder httpQueryString = new StringBuilder(SAML2AuthConstants.SAML_REQUEST + "=" + samlRequestString);
            httpQueryString.append("&" + SAML2AuthConstants.RELAY_STATE + "=" + URLEncoder.encode("relayState",
                    StandardCharsets.UTF_8.name()).trim());
            SAML2AuthUtils.addSignatureToHTTPQueryString(httpQueryString, "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
                    SAML2AuthUtils.getServerCredentials());

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                                                                + "?" + httpQueryString.toString(), HttpMethod.GET, false);
            String locationHeader = TestUtils.getResponseHeader(HttpHeaders.LOCATION, urlConnection);
            Assert.assertTrue(locationHeader.contains(TestConstants.RELAY_STATE));
            Assert.assertTrue(locationHeader.contains(TestConstants.EXTERNAL_IDP));

            String relayState = locationHeader.split(TestConstants.RELAY_STATE + "=")[1];
            relayState = relayState.split(TestConstants.QUERY_PARAM_SEPARATOR)[0];

            urlConnection = TestUtils.request
                    (TestConstants.GATEWAY_ENDPOINT + "?" + TestConstants.RELAY_STATE + "=" + relayState +
                     "&" + TestConstants.ASSERTION + "=" +
                     TestConstants.AUTHENTICATED_USER_NAME, HttpMethod.GET, false);

            String cookie = TestUtils.getResponseHeader(HttpHeaders.SET_COOKIE, urlConnection);
            cookie = cookie.split(org.wso2.carbon.identity.gateway.common.util.Constants.GATEWAY_COOKIE + "=")[1];
            Assert.assertNotNull(cookie);
        } catch (IOException e) {
            Assert.fail("Error while running testSAMLInboundAuthentication test case", e);
        }
    }

    /**
     * Test authentication with post binding.
     */
    @Test
    public void testSAMLInboundAuthenticationPost() {
        try {

            String requestRelayState = "6c72a926-119d-4b4d-b236-f7594a037b0e";

            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, TestConstants.SAMPLE_ISSUER_NAME, TestConstants.ACS_URL);

            SAML2AuthUtils.setSignature(samlRequest, "http://www.w3.org/2000/09/xmldsig#rsa-sha1", "http://www.w3" +
                    ".org/2000/09/xmldsig#sha1", true, SAML2AuthUtils.getServerCredentials());

            String authnRequest = SAML2AuthUtils.encodeForPost((SAML2AuthUtils.marshall(samlRequest)));
            authnRequest = URLEncoder.encode(authnRequest);
            String postBody = TestConstants.SAML_REQUEST_PARAM + "=" + authnRequest + TestConstants
                    .QUERY_PARAM_SEPARATOR + TestConstants
                    .RELAY_STATE + "=" + requestRelayState;

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                    , HttpMethod.POST, true);
            urlConnection.setDoOutput(true);
            urlConnection.getOutputStream().write(postBody.toString().getBytes(Charsets.UTF_8));
            String locationHeader = TestUtils.getResponseHeader(HttpHeaders.LOCATION, urlConnection);
            Assert.assertTrue(locationHeader.contains(TestConstants.RELAY_STATE));
            Assert.assertTrue(locationHeader.contains(TestConstants.EXTERNAL_IDP));

            String relayState = locationHeader.split(TestConstants.RELAY_STATE + "=")[1];
            relayState = relayState.split(TestConstants.QUERY_PARAM_SEPARATOR)[0];

            urlConnection = TestUtils.request
                    (TestConstants.GATEWAY_ENDPOINT + "?" + TestConstants.RELAY_STATE + "=" +
                     relayState + "&" + TestConstants.ASSERTION + "=" +
                     TestConstants.AUTHENTICATED_USER_NAME, HttpMethod.GET, false);

            String cookie = TestUtils.getResponseHeader(HttpHeaders.SET_COOKIE, urlConnection);
            cookie = cookie.split(org.wso2.carbon.identity.gateway.common.util.Constants.GATEWAY_COOKIE + "=")[1];
            Assert.assertNotNull(cookie);
        } catch (IOException e) {
            Assert.fail("Error while running testSAMLInboundAuthenticationPost test case", e);
        }
    }

    /**
     * Testing the content of the SAML response.
     */
    @Test
    public void testSAMLResponse() {
        try {

            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, TestConstants.SAMPLE_ISSUER_NAME, TestConstants.ACS_URL);
            String samlRequestString = SAML2AuthUtils.encodeForRedirect(samlRequest);
            SAML2AuthUtils.encodeForPost(SAML2AuthUtils.marshall(samlRequest));

            StringBuilder httpQueryString = new StringBuilder(SAML2AuthConstants.SAML_REQUEST + "=" + samlRequestString);
            httpQueryString.append("&" + SAML2AuthConstants.RELAY_STATE + "=" + URLEncoder.encode("relayState",
                    StandardCharsets.UTF_8.name()).trim());
            SAML2AuthUtils.addSignatureToHTTPQueryString(httpQueryString, "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
                    SAML2AuthUtils.getServerCredentials());

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                                                                + "?" + httpQueryString.toString(), HttpMethod.GET, false);
            String locationHeader = TestUtils.getResponseHeader(HttpHeaders.LOCATION, urlConnection);
            Assert.assertTrue(locationHeader.contains(TestConstants.RELAY_STATE));
            Assert.assertTrue(locationHeader.contains(TestConstants.EXTERNAL_IDP));

            String relayState = locationHeader.split(TestConstants.RELAY_STATE + "=")[1];
            relayState = relayState.split(TestConstants.QUERY_PARAM_SEPARATOR)[0];

            urlConnection = TestUtils.request
                    (TestConstants.GATEWAY_ENDPOINT + "?" + TestConstants.RELAY_STATE + "=" +
                     relayState + "&" + TestConstants.ASSERTION + "=" +
                     TestConstants.AUTHENTICATED_USER_NAME, HttpMethod.GET, false);

            String cookie = TestUtils.getResponseHeader(HttpHeaders.SET_COOKIE, urlConnection);

            cookie = cookie.split(org.wso2.carbon.identity.gateway.common.util.Constants.GATEWAY_COOKIE + "=")[1];
            Assert.assertNotNull(cookie);
            String response = TestUtils.getContent(urlConnection);
            String samlResponse = response.split("SAMLResponse' value='")[1].split("'>")[0];
            try {
                Response samlResponseObject = TestUtils.getSAMLResponse(samlResponse);
                Assert.assertEquals(samlResponseObject.getAssertions().get(0).getSubject().getNameID().getValue(),
                                    TestConstants.AUTHENTICATED_USER_NAME);
            } catch (SAML2SSOServerException e) {
                Assert.fail("Error while building response object", e);
            }


        } catch (IOException e) {
            Assert.fail("Error while running testSAMLResponse test case", e);
        }
    }

    /**
     * Sending out a request with wrong signature and asserting on response.
     */
    @Test
    public void testSAMLResponseWithWrongSignature() {
        try {
            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                                                                + "?" + TestConstants.SAML_REQUEST_PARAM + "=" + TestConstants
                    .SAML_REQUEST_INVALID_SIGNATURE, HttpMethod.GET, false);
            Assert.assertEquals(urlConnection.getResponseCode(), 200);
            String response = TestUtils.getContent(urlConnection);
            Assert.assertNotNull(response);
            String samlResponse = response.split("SAMLResponse' value='")[1].split("'>")[0];
            Response samlResponseObject = TestUtils.getSAMLResponse(samlResponse);
            Assert.assertEquals(samlResponseObject.getAssertions().size(), 0);
        } catch (IOException e) {
            Assert.fail("Error while running testSAMLResponseWithWrongSignature test case", e);
        } catch (SAML2SSOServerException e) {
            Assert.fail("Error while building Response object from SAMLResponse message.", e);
        }
    }

    /**
     * Sending out an authentication request without issuer and assert on response.
     */
    @Test
    public void testSAMLResponseWithEmptyIssuer() {
        try {
            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, "", TestConstants.ACS_URL);
            String samlRequestString = SAML2AuthUtils.encodeForRedirect(samlRequest);
            SAML2AuthUtils.encodeForPost(SAML2AuthUtils.marshall(samlRequest));

            StringBuilder httpQueryString = new StringBuilder(SAML2AuthConstants.SAML_REQUEST + "=" + samlRequestString);
            httpQueryString.append("&" + SAML2AuthConstants.RELAY_STATE + "=" + URLEncoder.encode("relayState",
                    StandardCharsets.UTF_8.name()).trim());
            SAML2AuthUtils.addSignatureToHTTPQueryString(httpQueryString, "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
                    SAML2AuthUtils.getServerCredentials());

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                                                                + "?" + httpQueryString.toString(), HttpMethod.GET, false);

            Assert.assertEquals(urlConnection.getResponseCode(), 200);
            String response = TestUtils.getContent(urlConnection);
            Assert.assertNotNull(response);
            String samlResponse = response.split("SAMLResponse' value='")[1].split("'>")[0];
            Response samlResponseObject = TestUtils.getSAMLResponse(samlResponse);
            Assert.assertEquals(samlResponseObject.getAssertions().size(), 0);

        } catch (IOException e) {
            Assert.fail("Error while running testSAMLResponseWithWrongSignature test case", e);
        } catch (SAML2SSOServerException e) {
            Assert.fail("Error while building Response object from SAMLResponse message.", e);
        }
    }

    /**
     * Sending out a request with non existing issuer
     */
    @Test
    public void testSAMLResponseWithWrongIssuer() {
        try {

            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, "NonExistingIssuer", TestConstants.ACS_URL);
            String samlRequestString = SAML2AuthUtils.encodeForRedirect(samlRequest);
            SAML2AuthUtils.encodeForPost(SAML2AuthUtils.marshall(samlRequest));

            StringBuilder httpQueryString = new StringBuilder(SAML2AuthConstants.SAML_REQUEST + "=" + samlRequestString);
            httpQueryString.append("&" + SAML2AuthConstants.RELAY_STATE + "=" + URLEncoder.encode("relayState",
                    StandardCharsets.UTF_8.name()).trim());
            SAML2AuthUtils.addSignatureToHTTPQueryString(httpQueryString, "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
                    SAML2AuthUtils.getServerCredentials());

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                                                                + "?" + httpQueryString.toString(), HttpMethod.GET, false);

            Assert.assertEquals(urlConnection.getResponseCode(), 200);
            String response = TestUtils.getContent(urlConnection);
            Assert.assertNotNull(response);
            String samlResponse = response.split("SAMLResponse' value='")[1].split("'>")[0];
            Response samlResponseObject = TestUtils.getSAMLResponse(samlResponse);
            Assert.assertEquals(samlResponseObject.getAssertions().size(), 0);
            String location = response.split("post' action='")[1].split("'>")[0];
            Assert.assertTrue(location.contains("notifications"));

        } catch (IOException e) {
            Assert.fail("Error while running testSAMLResponseWithWrongIssuer test case", e);
        } catch (SAML2SSOServerException e) {
            Assert.fail("Error while building Response object from SAMLResponse message.", e);
        }
    }

    /**
     * Enable assertion encryption and see whether we are getting back an encrypted assertion.
     */
    @Test
    public void testEnableAssertionEncryption() {
        ServiceProviderConfig serviceProviderConfig = TestUtils.getServiceProviderConfigs
                (TestConstants.SAMPLE_ISSUER_NAME, bundleContext);
        serviceProviderConfig.getResponseBuildingConfig().getResponseBuilderConfigs().get(0).getProperties()
                .setProperty(SAML2AuthConstants.Config.Name.AUTHN_RESPONSE_ENCRYPTED, "true");
        try {

            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, TestConstants.SAMPLE_ISSUER_NAME, TestConstants.ACS_URL);
            String samlRequestString = SAML2AuthUtils.encodeForRedirect(samlRequest);
            SAML2AuthUtils.encodeForPost(SAML2AuthUtils.marshall(samlRequest));

            StringBuilder httpQueryString = new StringBuilder(SAML2AuthConstants.SAML_REQUEST + "=" + samlRequestString);
            httpQueryString.append("&" + SAML2AuthConstants.RELAY_STATE + "=" + URLEncoder.encode("relayState",
                    StandardCharsets.UTF_8.name()).trim());
            SAML2AuthUtils.addSignatureToHTTPQueryString(httpQueryString, "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
                    SAML2AuthUtils.getServerCredentials());

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                                                                + "?" + httpQueryString.toString(), HttpMethod.GET, false);
            String locationHeader = TestUtils.getResponseHeader(HttpHeaders.LOCATION, urlConnection);
            Assert.assertTrue(locationHeader.contains(TestConstants.RELAY_STATE));
            Assert.assertTrue(locationHeader.contains(TestConstants.EXTERNAL_IDP));

            String relayState = locationHeader.split(TestConstants.RELAY_STATE + "=")[1];
            relayState = relayState.split(TestConstants.QUERY_PARAM_SEPARATOR)[0];

            urlConnection = TestUtils.request
                    (TestConstants.GATEWAY_ENDPOINT + "?" + TestConstants.RELAY_STATE + "=" +
                     relayState + "&" + TestConstants.ASSERTION + "=" +
                     TestConstants.AUTHENTICATED_USER_NAME, HttpMethod.GET, false);

            String cookie = TestUtils.getResponseHeader(HttpHeaders.SET_COOKIE, urlConnection);

            cookie = cookie.split(org.wso2.carbon.identity.gateway.common.util.Constants.GATEWAY_COOKIE + "=")[1];
            Assert.assertNotNull(cookie);
            String response = TestUtils.getContent(urlConnection);

            String samlResponse = response.split("SAMLResponse' value='")[1].split("'>")[0];
            try {
                Response samlResponseObject = TestUtils.getSAMLResponse(samlResponse);
                Assert.assertTrue(samlResponseObject.getAssertions().isEmpty());
                Assert.assertTrue(samlResponseObject.getEncryptedAssertions().size() > 0);
            } catch (SAML2SSOServerException e) {
                Assert.fail("Error while asserting on encrypted assertions test case", e);

            }
        } catch (IOException e) {
            Assert.fail("Error while asserting on encrypted assertions test case", e);
        } finally {
            serviceProviderConfig.getResponseBuildingConfig().getResponseBuilderConfigs().get(0).getProperties()
                    .setProperty(SAML2AuthConstants.Config.Name.AUTHN_RESPONSE_ENCRYPTED, "false");
        }
    }

    /**
     * Test response signing disabled.
     */
    @Test
    public void testSAMLResponseSigningDisabled() {
        try {
            ServiceProviderConfig serviceProviderConfig = TestUtils.getServiceProviderConfigs
                    (TestConstants.SAMPLE_ISSUER_NAME, bundleContext);
            serviceProviderConfig.getResponseBuildingConfig().getResponseBuilderConfigs().get(0).getProperties()
                    .setProperty(SAML2AuthConstants.Config.Name.AUTHN_RESPONSE_SIGNED, "false");

            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, TestConstants.SAMPLE_ISSUER_NAME, TestConstants.ACS_URL);
            String samlRequestString = SAML2AuthUtils.encodeForRedirect(samlRequest);
            SAML2AuthUtils.encodeForPost(SAML2AuthUtils.marshall(samlRequest));

            StringBuilder httpQueryString = new StringBuilder(SAML2AuthConstants.SAML_REQUEST + "=" + samlRequestString);
            httpQueryString.append("&" + SAML2AuthConstants.RELAY_STATE + "=" + URLEncoder.encode("relayState",
                    StandardCharsets.UTF_8.name()).trim());
            SAML2AuthUtils.addSignatureToHTTPQueryString(httpQueryString, "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
                    SAML2AuthUtils.getServerCredentials());

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                                                                + "?" + httpQueryString.toString(), HttpMethod.GET, false);
            String locationHeader = TestUtils.getResponseHeader(HttpHeaders.LOCATION, urlConnection);
            Assert.assertTrue(locationHeader.contains(TestConstants.RELAY_STATE));
            Assert.assertTrue(locationHeader.contains(TestConstants.EXTERNAL_IDP));

            String relayState = locationHeader.split(TestConstants.RELAY_STATE + "=")[1];
            relayState = relayState.split(TestConstants.QUERY_PARAM_SEPARATOR)[0];

            urlConnection = TestUtils.request
                    (TestConstants.GATEWAY_ENDPOINT + "?" + TestConstants.RELAY_STATE + "=" +
                     relayState + "&" + TestConstants.ASSERTION + "=" +
                     TestConstants.AUTHENTICATED_USER_NAME, HttpMethod.GET, false);

            String cookie = TestUtils.getResponseHeader(HttpHeaders.SET_COOKIE, urlConnection);
            cookie = cookie.split(org.wso2.carbon.identity.gateway.common.util.Constants.GATEWAY_COOKIE + "=")[1];
            Assert.assertNotNull(cookie);
            String response = TestUtils.getContent(urlConnection);
            String samlResponse = response.split("SAMLResponse' value='")[1].split("'>")[0];
            try {
                Response samlResponseObject = TestUtils.getSAMLResponse(samlResponse);
                Assert.assertEquals(TestConstants.AUTHENTICATED_USER_NAME, samlResponseObject
                        .getAssertions().get(0).getSubject().getNameID().getValue());
                Assert.assertNull(samlResponseObject.getSignature());
            } catch (SAML2SSOServerException e) {
                Assert.fail("Error while building response object", e);
            }


        } catch (IOException e) {
            Assert.fail("Error while running federated authentication test case", e);
        }
    }

    /**
     * Test whether we are getting back signature when the signing is enabled.
     */
    @Test
    public void testSAMLResponseSigningEnabled() {
        try {
            ServiceProviderConfig serviceProviderConfig = TestUtils.getServiceProviderConfigs
                    (TestConstants.SAMPLE_ISSUER_NAME, bundleContext);
            serviceProviderConfig.getResponseBuildingConfig().getResponseBuilderConfigs().get(0).getProperties()
                    .setProperty(SAML2AuthConstants.Config.Name.AUTHN_RESPONSE_SIGNED, "true");

            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, TestConstants.SAMPLE_ISSUER_NAME, TestConstants.ACS_URL);
            String samlRequestString = SAML2AuthUtils.encodeForRedirect(samlRequest);
            SAML2AuthUtils.encodeForPost(SAML2AuthUtils.marshall(samlRequest));

            StringBuilder httpQueryString = new StringBuilder(SAML2AuthConstants.SAML_REQUEST + "=" + samlRequestString);
            httpQueryString.append("&" + SAML2AuthConstants.RELAY_STATE + "=" + URLEncoder.encode("relayState",
                    StandardCharsets.UTF_8.name()).trim());
            SAML2AuthUtils.addSignatureToHTTPQueryString(httpQueryString, "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
                    SAML2AuthUtils.getServerCredentials());

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT + "?" +
                                                                httpQueryString.toString(), HttpMethod.GET,
                                                                false);
            String locationHeader = TestUtils.getResponseHeader(HttpHeaders.LOCATION, urlConnection);
            Assert.assertTrue(locationHeader.contains(TestConstants.RELAY_STATE));
            Assert.assertTrue(locationHeader.contains(TestConstants.EXTERNAL_IDP));

            String relayState = locationHeader.split(TestConstants.RELAY_STATE + "=")[1];
            relayState = relayState.split(TestConstants.QUERY_PARAM_SEPARATOR)[0];

            urlConnection = TestUtils.request
                    (TestConstants.GATEWAY_ENDPOINT + "?" + TestConstants.RELAY_STATE + "=" + relayState +
                     "&" + TestConstants.ASSERTION + "=" +
                     TestConstants.AUTHENTICATED_USER_NAME, HttpMethod.GET, false);

            String cookie = TestUtils.getResponseHeader(HttpHeaders.SET_COOKIE, urlConnection);

            cookie = cookie.split(org.wso2.carbon.identity.gateway.common.util.Constants.GATEWAY_COOKIE + "=")[1];
            Assert.assertNotNull(cookie);
            String response = TestUtils.getContent(urlConnection);

            String samlResponse = response.split("SAMLResponse' value='")[1].split("'>")[0];
            try {
                Response samlResponseObject = TestUtils.getSAMLResponse(samlResponse);
                Assert.assertEquals(TestConstants.AUTHENTICATED_USER_NAME, samlResponseObject
                        .getAssertions().get(0).getSubject().getNameID().getValue());
                Assert.assertNotNull(samlResponseObject.getSignature());
            } catch (SAML2SSOServerException e) {
                Assert.fail("Error while building response object from SAML response string", e);
            }


        } catch (IOException e) {
            Assert.fail("Error while running federated authentication test case", e);
        }
    }

    /**
     * Assert on error response
     * Added this as a unit test since bootstrapping is needed.
     */
//    @Test
    public void testHandleException() {
//        try {
//            DefaultBootstrap.bootstrap();
//            String errorResponse = Utils.SAMLResponseUtil.buildErrorResponse("ErrorStatus", "ErrorMessage",
//                                                                             "https://localhost:9292/error");
//            Assert.assertNotNull(errorResponse);
//        } catch (ConfigurationException e) {
//            Assert.fail("Error while bootstrapping opensaml");
//        }

    }

    /**
     * SAML request without signature validation turned on.
     */
    @Test
    public void testSAMLAssertionWithoutRequestSignatureValidation() {
        ServiceProviderConfig serviceProviderConfig = TestUtils.getServiceProviderConfigs
                (TestConstants.SAMPLE_ISSUER_NAME, bundleContext);
        try {
            serviceProviderConfig.getRequestValidationConfig().getRequestValidatorConfigs().get(0).getProperties()
                    .setProperty(SAML2AuthConstants.Config.Name.AUTHN_REQUEST_SIGNED, "false");

            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, TestConstants.SAMPLE_ISSUER_NAME, TestConstants.ACS_URL);
            String samlRequestString = SAML2AuthUtils.encodeForRedirect(samlRequest);
            SAML2AuthUtils.encodeForPost(SAML2AuthUtils.marshall(samlRequest));

            StringBuilder httpQueryString = new StringBuilder(SAML2AuthConstants.SAML_REQUEST + "=" + samlRequestString);
            httpQueryString.append("&" + SAML2AuthConstants.RELAY_STATE + "=" + URLEncoder.encode("relayState",
                    StandardCharsets.UTF_8.name()).trim());

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                                                                + "?" + httpQueryString.toString(), HttpMethod.GET, false);

            String locationHeader = TestUtils.getResponseHeader(HttpHeaders.LOCATION, urlConnection);
            Assert.assertTrue(locationHeader.contains(TestConstants.RELAY_STATE));
            Assert.assertTrue(locationHeader.contains(TestConstants.EXTERNAL_IDP));

            String relayState = locationHeader.split(TestConstants.RELAY_STATE + "=")[1];
            relayState = relayState.split(TestConstants.QUERY_PARAM_SEPARATOR)[0];

            urlConnection = TestUtils.request
                    (TestConstants.GATEWAY_ENDPOINT + "?" + TestConstants.RELAY_STATE + "=" +
                     relayState + "&" + TestConstants.ASSERTION + "=" +
                     TestConstants.AUTHENTICATED_USER_NAME, HttpMethod.GET, false);

            String cookie = TestUtils.getResponseHeader(HttpHeaders.SET_COOKIE, urlConnection);

            cookie = cookie.split(org.wso2.carbon.identity.gateway.common.util.Constants.GATEWAY_COOKIE + "=")[1];
            Assert.assertNotNull(cookie);
            String response = TestUtils.getContent(urlConnection);
            String samlResponse = response.split("SAMLResponse' value='")[1].split("'>")[0];
            try {
                Response samlResponseObject = TestUtils.getSAMLResponse(samlResponse);
                Assert.assertEquals(TestConstants.AUTHENTICATED_USER_NAME, samlResponseObject
                        .getAssertions().get(0).getSubject().getNameID().getValue());
            } catch (SAML2SSOServerException e) {
                log.error("Error while building response object from SAML response string", e);
            }

        } catch (IOException e) {
            Assert.fail("Error while running testSAMLAssertionWithoutRequestValidation test case");
        } finally {
            serviceProviderConfig.getRequestValidationConfig().getRequestValidatorConfigs().get(0).getProperties()
                    .setProperty(SAML2AuthConstants.Config.Name.AUTHN_REQUEST_SIGNED, "true");
        }
    }

    /**
     * SAML request without signature validation turned on.
     */
    @Test
    public void testSPInitSSOWithMinimumConfigs() {
        ServiceProviderConfig serviceProviderConfig = TestUtils.getServiceProviderConfigs
                (TestConstants.SAMPLE_ISSUER_NAME, bundleContext);
        Properties originalReqValidatorConfigs = serviceProviderConfig.getRequestValidationConfig()
                .getRequestValidatorConfigs().get(0).getProperties();
        Properties originalResponseBuilderConfigs = serviceProviderConfig.getResponseBuildingConfig()
                .getResponseBuilderConfigs().get(0).getProperties();
        try {
            Properties newReqValidatorConfigs = new Properties();
            Properties newResponseBuilderConfigs = new Properties();
            newReqValidatorConfigs.put(SAML2AuthConstants.Config.Name.SP_ENTITY_ID, originalReqValidatorConfigs
                    .get(SAML2AuthConstants.Config.Name.SP_ENTITY_ID));
            newReqValidatorConfigs.put(SAML2AuthConstants.Config.Name.DEFAULT_ASSERTION_CONSUMER_URL,
                    originalReqValidatorConfigs
                            .get(SAML2AuthConstants.Config.Name.DEFAULT_ASSERTION_CONSUMER_URL));

            newReqValidatorConfigs.put(SAML2AuthConstants.Config.Name.ASSERTION_CONSUMER_URLS,
                    originalReqValidatorConfigs.get(SAML2AuthConstants.Config.Name.ASSERTION_CONSUMER_URLS));


            serviceProviderConfig.getRequestValidationConfig().getRequestValidatorConfigs().get(0).setProperties
                    (newReqValidatorConfigs);
            serviceProviderConfig.getResponseBuildingConfig().getResponseBuilderConfigs().get(0).setProperties
                    (newResponseBuilderConfigs);

            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, TestConstants.SAMPLE_ISSUER_NAME, TestConstants.ACS_URL);
            String samlRequestString = SAML2AuthUtils.encodeForRedirect(samlRequest);
            SAML2AuthUtils.encodeForPost(SAML2AuthUtils.marshall(samlRequest));

            StringBuilder httpQueryString = new StringBuilder(SAML2AuthConstants.SAML_REQUEST + "=" + samlRequestString);
            httpQueryString.append("&" + SAML2AuthConstants.RELAY_STATE + "=" + URLEncoder.encode("relayState",
                    StandardCharsets.UTF_8.name()).trim());

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                    + "?" + httpQueryString.toString(), HttpMethod.GET, false);

            String locationHeader = TestUtils.getResponseHeader(HttpHeaders.LOCATION, urlConnection);
            Assert.assertTrue(locationHeader.contains(TestConstants.RELAY_STATE));
            Assert.assertTrue(locationHeader.contains(TestConstants.EXTERNAL_IDP));

            String relayState = locationHeader.split(TestConstants.RELAY_STATE + "=")[1];
            relayState = relayState.split(TestConstants.QUERY_PARAM_SEPARATOR)[0];

            urlConnection = TestUtils.request
                    (TestConstants.GATEWAY_ENDPOINT + "?" + TestConstants.RELAY_STATE + "=" +
                            relayState + "&" + TestConstants.ASSERTION + "=" +
                            TestConstants.AUTHENTICATED_USER_NAME, HttpMethod.GET, false);

            String cookie = TestUtils.getResponseHeader(HttpHeaders.SET_COOKIE, urlConnection);

            cookie = cookie.split(org.wso2.carbon.identity.gateway.common.util.Constants.GATEWAY_COOKIE + "=")[1];
            Assert.assertNotNull(cookie);
            String response = TestUtils.getContent(urlConnection);
            String samlResponse = response.split("SAMLResponse' value='")[1].split("'>")[0];
            try {
                Response samlResponseObject = TestUtils.getSAMLResponse(samlResponse);
                Assert.assertEquals(TestConstants.AUTHENTICATED_USER_NAME, samlResponseObject
                        .getAssertions().get(0).getSubject().getNameID().getValue());
            } catch (SAML2SSOServerException e) {
                log.error("Error while building response object from SAML response string", e);
            }

        } catch (IOException e) {
            Assert.fail("Error while running testSAMLAssertionWithoutRequestValidation test case");
        } finally {
            serviceProviderConfig.getRequestValidationConfig().getRequestValidatorConfigs().get(0).setProperties
                    (originalReqValidatorConfigs);
            serviceProviderConfig.getResponseBuildingConfig().getResponseBuilderConfigs().get(0).setProperties
                    (originalResponseBuilderConfigs);
        }
    }

    /**
     * SAML request with Wrong ACS in request.
     */
    @Test
    public void testWithWrongACS() {
        ServiceProviderConfig serviceProviderConfig = TestUtils.getServiceProviderConfigs
                (TestConstants.SAMPLE_ISSUER_NAME, bundleContext);
        Properties originalReqValidatorConfigs = serviceProviderConfig.getRequestValidationConfig()
                .getRequestValidatorConfigs().get(0).getProperties();
        Properties originalResponseBuilderConfigs = serviceProviderConfig.getResponseBuildingConfig()
                .getResponseBuilderConfigs().get(0).getProperties();
        try {
            Properties newReqValidatorConfigs = new Properties();
            Properties newResponseBuilderConfigs = new Properties();
            newReqValidatorConfigs.put(SAML2AuthConstants.Config.Name.SP_ENTITY_ID, originalReqValidatorConfigs
                    .get(SAML2AuthConstants.Config.Name.SP_ENTITY_ID));
            newReqValidatorConfigs.put(SAML2AuthConstants.Config.Name.DEFAULT_ASSERTION_CONSUMER_URL,
                    originalReqValidatorConfigs
                            .get(SAML2AuthConstants.Config.Name.DEFAULT_ASSERTION_CONSUMER_URL));

            newReqValidatorConfigs.put(SAML2AuthConstants.Config.Name.ASSERTION_CONSUMER_URLS,
                    originalReqValidatorConfigs.get(SAML2AuthConstants.Config.Name.ASSERTION_CONSUMER_URLS));


            serviceProviderConfig.getRequestValidationConfig().getRequestValidatorConfigs().get(0).setProperties
                    (newReqValidatorConfigs);
            serviceProviderConfig.getResponseBuildingConfig().getResponseBuilderConfigs().get(0).setProperties
                    (newResponseBuilderConfigs);

            AuthnRequest samlRequest = TestUtils.buildAuthnRequest("https://localhost:9292/gateway",
                    false, false, TestConstants.SAMPLE_ISSUER_NAME, "https://localhost:8080/wrongACS");
            String samlRequestString = SAML2AuthUtils.encodeForRedirect(samlRequest);
            SAML2AuthUtils.encodeForPost(SAML2AuthUtils.marshall(samlRequest));

            StringBuilder httpQueryString = new StringBuilder(SAML2AuthConstants.SAML_REQUEST + "=" + samlRequestString);
            httpQueryString.append("&" + SAML2AuthConstants.RELAY_STATE + "=" + URLEncoder.encode("relayState",
                    StandardCharsets.UTF_8.name()).trim());

            HttpURLConnection urlConnection = TestUtils.request(TestConstants.GATEWAY_ENDPOINT
                    + "?" + httpQueryString.toString(), HttpMethod.GET, false);

            String response = TestUtils.getContent(urlConnection);
            String samlResponse = response.split("SAMLResponse' value='")[1].split("'>")[0];
            try {
                Response samlResponseObject = TestUtils.getSAMLResponse(samlResponse);
                Assert.assertEquals(samlResponseObject.getStatus().getStatusMessage().getMessage()
                        , "Invalid Assertion Consumer Service URL in the AuthnRequest message.");
            } catch (SAML2SSOServerException e) {
                log.error("Error while building response object from SAML response string", e);
            }

        } catch (IOException e) {
            Assert.fail("Error while running testSAMLAssertionWithoutRequestValidation test case");
        } finally {
            serviceProviderConfig.getRequestValidationConfig().getRequestValidatorConfigs().get(0).setProperties
                    (originalReqValidatorConfigs);
            serviceProviderConfig.getResponseBuildingConfig().getResponseBuilderConfigs().get(0).setProperties
                    (originalResponseBuilderConfigs);
        }
    }
    /**
     * Test error responses
     */
//    @Test
    public void testSAMLResponseBuilderFactory() {
//        SPInitResponseHandler responseHandler = new SPInitResponseHandler();
//        AuthenticationContext authenticationContext = new AuthenticationContext(null);
//        MessageContext messageContext = new MessageContext(null, null);
//        SPInitRequest.SAMLSpInitRequestBuilder spInitRequestBuilder = new SPInitRequest
//                .SAMLSpInitRequestBuilder();
//
//        SAML2SSORequest samlRequest = new SPInitRequest(spInitRequestBuilder);
//        messageContext.setIdentityRequest(samlRequest);
//        messageContext.setPassive(true);
//        authenticationContext.addParameter(SAML2AuthConstants.SAML_CONTEXT, messageContext);
//        authenticationContext.setUniqueId(SAMLInboundTestConstants.SAMPLE_ISSUER_NAME);
//        try {
//            GatewayHandlerResponse response = responseHandler.buildErrorResponse(authenticationContext, new
//                    GatewayException("GatewayException"));
//            Assert.assertNotNull(response);
//            Assert.assertNotNull(response.getGatewayResponseBuilder());
//            SAML2SSOResponse.SAML2SSOResponseBuilder samlLoginResponseBuilder = (SAML2SSOResponse
//                    .SAML2SSOResponseBuilder) response
//                    .getGatewayResponseBuilder();
//            Assert.assertNotNull(samlLoginResponseBuilder.build());
//        } catch (ResponseHandlerException e) {
//            Assert.fail("Error while building error response", e);
//        }
    }
}
