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
package ee.ria.xroad.commonui;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.PasswordStore;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;
import ee.ria.xroad.signer.protocol.message.ActivateCert;
import ee.ria.xroad.signer.protocol.message.ActivateToken;
import ee.ria.xroad.signer.protocol.message.DeleteCert;
import ee.ria.xroad.signer.protocol.message.DeleteCertRequest;
import ee.ria.xroad.signer.protocol.message.DeleteKey;
import ee.ria.xroad.signer.protocol.message.GenerateCertRequest;
import ee.ria.xroad.signer.protocol.message.GenerateCertRequestResponse;
import ee.ria.xroad.signer.protocol.message.GenerateKey;
import ee.ria.xroad.signer.protocol.message.GenerateSelfSignedCert;
import ee.ria.xroad.signer.protocol.message.GenerateSelfSignedCertResponse;
import ee.ria.xroad.signer.protocol.message.GetCertificateInfoForHash;
import ee.ria.xroad.signer.protocol.message.GetCertificateInfoResponse;
import ee.ria.xroad.signer.protocol.message.GetKeyIdForCertHash;
import ee.ria.xroad.signer.protocol.message.GetKeyIdForCertHashResponse;
import ee.ria.xroad.signer.protocol.message.GetTokenInfo;
import ee.ria.xroad.signer.protocol.message.GetTokenInfoAndKeyIdForCertHash;
import ee.ria.xroad.signer.protocol.message.ImportCert;
import ee.ria.xroad.signer.protocol.message.ImportCertResponse;
import ee.ria.xroad.signer.protocol.message.InitSoftwareToken;
import ee.ria.xroad.signer.protocol.message.ListTokens;
import ee.ria.xroad.signer.protocol.message.SetCertStatus;
import ee.ria.xroad.signer.protocol.message.SetKeyFriendlyName;
import ee.ria.xroad.signer.protocol.message.SetTokenFriendlyName;

import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;

/**
 * Responsible for managing cryptographic tokens (smartcards, HSMs, etc.) through the signer.
 */
@Slf4j
public final class SignerProxy {

    private SignerProxy() {
    }

    public static final String SSL_TOKEN_ID = "0";

    /**
     * Initialize the software token with the given password.
     * @param password software token password
     * @throws Exception if any errors occur
     */
    public static void initSoftwareToken(char[] password) throws Exception {
        log.trace("Initializing software token");

        execute(new InitSoftwareToken(password));
    }

    /**
     * Gets information about all configured tokens.
     * @return a List of TokenInfo objects
     * @throws Exception if any errors occur
     */
    public static List<TokenInfo> getTokens() throws Exception {
        return execute(new ListTokens());
    }

    /**
     * Gets information about the token with the specified token ID.
     * @param tokenId ID of the token
     * @return TokenInfo
     * @throws Exception if any errors occur
     */
    public static TokenInfo getToken(String tokenId) throws Exception {
        return execute(new GetTokenInfo(tokenId));
    }

    /**
     * Activates the token with the given ID using the provided password.
     * @param tokenId ID of the token
     * @param password token password
     * @throws Exception if any errors occur
     */
    public static void activateToken(String tokenId, char[] password) throws Exception {
        PasswordStore.storePassword(tokenId, password);

        log.trace("Activating token '{}'", tokenId);

        execute(new ActivateToken(tokenId, true));
    }

    /**
     * Deactivates the token with the given ID.
     * @param tokenId ID of the token
     * @throws Exception if any errors occur
     */
    public static void deactivateToken(String tokenId) throws Exception {
        PasswordStore.storePassword(tokenId, null);

        log.trace("Deactivating token '{}'", tokenId);

        execute(new ActivateToken(tokenId, false));
    }

    /**
     * Sets the friendly name of the token with the given ID.
     * @param tokenId ID of the token
     * @param friendlyName new friendly name of the token
     * @throws Exception if any errors occur
     */
    public static void setTokenFriendlyName(String tokenId, String friendlyName) throws Exception {
        log.trace("Setting friendly name '{}' for token '{}'", friendlyName, tokenId);

        execute(new SetTokenFriendlyName(tokenId, friendlyName));
    }

    /**
     * Sets the friendly name of the key with the given ID.
     * @param keyId ID of the key
     * @param friendlyName new friendly name of the key
     * @throws Exception if any errors occur
     */
    public static void setKeyFriendlyName(String keyId, String friendlyName) throws Exception {
        log.trace("Setting friendly name '{}' for key '{}'", friendlyName, keyId);

        execute(new SetKeyFriendlyName(keyId, friendlyName));
    }

    /**
     * Generate a new key for the token with the given ID.
     * @param tokenId ID of the token
     * @param keyLabel label of the key
     * @return generated key KeyInfo object
     * @throws Exception if any errors occur
     */
    public static KeyInfo generateKey(String tokenId, String keyLabel) throws Exception {
        log.trace("Generating key for token '{}'", tokenId);

        KeyInfo keyInfo = execute(new GenerateKey(tokenId, keyLabel));

        log.trace("Received key with keyId '{}' and public key '{}'", keyInfo.getId(), keyInfo.getPublicKey());

        return keyInfo;
    }

    /**
     * Generate a self-signed certificate for the key with the given ID.
     * @param keyId ID of the key
     * @param memberId client ID of the certificate owner
     * @param keyUsage specifies whether the certificate is for signing or authentication
     * @param commonName common name of the certificate
     * @param notBefore date the certificate becomes valid
     * @param notAfter date the certificate becomes invalid
     * @return byte content of the generated certificate
     * @throws Exception if any errors occur
     */
    public static byte[] generateSelfSignedCert(String keyId, ClientId memberId, KeyUsageInfo keyUsage,
            String commonName, Date notBefore, Date notAfter) throws Exception {
        log.trace("Generate self-signed cert for key '{}'", keyId);

        GenerateSelfSignedCertResponse response = execute(new GenerateSelfSignedCert(keyId, commonName,
                notBefore, notAfter, keyUsage, memberId));

        byte[] certificateBytes = response.getCertificateBytes();

        log.trace("Certificate with length of {} bytes generated", certificateBytes.length);

        return certificateBytes;
    }

    /**
     * Imports the given byte array as a new certificate with the provided initial status.
     * @param certBytes byte content of the new certificate
     * @param initialStatus initial status of the certificate
     * @return key ID of the new certificate as a String
     * @throws Exception if any errors occur
     */
    public static String importCert(byte[] certBytes, String initialStatus) throws Exception {
        return importCert(certBytes, initialStatus, null);
    }

    /**
     * Imports the given byte array as a new certificate with the provided initial status and owner client ID.
     * @param certBytes byte content of the new certificate
     * @param initialStatus initial status of the certificate
     * @param clientId client ID of the certificate owner
     * @return key ID of the new certificate as a String
     * @throws Exception if any errors occur
     */
    public static String importCert(byte[] certBytes, String initialStatus, ClientId clientId) throws Exception {
        log.trace("Importing cert from file with length of '{}' bytes", certBytes.length);

        ImportCertResponse response = execute(new ImportCert(certBytes, initialStatus, clientId));

        log.trace("Cert imported successfully, keyId received: {}", response.getKeyId());

        return response.getKeyId();
    }

    /**
     * Activates the certificate with the given ID.
     * @param certId ID of the certificate
     * @throws Exception if any errors occur
     */
    public static void activateCert(String certId) throws Exception {
        log.trace("Activating cert '{}'", certId);

        execute(new ActivateCert(certId, true));
    }

    /**
     * Deactivates the certificate with the given ID.
     * @param certId ID of the certificate
     * @throws Exception if any errors occur
     */
    public static void deactivateCert(String certId) throws Exception {
        log.trace("Deactivating cert '{}'", certId);

        execute(new ActivateCert(certId, false));
    }

    /**
     * Generates a certificate request for the given key and with provided parameters.
     * @param keyId ID of the key
     * @param memberId client ID of the certificate owner
     * @param keyUsage specifies whether the certificate is for signing or authentication
     * @param subjectName subject name of the certificate
     * @param format the format of the request
     * @return byte content of the certificate request
     * @throws Exception if any errors occur
     */
    public static byte[] generateCertRequest(String keyId, ClientId memberId, KeyUsageInfo keyUsage, String subjectName,
            GenerateCertRequest.RequestFormat format) throws Exception {
        GenerateCertRequestResponse response = execute(new GenerateCertRequest(keyId, memberId, keyUsage, subjectName,
                format));

        byte[] certRequestBytes = response.getCertRequest();

        log.trace("Cert request with length of {} bytes generated", certRequestBytes.length);

        return certRequestBytes;
    }

    /**
     * Delete the certificate request with the given ID.
     * @param certRequestId ID of the certificate request
     * @throws Exception if any errors occur
     */
    public static void deleteCertRequest(String certRequestId) throws Exception {
        log.trace("Deleting cert request '{}'", certRequestId);

        execute(new DeleteCertRequest(certRequestId));
    }

    /**
     * Delete the certificate with the given ID.
     * @param certId ID of the certificate
     * @throws Exception if any errors occur
     */
    public static void deleteCert(String certId) throws Exception {
        log.trace("Deleting cert '{}'", certId);

        execute(new DeleteCert(certId));
    }

    /**
     * Delete the key with the given ID from the signer database. Optionally,
     * deletes it from the token as well.
     * @param keyId ID of the certificate request
     * @param deleteFromToken whether the key should be deleted from the token
     * @throws Exception if any errors occur
     */
    public static void deleteKey(String keyId, boolean deleteFromToken) throws Exception {
        log.trace("Deleting key '{}', from token = ", keyId, deleteFromToken);

        execute(new DeleteKey(keyId, deleteFromToken));
    }

    /**
     * Sets the status of the certificate with the given ID.
     * @param certId ID of the certificate
     * @param status new status of the certificate
     * @throws Exception if any errors occur
     */
    public static void setCertStatus(String certId, String status) throws Exception {
        log.trace("Setting cert ('{}') status to '{}'", certId, status);

        execute(new SetCertStatus(certId, status));
    }

    /**
     * Get a cert by it's hash
     * @param hash cert hash. Must be lowerCase!
     * @return CertificateInfo
     * @throws Exception
     */
    public static CertificateInfo getCertForHash(String hash) throws Exception {
        log.trace("Getting cert by hash '{}'", hash);
        checkLowerCase(hash);

        GetCertificateInfoResponse response = execute(new GetCertificateInfoForHash(hash));
        CertificateInfo certificateInfo = response.getCertificateInfo();

        log.trace("Cert with hash '{}' found", hash);

        return certificateInfo;
    }

    /**
     * Get key for a given cert hash
     * @param hash cert hash. Must be lowerCase!
     * @return CertificateInfo
     * @throws Exception
     */
    public static String getKeyIdForCerthash(String hash) throws Exception {
        log.trace("Getting cert by hash '{}'", hash);
        checkLowerCase(hash);

        GetKeyIdForCertHashResponse response = execute(new GetKeyIdForCertHash(hash));
        String keyId = response.getKeyId();

        log.trace("Cert with hash '{}' found", hash);

        return keyId;
    }

    /**
     * Get TokenInfoAndKeyId for a given cert hash
     * @param hash cert hash. Must be lowerCase!
     * @return TokenInfoAndKeyId
     * @throws Exception
     */
    public static TokenInfoAndKeyId getTokenAndKeyIdForCertHash(String hash) throws Exception {
        log.trace("Getting token and key id by cert hash '{}'", hash);
        checkLowerCase(hash);

        TokenInfoAndKeyId response = execute(new GetTokenInfoAndKeyIdForCertHash(hash));

        log.trace("Token and key id with hash '{}' found", hash);

        return response;
    }

    /**
     * @throws IllegalArgumentException if parameter was not a lowercase string
     */
    private static void checkLowerCase(String s) {
        if (s == null || !s.toLowerCase().equals(s)) {
            throw new IllegalArgumentException(s + " should be a lowerCase string");
        }
    }

    private static <T> T execute(Object message) throws Exception {
        return SignerClient.execute(message);
    }

}
