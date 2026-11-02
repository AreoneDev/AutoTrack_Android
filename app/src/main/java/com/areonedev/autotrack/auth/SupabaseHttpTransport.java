package com.areonedev.autotrack.auth;

import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

public final class SupabaseHttpTransport implements SupabaseAuthProvider.Transport {
    private final String baseUrl, publicKey;
    public SupabaseHttpTransport(String baseUrl, String publicKey) {
        if (!baseUrl.matches("https://[A-Za-z0-9.-]+(?::[0-9]+)?") || publicKey.isEmpty())
            throw new IllegalArgumentException("Configure the Supabase project URL and publishable key.");
        // Never accept server-side secret keys in the application configuration.
        if (!publicKey.startsWith("sb_publishable_"))
            throw new IllegalArgumentException("Use a Supabase publishable key.");
        this.baseUrl = baseUrl; this.publicKey = publicKey;
    }
    public SupabaseAuthProvider.Response post(String path, String bearer, Map<String, String> body) throws IOException {
        return request("POST", path, bearer, body);
    }
    public SupabaseAuthProvider.Response get(String path, String bearer) throws IOException {
        return request("GET", path, bearer, Collections.emptyMap());
    }
    public SupabaseAuthProvider.Response put(String path, String bearer, Map<String, String> body) throws IOException {
        return request("PUT", path, bearer, body);
    }
    private SupabaseAuthProvider.Response request(String method, String path, String bearer, Map<String, String> body) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(baseUrl + "/auth/v1" + path).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setRequestProperty("apikey", publicKey);
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
            connection.setRequestProperty("Content-Type", "application/json");
            if (!method.equals("GET")) {
              connection.setDoOutput(true);
              try (OutputStream out = connection.getOutputStream()) {
                Map<String, String> fields = new HashMap<>(body);
                JSONObject metadata = new JSONObject();
                for (String field : new String[]{"first_name", "last_name", "phone"}) {
                    String value = fields.remove("data." + field);
                    if (value != null) metadata.put(field, value);
                }
                JSONObject payload = new JSONObject(fields);
                if (metadata.length() > 0) payload.put("data", metadata);
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
              }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300 || status == 204)
                return new SupabaseAuthProvider.Response(status, Collections.emptyMap());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > 65536) throw new IOException("Invalid sign-in response.");
                    bytes.write(buffer, 0, count);
                }
            }
            JSONObject json = new JSONObject(bytes.toString("UTF-8"));
            Map<String, Object> result = new HashMap<>();
            for (String field : new String[]{"access_token", "refresh_token", "expires_at", "expires_in"})
                if (json.has(field)) result.put(field, json.get(field));
            if (json.has("user")) result.put("user", Collections.singletonMap("id", json.getJSONObject("user").getString("id")));
            if (path.equals("/user")) {
                result.put("id", json.getString("id"));
                result.put("email", json.optString("email", ""));
                JSONObject metadata = json.optJSONObject("user_metadata");
                Map<String, String> fields = new HashMap<>();
                if (metadata != null) for (String field : new String[]{"first_name", "last_name", "phone"}) fields.put(field, metadata.optString(field, ""));
                result.put("user_metadata", fields);
            }
            return new SupabaseAuthProvider.Response(status, result);
        } catch (org.json.JSONException invalid) { throw new IOException("Invalid sign-in response."); }
        finally { connection.disconnect(); }
    }
}
