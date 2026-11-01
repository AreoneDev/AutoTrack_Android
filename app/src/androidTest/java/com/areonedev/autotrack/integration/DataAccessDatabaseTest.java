package com.areonedev.autotrack.integration;

import android.util.Log;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

import com.areonedev.autotrack.application.Main;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.persistence.DataAccess;
import com.areonedev.autotrack.persistence.DataAccessObject; // Use the REAL one
import com.areonedev.autotrack.persistence.DataAccessTest;

@RunWith(AndroidJUnit4.class)
public class DataAccessDatabaseTest {
    private static String dbName = Main.dbName;
    private static final String TAG = "DataAccessDatabaseTest";

    @org.junit.Rule public com.areonedev.autotrack.testing.IsolatedDatabase database = new com.areonedev.autotrack.testing.IsolatedDatabase();

    @Test public void testDataAccess() {
        DataAccessTest.dataAccessTest(database.dao);
    }
}
