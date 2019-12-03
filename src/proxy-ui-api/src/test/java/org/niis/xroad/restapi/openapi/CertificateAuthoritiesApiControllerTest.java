/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.DnFieldValue;
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.openapi.model.KeyUsageType;
import org.niis.xroad.restapi.service.CertificateAuthorityService;
import org.niis.xroad.restapi.service.KeyService;
import org.niis.xroad.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * test cert auth api
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class CertificateAuthoritiesApiControllerTest {

    private static final String GOOD_SIGN_KEY_ID = "sign-key-which-exists";
    private static final String GOOD_AUTH_KEY_ID = "auth-key-which-exists";
    public static final String GENERAL_PURPOSE_CA_NAME = "fi-not-auth-only";

    @MockBean
    private KeyService keyService;

    @MockBean
    private CertificateAuthorityService certificateAuthorityService;

    @Autowired
    private CertificateAuthoritiesApiController caController;

    @Before
    public void setUp() throws Exception {
        KeyInfo signKeyInfo = TokenTestUtils.createTestKeyInfo(GOOD_SIGN_KEY_ID, KeyUsageInfo.SIGNING);
        KeyInfo authKeyInfo = TokenTestUtils.createTestKeyInfo(GOOD_AUTH_KEY_ID, KeyUsageInfo.AUTHENTICATION);
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String keyId = (String) args[0];
            if (keyId.equals(GOOD_AUTH_KEY_ID)) {
                return authKeyInfo;
            } else if (keyId.equals(GOOD_SIGN_KEY_ID)) {
                return signKeyInfo;
            } else {
                throw new KeyService.KeyNotFoundException("foo");
            }
        }).when(keyService).getKey(any());

        List<ApprovedCAInfo> approvedCAInfos = new ArrayList<>();
        approvedCAInfos.add(new ApprovedCAInfo(GENERAL_PURPOSE_CA_NAME, false,
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"));
        when(certificateAuthorityService.getCertificateAuthorities(any())).thenReturn(approvedCAInfos);
        when(certificateAuthorityService.getCertificateProfile(any(), any(), any()))
                .thenReturn(new CertificateProfileInfo() {
                    @Override
                    public DnFieldDescription[] getSubjectFields() {
                        return new DnFieldDescription[0];
                    }
                    @Override
                    public X500Principal createSubjectDn(DnFieldValue[] values) {
                        return null;
                    }
                    @Override
                    public void validateSubjectField(DnFieldValue field) throws Exception {
                    }
                });
    }

    @Test
    @WithMockUser(authorities = { "GENERATE_AUTH_CERT_REQ" })
    public void getApprovedCertificateAuthoritiesAuthWithAuthPermission() throws Exception {
        caController.getApprovedCertificateAuthorities(KeyUsageType.AUTHENTICATION);
        caController.getApprovedCertificateAuthorities(null);

        try {
            caController.getApprovedCertificateAuthorities(KeyUsageType.SIGNING);
            fail("should have thrown exception");
        } catch (AccessDeniedException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "GENERATE_SIGN_CERT_REQ" })
    public void getApprovedCertificateAuthoritiesAuthWithSignPermission() throws Exception {
        caController.getApprovedCertificateAuthorities(KeyUsageType.SIGNING);

        try {
            caController.getApprovedCertificateAuthorities(KeyUsageType.AUTHENTICATION);
            fail("should have thrown exception");
        } catch (AccessDeniedException expected) {
        }
        try {
            caController.getApprovedCertificateAuthorities(null);
            fail("should have thrown exception");
        } catch (AccessDeniedException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "GENERATE_AUTH_CERT_REQ" })
    public void getSubjectFieldDescriptionsAuthWithAuthPermission() throws Exception {
        caController.getSubjectFieldDescriptions(GENERAL_PURPOSE_CA_NAME, KeyUsageType.AUTHENTICATION,
                GOOD_AUTH_KEY_ID, "FI:GOV:M1");

        try {
            caController.getSubjectFieldDescriptions(GENERAL_PURPOSE_CA_NAME, KeyUsageType.SIGNING,
                    GOOD_SIGN_KEY_ID, "FI:GOV:M1");
            fail("should have thrown exception");
        } catch (AccessDeniedException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "GENERATE_SIGN_CERT_REQ" })
    public void getSubjectFieldDescriptionsAuthWithSignPermission() throws Exception {
        caController.getSubjectFieldDescriptions(GENERAL_PURPOSE_CA_NAME, KeyUsageType.SIGNING,
                GOOD_SIGN_KEY_ID, "FI:GOV:M1");

        try {
            caController.getSubjectFieldDescriptions(GENERAL_PURPOSE_CA_NAME, KeyUsageType.AUTHENTICATION,
                    GENERAL_PURPOSE_CA_NAME, "FI:GOV:M1");
            fail("should have thrown exception");
        } catch (AccessDeniedException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "GENERATE_SIGN_CERT_REQ", "GENERATE_AUTH_CERT_REQ" })
    public void getSubjectFieldsKeyIsOptional() throws Exception {
        caController.getSubjectFieldDescriptions(GENERAL_PURPOSE_CA_NAME, KeyUsageType.SIGNING,
                null, "FI:GOV:M1");
        caController.getSubjectFieldDescriptions(GENERAL_PURPOSE_CA_NAME, KeyUsageType.AUTHENTICATION,
                null, "FI:GOV:M1");
    }
}