package com.sample.samsung.ota;

interface IAttestationService {
    List<String> getAttestationCerts(String alias, in byte[] challenge);
}