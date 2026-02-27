package com.samsung.android.security.keystore;

public final class AttestationUtils {
    public Iterable<byte[]> attestKey(String alias, byte[] challenge) {
        throw new RuntimeException("Stub!");
    }

    public Iterable<byte[]> attestKey(AttestParameterSpec spec) {
        throw new RuntimeException("Stub!");
    }

    public Iterable<byte[]> attestDevice(AttestParameterSpec spec) {
        throw new RuntimeException("Stub!");
    }

    public void storeCertificateChain(String alias, Iterable<byte[]> certChain) {
        throw new RuntimeException("Stub!");
    }
}