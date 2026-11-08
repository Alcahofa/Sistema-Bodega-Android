package com.example.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OpeningMovementTest {
    private static final String DB_NAME = "opening_movement_test.db";
    private Context context;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DB_NAME);
    }

    @After public void tearDown() {
        context.deleteDatabase(DB_NAME);
    }

    @Test public void freshSchemaCreatesOpeningOnlyForPositiveInitialStock() {
        DBHelper helper = new DBHelper(context, DB_NAME);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(9, DBHelper.scalar(db, "PRAGMA user_version"));
            long userId = helper.createInitialAdmin("opening.admin", "Opening Admin", "SecurePass_72");
            long categoryId = insertCategory(helper);

            long zeroProductId = helper.createProductWithOpening(productValues("SKU-ZERO", categoryId), 0, userId);
            db = helper.getReadableDatabase();
            assertEquals(0, DBHelper.scalar(db,
                    "SELECT COUNT(*) FROM MOVIMIENTO WHERE id_producto=" + zeroProductId));
            assertEquals(0, DBHelper.scalar(db,
                    "SELECT stock_actual FROM PRODUCTO WHERE id_producto=" + zeroProductId));

            long positiveProductId = helper.createProductWithOpening(
                    productValues("SKU-OPEN", categoryId), 7, userId);
            db = helper.getReadableDatabase();
            assertEquals(7, DBHelper.scalar(db,
                    "SELECT stock_actual FROM PRODUCTO WHERE id_producto=" + positiveProductId));
            try (Cursor c = db.rawQuery(
                    "SELECT id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,id_proveedor,nota " +
                            "FROM MOVIMIENTO WHERE id_producto=?", new String[]{String.valueOf(positiveProductId)})) {
                assertTrue(c.moveToFirst());
                assertEquals("APERTURA", c.getString(1));
                assertEquals(7, c.getInt(2));
                assertFalse(c.isNull(3));
                assertEquals(positiveProductId, c.getLong(4));
                assertEquals(userId, c.getLong(5));
                assertTrue(c.isNull(6));
                assertEquals("Stock inicial", c.getString(7));
                assertFalse(c.moveToNext());
            }
        } finally {
            helper.close();
        }
    }

    @Test public void failedOpeningInsertRollsBackProductAndMovement() {
        DBHelper helper = new DBHelper(context, DB_NAME);
        try {
            helper.getWritableDatabase();
            long categoryId = insertCategory(helper);
            try {
                helper.createProductWithOpening(productValues("SKU-ROLLBACK", categoryId), 4, 9999);
                fail("La apertura debe exigir un usuario válido.");
            } catch (SQLiteConstraintException expected) {
                // La FK del usuario rechaza el movimiento.
            }
            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(0, DBHelper.scalar(db,
                    "SELECT COUNT(*) FROM PRODUCTO WHERE sku_codigo='SKU-ROLLBACK'"));
            assertEquals(0, DBHelper.scalar(db,
                    "SELECT COUNT(*) FROM MOVIMIENTO WHERE tipo='APERTURA'"));
        } finally {
            helper.close();
        }
    }

    @Test public void retryWithSameSkuDoesNotCreateDuplicateProductOrOpening() {
        DBHelper helper = new DBHelper(context, DB_NAME);
        try {
            helper.getWritableDatabase();
            long userId = helper.createInitialAdmin("retry.admin", "Retry Admin", "SecurePass_72");
            long categoryId = insertCategory(helper);
            long productId = helper.createProductWithOpening(
                    productValues("SKU-RETRY", categoryId), 6, userId);

            try {
                helper.createProductWithOpening(productValues("SKU-RETRY", categoryId), 6, userId);
                fail("La repetición con el mismo SKU debe rechazarse por la restricción única.");
            } catch (SQLiteConstraintException expected) {
                // La repetición no debe dejar otra apertura ni un producto parcial.
            }

            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(1, DBHelper.scalar(db,
                    "SELECT COUNT(*) FROM PRODUCTO WHERE sku_codigo='SKU-RETRY'"));
            assertEquals(1, DBHelper.scalar(db,
                    "SELECT COUNT(*) FROM MOVIMIENTO WHERE id_producto=" + productId + " AND tipo='APERTURA'"));
            assertEquals(6, DBHelper.scalar(db,
                    "SELECT stock_actual FROM PRODUCTO WHERE id_producto=" + productId));
        } finally {
            helper.close();
        }
    }

    @Test public void v8MigrationPreservesMovementRowsForeignKeysAndSequence() {
        createVersion8Database();
        DBHelper helper = new DBHelper(context, DB_NAME);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(9, DBHelper.scalar(db, "PRAGMA user_version"));
            assertEquals(2, DBHelper.scalar(db, "SELECT COUNT(*) FROM MOVIMIENTO"));
            try (Cursor c = db.rawQuery(
                    "SELECT id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,id_proveedor,nota " +
                            "FROM MOVIMIENTO WHERE id_movimiento=11", null)) {
                assertTrue(c.moveToFirst());
                assertEquals("ENTRADA", c.getString(1));
                assertEquals(5, c.getInt(2));
                assertEquals("2024-01-10 09:15:00", c.getString(3));
                assertEquals(3, c.getLong(4));
                assertEquals(7, c.getLong(5));
                assertEquals(5, c.getLong(6));
                assertEquals("entrada anterior", c.getString(7));
            }
            try (Cursor c = db.rawQuery(
                    "SELECT id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,id_proveedor,nota " +
                            "FROM MOVIMIENTO WHERE id_movimiento=29", null)) {
                assertTrue(c.moveToFirst());
                assertEquals("SALIDA", c.getString(1));
                assertEquals(2, c.getInt(2));
                assertEquals("2024-01-11 10:25:00", c.getString(3));
                assertEquals(3, c.getLong(4));
                assertEquals(7, c.getLong(5));
                assertTrue(c.isNull(6));
                assertEquals("salida anterior", c.getString(7));
            }
            try (Cursor c = db.rawQuery("PRAGMA foreign_key_check", null)) {
                assertFalse(c.moveToFirst());
            }
            assertEquals(60, DBHelper.scalar(db,
                    "SELECT seq FROM sqlite_sequence WHERE name='MOVIMIENTO'"));

            assertEquals(61, db.insertOrThrow("MOVIMIENTO", null,
                    movementValues("ENTRADA", 3, 3, 7)));
            assertEquals(62, db.insertOrThrow("MOVIMIENTO", null,
                    movementValues("SALIDA", 1, 3, 7)));
            assertEquals(63, db.insertOrThrow("MOVIMIENTO", null,
                    movementValues("APERTURA", 4, 3, 7)));
            assertEquals(63, DBHelper.scalar(db,
                    "SELECT seq FROM sqlite_sequence WHERE name='MOVIMIENTO'"));
            try (Cursor c = db.rawQuery(
                    "SELECT id_movimiento,tipo FROM MOVIMIENTO WHERE id_movimiento>=61 ORDER BY id_movimiento", null)) {
                assertTrue(c.moveToFirst());
                assertEquals(61, c.getLong(0));
                assertEquals("ENTRADA", c.getString(1));
                assertTrue(c.moveToNext());
                assertEquals(62, c.getLong(0));
                assertEquals("SALIDA", c.getString(1));
                assertTrue(c.moveToNext());
                assertEquals(63, c.getLong(0));
                assertEquals("APERTURA", c.getString(1));
                assertFalse(c.moveToNext());
            }
        } finally {
            helper.close();
        }
    }

    @Test public void v7MigrationPreservesHistoryAndDoesNotCreateRetroactiveOpenings() {
        createVersion7Database();
        DBHelper helper = new DBHelper(context, DB_NAME);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(9, DBHelper.scalar(db, "PRAGMA user_version"));
            assertEquals(2, DBHelper.scalar(db, "SELECT COUNT(*) FROM MOVIMIENTO"));
            assertEquals(0, DBHelper.scalar(db,
                    "SELECT COUNT(*) FROM MOVIMIENTO WHERE tipo='APERTURA'"));
            assertEquals(1, DBHelper.scalar(db,
                    "SELECT debe_cambiar_clave FROM USUARIO WHERE id_usuario=51"));
            try (Cursor c = db.rawQuery(
                    "SELECT id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,id_proveedor,nota " +
                            "FROM MOVIMIENTO WHERE id_movimiento=5", null)) {
                assertTrue(c.moveToFirst());
                assertEquals("ENTRADA", c.getString(1));
                assertEquals(6, c.getInt(2));
                assertEquals("2023-06-12 08:30:00", c.getString(3));
                assertEquals(4, c.getLong(4));
                assertEquals(51, c.getLong(5));
                assertEquals(3, c.getLong(6));
                assertEquals("entrada histórica v7", c.getString(7));
            }
            try (Cursor c = db.rawQuery(
                    "SELECT id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,id_proveedor,nota " +
                            "FROM MOVIMIENTO WHERE id_movimiento=8", null)) {
                assertTrue(c.moveToFirst());
                assertEquals("SALIDA", c.getString(1));
                assertEquals(2, c.getInt(2));
                assertEquals("2023-06-13 09:45:00", c.getString(3));
                assertEquals(4, c.getLong(4));
                assertEquals(51, c.getLong(5));
                assertTrue(c.isNull(6));
                assertEquals("salida histórica v7", c.getString(7));
            }
            try (Cursor c = db.rawQuery("PRAGMA foreign_key_check", null)) {
                assertFalse(c.moveToFirst());
            }
        } finally {
            helper.close();
        }
    }

    private long insertCategory(DBHelper helper) {
        ContentValues category = new ContentValues();
        category.put("nombre_categoria", "General");
        category.put("descripcion", "");
        SQLiteDatabase db = helper.getWritableDatabase();
        try { return db.insertOrThrow("CATEGORIA", null, category); }
        finally { db.close(); }
    }

    private ContentValues productValues(String sku, long categoryId) {
        ContentValues product = new ContentValues();
        product.put("sku_codigo", sku);
        product.put("codigo_barras", "");
        product.put("nombre", "Producto " + sku);
        product.put("descripcion", "");
        product.put("stock_minimo", 0);
        product.put("unidad_medida", "unidad");
        product.put("id_categoria", categoryId);
        product.put("activo", 1);
        return product;
    }

    private ContentValues movementValues(String type, int quantity, long productId, long userId) {
        ContentValues movement = new ContentValues();
        movement.put("tipo", type);
        movement.put("cantidad", quantity);
        movement.put("id_producto", productId);
        movement.put("id_usuario", userId);
        return movement;
    }

    private void createVersion7Database() {
        java.io.File databaseFile = context.getDatabasePath(DB_NAME);
        java.io.File databaseDirectory = databaseFile.getParentFile();
        if (databaseDirectory != null && !databaseDirectory.exists()) databaseDirectory.mkdirs();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        try {
            db.setForeignKeyConstraintsEnabled(true);
            db.execSQL("CREATE TABLE ROL (id_rol INTEGER PRIMARY KEY AUTOINCREMENT,nombre_rol TEXT NOT NULL UNIQUE," +
                    "descripcion TEXT NOT NULL DEFAULT '',activo INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE TABLE USUARIO (id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT NOT NULL UNIQUE," +
                    "password_hash TEXT NOT NULL,nombre_completo TEXT NOT NULL,id_rol INTEGER NOT NULL,activo INTEGER NOT NULL DEFAULT 1," +
                    "FOREIGN KEY(id_rol) REFERENCES ROL(id_rol))");
            db.execSQL("CREATE TABLE CATEGORIA (id_categoria INTEGER PRIMARY KEY AUTOINCREMENT,nombre_categoria TEXT NOT NULL UNIQUE," +
                    "descripcion TEXT NOT NULL DEFAULT '',activo INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE TABLE PROVEEDOR (id_proveedor INTEGER PRIMARY KEY AUTOINCREMENT,rut_empresa TEXT NOT NULL UNIQUE," +
                    "razon_social TEXT NOT NULL,contacto TEXT NOT NULL,activo INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE TABLE PRODUCTO (id_producto INTEGER PRIMARY KEY AUTOINCREMENT,sku_codigo TEXT NOT NULL UNIQUE," +
                    "codigo_barras TEXT NOT NULL DEFAULT '',nombre TEXT NOT NULL,descripcion TEXT NOT NULL DEFAULT ''," +
                    "stock_actual INTEGER NOT NULL DEFAULT 0 CHECK(stock_actual>=0),stock_minimo INTEGER NOT NULL DEFAULT 0 CHECK(stock_minimo>=0)," +
                    "unidad_medida TEXT NOT NULL DEFAULT 'unidad',id_categoria INTEGER NOT NULL,activo INTEGER NOT NULL DEFAULT 1," +
                    "FOREIGN KEY(id_categoria) REFERENCES CATEGORIA(id_categoria))");
            db.execSQL("CREATE TABLE MOVIMIENTO (id_movimiento INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "tipo TEXT NOT NULL CHECK(tipo IN ('ENTRADA','SALIDA')),cantidad INTEGER NOT NULL CHECK(cantidad>0)," +
                    "fecha TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,id_producto INTEGER NOT NULL,id_usuario INTEGER,id_proveedor INTEGER," +
                    "nota TEXT NOT NULL DEFAULT '',FOREIGN KEY(id_producto) REFERENCES PRODUCTO(id_producto)," +
                    "FOREIGN KEY(id_usuario) REFERENCES USUARIO(id_usuario),FOREIGN KEY(id_proveedor) REFERENCES PROVEEDOR(id_proveedor))");
            db.execSQL("INSERT INTO ROL(id_rol,nombre_rol) VALUES(1,'Administrador')");
            db.execSQL("INSERT INTO USUARIO(id_usuario,username,password_hash,nombre_completo,id_rol) " +
                    "VALUES(51,'admin.v7','hash-v7','Administrador legado',1)");
            db.execSQL("INSERT INTO CATEGORIA(id_categoria,nombre_categoria) VALUES(6,'General v7')");
            db.execSQL("INSERT INTO PROVEEDOR(id_proveedor,rut_empresa,razon_social,contacto) " +
                    "VALUES(3,'22-2','Proveedor v7','321')");
            db.execSQL("INSERT INTO PRODUCTO(id_producto,sku_codigo,nombre,id_categoria,stock_actual) " +
                    "VALUES(4,'SKU-V7','Producto legado',6,9)");
            db.execSQL("INSERT INTO MOVIMIENTO(id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,id_proveedor,nota) " +
                    "VALUES(5,'ENTRADA',6,'2023-06-12 08:30:00',4,51,3,'entrada histórica v7')");
            db.execSQL("INSERT INTO MOVIMIENTO(id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,nota) " +
                    "VALUES(8,'SALIDA',2,'2023-06-13 09:45:00',4,51,'salida histórica v7')");
            db.execSQL("PRAGMA user_version=7");
        } finally {
            db.close();
        }
    }

    private void createVersion8Database() {
        java.io.File databaseFile = context.getDatabasePath(DB_NAME);
        java.io.File databaseDirectory = databaseFile.getParentFile();
        if (databaseDirectory != null && !databaseDirectory.exists()) databaseDirectory.mkdirs();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        try {
            db.setForeignKeyConstraintsEnabled(true);
            db.execSQL("CREATE TABLE ROL (id_rol INTEGER PRIMARY KEY AUTOINCREMENT,nombre_rol TEXT NOT NULL UNIQUE," +
                    "descripcion TEXT NOT NULL DEFAULT '',activo INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE TABLE USUARIO (id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT NOT NULL UNIQUE," +
                    "password_hash TEXT NOT NULL,nombre_completo TEXT NOT NULL,id_rol INTEGER NOT NULL,activo INTEGER NOT NULL DEFAULT 1," +
                    "debe_cambiar_clave INTEGER NOT NULL DEFAULT 0 CHECK(debe_cambiar_clave IN (0,1))," +
                    "FOREIGN KEY(id_rol) REFERENCES ROL(id_rol))");
            db.execSQL("CREATE TABLE CATEGORIA (id_categoria INTEGER PRIMARY KEY AUTOINCREMENT,nombre_categoria TEXT NOT NULL UNIQUE," +
                    "descripcion TEXT NOT NULL DEFAULT '',activo INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE TABLE PROVEEDOR (id_proveedor INTEGER PRIMARY KEY AUTOINCREMENT,rut_empresa TEXT NOT NULL UNIQUE," +
                    "razon_social TEXT NOT NULL,contacto TEXT NOT NULL,activo INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE TABLE PRODUCTO (id_producto INTEGER PRIMARY KEY AUTOINCREMENT,sku_codigo TEXT NOT NULL UNIQUE," +
                    "codigo_barras TEXT NOT NULL DEFAULT '',nombre TEXT NOT NULL,descripcion TEXT NOT NULL DEFAULT ''," +
                    "stock_actual INTEGER NOT NULL DEFAULT 0 CHECK(stock_actual>=0),stock_minimo INTEGER NOT NULL DEFAULT 0 CHECK(stock_minimo>=0)," +
                    "unidad_medida TEXT NOT NULL DEFAULT 'unidad',id_categoria INTEGER NOT NULL,activo INTEGER NOT NULL DEFAULT 1," +
                    "FOREIGN KEY(id_categoria) REFERENCES CATEGORIA(id_categoria))");
            db.execSQL("CREATE TABLE MOVIMIENTO (id_movimiento INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "tipo TEXT NOT NULL CHECK(tipo IN ('ENTRADA','SALIDA')),cantidad INTEGER NOT NULL CHECK(cantidad>0)," +
                    "fecha TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,id_producto INTEGER NOT NULL,id_usuario INTEGER,id_proveedor INTEGER," +
                    "nota TEXT NOT NULL DEFAULT '',FOREIGN KEY(id_producto) REFERENCES PRODUCTO(id_producto)," +
                    "FOREIGN KEY(id_usuario) REFERENCES USUARIO(id_usuario),FOREIGN KEY(id_proveedor) REFERENCES PROVEEDOR(id_proveedor))");
            db.execSQL("INSERT INTO ROL(id_rol,nombre_rol) VALUES(1,'Administrador')");
            db.execSQL("INSERT INTO USUARIO(id_usuario,username,password_hash,nombre_completo,id_rol) " +
                    "VALUES(7,'migration.user','hash','Usuario migración',1)");
            db.execSQL("INSERT INTO CATEGORIA(id_categoria,nombre_categoria) VALUES(4,'General')");
            db.execSQL("INSERT INTO PROVEEDOR(id_proveedor,rut_empresa,razon_social,contacto) " +
                    "VALUES(5,'11-1','Proveedor','123')");
            db.execSQL("INSERT INTO PRODUCTO(id_producto,sku_codigo,nombre,id_categoria,stock_actual) " +
                    "VALUES(3,'SKU-MIG','Producto migración',4,10)");
            db.execSQL("INSERT INTO MOVIMIENTO(id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,id_proveedor,nota) " +
                    "VALUES(11,'ENTRADA',5,'2024-01-10 09:15:00',3,7,5,'entrada anterior')");
            db.execSQL("INSERT INTO MOVIMIENTO(id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,nota) " +
                    "VALUES(29,'SALIDA',2,'2024-01-11 10:25:00',3,7,'salida anterior')");
            db.execSQL("INSERT INTO MOVIMIENTO(id_movimiento,tipo,cantidad,id_producto,id_usuario) " +
                    "VALUES(60,'ENTRADA',1,3,7)");
            db.execSQL("DELETE FROM MOVIMIENTO WHERE id_movimiento=60");
            db.execSQL("PRAGMA user_version=8");
        } finally {
            db.close();
        }
    }
}
