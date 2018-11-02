package io.choerodon.gateway.helper.api.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.gateway.helper.api.service.GetUserDetailsService;
import io.choerodon.gateway.helper.domain.CheckState;
import io.choerodon.gateway.helper.domain.CustomUserDetailsWithResult;
import io.choerodon.gateway.helper.infra.properties.HelperProperties;

@Service
public class GetUserDetailsServiceImpl implements GetUserDetailsService {


    private static final Logger LOGGER = LoggerFactory.getLogger(GetUserDetailsService.class);

    private static final String PRINCIPAL = "principal";

    private static final String OAUTH2REQUEST = "oauth2Request";

    private static final String ADDITION_INFO = "additionInfo";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;

    private HelperProperties helperProperties;

    public GetUserDetailsServiceImpl(RestTemplate restTemplate, HelperProperties helperProperties) {
        this.restTemplate = restTemplate;
        this.helperProperties = helperProperties;
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    @Cacheable(value = "user", key = "'choerodon:userdetails:'+#token", unless = "#result.customUserDetails == null")
    public CustomUserDetailsWithResult getUserDetails(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, token);
        HttpEntity<String> entity = new HttpEntity<>("", headers);
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(helperProperties.getOauthInfoUri(), HttpMethod.GET, entity, String.class);
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                CustomUserDetails userDetails = extractPrincipal(objectMapper.readValue(responseEntity.getBody(), Map.class));
                return new CustomUserDetailsWithResult(userDetails, CheckState.SUCCESS_PASS_SITE);
            } else {
                return new CustomUserDetailsWithResult(CheckState.PERMISSION_GET_USE_DETAIL_FAILED,
                        "Get customUserDetails error from oauth-server, token: " + token + " response: " + responseEntity);
            }
        } catch (RestClientException e) {
            LOGGER.warn("Get customUserDetails error from oauth-server, token: {}", token, e);
            return new CustomUserDetailsWithResult(CheckState.PERMISSION_ACCESS_TOKEN_EXPIRED,
                    "Access_token is expired or invalid, Please re-login and set correct access_token by HTTP header 'Authorization'");
        } catch (IOException e) {
            return new CustomUserDetailsWithResult(CheckState.EXCEPTION_GATEWAY_HELPER,
                    "Gateway helper error happened: " + e.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private CustomUserDetails extractPrincipal(Map<String, Object> map) {
        boolean isClientOnly = false;
        if (map.get(OAUTH2REQUEST) != null) {
            Map<String, Object> oauth2request = (Map) map.get(OAUTH2REQUEST);
            if (oauth2request.get("grantType").equals("client_credentials")) {
                isClientOnly = true;
            }
        }
        if (map.get(PRINCIPAL) != null) {
            map = (Map) map.get(PRINCIPAL);
        }
        if (map.containsKey("userId")) {
            CustomUserDetails user = new CustomUserDetails((String) map.get("username"),
                    "unknown password", Collections.emptyList());
            if(map.get("userId")!=null){
                user.setUserId((long) (Integer) map.get("userId"));
                user.setLanguage((String) map.get("language"));
                user.setAdmin((Boolean) map.get("admin"));
                user.setTimeZone((String) map.get("timeZone"));
                user.setOrganizationId((long) (Integer) map.get("organizationId"));
                if (map.get("email") != null) {
                    user.setEmail((String) map.get("email"));
                }
            }
            if (isClientOnly) {
                user.setClientId((long) (Integer) map.get("clientId"));
                user.setClientName((String) map.get("clientName"));
                user.setClientAccessTokenValiditySeconds((Integer) map.get("clientAccessTokenValiditySeconds"));
                user.setClientRefreshTokenValiditySeconds((Integer) map.get("clientRefreshTokenValiditySeconds"));
                user.setClientAuthorizedGrantTypes((Collection<String>) map.get("clientAuthorizedGrantTypes"));
                user.setClientAutoApproveScopes((Collection<String>) map.get("clientAutoApproveScopes"));
                user.setClientRegisteredRedirectUri((Collection<String>)map.get("clientRegisteredRedirectUri"));
                user.setClientResourceIds((Collection<String>) map.get("clientResourceIds"));
                user.setClientScope((Collection<String>) map.get("clientScope"));
            }
            try {
                if (map.get(ADDITION_INFO) != null) {
                    user.setAdditionInfo((Map) map.get(ADDITION_INFO));
                }
            } catch (Exception e) {
                LOGGER.warn("Parser addition info error:{}", e);
            }
            return user;
        }
        return null;
    }
}
