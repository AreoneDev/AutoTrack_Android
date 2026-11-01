package com.areonedev.autotrack.application;

import android.content.Context;

import com.areonedev.autotrack.persistence.DataAccess;
import com.areonedev.autotrack.persistence.DataAccessObject;

public class Services {
    private static DataAccess dataAccessService = null;
    private static Context appContext;
    private static String accountId;
    private static long accountGeneration;
    private static DataAccess accountView;

    public static synchronized void openAccount(String userId) {
        String filename = com.areonedev.autotrack.auth.AccountSession.databaseName(userId);
        if (userId.equals(accountId) && dataAccessService != null) return;
        closeDataAccess();
        if (appContext == null) throw new IllegalStateException("Application context is unavailable.");
        String path = appContext.getDatabasePath(filename).getAbsolutePath();
        Main.setDBPathName(path);
        DataAccessObject database = new DataAccessObject(path, false, userId);
        database.open(path);
        if (!database.isOpen()) throw new IllegalStateException("Account database unavailable.");
        dataAccessService = database;
        bindAccount(userId);
    }

    /** Scopes an already injected data source; also supports isolated test fixtures. */
    public static synchronized void bindAccount(String userId) {
        com.areonedev.autotrack.auth.AccountSession.databaseName(userId);
        if (dataAccessService == null) throw new IllegalStateException("Data source unavailable.");
        accountGeneration++;
        accountId = userId;
        final long ticket = accountGeneration;
        final DataAccess target = dataAccessService;
        accountView = (DataAccess) java.lang.reflect.Proxy.newProxyInstance(
                DataAccess.class.getClassLoader(), new Class<?>[]{DataAccess.class}, (proxy, method, args) -> {
            synchronized (Services.class) {
                if (ticket != accountGeneration || target != dataAccessService)
                    throw new IllegalStateException("This account is no longer active.");
                if (method.getName().equals("open") || method.getName().equals("close"))
                    throw new IllegalStateException("Account database lifecycle is managed by Services.");
                try { return method.invoke(target, args); }
                catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
            }
        });
    }

    public static synchronized String activeAccount() { return accountId; }

    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
    }

    /*** Both Context and DataAccess are static for the entire app ***/
    public static Context getAppContext() {
        return appContext;
    }
    public static DataAccess createDataAccess(String dbPath)
    {
        if (dataAccessService == null)
        {
            dataAccessService = new DataAccessObject(dbPath);
            dataAccessService.open(dbPath);
        }
        return dataAccessService;
    }

    /*** STUB ***/
    public static DataAccess createDataAccess(DataAccess alternateDataAccessService)
    {
        if (dataAccessService == null)
        {
            dataAccessService = alternateDataAccessService;
            dataAccessService.open(Main.getDBPathName());
        }
        return dataAccessService;
    }

    public static synchronized DataAccess getDataAccess(String dbName)
    {
        if (dataAccessService == null)
        {
            System.out.println("Connection to data access has not been established.");
            throw new IllegalStateException("Sign in before accessing account data.");
        }
        return accountView != null ? accountView : dataAccessService;
    }

    public static synchronized void closeDataAccess()
    {
        accountGeneration++;
        accountId = null;
        accountView = null;
        if (dataAccessService != null)
        {
            dataAccessService.close();
        }
        dataAccessService = null;
    }
}
