package com.google.firebase.installations.local;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import org.json.JSONException;
import org.json.JSONObject;

/* compiled from: com.google.firebase:firebase-installations@@16.0.0 */
public class IidStore {
    private static final String DEFAULT_SCOPE = "|*";
    private static final String IID_SHARED_PREFS_NAME = "com.google.android.gms.appid";
    private static final String JSON_ENCODED_PREFIX = "{";
    private static final String JSON_TOKEN_KEY = "token";
    private static final String STORE_KEY_ID = "|S|id";
    private static final String STORE_KEY_PUB = "|S||P|";
    private static final String STORE_KEY_TOKEN = "|T|";
    private final String defaultSenderId;
    private final SharedPreferences iidPrefs;

    public IidStore(FirebaseApp firebaseApp) {
        this.iidPrefs = firebaseApp.getApplicationContext().getSharedPreferences(IID_SHARED_PREFS_NAME, 0);
        this.defaultSenderId = getDefaultSenderId(firebaseApp);
    }

    private static String getDefaultSenderId(FirebaseApp firebaseApp) {
        String gcmSenderId = firebaseApp.getOptions().getGcmSenderId();
        if (gcmSenderId != null) {
            return gcmSenderId;
        }
        String applicationId = firebaseApp.getOptions().getApplicationId();
        if (!applicationId.startsWith("1:") && !applicationId.startsWith("2:")) {
            return applicationId;
        }
        String[] split = applicationId.split(":");
        if (split.length != 4) {
            return null;
        }
        String str = split[1];
        if (str.isEmpty()) {
            return null;
        }
        return str;
    }

    private String createTokenKey(String str) {
        return STORE_KEY_TOKEN + str + DEFAULT_SCOPE;
    }

    public String readToken() {
        synchronized (this.iidPrefs) {
            String string = this.iidPrefs.getString(createTokenKey(this.defaultSenderId), null);
            if (TextUtils.isEmpty(string)) {
                return null;
            }
            if (!string.startsWith(JSON_ENCODED_PREFIX)) {
                return string;
            }
            String parseIidTokenFromJson = parseIidTokenFromJson(string);
            return parseIidTokenFromJson;
        }
    }

    private String parseIidTokenFromJson(String str) {
        try {
            return new JSONObject(str).getString(JSON_TOKEN_KEY);
        } catch (JSONException unused) {
            return null;
        }
    }

    public String readIid() {
        synchronized (this.iidPrefs) {
            String readInstanceIdFromLocalStorage = readInstanceIdFromLocalStorage();
            if (readInstanceIdFromLocalStorage != null) {
                return readInstanceIdFromLocalStorage;
            }
            String readPublicKeyFromLocalStorageAndCalculateInstanceId = readPublicKeyFromLocalStorageAndCalculateInstanceId();
            return readPublicKeyFromLocalStorageAndCalculateInstanceId;
        }
    }

    private String readInstanceIdFromLocalStorage() {
        String string;
        synchronized (this.iidPrefs) {
            string = this.iidPrefs.getString(STORE_KEY_ID, null);
        }
        return string;
    }

    private String readPublicKeyFromLocalStorageAndCalculateInstanceId() {
        synchronized (this.iidPrefs) {
            String string = this.iidPrefs.getString(STORE_KEY_PUB, null);
            if (string == null) {
                return null;
            }
            PublicKey parseKey = parseKey(string);
            if (parseKey == null) {
                return null;
            }
            String idFromPublicKey = getIdFromPublicKey(parseKey);
            return idFromPublicKey;
        }
    }

    private static String getIdFromPublicKey(PublicKey publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA1").digest(publicKey.getEncoded());
            digest[0] = (byte) (((digest[0] & 15) + 112) & 255);
            return Base64.encodeToString(digest, 0, 8, 11);
        } catch (NoSuchAlgorithmException unused) {
            Log.w("ContentValues", "Unexpected error, device missing required algorithms");
            return null;
        }
    }

    private PublicKey parseKey(String str) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(str, 8)));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.w("ContentValues", "Invalid key stored " + e);
            return null;
        }
    }
}
