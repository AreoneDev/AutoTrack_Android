package com.areonedev.autotrack.integration;

import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.areonedev.autotrack.persistence.CryptoManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class KeystoreContractTest {
    @Test public void randomizedEncryptionAndCrossInstanceUnicodeRoundTrip() {
        CryptoManager crypto = new CryptoManager();
        String plain = "合成客户 🔐 synthetic@example.invalid";
        String a = crypto.encrypt(plain), b = crypto.encrypt(plain);
        assertNotEquals(plain, a);
        assertNotEquals(a, b);
        assertEquals(plain, new CryptoManager().decrypt(a));
        assertEquals(plain, crypto.decrypt(b));
    }
    @Test public void tamperingCannotRecoverOriginalPlaintext() {
        CryptoManager crypto = new CryptoManager();
        String plain = "Synthetic sensitive data";
        byte[] encoded = Base64.decode(crypto.encrypt(plain), Base64.DEFAULT);
        encoded[encoded.length - 1] ^= 1;
        assertNotEquals(plain, crypto.decrypt(Base64.encodeToString(encoded, Base64.DEFAULT)));
        // Current API returns unreadable input on failure; this is NOT proof of fail-closed handling.
    }
    @Test public void absentOptionalFieldsRemainAbsent() {
        CryptoManager crypto = new CryptoManager();
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decrypt(null));
        assertEquals("", crypto.encrypt(""));
        assertEquals("", crypto.decrypt(""));
    }
}
