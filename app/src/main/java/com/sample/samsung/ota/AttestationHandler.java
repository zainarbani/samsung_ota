package com.sample.samsung.ota;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import com.samsung.android.security.keystore.AttestParameterSpec;
import com.samsung.android.security.keystore.AttestationUtils;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.List;

public class AttestationHandler {
    private static final String TAG = "AttestationHandler";
    private final List<byte[]> attestCertificates = new ArrayList<>();
    private final String alias;
    private final byte[] challenge;

    public AttestationHandler (String alias, byte[] challenge) {
        this.alias = alias;
        this.challenge = challenge;
    }

    public AttestationHandler runAttestation() {
        try {
            Log.d(TAG, "Starting Attestation for alias: " + this.alias);
            
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            
            if (keyStore.containsAlias(this.alias)) {
                keyStore.deleteEntry(this.alias);
            }

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            
            KeyGenParameterSpec params = new KeyGenParameterSpec.Builder(this.alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAttestationChallenge(this.challenge)
                    .build();
            
            keyPairGenerator.initialize(params);
            keyPairGenerator.generateKeyPair();

            AttestationUtils utils = new AttestationUtils();
            AttestParameterSpec spec = new AttestParameterSpec.Builder(this.alias, this.challenge)
                    .setAlgorithm(KeyProperties.KEY_ALGORITHM_EC)
                    .setKeyGenParameterSpec(params)
                    .setVerifiableIntegrity(true)
                    .setDeviceAttestation(true)
                    .setPackageName("android")
                    .build();

            Iterable<byte[]> certChain;
            if (spec.isDeviceAttestation()) {
                certChain = utils.attestDevice(spec);
            } else {
                certChain = utils.attestKey(spec);
            }

            if (certChain != null) {
                utils.storeCertificateChain(this.alias, certChain);
                
                Certificate[] chain = keyStore.getCertificateChain(this.alias);
                if (chain != null) {
                    for (Certificate cert : chain) {
                        this.attestCertificates.add(cert.getEncoded());
                    }
                    Log.d(TAG, "SAK Attestation successful, found " + attestCertificates.size() + " certs");
                } else {
                    Log.e(TAG, "Certificate chain is null after storeCertificateChain");
                    for (byte[] cert : certChain) {
                        this.attestCertificates.add(cert);
                    }
                }
            } else {
                Log.e(TAG, "SAK Attestation returned null certChain");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Attestation failed", e);
        }

        return this;
    }

    public List<byte[]> getCertificates() {
        return this.attestCertificates;
    }
}
