package org.wso2.carbon.identity.saml.request;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.gateway.processor.request.ClientAuthenticationRequest;
import org.wso2.carbon.identity.saml.SAMLSSOConstants;
import org.wso2.msf4j.Request;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class SAMLGatewayRequest extends ClientAuthenticationRequest {

    private static Logger log = LoggerFactory.getLogger(SAMLGatewayRequest.class);


    public SAMLGatewayRequest(SAMLGatewayRequestBuilder builder) {
        super(builder);
    }

    public String getRelayState() {
        if (this.getParameter(SAMLSSOConstants.RELAY_STATE) != null) {
            return (String) this.getParameter(SAMLSSOConstants.RELAY_STATE);
        } else {
            try {
                String relayState = getParameter(SAMLSSOConstants.RELAY_STATE);
                if (StringUtils.isNotEmpty(relayState)) {
                   return URLDecoder.decode(this.getQueryParameter(SAMLSSOConstants.RELAY_STATE), StandardCharsets.UTF_8
                            .name());
                }
            } catch (UnsupportedEncodingException e) {
//                if (log.isDebugEnabled()) {
//                    log.debug("Failed to decode the Relay State ", e);
//                }
            }
        }
        return null;
    }

    public static class SAMLGatewayRequestBuilder extends ClientAuthenticationRequest.ClientAuthenticationRequestBuilder {
        public SAMLGatewayRequestBuilder(Request request) {
            super();
        }

        public SAMLGatewayRequestBuilder() {
            super();
        }
    }

    public boolean isRedirect() {
        return this.getHttpMethod() == SAMLSSOConstants.GET_METHOD;
    }
}
