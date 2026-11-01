package com.areonedev.autotrack.testing;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.application.Main;
import com.areonedev.autotrack.persistence.DataAccessObject;
import org.junit.rules.ExternalResource;
import java.io.File;
import java.util.UUID;

/** Never opens/deletes the application's customer database. No key deletion. */
public class IsolatedDatabase extends ExternalResource {
    public DataAccessObject dao;
    public File file;
    private String previousPath;
    @Override protected void before() throws Throwable {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Services.closeDataAccess();
        Services.initialize(context);
        file = new File(context.getCacheDir(), "autotrack-test-" + UUID.randomUUID() + ".db");
        previousPath = Main.getDBPathName();
        Main.setDBPathName(file.getAbsolutePath());
        dao = new DataAccessObject(file.getAbsolutePath());
        Services.createDataAccess(dao);
        String syntheticUser = UUID.randomUUID().toString();
        Services.bindAccount(syntheticUser);
        com.areonedev.autotrack.auth.SyntheticAccount.install(syntheticUser);
    }
    @Override protected void after() {
        Services.closeDataAccess();
        com.areonedev.autotrack.auth.SyntheticAccount.reset();
        Main.setDBPathName(previousPath);
        if (file != null) android.database.sqlite.SQLiteDatabase.deleteDatabase(file);
    }
}
