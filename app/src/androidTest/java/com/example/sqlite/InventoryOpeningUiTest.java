package com.example.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** UI integration checks. Run only on an AVD with the isolated phase2Test application ID. */
@RunWith(AndroidJUnit4.class)
public class InventoryOpeningUiTest {
    private static final String APP_DB_NAME = "BD_BODEGA.db";
    private static final String PASSWORD = "SecurePass_UI_72";

    private Context context;
    private DBHelper helper;
    private long adminId;
    private long categoryId;
    private boolean isolatedPackageVerified;
    private ActivityScenario<Leer> inventoryScenario;
    private ActivityScenario<HistorialActivity> historyScenario;

    @Before public void setUp() {
        Assume.assumeTrue("Physical devices are not allowed for this database-resetting test.", isEmulator());

        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue("Use the isolated phase2Test variant; refusing to touch the regular app database.",
                "com.example.sqlite.phase2test".equals(context.getPackageName()));
        isolatedPackageVerified = true;
        context.deleteDatabase(APP_DB_NAME);
        helper = new DBHelper(context);
        helper.getWritableDatabase();
        adminId = helper.createInitialAdmin("ui.audit.admin", "UI Audit Admin", PASSWORD);
        categoryId = insertCategory("UI Audit Category");
    }

    @After public void tearDown() {
        if (inventoryScenario != null) inventoryScenario.close();
        if (historyScenario != null) historyScenario.close();
        if (context == null || !isolatedPackageVerified) return;
        new SessionStore(context).clear();
        if (helper != null) helper.close();
        context.deleteDatabase(APP_DB_NAME);
    }

    @Test public void inventoryCreatePropagatesSessionUserAndEditDoesNotCreateOpening() {
        openInventory();
        enterNewProduct("UI-OPEN-1", "UI Open Product", "5");

        long productId = productId("UI-OPEN-1");
        assertEquals(5, scalar("SELECT stock_actual FROM PRODUCTO WHERE id_producto=" + productId));
        assertEquals(1, scalar("SELECT COUNT(*) FROM MOVIMIENTO WHERE id_producto=" + productId
                + " AND tipo='APERTURA'"));
        assertEquals(adminId, scalar("SELECT id_usuario FROM MOVIMIENTO WHERE id_producto=" + productId
                + " AND tipo='APERTURA'"));

        waitForInventoryRow("UI-OPEN-1");
        onData(hasToString(containsString("UI-OPEN-1")))
                .inAdapterView(withId(R.id.listProducts)).perform(click());
        onView(withText("Editar")).perform(click());
        onView(ViewMatchers.withHint("Nombre del producto"))
                .perform(replaceText("UI Open Product Edited"), closeSoftKeyboard());
        onView(withText("Guardar")).perform(click());
        waitForSql("SELECT COUNT(*) FROM PRODUCTO WHERE id_producto=" + productId
                + " AND nombre='UI Open Product Edited'", 1);

        assertEquals(5, scalar("SELECT stock_actual FROM PRODUCTO WHERE id_producto=" + productId));
        assertEquals(1, scalar("SELECT COUNT(*) FROM MOVIMIENTO WHERE id_producto=" + productId
                + " AND tipo='APERTURA'"));

        enterNewProduct("UI-OPEN-0", "UI Zero Product", "0");
        long zeroProductId = productId("UI-OPEN-0");
        assertEquals(0, scalar("SELECT stock_actual FROM PRODUCTO WHERE id_producto=" + zeroProductId));
        assertEquals(0, scalar("SELECT COUNT(*) FROM MOVIMIENTO WHERE id_producto=" + zeroProductId
                + " AND tipo='APERTURA'"));
    }

    @Test public void historyFiltersOpeningLabelAndCountsMatchStoredRows() {
        long secondUserId = insertUser("ui.audit.operator", "UI Audit Operator");
        long firstProductId = createProduct("UI-HIST-1", "History Product One", 3, adminId);
        long secondProductId = createProduct("UI-HIST-2", "History Product Two", 2, secondUserId);
        insertMovement("ENTRADA", 4, firstProductId, adminId);
        insertMovement("SALIDA", 1, secondProductId, secondUserId);

        saveSession();
        historyScenario = ActivityScenario.launch(HistorialActivity.class);
        waitForHistorySummary("Movimientos: 4 · Entradas: 1 · Salidas: 1 · Aperturas: 2");

        selectSpinnerItem(R.id.spinnerHistoryType, "Aperturas de stock");
        waitForHistorySummary("Movimientos: 2 · Entradas: 0 · Salidas: 0 · Aperturas: 2");
        onData(hasToString(containsString("APERTURA")))
                .inAdapterView(withId(R.id.listHistory)).atPosition(0).check(matches(isDisplayed()));

        selectSpinnerItem(R.id.spinnerHistoryType, "Entradas");
        waitForHistorySummary("Movimientos: 1 · Entradas: 1 · Salidas: 0 · Aperturas: 0");
        onData(hasToString(containsString("ENTRADA")))
                .inAdapterView(withId(R.id.listHistory)).check(matches(isDisplayed()));

        selectSpinnerItem(R.id.spinnerHistoryType, "Salidas");
        waitForHistorySummary("Movimientos: 1 · Entradas: 0 · Salidas: 1 · Aperturas: 0");
        onData(hasToString(containsString("SALIDA")))
                .inAdapterView(withId(R.id.listHistory)).check(matches(isDisplayed()));

        selectSpinnerItem(R.id.spinnerHistoryType, "Aperturas de stock");

        selectSpinnerItem(R.id.spinnerHistoryUser, "UI Audit Operator");
        waitForHistorySummary("Movimientos: 1 · Entradas: 0 · Salidas: 0 · Aperturas: 1");
        selectSpinnerItem(R.id.spinnerHistoryProduct, "History Product Two");
        waitForHistorySummary("Movimientos: 1 · Entradas: 0 · Salidas: 0 · Aperturas: 1");

        selectSpinnerItem(R.id.spinnerHistoryType, "Todos los movimientos");
        waitForHistorySummary("Movimientos: 2 · Entradas: 0 · Salidas: 1 · Aperturas: 1");
        assertEquals(2, scalar("SELECT COUNT(*) FROM MOVIMIENTO WHERE id_producto=" + secondProductId));
    }

    private void openInventory() {
        saveSession();
        inventoryScenario = ActivityScenario.launch(Leer.class);
        onView(withId(R.id.btnAddProduct)).check(matches(isDisplayed()));
    }

    private void saveSession() {
        new SessionStore(context).save(adminId, "ui.audit.admin", "UI Audit Admin", "Administrador");
    }

    private void enterNewProduct(String sku, String productName, String initialStock) {
        onView(withId(R.id.btnAddProduct)).perform(click());
        enterField("Código SKU único", sku);
        enterField("Nombre del producto", productName);
        enterField("Descripción", "Creado por prueba instrumentada");
        enterField("Unidad de medida (ej. unidad, kg, litro)", "unidad");
        enterField("Stock inicial", initialStock);
        enterField("Stock mínimo", "1");
        onView(withText("Guardar")).perform(click());
        waitForSql("SELECT COUNT(*) FROM PRODUCTO WHERE sku_codigo='" + sku + "'", 1);
    }

    private void enterField(String hint, String value) {
        if (value.isEmpty()) return;
        onView(ViewMatchers.withHint(hint)).perform(replaceText(value), closeSoftKeyboard());
    }

    private long insertCategory(String categoryName) {
        ContentValues values = new ContentValues();
        values.put("nombre_categoria", categoryName);
        values.put("descripcion", "");
        SQLiteDatabase db = helper.getWritableDatabase();
        try { return db.insertOrThrow("CATEGORIA", null, values); }
        finally { db.close(); }
    }

    private long insertUser(String username, String fullName) {
        long roleId = scalar("SELECT id_rol FROM ROL WHERE nombre_rol='Administrador'");
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password_hash", DBHelper.hash(PASSWORD));
        values.put("nombre_completo", fullName);
        values.put("id_rol", roleId);
        values.put("activo", 1);
        values.put("debe_cambiar_clave", 0);
        SQLiteDatabase db = helper.getWritableDatabase();
        try { return db.insertOrThrow("USUARIO", null, values); }
        finally { db.close(); }
    }

    private long createProduct(String sku, String productName, int stock, long userId) {
        ContentValues product = new ContentValues();
        product.put("sku_codigo", sku);
        product.put("codigo_barras", "");
        product.put("nombre", productName);
        product.put("descripcion", "");
        product.put("stock_minimo", 0);
        product.put("unidad_medida", "unidad");
        product.put("id_categoria", categoryId);
        product.put("activo", 1);
        return helper.createProductWithOpening(product, stock, userId);
    }

    private void insertMovement(String type, int quantity, long productId, long userId) {
        ContentValues movement = new ContentValues();
        movement.put("tipo", type);
        movement.put("cantidad", quantity);
        movement.put("id_producto", productId);
        movement.put("id_usuario", userId);
        movement.put("nota", "Fixture de historial");
        SQLiteDatabase db = helper.getWritableDatabase();
        try { db.insertOrThrow("MOVIMIENTO", null, movement); }
        finally { db.close(); }
    }

    private long productId(String sku) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT id_producto FROM PRODUCTO WHERE sku_codigo=?",
                new String[]{sku})) {
            if (!cursor.moveToFirst()) throw new AssertionError("Product not found: " + sku);
            return cursor.getLong(0);
        } finally { db.close(); }
    }

    private long scalar(String sql) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try { return DBHelper.scalar(db, sql); }
        finally { db.close(); }
    }

    private void waitForSql(String sql, long expected) {
        long deadline = android.os.SystemClock.uptimeMillis() + 8_000L;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (scalar(sql) == expected) return;
            android.os.SystemClock.sleep(100L);
        }
        assertEquals(expected, scalar(sql));
    }

    private void waitForInventoryRow(String sku) {
        long deadline = android.os.SystemClock.uptimeMillis() + 8_000L;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            try {
                onData(hasToString(containsString(sku)))
                        .inAdapterView(withId(R.id.listProducts)).check(matches(isDisplayed()));
                return;
            } catch (AssertionError ignored) {
                android.os.SystemClock.sleep(100L);
            }
        }
        fail("The inventory list did not show " + sku);
    }

    private void waitForHistorySummary(String expected) {
        long deadline = android.os.SystemClock.uptimeMillis() + 8_000L;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            try {
                onView(withId(R.id.tvHistorySummary)).check(matches(withText(containsString(expected))));
                return;
            } catch (AssertionError ignored) {
                android.os.SystemClock.sleep(100L);
            }
        }
        onView(withId(R.id.tvHistorySummary)).check(matches(withText(containsString(expected))));
    }

    private void selectSpinnerItem(int spinnerId, String label) {
        onView(withId(spinnerId)).perform(click());
        onData(hasToString(containsString(label))).perform(click());
    }

    private boolean isEmulator() {
        String model = Build.MODEL == null ? "" : Build.MODEL.toLowerCase(Locale.US);
        String hardware = Build.HARDWARE == null ? "" : Build.HARDWARE.toLowerCase(Locale.US);
        String fingerprint = Build.FINGERPRINT == null ? "" : Build.FINGERPRINT.toLowerCase(Locale.US);
        return model.contains("emulator") || model.contains("sdk") || hardware.contains("ranchu")
                || hardware.contains("goldfish") || fingerprint.startsWith("generic");
    }
}
