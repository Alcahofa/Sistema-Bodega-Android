package com.example.sqlite;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
public class DBHelper extends SQLiteOpenHelper {
 private static final String DB_NAME="BD_BODEGA.db"; private static final int DB_VERSION=9;
 public DBHelper(Context c){this(c,DB_NAME);}
 DBHelper(Context c,String name){super(c,name,null,DB_VERSION);}
 @Override public void onConfigure(SQLiteDatabase db){super.onConfigure(db);db.setForeignKeyConstraintsEnabled(true);}
 @Override public void onCreate(SQLiteDatabase db){schema(db);seed(db);permissionSchema(db);seedPermissions(db);locationSchema(db);}
 private void schema(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS ROL (id_rol INTEGER PRIMARY KEY AUTOINCREMENT,nombre_rol TEXT NOT NULL UNIQUE,descripcion TEXT NOT NULL DEFAULT '',activo INTEGER NOT NULL DEFAULT 1)");
  db.execSQL("CREATE TABLE IF NOT EXISTS USUARIO (id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT NOT NULL UNIQUE,password_hash TEXT NOT NULL,nombre_completo TEXT NOT NULL,id_rol INTEGER NOT NULL,activo INTEGER NOT NULL DEFAULT 1,debe_cambiar_clave INTEGER NOT NULL DEFAULT 0 CHECK(debe_cambiar_clave IN (0,1)),FOREIGN KEY(id_rol) REFERENCES ROL(id_rol))");
  db.execSQL("CREATE TABLE IF NOT EXISTS CATEGORIA (id_categoria INTEGER PRIMARY KEY AUTOINCREMENT,nombre_categoria TEXT NOT NULL UNIQUE,descripcion TEXT NOT NULL DEFAULT '',activo INTEGER NOT NULL DEFAULT 1)");
  db.execSQL("CREATE TABLE IF NOT EXISTS PROVEEDOR (id_proveedor INTEGER PRIMARY KEY AUTOINCREMENT,rut_empresa TEXT NOT NULL UNIQUE,razon_social TEXT NOT NULL,contacto TEXT NOT NULL,activo INTEGER NOT NULL DEFAULT 1)");
  db.execSQL("CREATE TABLE IF NOT EXISTS PRODUCTO (id_producto INTEGER PRIMARY KEY AUTOINCREMENT,sku_codigo TEXT NOT NULL UNIQUE,codigo_barras TEXT NOT NULL DEFAULT '',nombre TEXT NOT NULL,descripcion TEXT NOT NULL DEFAULT '',stock_actual INTEGER NOT NULL DEFAULT 0 CHECK(stock_actual>=0),stock_minimo INTEGER NOT NULL DEFAULT 0 CHECK(stock_minimo>=0),unidad_medida TEXT NOT NULL DEFAULT 'unidad',id_categoria INTEGER NOT NULL,activo INTEGER NOT NULL DEFAULT 1,FOREIGN KEY(id_categoria) REFERENCES CATEGORIA(id_categoria))");
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_producto_codigo_barras ON PRODUCTO(codigo_barras) WHERE codigo_barras <> ''");
  createMovementTable(db,"MOVIMIENTO");
 }
 private void createMovementTable(SQLiteDatabase db,String table){
  if(!"MOVIMIENTO".equals(table)&&!"MOVIMIENTO_V9".equals(table))throw new IllegalArgumentException("Nombre de tabla de movimientos no permitido.");
  db.execSQL("CREATE TABLE IF NOT EXISTS "+table+" (id_movimiento INTEGER PRIMARY KEY AUTOINCREMENT,tipo TEXT NOT NULL CHECK(tipo IN ('ENTRADA','SALIDA','APERTURA')),cantidad INTEGER NOT NULL CHECK(cantidad>0),fecha TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,id_producto INTEGER NOT NULL,id_usuario INTEGER,id_proveedor INTEGER,nota TEXT NOT NULL DEFAULT '',FOREIGN KEY(id_producto) REFERENCES PRODUCTO(id_producto),FOREIGN KEY(id_usuario) REFERENCES USUARIO(id_usuario),FOREIGN KEY(id_proveedor) REFERENCES PROVEEDOR(id_proveedor))");
 }
 private void seed(SQLiteDatabase db){db.execSQL("INSERT OR IGNORE INTO ROL(nombre_rol,descripcion) VALUES('Administrador','Acceso completo'),('Operador','Operación de bodega')");}
 private void permissionSchema(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS PERMISO(id_permiso INTEGER PRIMARY KEY AUTOINCREMENT,clave TEXT NOT NULL UNIQUE,nombre TEXT NOT NULL,grupo TEXT NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS ROL_PERMISO(id_rol INTEGER NOT NULL,id_permiso INTEGER NOT NULL,permitido INTEGER NOT NULL DEFAULT 0 CHECK(permitido IN (0,1)),PRIMARY KEY(id_rol,id_permiso),FOREIGN KEY(id_rol) REFERENCES ROL(id_rol) ON DELETE CASCADE,FOREIGN KEY(id_permiso) REFERENCES PERMISO(id_permiso) ON DELETE CASCADE)");
  db.execSQL("CREATE TABLE IF NOT EXISTS USUARIO_PERMISO(id_usuario INTEGER NOT NULL,id_permiso INTEGER NOT NULL,permitido INTEGER NOT NULL CHECK(permitido IN (0,1)),PRIMARY KEY(id_usuario,id_permiso),FOREIGN KEY(id_usuario) REFERENCES USUARIO(id_usuario) ON DELETE CASCADE,FOREIGN KEY(id_permiso) REFERENCES PERMISO(id_permiso) ON DELETE CASCADE)");
 }
 private void seedPermissions(SQLiteDatabase db){
  String[][] p={{"PRODUCT_VIEW","Ver productos","Inventario"},{"PRODUCT_CREATE","Crear productos","Inventario"},{"PRODUCT_EDIT","Editar productos","Inventario"},{"PRODUCT_DISABLE","Desactivar productos","Inventario"},{"MOVEMENT_VIEW","Ver movimientos","Movimientos"},{"MOVEMENT_ENTRY","Registrar entradas","Movimientos"},{"MOVEMENT_EXIT","Registrar salidas","Movimientos"},{"REPORT_VIEW","Ver reportes de stock","Reportes"},{"HISTORY_VIEW","Ver historial","Reportes"},{"CATEGORY_VIEW","Ver categorías","Datos maestros"},{"USER_MANAGE","Gestionar usuarios","Administración"},{"PROFILE_MANAGE","Gestionar perfiles y permisos","Administración"},{"MASTER_DATA_MANAGE","Gestionar categorías y proveedores","Administración"},{"LOCATION_VIEW","Ver ubicación del local","Local"},{"LOCATION_MANAGE","Editar ubicación del local","Local"}};
  for(String[] row:p){ContentValues v=new ContentValues();v.put("clave",row[0]);v.put("nombre",row[1]);v.put("grupo",row[2]);db.insertWithOnConflict("PERMISO",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
  try(Cursor roles=db.rawQuery("SELECT id_rol,nombre_rol FROM ROL",null)){while(roles.moveToNext()){long id=roles.getLong(0);boolean admin="Administrador".equalsIgnoreCase(roles.getString(1));for(String[] row:p){boolean allow=admin||("Operador".equalsIgnoreCase(roles.getString(1))&&java.util.Arrays.asList("PRODUCT_VIEW","MOVEMENT_VIEW","MOVEMENT_ENTRY","MOVEMENT_EXIT","REPORT_VIEW","HISTORY_VIEW","CATEGORY_VIEW","LOCATION_VIEW").contains(row[0]));long perm=scalar(db,"SELECT id_permiso FROM PERMISO WHERE clave='"+row[0]+"'");db.execSQL("INSERT OR IGNORE INTO ROL_PERMISO(id_rol,id_permiso,permitido) VALUES(?,?,?)",new Object[]{id,perm,allow?1:0});}}}
 }
 private void locationSchema(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS UBICACION_LOCAL(id_ubicacion INTEGER PRIMARY KEY CHECK(id_ubicacion=1),nombre TEXT NOT NULL DEFAULT 'Mi local',direccion TEXT NOT NULL DEFAULT '',latitud REAL,longitud REAL,actualizado_en TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,CHECK((latitud IS NULL AND longitud IS NULL) OR (latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180)))");ContentValues v=new ContentValues();v.put("id_ubicacion",1);v.put("nombre","Instituto Profesional Santo Tomás");v.put("direccion","Benjamín Vicuña Mackenna 520, 1840103 Ovalle, Coquimbo");v.put("latitud",-30.6045891);v.put("longitud",-71.2047616);db.insertWithOnConflict("UBICACION_LOCAL",null,v,SQLiteDatabase.CONFLICT_IGNORE);db.execSQL("UPDATE UBICACION_LOCAL SET nombre=?,direccion=?,latitud=?,longitud=? WHERE id_ubicacion=1 AND nombre='Mi local' AND direccion='' AND latitud IS NULL AND longitud IS NULL",new Object[]{"Instituto Profesional Santo Tomás","Benjamín Vicuña Mackenna 520, 1840103 Ovalle, Coquimbo",-30.6045891,-71.2047616});}
 @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
  if(oldVersion<3&&!hasColumn(db,"PRODUCTO","unidad_medida"))
   if(tableExists(db,"PRODUCTO"))db.execSQL("ALTER TABLE PRODUCTO ADD COLUMN unidad_medida TEXT NOT NULL DEFAULT 'unidad'");
  if(oldVersion<4&&!hasColumn(db,"PRODUCTO","codigo_barras"))
   if(tableExists(db,"PRODUCTO"))db.execSQL("ALTER TABLE PRODUCTO ADD COLUMN codigo_barras TEXT NOT NULL DEFAULT ''");
  if(oldVersion<8&&tableExists(db,"USUARIO")){
   if(!hasColumn(db,"USUARIO","debe_cambiar_clave"))
    db.execSQL("ALTER TABLE USUARIO ADD COLUMN debe_cambiar_clave INTEGER NOT NULL DEFAULT 0 CHECK(debe_cambiar_clave IN (0,1))");
   db.execSQL("UPDATE USUARIO SET debe_cambiar_clave=1 WHERE id_rol IN (SELECT id_rol FROM ROL WHERE nombre_rol='Administrador')");
  }
  if(oldVersion<9&&tableExists(db,"MOVIMIENTO"))upgradeMovementsToV9(db);
  schema(db);seed(db);permissionSchema(db);seedPermissions(db);locationSchema(db);
 }

 private void upgradeMovementsToV9(SQLiteDatabase db){
  Long previousSequence=null;
  try(Cursor sequence=db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name='MOVIMIENTO'",null)){
   if(sequence.moveToFirst())previousSequence=sequence.getLong(0);
  }
  if(tableExists(db,"MOVIMIENTO_V9"))throw new IllegalStateException("Existe una tabla temporal de migración de movimientos.");
  createMovementTable(db,"MOVIMIENTO_V9");
  db.execSQL("INSERT INTO MOVIMIENTO_V9(id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,id_proveedor,nota) "+
          "SELECT id_movimiento,tipo,cantidad,fecha,id_producto,id_usuario,id_proveedor,nota FROM MOVIMIENTO");
  db.execSQL("DROP TABLE MOVIMIENTO");
  db.execSQL("ALTER TABLE MOVIMIENTO_V9 RENAME TO MOVIMIENTO");
  if(previousSequence==null){
   db.execSQL("DELETE FROM sqlite_sequence WHERE name='MOVIMIENTO'");
  }else{
   db.execSQL("UPDATE sqlite_sequence SET seq=? WHERE name='MOVIMIENTO'",new Object[]{previousSequence});
   try(Cursor sequence=db.rawQuery("SELECT 1 FROM sqlite_sequence WHERE name='MOVIMIENTO'",null)){
    if(!sequence.moveToFirst())db.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES('MOVIMIENTO',?)",new Object[]{previousSequence});
   }
  }
 }

 boolean requiresPasswordChange(long userId){
  SQLiteDatabase db=getReadableDatabase();
  try(Cursor c=db.rawQuery("SELECT debe_cambiar_clave FROM USUARIO WHERE id_usuario=? AND activo=1",new String[]{String.valueOf(userId)})){
   return c.moveToFirst()&&c.getInt(0)==1;
  }finally{db.close();}
 }

 long createInitialAdmin(String username,String name,String password){
  if(username==null||username.trim().isEmpty()||name==null||name.trim().isEmpty()||!meetsPasswordMinimum(password))
   throw new IllegalArgumentException("Completa los datos y usa una contraseña de al menos 8 caracteres.");
  SQLiteDatabase db=getWritableDatabase();
  db.beginTransaction();
  try{
   if(scalar(db,"SELECT COUNT(*) FROM USUARIO")!=0)throw new IllegalStateException("La configuración inicial ya se completó.");
   long roleId;
   try(Cursor role=db.rawQuery("SELECT id_rol FROM ROL WHERE nombre_rol='Administrador' AND activo=1",null)){
    if(!role.moveToFirst())throw new IllegalStateException("No está disponible el perfil Administrador.");
    roleId=role.getLong(0);
   }
   ContentValues user=new ContentValues();
   user.put("username",username.trim());
   user.put("password_hash",hash(password));
   user.put("nombre_completo",name.trim());
   user.put("id_rol",roleId);
   user.put("activo",1);
   user.put("debe_cambiar_clave",0);
   long id=db.insertOrThrow("USUARIO",null,user);
   db.setTransactionSuccessful();
   return id;
  }finally{db.endTransaction();db.close();}
 }

 boolean rotatePassword(long userId,String newPassword){
  if(!meetsPasswordMinimum(newPassword))throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
  SQLiteDatabase db=getWritableDatabase();
  db.beginTransaction();
  try{
   String currentHash;
   try(Cursor current=db.rawQuery("SELECT password_hash FROM USUARIO WHERE id_usuario=? AND activo=1 AND debe_cambiar_clave=1",new String[]{String.valueOf(userId)})){
    if(!current.moveToFirst())return false;
    currentHash=current.getString(0);
   }
   if(verify(newPassword,currentHash))throw new IllegalArgumentException("La contraseña nueva debe ser distinta de la actual.");
   ContentValues values=new ContentValues();
   values.put("password_hash",hash(newPassword));
   values.put("debe_cambiar_clave",0);
   boolean changed=db.update("USUARIO",values,"id_usuario=? AND activo=1 AND debe_cambiar_clave=1",new String[]{String.valueOf(userId)})==1;
   if(changed)db.setTransactionSuccessful();
   return changed;
  }finally{db.endTransaction();db.close();}
 }

 long createProductWithOpening(ContentValues productValues,int initialStock,long userId){
  if(initialStock<0)throw new IllegalArgumentException("El stock inicial no puede ser negativo.");
  if(initialStock>0&&userId<=0)throw new IllegalArgumentException("Se requiere un usuario para registrar el stock inicial.");
  SQLiteDatabase db=getWritableDatabase();
  db.beginTransaction();
  try{
   ContentValues product=new ContentValues(productValues);
   product.put("stock_actual",initialStock);
   long productId=db.insertOrThrow("PRODUCTO",null,product);
   if(initialStock>0){
    ContentValues opening=new ContentValues();
    opening.put("tipo","APERTURA");
    opening.put("cantidad",initialStock);
    opening.put("id_producto",productId);
    opening.put("id_usuario",userId);
    opening.put("nota","Stock inicial");
    db.insertOrThrow("MOVIMIENTO",null,opening);
   }
   db.setTransactionSuccessful();
   return productId;
  }finally{db.endTransaction();db.close();}
 }

 static boolean meetsPasswordMinimum(String password){return password!=null&&password.length()>=8;}
 private boolean tableExists(SQLiteDatabase db,String table){
  try(Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{table})){
   return c.moveToFirst();
  }
 }
 private boolean hasColumn(SQLiteDatabase db,String table,String column){
  try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)){
   while(c.moveToNext())if(column.equalsIgnoreCase(c.getString(c.getColumnIndexOrThrow("name"))))return true;
  }
  return false;
 }
 public static long scalar(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):-1;}}
 public static String hash(String value){try{byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);byte[] key=derive(value,salt);return Base64.encodeToString(salt,Base64.NO_WRAP)+":"+Base64.encodeToString(key,Base64.NO_WRAP);}catch(Exception e){throw new IllegalStateException(e);}}
 public static boolean verify(String value,String stored){try{String[] p=stored.split(":");return p.length==2&&MessageDigest.isEqual(Base64.decode(p[1],Base64.NO_WRAP),derive(value,Base64.decode(p[0],Base64.NO_WRAP)));}catch(Exception e){return false;}}
 private static byte[] derive(String value,byte[] salt)throws Exception{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(value.toCharArray(),salt,120000,256)).getEncoded();}
}

