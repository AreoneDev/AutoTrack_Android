package com.areonedev.autotrack.presentation;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.auth.*;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.persistence.DataAccess;
import com.areonedev.autotrack.sync.SyncRuntime;
import org.json.JSONObject;
import java.util.List;

public final class SyncActivity extends AccountActivity {
    private LinearLayout content;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32,32,32,32);scroll.addView(content);setContentView(scroll);
    }
    @Override protected void onResume() {super.onResume();if(AuthRuntime.hasAccount()) refresh();}
    private void refresh() {
        content.removeAllViews();TextView status=new TextView(this);status.setText(SyncRuntime.status());content.addView(status);
        Button sync=new Button(this);sync.setText(R.string.sync_now);content.addView(sync);
        sync.setOnClickListener(v->{SyncRuntime.request(getApplicationContext());refresh();});
        AuthRuntime.execute(()-> {
            try {
                AccountSession accounts=AuthRuntime.accounts(this);List<String> conflicts;String owner;long generation;
                synchronized(accounts) {
                    AccountSession.Session session=accounts.current();if(session==null) return;
                    owner=session.userId;generation=accounts.generation();conflicts=Services.getDataAccess("AutoTrack").syncConflicts(owner);
                }
                runOnUiThread(()-> {if(!accounts.isCurrent(owner,generation) || isFinishing()) return;
                    for(String conflict:conflicts) {
                        try {
                            JSONObject item=new JSONObject(conflict);JSONObject remote=item.getJSONObject("response").getJSONObject("remote");
                            JSONObject local=item.getJSONObject("local");JSONObject fields=local.optJSONObject("fields");
                            String name=remote.optString("first_name","")+" "+remote.optString("last_name","");
                            if(name.trim().isEmpty() && fields!=null) name=fields.optString("first_name","")+" "+fields.optString("last_name","");
                            Button review=new Button(this);review.setText(getString(R.string.sync_customer_conflict,name.trim()));content.addView(review);
                            String localNotes=local.optString("kind").equals("delete")?getString(R.string.sync_deleted):fields==null?"":fields.optString("notes","");
                            String remoteNotes=remote.optBoolean("deleted")?getString(R.string.sync_deleted):remote.optString("notes","");
                            String message=getString(R.string.sync_choose)+"\n\n"+getString(R.string.sync_compare_notes,localNotes,remoteNotes);
                            review.setOnClickListener(v->new AlertDialog.Builder(this).setMessage(message)
                                .setPositiveButton(R.string.sync_keep_local,(dialog,which)->resolve(owner,generation,item.optString("operation_id"),true))
                                .setNegativeButton(R.string.sync_keep_remote,(dialog,which)->resolve(owner,generation,item.optString("operation_id"),false)).show());
                        } catch(Exception invalid) {status.setText(R.string.sync_unavailable);}
                    }
                });
            } catch(Exception error) {runOnUiThread(()->status.setText(R.string.sync_unavailable));}
        });
    }
    private void resolve(String owner,long generation,String operation,boolean keepLocal) {
        AuthRuntime.execute(()-> {
            try {
                AccountSession accounts=AuthRuntime.accounts(this);
                synchronized(accounts) {
                    if(!accounts.isCurrent(owner,generation)) return;
                    Services.getDataAccess("AutoTrack").syncResolve(owner,operation,keepLocal);
                }
                SyncRuntime.request(getApplicationContext());runOnUiThread(this::refresh);
            } catch(Exception error) {runOnUiThread(()->Toast.makeText(this,R.string.sync_resolution_failed,Toast.LENGTH_LONG).show());}
        });
    }
}
