package com.areonedev.autotrack.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Dedicated fail-closed token storage. Never uses the legacy plaintext fallback. */
public final class SecureSessionStore implements AccountSession.Store {
    private final SharedPreferences preferences;
    private final String alias;
    public SecureSessionStore(Context context, String name, String alias) {
        preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        this.alias = alias;
    }
    private SecretKey key() throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        if (!keys.containsAlias(alias)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return generator.generateKey();
        }
        return (SecretKey) keys.getKey(alias, null);
    }
    public synchronized void save(AccountSession.Session session) throws IOException {
        if (preferences.getBoolean("logout_pending", false)) throw new AccountSession.LogoutPendingException();
        try {
            ByteArrayOutputStream plain = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(plain)) {
                out.writeUTF(session.userId); out.writeUTF(session.accessToken);
                out.writeUTF(session.refreshToken); out.writeLong(session.expiresAt);
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(plain.toByteArray());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(cipher.getIV()); bytes.write(encrypted);
            if (!preferences.edit().putString("session", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)).commit())
                throw new IOException();
        } catch (Exception unavailable) { throw new IOException("Unable to save your session securely."); }
    }
    public synchronized AccountSession.Session load() throws IOException {
        if (preferences.getBoolean("logout_pending", false)) throw new AccountSession.LogoutPendingException();
        String stored = preferences.getString("session", null);
        if (stored == null) return null;
        try {
            byte[] bytes = Base64.decode(stored, Base64.NO_WRAP);
            if (bytes.length < 29) throw new IOException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 0, 12));
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(cipher.doFinal(bytes, 12, bytes.length - 12)))) {
                return new AccountSession.Session(in.readUTF(), in.readUTF(), in.readUTF(), in.readLong());
            }
        } catch (Exception invalid) { throw new IOException("Saved session unavailable. Please sign in again."); }
    }
    public synchronized void clear() throws IOException {
        if (!preferences.edit().putBoolean("logout_pending", true).commit())
            throw new IOException("Unable to record sign out.");
        try {
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            keys.deleteEntry(alias);
        } catch (Exception unavailable) {
            throw new IOException("Unable to remove the session encryption key.");
        }
        if (!preferences.edit().remove("session").remove("logout_pending").commit()) throw new IOException("Unable to clear the saved session.");
    }
}
