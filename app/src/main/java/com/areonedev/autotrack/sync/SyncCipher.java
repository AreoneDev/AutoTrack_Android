package com.areonedev.autotrack.sync;

import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public final class SyncCipher {
    private final String alias;
    public SyncCipher(String owner) { com.areonedev.autotrack.auth.AccountSession.databaseName(owner);alias="AutoTrackSync-"+owner; }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(alias)) return (SecretKey)store.getKey(alias,null);
        KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public String encrypt(String plain) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] bytes=new byte[12+encrypted.length];System.arraycopy(c.getIV(),0,bytes,0,12);System.arraycopy(encrypted,0,bytes,12,encrypted.length);
        return Base64.encodeToString(bytes,Base64.NO_WRAP);
    }
    public String decrypt(String stored) throws Exception {
        byte[] bytes=Base64.decode(stored,Base64.NO_WRAP);if(bytes.length<28) throw new IllegalStateException("Invalid sync payload.");
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(alias)) throw new IllegalStateException("Sync encryption key is unavailable.");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,(SecretKey)store.getKey(alias,null),new GCMParameterSpec(128,bytes,0,12));
        return new String(c.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
    }
}
