package com.example.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class InitialAdminFlowTest {
    private static final String DB_NAME = "initial_admin_flow_test.db";
    private Context context;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DB_NAME);
    }

    @After public void tearDown() {
        context.deleteDatabase(DB_NAME);
    }

    @Test public void freshDatabaseHasNoUserUntilFirstAdminIsCreated() {
        DBHelper helper = new DBHelper(context, DB_NAME);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(0, DBHelper.scalar(db, "SELECT COUNT(*) FROM USUARIO"));
            assertEquals(1, DBHelper.scalar(db,
                    "SELECT COUNT(*) FROM ROL WHERE nombre_rol='Administrador'"));

            long id = helper.createInitialAdmin("root.user", "Bodega Admin", "SecurePass_83");
            assertTrue(id > 0);
            db = helper.getReadableDatabase();
            assertEquals(1, DBHelper.scalar(db, "SELECT COUNT(*) FROM USUARIO"));
            assertFalse(helper.requiresPasswordChange(id));
            db = helper.getReadableDatabase();
            assertEquals(1, DBHelper.scalar(db,
                    "SELECT COUNT(*) FROM USUARIO WHERE id_usuario=" + id +
                            " AND username='root.user' AND debe_cambiar_clave=0"));

            try {
                helper.createInitialAdmin("another", "Another Admin", "AnotherSecure_83");
                fail("El setup inicial debe quedar cerrado después del primer usuario.");
            } catch (IllegalStateException expected) {
                db = helper.getReadableDatabase();
                assertEquals(1, DBHelper.scalar(db, "SELECT COUNT(*) FROM USUARIO"));
            }
        } finally {
            helper.close();
        }
    }

    @Test public void v7MigrationPreservesAdminAndRequiresPasswordRotation() {
        String oldPassword = "ExistingAdminPass_78";
        String oldHash = DBHelper.hash(oldPassword);
        String inactiveAdminHash = DBHelper.hash("InactiveAdminPass_35");
        String operatorPassword = "ExistingOperatorPass_21";
        String operatorHash = DBHelper.hash(operatorPassword);
        createVersion7Database(oldHash, inactiveAdminHash, operatorHash);

        DBHelper helper = new DBHelper(context, DB_NAME);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(9, DBHelper.scalar(db, "PRAGMA user_version"));
            assertEquals(3, DBHelper.scalar(db, "SELECT COUNT(*) FROM USUARIO"));
            assertEquals(41, DBHelper.scalar(db,
                    "SELECT id_usuario FROM USUARIO WHERE username='admin.user'"));
            assertEquals(1, DBHelper.scalar(db,
                    "SELECT COUNT(*) FROM USUARIO WHERE id_usuario=41 AND password_hash='" + oldHash + "'"));
            assertTrue(DBHelper.verify(oldPassword, oldHash));
            assertTrue(helper.requiresPasswordChange(41));
            db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT id_usuario,activo,password_hash,debe_cambiar_clave FROM USUARIO WHERE username='inactive.admin'", null)) {
                assertTrue(c.moveToFirst());
                assertEquals(43, c.getLong(0));
                assertEquals(0, c.getInt(1));
                assertEquals(inactiveAdminHash, c.getString(2));
                assertEquals(1, c.getInt(3));
            }
            try (Cursor c = db.rawQuery(
                    "SELECT U.id_usuario,U.username,R.nombre_rol,U.activo,U.password_hash,U.debe_cambiar_clave " +
                            "FROM USUARIO U JOIN ROL R ON R.id_rol=U.id_rol WHERE U.id_usuario=42", null)) {
                assertTrue(c.moveToFirst());
                assertEquals(42, c.getLong(0));
                assertEquals("operator.user", c.getString(1));
                assertEquals("Operador", c.getString(2));
                assertEquals(1, c.getInt(3));
                assertEquals(operatorHash, c.getString(4));
                assertEquals(0, c.getInt(5));
                assertTrue(DBHelper.verify(operatorPassword, c.getString(4)));
            }

            try {
                helper.rotatePassword(41, oldPassword);
                fail("No debe permitirse reutilizar la contraseña actual.");
            } catch (IllegalArgumentException expected) {
                // La contraseña no se incluye en el mensaje ni se altera el estado pendiente.
            }
            db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT password_hash,debe_cambiar_clave FROM USUARIO WHERE id_usuario=41", null)) {
                assertTrue(c.moveToFirst());
                assertEquals(oldHash, c.getString(0));
                assertEquals(1, c.getInt(1));
            }

            String newPassword = "RotatedAdminPass_92";
            assertTrue(helper.rotatePassword(41, newPassword));
            assertFalse(helper.requiresPasswordChange(41));
            db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery(
                    "SELECT password_hash,debe_cambiar_clave FROM USUARIO WHERE id_usuario=41", null)) {
                assertTrue(c.moveToFirst());
                assertNotEquals(oldHash, c.getString(0));
                assertEquals(0, c.getInt(1));
                assertTrue(DBHelper.verify(newPassword, c.getString(0)));
                assertFalse(DBHelper.verify(oldPassword, c.getString(0)));
            }
        } finally {
            helper.close();
        }
    }

    @Test public void adminPanelAllowsOnlyAnyOfItsThreeManagementPermissions() {
        assertTrue(MainActivity.canAccessAdminPanel(false, false, true));
        assertTrue(MainActivity.canAccessAdminPanel(true, false, false));
        assertTrue(MainActivity.canAccessAdminPanel(false, true, false));
        assertFalse(MainActivity.canAccessAdminPanel(false, false, false));
    }

    @Test public void newPasswordsMustHaveAtLeastEightCharacters() {
        assertFalse(DBHelper.meetsPasswordMinimum("1234567"));
        assertTrue(DBHelper.meetsPasswordMinimum("12345678"));
        assertFalse(DBHelper.meetsPasswordMinimum(null));
    }

    private void createVersion7Database(String adminHash, String inactiveAdminHash, String operatorHash) {
        java.io.File databaseFile = context.getDatabasePath(DB_NAME);
        java.io.File databaseDirectory = databaseFile.getParentFile();
        if (databaseDirectory != null && !databaseDirectory.exists()) databaseDirectory.mkdirs();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(
                databaseFile, null);
        try {
            db.execSQL("CREATE TABLE ROL (id_rol INTEGER PRIMARY KEY, nombre_rol TEXT NOT NULL UNIQUE, " +
                    "descripcion TEXT NOT NULL DEFAULT '', activo INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE TABLE USUARIO (id_usuario INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, " +
                    "password_hash TEXT NOT NULL, nombre_completo TEXT NOT NULL, id_rol INTEGER NOT NULL, " +
                    "activo INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("INSERT INTO ROL(id_rol,nombre_rol) VALUES(1,'Administrador'),(2,'Operador')");

            ContentValues admin = new ContentValues();
            admin.put("id_usuario", 41);
            admin.put("username", "admin.user");
            admin.put("password_hash", adminHash);
            admin.put("nombre_completo", "Existing Admin");
            admin.put("id_rol", 1);
            db.insertOrThrow("USUARIO", null, admin);

            ContentValues inactiveAdmin = new ContentValues();
            inactiveAdmin.put("id_usuario", 43);
            inactiveAdmin.put("username", "inactive.admin");
            inactiveAdmin.put("password_hash", inactiveAdminHash);
            inactiveAdmin.put("nombre_completo", "Inactive Existing Admin");
            inactiveAdmin.put("id_rol", 1);
            inactiveAdmin.put("activo", 0);
            db.insertOrThrow("USUARIO", null, inactiveAdmin);

            ContentValues operator = new ContentValues();
            operator.put("id_usuario", 42);
            operator.put("username", "operator.user");
            operator.put("password_hash", operatorHash);
            operator.put("nombre_completo", "Existing Operator");
            operator.put("id_rol", 2);
            db.insertOrThrow("USUARIO", null, operator);
            db.execSQL("PRAGMA user_version=7");
        } finally {
            db.close();
        }
    }
}
