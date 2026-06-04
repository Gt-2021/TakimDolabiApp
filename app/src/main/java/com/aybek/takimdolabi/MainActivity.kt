package com.aybek.takimdolabi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Entity(tableName = "users")
data class AppUser(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val password: String,
    val role: String
)

@Entity(tableName = "tools")
data class ToolItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val name: String,
    val stock: Int,
    val criticalLevel: Int,
    val barcode: String = "",
    val photoUri: String = ""
)

@Entity(tableName = "movements")
data class ToolMovement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val toolId: Int,
    val toolCode: String,
    val toolName: String,
    val username: String,
    val action: String,
    val quantity: Int,
    val dateTime: String,
    val deliveryMode: String = "",
    val deliveredBy: String = "",
    val recipientName: String = ""
)

@Dao
interface AppDao {
    @Query("SELECT * FROM users WHERE username=:username AND password=:password LIMIT 1")
    suspend fun login(username: String, password: String): AppUser?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun userCount(): Int

    @Insert
    suspend fun insertUser(user: AppUser)

    @Update
    suspend fun updateUser(user: AppUser)

    @Delete
    suspend fun deleteUser(user: AppUser)

    @Query("SELECT * FROM users ORDER BY username")
    suspend fun users(): List<AppUser>

    @Insert
    suspend fun insertTool(tool: ToolItem)

    @Update
    suspend fun updateTool(tool: ToolItem)

    @Query("SELECT * FROM tools ORDER BY name")
    suspend fun tools(): List<ToolItem>

    @Query("SELECT * FROM tools WHERE code=:text OR barcode=:text LIMIT 1")
    suspend fun toolByCodeOrBarcode(text: String): ToolItem?

    @Insert
    suspend fun insertMovement(movement: ToolMovement)

    @Query("SELECT * FROM movements ORDER BY id DESC LIMIT 300")
    suspend fun movements(): List<ToolMovement>

    @Query("SELECT * FROM movements ORDER BY id DESC")
    suspend fun allMovements(): List<ToolMovement>
}

@Database(entities = [AppUser::class, ToolItem::class, ToolMovement::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "takim_dolabi.db")
            .fallbackToDestructiveMigration()
            .build()
        setContent { TakimDolabiApp(db.dao()) }
    }
}

@Composable
fun TakimDolabiApp(dao: AppDao) {
    val scope = rememberCoroutineScope()
    var currentUser by remember { mutableStateOf<AppUser?>(null) }

    LaunchedEffect(Unit) {
        if (dao.userCount() == 0) {
            dao.insertUser(AppUser(username = "admin", password = "1234", role = "ADMIN"))
            dao.insertUser(AppUser(username = "operator", password = "1234", role = "OPERATOR"))
            dao.insertTool(ToolItem(code = "T-001", name = "Freze Takımı Ø8", stock = 10, criticalLevel = 3))
            dao.insertTool(ToolItem(code = "T-002", name = "Matkap Ø5", stock = 8, criticalLevel = 2))
            dao.insertTool(ToolItem(code = "T-003", name = "Kılavuz M6", stock = 5, criticalLevel = 2))
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (currentUser == null) {
                LoginScreen { username, password ->
                    scope.launch { currentUser = dao.login(username.trim(), password.trim()) }
                }
            } else {
                MainScreen(dao = dao, user = currentUser!!, onLogout = { currentUser = null })
            }
        }
    }
}

@Composable
fun LoginScreen(onLogin: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.aybek_logo),
            contentDescription = "Aybek Havacılık",
            modifier = Modifier.fillMaxWidth().height(120.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(16.dp))
        Text("Takım Dolabı Kayıt Programı", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(username, { username = it }, label = { Text("Kullanıcı adı") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Şifre") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onLogin(username, password) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("GİRİŞ YAP")
        }
        Spacer(Modifier.height(12.dp))
        Text("İlk giriş: admin / 1234 veya operator / 1234", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun MainScreen(dao: AppDao, user: AppUser, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var tools by remember { mutableStateOf<List<ToolItem>>(emptyList()) }
    var movements by remember { mutableStateOf<List<ToolMovement>>(emptyList()) }
    var users by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var scannerAction by remember { mutableStateOf<String?>(null) }
    var pendingScannedTool by remember { mutableStateOf<ToolItem?>(null) }
    var message by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            tools = dao.tools()
            movements = dao.movements()
            users = dao.users()
        }
    }

    LaunchedEffect(Unit) { refresh() }

    if (scannerAction != null) {
        QRScannerScreen(
            onResult = { scanned ->
                scope.launch {
                    val tool = dao.toolByCodeOrBarcode(scanned.trim())
                    if (tool == null) {
                        message = "Okutulan barkod/QR için takım bulunamadı: $scanned"
                        scannerAction = null
                    } else if (scannerAction == "ALDI") {
                        pendingScannedTool = tool
                        scannerAction = null
                    } else {
                        makeReturnMovement(dao, user, tool)
                        message = "${tool.code} - ${tool.name} için İADE işlemi yapıldı."
                        scannerAction = null
                        refresh()
                    }
                }
            },
            onClose = { scannerAction = null }
        )
        return
    }

    pendingScannedTool?.let { tool ->
        IssueMovementDialog(
            tool = tool,
            currentUser = user,
            users = users,
            onDismiss = { pendingScannedTool = null },
            onConfirm = { mode, recipient ->
                scope.launch {
                    makeTakeMovement(dao, user, tool, mode, recipient)
                    message = "${tool.code} - ${tool.name} için ALDI işlemi yapıldı."
                    pendingScannedTool = null
                    refresh()
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Image(
                    painter = painterResource(id = R.drawable.aybek_logo),
                    contentDescription = "Aybek Havacılık",
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Takım Dolabı", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Kullanıcı: ${user.username} / Yetki: ${user.role}")
                }
            }
            OutlinedButton(onClick = onLogout) { Text("Çıkış") }
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { selectedTab = 0 }, modifier = Modifier.weight(1f)) { Text("Stok") }
            Button(onClick = { selectedTab = 1 }, modifier = Modifier.weight(1f)) { Text("Kayıt") }
            Button(onClick = { selectedTab = 3 }, modifier = Modifier.weight(1f)) { Text("Rapor") }
            if (user.role == "ADMIN") Button(onClick = { selectedTab = 2 }, modifier = Modifier.weight(1f)) { Text("Yetki") }
        }
        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            0 -> StockScreen(dao, user, tools, users, onChanged = { refresh() }, onScan = { scannerAction = it })
            1 -> MovementScreen(movements)
            2 -> AdminScreen(dao, users, currentUser = user, onChanged = { refresh() })
            3 -> ReportScreen(tools, movements, onExport = {
                scope.launch {
                    val file = exportReportCsv(context, dao.tools(), dao.allMovements())
                    message = "Excel uyumlu rapor oluşturuldu: ${file.absolutePath}"
                }
            })
        }
    }
}

@Composable
fun StockScreen(
    dao: AppDao,
    user: AppUser,
    tools: List<ToolItem>,
    users: List<AppUser>,
    onChanged: () -> Unit,
    onScan: (String) -> Unit
) {
    var showAddTool by remember { mutableStateOf(false) }
    var editTool by remember { mutableStateOf<ToolItem?>(null) }

    if (showAddTool) {
        AddToolDialog(
            dao = dao,
            editTool = editTool,
            onDismiss = {
                showAddTool = false
                editTool = null
            },
            onChanged = {
                showAddTool = false
                editTool = null
                onChanged()
            }
        )
    }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onScan("ALDI") }, modifier = Modifier.weight(1f).height(48.dp)) { Text("QR/BARKOD AL") }
            Button(onClick = { onScan("İADE") }, modifier = Modifier.weight(1f).height(48.dp)) { Text("QR/BARKOD İADE") }
        }
        Spacer(Modifier.height(8.dp))
        if (user.role == "ADMIN") {
            Button(onClick = { editTool = null; showAddTool = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Yeni Takım Ekle")
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tools) { tool ->
                ToolCard(
                    dao = dao,
                    user = user,
                    users = users,
                    tool = tool,
                    onChanged = onChanged,
                    onEdit = {
                        editTool = tool
                        showAddTool = true
                    }
                )
            }
        }
    }
}

@Composable
fun ToolCard(
    dao: AppDao,
    user: AppUser,
    users: List<AppUser>,
    tool: ToolItem,
    onChanged: () -> Unit,
    onEdit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val critical = tool.stock <= tool.criticalLevel
    var showIssueDialog by remember { mutableStateOf(false) }

    if (showIssueDialog) {
        IssueMovementDialog(
            tool = tool,
            currentUser = user,
            users = users,
            onDismiss = { showIssueDialog = false },
            onConfirm = { mode, recipient ->
                scope.launch {
                    makeTakeMovement(dao, user, tool, mode, recipient)
                    showIssueDialog = false
                    onChanged()
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (critical) Color(0xFFFFCDD2) else Color(0xFFE8F5E9))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (tool.photoUri.isNotBlank()) {
                    AsyncImage(
                        model = Uri.parse(tool.photoUri),
                        contentDescription = "Takım Fotoğrafı",
                        modifier = Modifier.size(72.dp).background(Color.White)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("${tool.code} - ${tool.name}", fontWeight = FontWeight.Bold)
                    Text("Stok: ${tool.stock} / Kritik seviye: ${tool.criticalLevel}")
                    if (tool.barcode.isNotBlank()) Text("Barkod/QR: ${tool.barcode}")
                    if (critical) Text("KRİTİK SEVİYE! Takım azaldı.", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = tool.stock > 0,
                    onClick = { showIssueDialog = true },
                    modifier = Modifier.weight(1f)
                ) { Text("TAKIM AL") }
                Button(
                    onClick = {
                        scope.launch {
                            makeReturnMovement(dao, user, tool)
                            onChanged()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("İADE ET") }
                if (user.role == "ADMIN") {
                    OutlinedButton(onClick = onEdit) { Text("Düzenle") }
                }
            }
        }
    }
}

@Composable
fun AddToolDialog(dao: AppDao, editTool: ToolItem?, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var code by remember { mutableStateOf(editTool?.code ?: "") }
    var name by remember { mutableStateOf(editTool?.name ?: "") }
    var stock by remember { mutableStateOf(editTool?.stock?.toString() ?: "") }
    var critical by remember { mutableStateOf(editTool?.criticalLevel?.toString() ?: "") }
    var barcode by remember { mutableStateOf(editTool?.barcode ?: "") }
    var photoUri by remember { mutableStateOf(editTool?.photoUri ?: "") }
    var scanForBarcode by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            photoUri = it.toString()
        }
    }

    if (scanForBarcode) {
        QRScannerScreen(onResult = { barcode = it; scanForBarcode = false }, onClose = { scanForBarcode = false })
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Scaffold(
                bottomBar = {
                    Surface(shadowElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            if (errorText.isNotBlank()) {
                                Text(errorText, color = Color.Red, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("İPTAL") }
                                Button(
                                    onClick = {
                                        val nameValue = name.trim()
                                        if (nameValue.isBlank()) {
                                            errorText = "Takım adı boş bırakılamaz."
                                            return@Button
                                        }
                                        val generatedCode = if (code.trim().isBlank()) autoToolCode() else code.trim()
                                        scope.launch {
                                            val item = ToolItem(
                                                id = editTool?.id ?: 0,
                                                code = generatedCode,
                                                name = nameValue,
                                                stock = stock.toIntOrNull() ?: 0,
                                                criticalLevel = critical.toIntOrNull() ?: 0,
                                                barcode = barcode.trim(),
                                                photoUri = photoUri
                                            )
                                            if (editTool == null) dao.insertTool(item) else dao.updateTool(item)
                                            onChanged()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (editTool == null) "KAYDET" else "GÜNCELLE")
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    Modifier
                        .padding(innerPadding)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(if (editTool == null) "Yeni Takım Bilgisi" else "Takım Düzenleme", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        code,
                        { code = it },
                        label = { Text("Takım kodu (boşsa otomatik oluşturulur)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(name, { name = it }, label = { Text("Takım adı") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(stock, { stock = it }, label = { Text("Stok miktarı") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(critical, { critical = it }, label = { Text("Kritik seviye") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        barcode,
                        { barcode = it },
                        label = { Text("Barkod / QR değeri (isteğe bağlı)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { scanForBarcode = true }, modifier = Modifier.weight(1f)) { Text("Barkod Okut") }
                        OutlinedButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Fotoğraf Seç") }
                    }
                    Text("Not: Barkod ve fotoğraf girmek zorunlu değildir.", style = MaterialTheme.typography.bodySmall)
                    if (photoUri.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = Uri.parse(photoUri),
                            contentDescription = "Seçili Fotoğraf",
                            modifier = Modifier.size(110.dp).background(Color.White)
                        )
                        OutlinedButton(onClick = { photoUri = "" }) { Text("Fotoğrafı Kaldır") }
                    }
                    Spacer(Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
fun IssueMovementDialog(
    tool: ToolItem,
    currentUser: AppUser,
    users: List<AppUser>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var deliveryMode by remember { mutableStateOf("SELF") }
    var recipientName by remember { mutableStateOf(currentUser.username) }
    var errorText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Takım Alma Kaydı", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${tool.code} - ${tool.name}")
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = deliveryMode == "SELF", onClick = {
                        deliveryMode = "SELF"
                        recipientName = currentUser.username
                    })
                    Text("Kullanıcı kendi alıyor")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = deliveryMode == "DELIVERED", onClick = {
                        deliveryMode = "DELIVERED"
                        if (recipientName == currentUser.username) recipientName = ""
                    })
                    Text("Takımhane sorumlusu teslim ediyor")
                }

                if (deliveryMode == "DELIVERED") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        recipientName,
                        { recipientName = it },
                        label = { Text("Teslim alan kişi") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Hızlı seçim:", fontWeight = FontWeight.SemiBold)
                    users.forEach { u ->
                        OutlinedButton(onClick = { recipientName = u.username }, modifier = Modifier.fillMaxWidth()) {
                            Text(u.username)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text("Teslim eden: ${currentUser.username}", style = MaterialTheme.typography.bodySmall)
                }

                if (errorText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorText, color = Color.Red, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("İPTAL") }
                    Button(onClick = {
                        if (deliveryMode == "DELIVERED" && recipientName.trim().isBlank()) {
                            errorText = "Teslim alan kişi boş bırakılamaz."
                        } else {
                            val finalRecipient = if (deliveryMode == "SELF") currentUser.username else recipientName.trim()
                            onConfirm(deliveryMode, finalRecipient)
                        }
                    }, modifier = Modifier.weight(1f)) { Text("ONAYLA") }
                }
            }
        }
    }
}

@Composable
fun MovementScreen(movements: List<ToolMovement>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(movements) { m ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("${m.dateTime} - ${m.username}", fontWeight = FontWeight.Bold)
                    Text("${m.toolCode} ${m.toolName} / İşlem: ${m.action} / Miktar: ${m.quantity}")
                    if (m.action == "ALDI") {
                        val modeText = if (m.deliveryMode == "DELIVERED") "Takımhane sorumlusu teslim etti" else "Kullanıcı kendi aldı"
                        Text("Teslim şekli: $modeText")
                        if (m.deliveredBy.isNotBlank()) Text("Teslim eden: ${m.deliveredBy}")
                        if (m.recipientName.isNotBlank()) Text("Teslim alan: ${m.recipientName}")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminScreen(dao: AppDao, users: List<AppUser>, currentUser: AppUser, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AppUser?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("OPERATOR") }

    fun fillUser(u: AppUser) {
        editing = u
        username = u.username
        password = u.password
        role = u.role
    }

    Column {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Kullanıcı / Yetkilendirme", fontWeight = FontWeight.Bold)
                OutlinedTextField(username, { username = it }, label = { Text("Kullanıcı adı") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Şifre") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { role = "OPERATOR" }, modifier = Modifier.weight(1f)) { Text("Operatör") }
                    Button(onClick = { role = "ADMIN" }, modifier = Modifier.weight(1f)) { Text("Admin") }
                }
                Text("Seçili yetki: $role")
                Button(onClick = {
                    scope.launch {
                        val u = AppUser(id = editing?.id ?: 0, username = username.trim(), password = password.trim(), role = role)
                        if (editing == null) dao.insertUser(u) else dao.updateUser(u)
                        editing = null
                        username = ""
                        password = ""
                        role = "OPERATOR"
                        onChanged()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (editing == null) "KULLANICI EKLE" else "KULLANICI GÜNCELLE") }
                if (editing != null) {
                    OutlinedButton(onClick = {
                        editing = null
                        username = ""
                        password = ""
                        role = "OPERATOR"
                    }, modifier = Modifier.fillMaxWidth()) { Text("Vazgeç") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Tanımlı Kullanıcılar", fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(users) { u ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.username, fontWeight = FontWeight.Bold)
                            Text("Yetki: ${u.role}")
                        }
                        OutlinedButton(onClick = { fillUser(u) }) { Text("Düzenle") }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            enabled = u.id != currentUser.id && u.username != "admin",
                            onClick = { scope.launch { dao.deleteUser(u); onChanged() } }
                        ) { Text("Sil") }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportScreen(tools: List<ToolItem>, movements: List<ToolMovement>, onExport: () -> Unit) {
    val criticalTools = tools.filter { it.stock <= it.criticalLevel }
    val outCount = movements.count { it.action == "ALDI" }
    val returnCount = movements.count { it.action == "İADE" }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("EXCEL'E AKTAR") }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
            Column(Modifier.padding(12.dp)) {
                Text("Rapor Özeti", fontWeight = FontWeight.Bold)
                Text("Toplam takım çeşidi: ${tools.size}")
                Text("Kritik seviyedeki takım: ${criticalTools.size}")
                Text("Toplam alınan işlem: $outCount")
                Text("Toplam iade işlem: $returnCount")
            }
        }
        Text("Kritik Seviyedeki Takımlar", fontWeight = FontWeight.Bold)
        if (criticalTools.isEmpty()) Text("Kritik seviyede takım yok.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(criticalTools) { t ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2))) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${t.code} - ${t.name}", fontWeight = FontWeight.Bold, color = Color.Red)
                        Text("Stok: ${t.stock} / Kritik seviye: ${t.criticalLevel}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun QRScannerScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var manualText by remember { mutableStateOf("") }
    var handled by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("QR / Barkod Okutma", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text("Kamera çalışmazsa barkod değerini elle yazabilirsiniz.")
        Spacer(Modifier.height(8.dp))

        if (!hasPermission) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                Text("KAMERA İZNİ VER")
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val scanner = BarcodeScanning.getClient()
                        val executor = Executors.newSingleThreadExecutor()
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && !handled) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val value = barcodes.firstOrNull()?.rawValue
                                        if (!value.isNullOrBlank() && !handled) {
                                            handled = true
                                            onResult(value)
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxWidth().height(320.dp).background(Color.Black)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(manualText, { manualText = it }, label = { Text("Elle barkod / QR değeri") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (manualText.isNotBlank()) onResult(manualText) }, modifier = Modifier.weight(1f)) { Text("Elle Kullan") }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Kapat") }
        }
    }
}

suspend fun makeTakeMovement(dao: AppDao, user: AppUser, tool: ToolItem, deliveryMode: String, recipientName: String) {
    val newStock = (tool.stock - 1).coerceAtLeast(0)
    dao.updateTool(tool.copy(stock = newStock))
    dao.insertMovement(
        ToolMovement(
            toolId = tool.id,
            toolCode = tool.code,
            toolName = tool.name,
            username = user.username,
            action = "ALDI",
            quantity = 1,
            dateTime = nowText(),
            deliveryMode = deliveryMode,
            deliveredBy = if (deliveryMode == "DELIVERED") user.username else "",
            recipientName = recipientName
        )
    )
}

suspend fun makeReturnMovement(dao: AppDao, user: AppUser, tool: ToolItem) {
    val newStock = tool.stock + 1
    dao.updateTool(tool.copy(stock = newStock))
    dao.insertMovement(
        ToolMovement(
            toolId = tool.id,
            toolCode = tool.code,
            toolName = tool.name,
            username = user.username,
            action = "İADE",
            quantity = 1,
            dateTime = nowText()
        )
    )
}

suspend fun exportReportCsv(context: Context, tools: List<ToolItem>, movements: List<ToolMovement>): File = withContext(Dispatchers.IO) {
    val dir = File(context.getExternalFilesDir(null), "raporlar")
    dir.mkdirs()
    val file = File(dir, "takim_dolabi_rapor_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.csv")
    val sb = StringBuilder()
    sb.appendLine("AYBEK HAVACILIK TAKIM DOLABI RAPORU")
    sb.appendLine("Olusturma Tarihi;${nowText()}")
    sb.appendLine()
    sb.appendLine("TAKIM STOK LISTESI")
    sb.appendLine("Kod;Ad;Stok;Kritik Seviye;Durum;Barkod/QR;Foto URI")
    tools.forEach { t ->
        val status = if (t.stock <= t.criticalLevel) "KRITIK" else "UYGUN"
        sb.appendLine("${safeCsv(t.code)};${safeCsv(t.name)};${t.stock};${t.criticalLevel};$status;${safeCsv(t.barcode)};${safeCsv(t.photoUri)}")
    }
    sb.appendLine()
    sb.appendLine("HAREKET KAYITLARI")
    sb.appendLine("Tarih Saat;Kullanici;Takim Kodu;Takim Adi;Islem;Miktar;Teslim Sekli;Teslim Eden;Teslim Alan")
    movements.forEach { m ->
        val modeText = when (m.deliveryMode) {
            "DELIVERED" -> "Takımhane sorumlusu teslim etti"
            "SELF" -> "Kullanıcı kendi aldı"
            else -> ""
        }
        sb.appendLine(
            "${safeCsv(m.dateTime)};${safeCsv(m.username)};${safeCsv(m.toolCode)};${safeCsv(m.toolName)};${safeCsv(m.action)};${m.quantity};${safeCsv(modeText)};${safeCsv(m.deliveredBy)};${safeCsv(m.recipientName)}"
        )
    }
    file.writeText(sb.toString(), Charsets.UTF_8)
    file
}

fun safeCsv(text: String): String = text.replace(";", ",").replace("\n", " ").replace("\r", " ")
fun nowText(): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date())
fun autoToolCode(): String = "OTO-" + SimpleDateFormat("yyMMddHHmmss", Locale.US).format(Date())
