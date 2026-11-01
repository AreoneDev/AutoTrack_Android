package com.areonedev.autotrack.sync;

import com.areonedev.autotrack.auth.AccountSession;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

public final class SupabaseSyncRemote implements SyncEngine.Remote {
    public interface Transport { String post(String rpc,String bearer,String body) throws Exception; }
    private final Transport transport;
    public SupabaseSyncRemote(Transport transport) { this.transport=transport; }
    public String push(AccountSession.Session session,SyncEngine.Operation op) throws Exception {
        return transport.post("autotrack_sync_lead",session.accessToken,"{\"p_operation\":"+op.body+"}");
    }
    public String snapshot(AccountSession.Session session) throws Exception { return transport.post("autotrack_sync_snapshot",session.accessToken,"{}"); }
    public static final class Http implements Transport {
        private final String base,key;
        public Http(String base,String key) {
            if(!base.matches("https://[A-Za-z0-9.-]+(?::[0-9]+)?") || !key.startsWith("sb_publishable_")) throw new IllegalArgumentException("Configure the Supabase publishable connection.");
            this.base=base;this.key=key;
        }
        public String post(String rpc,String bearer,String body) throws Exception {
            if(!rpc.equals("autotrack_sync_lead") && !rpc.equals("autotrack_sync_snapshot")) throw new IllegalArgumentException("Unknown sync RPC.");
            HttpsURLConnection c=(HttpsURLConnection)new URL(base+"/rest/v1/rpc/"+rpc).openConnection();
            try {
                c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(30000);
                c.setRequestProperty("apikey",key);c.setRequestProperty("Authorization","Bearer "+bearer);c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);
                try(OutputStream out=c.getOutputStream()) {out.write(body.getBytes(StandardCharsets.UTF_8));}
                int status=c.getResponseCode();
                if(status<200 || status>=300) throw new IOException(status==401?"Sync session expired. Sign in again.":"Sync unavailable (HTTP "+status+").");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=c.getInputStream()) { byte[] buffer=new byte[8192];int count;
                    while((count=in.read(buffer))!=-1) {if(bytes.size()+count>16*1024*1024) throw new IOException("Sync snapshot is too large. Local data is unchanged.");bytes.write(buffer,0,count);} }
                return bytes.toString("UTF-8");
            } finally {c.disconnect();}
        }
    }
}
