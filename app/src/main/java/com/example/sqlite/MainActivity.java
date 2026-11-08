package com.example.sqlite;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private DBHelper helper;
    private SessionStore session;
    private OnBackPressedCallback passwordRotationBackCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        passwordRotationBackCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                toast("Debes cambiar la contraseña para continuar.");
            }
        };
        getOnBackPressedDispatcher().addCallback(this, passwordRotationBackCallback);
        helper = new DBHelper(this);
        helper.getWritableDatabase();
        session = new SessionStore(this);
        if (session.active() && refreshSession()) {
            if (helper.requiresPasswordChange(session.id())) {
                setPasswordRotationRequired(true);
                showPasswordRotation();
            } else dashboard();
        } else if (userCount() == 0) {
            session.clear();
            showInitialAdminSetup();
        } else {
            session.clear();
            showLogin();
        }
    }

    private long userCount() {
        SQLiteDatabase db = helper.getReadableDatabase();
        try { return DBHelper.scalar(db, "SELECT COUNT(*) FROM USUARIO"); }
        finally { db.close(); }
    }

    private boolean refreshSession() {
        long id = session.id();
        if (id < 1) return false;
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT U.username,U.nombre_completo,U.activo,R.nombre_rol,R.activo " +
                        "FROM USUARIO U JOIN ROL R ON R.id_rol=U.id_rol WHERE U.id_usuario=?",
                new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst() || c.getInt(2) != 1 || c.getInt(4) != 1) return false;
            session.save(id, c.getString(0), c.getString(1), c.getString(3));
            return true;
        } finally {
            db.close();
        }
    }

    private void showLogin() {
        setContentView(R.layout.activity_main);
        EditText username = findViewById(R.id.etUsername);
        EditText password = findViewById(R.id.etPassword);
        Button login = findViewById(R.id.btnLogin);
        login.setOnClickListener(v -> authenticate(username, password));
        findViewById(R.id.btnLoginLocation).setOnClickListener(v -> {
            Intent intent = new Intent(this, LocationActivity.class);
            intent.putExtra(LocationActivity.EXTRA_PUBLIC_VIEW, true);
            startActivity(intent);
        });
    }

    private void authenticate(EditText username, EditText password) {
        String user = username.getText().toString().trim();
        String pass = password.getText().toString();
        if (user.isEmpty() || pass.isEmpty()) {
            toast("Ingresa tu usuario y contraseña.");
            return;
        }
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT U.id_usuario,U.username,U.nombre_completo,U.activo,U.password_hash," +
                        "R.nombre_rol,R.activo,U.debe_cambiar_clave FROM USUARIO U JOIN ROL R ON R.id_rol=U.id_rol " +
                        "WHERE U.username=?",
                new String[]{user})) {
            if (!c.moveToFirst() || !DBHelper.verify(pass, c.getString(4))) {
                password.setText("");
                toast("Credenciales inválidas.");
                return;
            }
            if (c.getInt(3) != 1) {
                toast("Usuario deshabilitado. Contacta al administrador.");
                return;
            }
            if (c.getInt(6) != 1) {
                toast("El rol está deshabilitado.");
                return;
            }
            session.save(c.getLong(0), c.getString(1), c.getString(2), c.getString(5));
            if (c.getInt(7) == 1) {
                setPasswordRotationRequired(true);
                showPasswordRotation();
            } else dashboard();
        } finally {
            db.close();
        }
    }

    private void showInitialAdminSetup() {
        setContentView(R.layout.activity_initial_admin_setup);
        EditText name = findViewById(R.id.etInitialAdminName);
        EditText username = findViewById(R.id.etInitialAdminUsername);
        EditText password = findViewById(R.id.etInitialAdminPassword);
        EditText confirmation = findViewById(R.id.etInitialAdminPasswordConfirmation);
        findViewById(R.id.btnCreateInitialAdmin).setOnClickListener(v -> {
            String fullName = name.getText().toString().trim();
            String user = username.getText().toString().trim();
            String secret = password.getText().toString();
            String confirmed = confirmation.getText().toString();
            if (fullName.isEmpty() || user.isEmpty()) {
                toast("Ingresa el nombre y el usuario del administrador.");
                return;
            }
            if (!DBHelper.meetsPasswordMinimum(secret)) {
                toast("La contraseña debe tener al menos 8 caracteres.");
                return;
            }
            if (!secret.equals(confirmed)) {
                toast("Las contraseñas no coinciden.");
                return;
            }
            try {
                long id = helper.createInitialAdmin(user, fullName, secret);
                session.save(id, user, fullName, "Administrador");
                dashboard();
            } catch (IllegalStateException e) {
                if (userCount() == 0) toast("No se pudo crear el administrador. Revisa los datos.");
                else showLogin();
            } catch (Exception e) {
                toast("No se pudo crear el administrador. Revisa los datos.");
            }
        });
    }

    private void showPasswordRotation() {
        setContentView(R.layout.activity_password_rotation);
        EditText password = findViewById(R.id.etNewPassword);
        EditText confirmation = findViewById(R.id.etNewPasswordConfirmation);
        findViewById(R.id.btnSaveNewPassword).setOnClickListener(v -> {
            String next = password.getText().toString();
            if (!DBHelper.meetsPasswordMinimum(next)) {
                toast("La contraseña debe tener al menos 8 caracteres.");
                return;
            }
            if (!next.equals(confirmation.getText().toString())) {
                toast("Las contraseñas no coinciden.");
                return;
            }
            try {
                if (!helper.rotatePassword(session.id(), next)) {
                    session.clear();
                    setPasswordRotationRequired(false);
                    showLogin();
                    toast("Vuelve a iniciar sesión para continuar.");
                    return;
                }
                setPasswordRotationRequired(false);
                dashboard();
                toast("Contraseña actualizada.");
            } catch (Exception e) {
                toast("No se pudo actualizar la contraseña. Inténtalo nuevamente.");
            }
        });
        findViewById(R.id.btnSignOutDuringPasswordRotation).setOnClickListener(v -> {
            session.clear();
            setPasswordRotationRequired(false);
            showLogin();
        });
    }

    private void dashboard() {
        setContentView(R.layout.activity_dashboard);
        TextView welcome = findViewById(R.id.tvWelcome);
        String name = session.name();
        String role = session.role();
        welcome.setText(name.equalsIgnoreCase(role) ? role : name + " · " + role);

        Button inventory = findViewById(R.id.btnInventory);
        Button movements = findViewById(R.id.btnMovements);
        Button stockReport = findViewById(R.id.btnStockReport);
        Button movementHistory = findViewById(R.id.btnMovementHistory);
        Button categories = findViewById(R.id.btnCategories);
        Button location = findViewById(R.id.btnLocation);
        Button admin = findViewById(R.id.btnAdmin);
        Button logout = findViewById(R.id.btnLogout);

        inventory.setOnClickListener(v -> startActivity(new Intent(this, Leer.class)));
        movements.setOnClickListener(v -> startActivity(new Intent(this, MovimientosActivity.class)));
        stockReport.setOnClickListener(v -> startActivity(new Intent(this, StockReportActivity.class)));
        movementHistory.setOnClickListener(v -> startActivity(new Intent(this, HistorialActivity.class)));
        categories.setOnClickListener(v -> startActivity(new Intent(this, CategoriasActivity.class)));
        boolean canUsers=PermissionStore.allowed(this,session.id(),"USER_MANAGE");
        boolean canProfiles=PermissionStore.allowed(this,session.id(),"PROFILE_MANAGE");
        boolean canMasterData=PermissionStore.allowed(this,session.id(),"MASTER_DATA_MANAGE");
        inventory.setVisibility(PermissionStore.allowed(this,session.id(),"PRODUCT_VIEW")?View.VISIBLE:View.GONE);
        movements.setVisibility(PermissionStore.allowed(this,session.id(),"MOVEMENT_VIEW")||PermissionStore.allowed(this,session.id(),"MOVEMENT_ENTRY")||PermissionStore.allowed(this,session.id(),"MOVEMENT_EXIT")?View.VISIBLE:View.GONE);
        stockReport.setVisibility(PermissionStore.allowed(this,session.id(),"REPORT_VIEW")?View.VISIBLE:View.GONE);
        movementHistory.setVisibility(PermissionStore.allowed(this,session.id(),"HISTORY_VIEW")?View.VISIBLE:View.GONE);
        categories.setVisibility(PermissionStore.allowed(this,session.id(),"CATEGORY_VIEW")?View.VISIBLE:View.GONE);
        boolean locationAccess=PermissionStore.allowed(this,session.id(),"LOCATION_VIEW")||PermissionStore.allowed(this,session.id(),"LOCATION_MANAGE");
        location.setVisibility(locationAccess?View.VISIBLE:View.GONE);
        location.setOnClickListener(v->startActivity(new Intent(this,LocationActivity.class)));
        admin.setVisibility(canAccessAdminPanel(canUsers,canProfiles,canMasterData)?View.VISIBLE:View.GONE);
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        logout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Cerrar la sesión actual?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar", (dialog, which) -> {
                    session.clear();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }).show());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    static boolean canAccessAdminPanel(boolean canUsers, boolean canProfiles, boolean canMasterData) {
        return canUsers || canProfiles || canMasterData;
    }

    private void setPasswordRotationRequired(boolean required) {
        if (passwordRotationBackCallback != null) passwordRotationBackCallback.setEnabled(required);
    }

    @Override
    protected void onDestroy() {
        if (helper != null) helper.close();
        super.onDestroy();
    }

}
