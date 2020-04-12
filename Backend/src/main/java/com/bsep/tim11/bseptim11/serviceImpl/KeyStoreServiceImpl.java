package com.bsep.tim11.bseptim11.serviceImpl;

import com.bsep.tim11.bseptim11.dto.CertificateDTO;
import com.bsep.tim11.bseptim11.dto.EntityDTO;
import com.bsep.tim11.bseptim11.dto.ExtendedKeyUsageDTO;
import com.bsep.tim11.bseptim11.dto.KeyUsageDTO;
import com.bsep.tim11.bseptim11.enumeration.CertificateRole;
import com.bsep.tim11.bseptim11.service.KeyStoreService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

@Service
public class KeyStoreServiceImpl implements KeyStoreService {

    private final String ROOT_KEYSTORE = "root_keystore.pkcs12";
    private final String INTERMEDIATE_KEYSTORE = "intermediate_keystore.pkcs12";
    private final String END_ENTITY_KEYSTORE = "end_entity_keystore.pkcs12";

    @Override
    public void store(
            String keyStorePassword,
            String alias,
            PrivateKey privateKey,
            String keyPassword,
            Certificate certificate)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

        char[] keyPassArray = keyPassword.toCharArray();
        char[] keyStorePassArray = keyStorePassword.toCharArray();

        CertificateRole certificateRole = getCertificateRole(certificate);
        String keyStorePath = getKeyStorePath(certificateRole);

        KeyStore keyStore = getKeyStore(keyStorePath, keyStorePassword);
        keyStore.setKeyEntry(alias, privateKey, keyPassArray, new Certificate[] { certificate });
        keyStore.store(new FileOutputStream(keyStorePath), keyStorePassArray);
    }

    @Override
    public PrivateKey getPrivateKey(
            CertificateRole certificateRole,
            String keyStorePassword,
            String alias,
            String keyPassword)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
            UnrecoverableKeyException, IOException {

        char[] keyPassArray = keyPassword.toCharArray();
        String keyStorePath = getKeyStorePath(certificateRole);

        KeyStore keyStore = getKeyStore(keyStorePath, keyStorePassword);
        return (PrivateKey) keyStore.getKey(alias, keyPassArray);
    }

    @Override
    public PublicKey getPublicKey(
            CertificateRole certificateRole,
            String keyStorePassword,
            String alias)
            throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {

        String keyStorePath = getKeyStorePath(certificateRole);
        KeyStore keyStore = getKeyStore(keyStorePath, keyStorePassword);
        Certificate certificate = keyStore.getCertificate(alias);

        return certificate.getPublicKey();
    }

    @Override
    public Certificate getCertificate(
            CertificateRole certificateRole,
            String keyStorePassword,
            String alias)
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {

        String keyStorePath = getKeyStorePath(certificateRole);
        KeyStore keyStore = getKeyStore(keyStorePath, keyStorePassword);
        Certificate certificate = keyStore.getCertificate(alias);

        return certificate;
    }

    @Override
    public List<CertificateDTO> getCertificates(
            String role,
            String keyStorePassword)
            throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException {

        String keyStorePath = getKeyStorePath(getCertificateRole(role));
        KeyStore keyStore = getKeyStore(keyStorePath, keyStorePassword);
        int i = 0;
        Enumeration<String> aliases = keyStore.aliases();
        List<CertificateDTO> certificateDTOS = new ArrayList<>();

        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            certificateDTOS.add(createCertificateDTO((X509Certificate) keyStore.getCertificate(alias), alias));
            i++;
        }

        return certificateDTOS;
    }

    @Override
    public List<CertificateDTO> getCACertificates(
            String rootKeyStoragePassword,
            String intermediateKeyStoragePassword)
            throws CertificateException, KeyStoreException, NoSuchAlgorithmException, IOException {

        if (rootKeyStoragePassword.isEmpty() && intermediateKeyStoragePassword.isEmpty()) {
            throw new BadCredentialsException("Please enter at least one of the passwords.");
        }

        List<CertificateDTO> certificateDTOS = new ArrayList<>();
        if (!rootKeyStoragePassword.isEmpty()) {
            loadCertificates(certificateDTOS, rootKeyStoragePassword, "root");
        }
        if (!intermediateKeyStoragePassword.isEmpty()) {
            loadCertificates(certificateDTOS, intermediateKeyStoragePassword, "intermediate");
        }

        return certificateDTOS;
    }

    private CertificateRole getCertificateRole(String role) {
        if (role.equals("root")) {
            return CertificateRole.ROOT;
        } else if (role.equals("intermediate")) {
            return CertificateRole.INTERMEDIATE;
        } else {
            return CertificateRole.END_ENTITY;
        }
    }

    private CertificateRole getCertificateRole(Certificate certificate) throws CertificateEncodingException {
        X509Certificate tempCertificate = (X509Certificate) certificate;
        X500Name subject = new JcaX509CertificateHolder(tempCertificate).getSubject();
        X500Name issuer = new JcaX509CertificateHolder(tempCertificate).getIssuer();

        if (subject.getRDNs(BCStyle.CN)[0].getFirst().getValue().toString()
                .equals(issuer.getRDNs(BCStyle.CN)[0].getFirst().getValue().toString())) {
            return CertificateRole.ROOT;
        } else if (tempCertificate.getBasicConstraints() != -1) {
            // If the path length has been set, it's a CA certificate
            return CertificateRole.INTERMEDIATE;
        } else {
            return CertificateRole.END_ENTITY;
        }
    }

    private String getKeyStorePath(CertificateRole certificateRole) {
        switch (certificateRole) {
            case ROOT:
                return ROOT_KEYSTORE;
            case INTERMEDIATE:
                return INTERMEDIATE_KEYSTORE;
            default:
                return END_ENTITY_KEYSTORE;
        }
    }

    private KeyStore getKeyStore(String keyStorePath, String keyStorePassword)
        throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

        char[] keyStorePassArray = keyStorePassword.toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try {
            keyStore.load(new FileInputStream(keyStorePath), keyStorePassArray);
        } catch (FileNotFoundException e) {
            keyStore.load(null, keyStorePassArray);
        }

        return keyStore;
    }

    private CertificateDTO createCertificateDTO(X509Certificate x509Certificate, String alias)
        throws CertificateParsingException, CertificateEncodingException {

        CertificateDTO certificateDTO = new CertificateDTO();
        certificateDTO.setSerialNumber(x509Certificate.getSerialNumber().toString(16));

        X500Name x500Name = new JcaX509CertificateHolder(x509Certificate).getSubject();
        boolean[] subjectUniqueID = x509Certificate.getSubjectUniqueID();

        certificateDTO.setSubject(new EntityDTO(x500Name, boolArrayToLong(subjectUniqueID)));

        X500Name x500nameIssuer = new JcaX509CertificateHolder(x509Certificate).getIssuer();
        boolean[] issuerUniqueID = x509Certificate.getIssuerUniqueID();
        certificateDTO.setIssuer(new EntityDTO(x500nameIssuer, boolArrayToLong(issuerUniqueID)));

        // The method getExtensionValue() Gets the DER-encoded OCTET string for the extension
        // value (extnValue) identified by the passed-in OID String.
        // https://docs.oracle.com/javase/7/docs/api/java/security/cert/X509Extension.html#getExtensionValue
        if (x509Certificate.getExtensionValue("2.5.29.35") != null) {
            certificateDTO.setAuthorityKeyIdentifier(true);
        }
        if (x509Certificate.getExtensionValue("2.5.29.14") != null) {
            certificateDTO.setSubjectKeyIdentifier(true);
        }

        // Gets the certificate constraints path length from the critical
        // BasicConstraints extension, (OID = 2.5.29.19).
        if (x509Certificate.getBasicConstraints() != -1) {
            certificateDTO.setSubjectIsCa(true);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        certificateDTO.setValidFrom(dateFormat.format(x509Certificate.getNotBefore()));
        certificateDTO.setValidTo(dateFormat.format(x509Certificate.getNotAfter()));

        boolean[] keyUsage = x509Certificate.getKeyUsage();
        if (keyUsage != null) {
            certificateDTO.setKeyUsage(new KeyUsageDTO(x509Certificate.getKeyUsage()));
        }

        List<String> extendedKeyUsage = x509Certificate.getExtendedKeyUsage();
        if (extendedKeyUsage != null && !extendedKeyUsage.isEmpty()) {
            certificateDTO.setExtendedKeyUsage(new ExtendedKeyUsageDTO(extendedKeyUsage));
        }
        certificateDTO.setAlias(alias);

        return certificateDTO;
    }

    private long boolArrayToLong(boolean[] attributes) {
        char[] chars = new char[7];
        Arrays.fill(chars, '0');

        if (attributes != null) {
            for (int i = 0; i < attributes.length; i++) {
                if (attributes[i]) {
                    chars[i] = '1';
                }
            }
        }

        return new BigInteger(new String(chars), 2).longValue();
    }

    private List<CertificateDTO> loadCertificates(
            List<CertificateDTO> certificateDTOS,
            String keyStorePassword,
            String role)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {

        String keyStorePath = getKeyStorePath(getCertificateRole(role));
        char[] keyStorePassArray = keyStorePassword.toCharArray();

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try {
            keyStore.load(new FileInputStream(keyStorePath), keyStorePassArray);
        } catch (FileNotFoundException e) {
            return certificateDTOS;
        }

        int i = 0;
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            certificateDTOS.add(createCertificateDTO((X509Certificate) keyStore.getCertificate(alias), alias));
            i++;
        }

        return certificateDTOS;
    }

}
