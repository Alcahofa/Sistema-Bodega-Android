package com.example.sqlite;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;

import java.util.Date;
import java.util.Locale;

/** Guarda la ubicación del local en SQLite y la muestra en Google Maps. */
public class LocationActivity extends AppCompatActivity implements OnMapReadyCallback {
    public static final String EXTRA_PUBLIC_VIEW = "com.example.sqlite.PUBLIC_LOCATION_VIEW";
    private static final long MAP_LOAD_TIMEOUT_MS = 12_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable mapLoadTimeout = () -> setMapStatus(
            "El mapa no respondió. Puedes abrir Google Maps o las indicaciones abajo.", true);

    private DBHelper helper;
    private EditText name, address, latitude, longitude;
    private TextView updated, mapStatus;
    private boolean canEdit;
    private Double savedLatitude, savedLongitude;
    private String savedAddress = "";
    private GoogleMap googleMap;
    private LocationManager locationManager;
    private CancellationSignal locationCancellation;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    if (isFinishing() || isDestroyed()) return;
                    boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (fine || coarse) locateCurrentPosition();
                    else toast("Permiso de ubicación denegado. El mapa guardado y las indicaciones siguen disponibles.");
                });

        SessionStore session = new SessionStore(this);
        boolean publicView = getIntent().getBooleanExtra(EXTRA_PUBLIC_VIEW, false);
        canEdit = session.active() && PermissionStore.allowed(this, session.id(), "LOCATION_MANAGE");
        if ((!session.active() && !publicView) || (session.active() && !canEdit
                && !PermissionStore.allowed(this, session.id(), "LOCATION_VIEW"))) {
            finish();
            return;
        }

        helper = new DBHelper(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        setContentView(R.layout.activity_location);
        name = findViewById(R.id.etLocationName);
        address = findViewById(R.id.etLocationAddress);
        latitude = findViewById(R.id.etLocationLatitude);
        longitude = findViewById(R.id.etLocationLongitude);
        updated = findViewById(R.id.tvLocationUpdated);
        mapStatus = findViewById(R.id.tvMapStatus);

        MaterialButton save = findViewById(R.id.btnSaveLocation);
        save.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        name.setEnabled(canEdit);
        address.setEnabled(canEdit);
        latitude.setEnabled(canEdit);
        longitude.setEnabled(canEdit);
        findViewById(R.id.btnLocationBack).setOnClickListener(v -> finish());
        save.setOnClickListener(v -> saveLocation());
        findViewById(R.id.btnOpenMap).setOnClickListener(v -> openMap());
        findViewById(R.id.btnDirections).setOnClickListener(v -> openDirections(null));
        findViewById(R.id.btnCurrentLocation).setOnClickListener(v -> requestCurrentLocation());

        loadLocation();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapContainer);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mapContainer, mapFragment)
                    .commit();
        }
        mapFragment.getMapAsync(this);
        handler.postDelayed(mapLoadTimeout, MAP_LOAD_TIMEOUT_MS);
    }

    private void loadLocation() {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT nombre,direccion,latitud,longitud,actualizado_en "
                + "FROM UBICACION_LOCAL WHERE id_ubicacion=1", null)) {
            if (c.moveToFirst()) {
                name.setText(c.getString(0));
                savedAddress = c.getString(1) == null ? "" : c.getString(1).trim();
                address.setText(savedAddress);
                savedLatitude = c.isNull(2) ? null : c.getDouble(2);
                savedLongitude = c.isNull(3) ? null : c.getDouble(3);
                latitude.setText(savedLatitude == null ? "" : String.valueOf(savedLatitude));
                longitude.setText(savedLongitude == null ? "" : String.valueOf(savedLongitude));
                updated.setText("Última actualización: " + c.getString(4));
            }
        } finally {
            db.close();
        }
        showSavedLocationOnMap();
    }

    @Override public void onMapReady(GoogleMap map) {
        googleMap = map;
        showSavedLocationOnMap();
        if (hasSavedCoordinates()) {
            googleMap.setOnMapLoadedCallback(() -> {
                handler.removeCallbacks(mapLoadTimeout);
                mapStatus.setVisibility(View.GONE);
            });
            setMapStatus("Cargando mapa…", true);
        } else {
            handler.removeCallbacks(mapLoadTimeout);
            String message = savedLatitude == null || savedLongitude == null
                    ? "Guarda coordenadas para fijar el local en el mapa. Puedes abrir indicaciones por dirección."
                    : "Las coordenadas guardadas no son válidas. Corrígelas para mostrar el local en el mapa.";
            setMapStatus(message, true);
        }
    }

    private void showSavedLocationOnMap() {
        if (googleMap == null || savedLatitude == null || savedLongitude == null
                || !LocationMapsLinks.isValidCoordinate(savedLatitude, savedLongitude)) return;
        LatLng location = new LatLng(savedLatitude, savedLongitude);
        googleMap.clear();
        googleMap.addMarker(new MarkerOptions().position(location).title(name.getText().toString().trim()));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f));
    }

    private void saveLocation() {
        String title = name.getText().toString().trim();
        String street = address.getText().toString().trim();
        String lat = latitude.getText().toString().trim().replace(',', '.');
        String lon = longitude.getText().toString().trim().replace(',', '.');
        if (title.isEmpty() || (lat.isEmpty() != lon.isEmpty())) {
            toast("Ingresa el nombre y completa ambas coordenadas, o déjalas vacías.");
            return;
        }
        Double latValue = null, lonValue = null;
        if (!lat.isEmpty()) {
            try {
                latValue = Double.parseDouble(lat);
                lonValue = Double.parseDouble(lon);
            } catch (NumberFormatException e) {
                toast("Las coordenadas deben ser números decimales.");
                return;
            }
            if (!LocationMapsLinks.isValidCoordinate(latValue, lonValue)) {
                toast("Latitud: −90 a 90. Longitud: −180 a 180.");
                return;
            }
        }

        ContentValues values = new ContentValues();
        values.put("nombre", title);
        values.put("direccion", street);
        if (latValue == null) {
            values.putNull("latitud");
            values.putNull("longitud");
        } else {
            values.put("latitud", latValue);
            values.put("longitud", lonValue);
        }
        values.put("actualizado_en", new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            db.update("UBICACION_LOCAL", values, "id_ubicacion=1", null);
        } finally {
            db.close();
        }
        toast("Ubicación guardada en SQLite.");
        loadLocation();
    }

    private void openMap() {
        try {
            if (hasSavedCoordinates()) {
                openUrl(LocationMapsLinks.mapUrl(savedLatitude, savedLongitude));
            } else if (!savedAddress.isEmpty()) {
                openUrl(LocationMapsLinks.mapUrl(savedAddress));
            } else {
                toast("Guarda las coordenadas o la dirección de la empresa para abrir el mapa.");
            }
        } catch (Exception e) {
            toast("No se pudo abrir Google Maps. Usa las indicaciones desde la dirección guardada.");
        }
    }

    private void openDirections(Location origin) {
        try {
            if (hasSavedCoordinates()) {
                String url = origin == null
                        ? LocationMapsLinks.directionsUrl(savedLatitude, savedLongitude)
                        : LocationMapsLinks.directionsUrl(origin.getLatitude(), origin.getLongitude(),
                                savedLatitude, savedLongitude);
                openUrl(url);
            } else if (origin == null && !savedAddress.isEmpty()) {
                openUrl(LocationMapsLinks.directionsUrl(savedAddress));
            } else {
                toast("Guarda coordenadas o la dirección de destino para generar indicaciones.");
            }
        } catch (Exception e) {
            toast("No se pudieron abrir las indicaciones en Google Maps.");
        }
    }

    private boolean hasSavedCoordinates() {
        return savedLatitude != null && savedLongitude != null
                && LocationMapsLinks.isValidCoordinate(savedLatitude, savedLongitude);
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void requestCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locateCurrentPosition();
            return;
        }
        locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    private void locateCurrentPosition() {
        if (locationManager == null) {
            toast("Este dispositivo no ofrece servicios de ubicación.");
            return;
        }
        try {
            boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (!networkEnabled && !gpsEnabled) {
                toast("Los servicios de ubicación están apagados. Actívalos para mostrar tu posición.");
                return;
            }
            String provider = fine && gpsEnabled ? LocationManager.GPS_PROVIDER
                    : networkEnabled ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
            if (locationCancellation != null) locationCancellation.cancel();
            locationCancellation = new CancellationSignal();
            locationManager.getCurrentLocation(provider, locationCancellation, getMainExecutor(), location -> {
                locationCancellation = null;
                if (isFinishing() || isDestroyed()) return;
                if (location == null) {
                    toast("No se pudo obtener tu ubicación actual. Revisa la señal y vuelve a intentarlo.");
                    return;
                }
                if (googleMap != null) {
                    LatLng current = new LatLng(location.getLatitude(), location.getLongitude());
                    googleMap.addMarker(new MarkerOptions().position(current).title("Tu ubicación actual"));
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 15f));
                } else if (hasSavedCoordinates()) {
                    openDirections(location);
                } else {
                    toast("Ubicación actual: " + LocationMapsLinks.destination(
                            location.getLatitude(), location.getLongitude()));
                }
            });
        } catch (SecurityException e) {
            toast("No hay permiso de ubicación. Puedes seguir usando el mapa guardado y las indicaciones.");
        } catch (IllegalArgumentException e) {
            toast("No hay un proveedor de ubicación disponible en este dispositivo.");
        }
    }

    private void setMapStatus(String message, boolean visible) {
        if (mapStatus == null) return;
        mapStatus.setText(message);
        mapStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(mapLoadTimeout);
        if (locationCancellation != null) {
            locationCancellation.cancel();
            locationCancellation = null;
        }
        if (helper != null) helper.close();
        super.onDestroy();
    }
}
