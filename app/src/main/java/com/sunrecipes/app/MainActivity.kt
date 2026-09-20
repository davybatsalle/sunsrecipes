package com.sunrecipes.app

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.content.Intent
import androidx.core.content.FileProvider
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunrecipes.app.data.RecipeContent
import com.sunrecipes.app.data.RecipeEntity
import com.sunrecipes.app.data.RecipeFamilies
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.sunrecipes.app.data.RecipeIngredient
import android.graphics.BitmapFactory
import android.graphics.Bitmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SunRecipesApp() }
    }
}

private val paper = Color(0xFFFAF7F2)
private val ink = Color(0xFF302B27)
private val coral = Color(0xFFD97745)
private val recipeFamilies = RecipeFamilies.allowed

@Composable
fun SunRecipesApp(recipeViewModel: RecipeViewModel = viewModel()) {
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var editingRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var showCameraFallback by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val pendingRecipes by recipeViewModel.pendingRecipes.collectAsStateWithLifecycle()
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(recipeViewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(recipeViewModel::importRecipes) }
    val documentScannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri?.let { uri ->
                copyScannedUri(context, uri)?.let(recipeViewModel::processScan)
            }
        }
    }

    fun launchDocumentScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(context as Activity)
            .addOnSuccessListener { sender ->
                documentScannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { showCameraFallback = true }
    }

    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = coral, background = paper, surface = paper, onBackground = ink, onSurface = ink)) {
        if (showSettings) {
            GeminiSettingsScreen(
                viewModel = recipeViewModel,
                onBack = { showSettings = false },
                onOpenAiStudio = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
                }
            )
        } else if (pendingRecipes.isNotEmpty()) {
            RecipeReviewScreen(
                recipes = pendingRecipes,
                onCancel = {
                    recipeViewModel.cancelPendingScan()
                    showScanner = false
                },
                onConfirm = {
                    recipeViewModel.confirmRecipes(it)
                    showScanner = false
                }
            )
        } else if (editingRecipe != null) {
            RecipeEditScreen(
                recipe = editingRecipe!!,
                onCancel = { editingRecipe = null },
                onSave = {
                    recipeViewModel.updateRecipe(it)
                    selectedRecipe = it
                    editingRecipe = null
                }
            )
        } else if (selectedRecipe != null) {
            RecipeDetailScreen(
                recipe = selectedRecipe!!,
                onBack = { selectedRecipe = null },
                onEdit = { editingRecipe = it },
                onCrop = { recipe, bitmap ->
                    recipeViewModel.cropScan(recipe, bitmap) { updated -> selectedRecipe = updated }
                },
                onDelete = {
                    recipeViewModel.delete(it)
                    selectedRecipe = null
                }
            )
        } else if (showCameraFallback || showScanner) {
            if (cameraGranted) {
            ScannerScreen(recipeViewModel) { showScanner = false; showCameraFallback = false }
            } else {
                CameraPermissionScreen { permissionLauncher.launch(Manifest.permission.CAMERA) }
            }
        } else {
            RecipeHomeScreen(recipeViewModel, onScan = {
                launchDocumentScanner()
            }, onBackup = { backupLauncher.launch("sun-recipes-backup.json") }, onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) }, onRecipeClick = { selectedRecipe = it }, onSettings = { showSettings = true })
        }
    }
}

private fun copyScannedUri(context: android.content.Context, uri: Uri): java.io.File? = runCatching {
    val file = java.io.File.createTempFile("document-scan-", ".jpg", context.cacheDir)
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    file
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiSettingsScreen(viewModel: RecipeViewModel, onBack: () -> Unit, onOpenAiStudio: () -> Unit) {
    val hasKey by viewModel.hasGeminiKey.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    Scaffold(
        containerColor = paper,
        topBar = {
            TopAppBar(
                title = { Text("Analyse IA Gemini") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Pour un usage personnel, vous pouvez utiliser votre clé Google AI Studio.", color = Color(0xFF746A63))
            Button(onClick = onOpenAiStudio, modifier = Modifier.fillMaxWidth()) {
                Text("Ouvrir Google AI Studio")
            }
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Clé API Gemini") },
                singleLine = true
            )
            Button(
                enabled = key.isNotBlank(),
                onClick = { viewModel.saveGeminiKey(key); key = "" },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Enregistrer la clé") }
            if (hasKey) {
                Text("Une clé Gemini est configurée et sera utilisée pour les prochains scans.", color = coral)
                TextButton(onClick = viewModel::clearGeminiKey) { Text("Supprimer la clé") }
            } else {
                Text("Une clé Gemini est nécessaire pour analyser les scans.", color = Color(0xFF746A63))
            }
            Text("La clé est chiffrée avec Android Keystore. Pour une application distribuée, utilisez plutôt un serveur intermédiaire.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF746A63))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeHomeScreen(viewModel: RecipeViewModel, onScan: () -> Unit, onBackup: () -> Unit, onImport: () -> Unit, onRecipeClick: (RecipeEntity) -> Unit, onSettings: () -> Unit) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val scanMessage by viewModel.scanMessage.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(scanMessage) {
        scanMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    Scaffold(
        containerColor = paper,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(com.sunrecipes.app.R.drawable.ic_launcher_foreground),
                    contentDescription = "Sun's recipes",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Sun's recipes", style = MaterialTheme.typography.headlineSmall)
            }
        }, actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Configurer Gemini") }
            IconButton(onClick = onImport) { Icon(Icons.Default.FileOpen, "Importer un backup") }
            IconButton(onClick = onBackup) { Icon(Icons.Default.CloudUpload, "Sauvegarder sur Google Drive") }
        }) },
        floatingActionButton = { FloatingActionButton(onClick = onScan, containerColor = coral, contentColor = Color.White) { Icon(Icons.Default.AddAPhoto, "Scanner une recette") } }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp).fillMaxSize()) {
            Text("Votre carnet de cuisine", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF746A63))
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(value = query, onValueChange = viewModel::updateQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Nom, ingrédient ou famille") }, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(18.dp))
            FamilyRow(recipes) { family ->
                viewModel.updateQuery(if (query.equals(family, ignoreCase = true)) "" else family)
            }
            Spacer(Modifier.height(12.dp))
            Text("${recipes.size} recette${if (recipes.size > 1) "s" else ""}", style = MaterialTheme.typography.labelLarge, color = Color(0xFF746A63))
            Spacer(Modifier.height(6.dp))
            if (recipes.isEmpty()) EmptyState(onScan) else LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recipes, key = { it.id }) { RecipeCard(it, viewModel::delete, onRecipeClick) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeReviewScreen(
    recipes: List<RecipeEntity>,
    onCancel: () -> Unit,
    onConfirm: (List<RecipeEntity>) -> Unit
) {
    var drafts by remember(recipes) { mutableStateOf(recipes) }
    Scaffold(
        containerColor = paper,
        topBar = {
            TopAppBar(
                title = { Text("Vérifier le scan") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Annuler") } },
                actions = { TextButton(onClick = onCancel) { Text("Annuler") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "L’analyse IA peut faire des erreurs. Vérifiez les champs avant d’ajouter la recette.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color(0xFF746A63)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(drafts, key = { index, recipe -> "${recipe.id}-$index" }) { index, recipe ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Recette ${index + 1}", style = MaterialTheme.typography.labelLarge, color = coral)
                            OutlinedTextField(
                                value = recipe.nameFrench.ifBlank { recipe.name },
                                onValueChange = { value -> drafts = drafts.updated(index) { copy(nameFrench = value) } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Titre") },
                                singleLine = true
                            )
                            FamilySelector(recipe.familiesJson, recipe.family) { families ->
                                drafts = drafts.updated(index) { withFamilies(families) }
                            }
                            OutlinedTextField(
                                value = frenchIngredientText(recipe),
                                onValueChange = { value -> drafts = drafts.updated(index) { withFrenchIngredients(value) } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Ingrédients principaux") },
                                minLines = 3
                            )
                            Text(RecipeFamilies.decode(recipe.familiesJson, recipe.family).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Color(0xFF746A63))
                        }
                    }
                }
            }
            Button(
                onClick = { onConfirm(drafts) },
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Text("Ajouter au carnet")
            }
        }
    }
}

private fun List<RecipeEntity>.updated(index: Int, transform: RecipeEntity.() -> RecipeEntity): List<RecipeEntity> =
    mapIndexed { currentIndex, recipe -> if (currentIndex == index) recipe.transform() else recipe }

private fun String.toAllowedFamily(): String = recipeFamilies.firstOrNull { it.equals(this, ignoreCase = true) } ?: "autres"

@Composable
private fun FamilySelector(value: String, fallback: String, onChange: (List<String>) -> Unit) {
    val selected = RecipeFamilies.decode(value, fallback)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Familles", style = MaterialTheme.typography.labelMedium, color = Color(0xFF746A63))
        recipeFamilies.forEach { family ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = family in selected, onCheckedChange = { checked ->
                    val next = if (checked) (selected + family).distinct() else selected - family
                    onChange(if (next.isEmpty()) listOf("autres") else next)
                })
                Text(family)
            }
        }
    }
}

private fun frenchIngredientText(recipe: RecipeEntity): String = RecipeContent.decodeIngredients(recipe.ingredientsFrenchJson)
    .joinToString("\n") { it.name }

private fun RecipeEntity.withFrenchIngredients(value: String): RecipeEntity {
    val ingredients = value.lines().mapNotNull { line ->
        val cleanLine = line.trim()
        cleanLine.takeIf(String::isNotBlank)?.let(::RecipeIngredient)
    }
    return copy(
        ingredientsFrenchJson = RecipeContent.encodeIngredients(ingredients),
        ingredientOne = ingredients.getOrNull(0)?.name ?: ingredientOne,
        ingredientTwo = ingredients.getOrNull(1)?.name ?: ingredientTwo
    )
}

private fun RecipeEntity.withFamilies(families: List<String>): RecipeEntity = copy(
    family = families.firstOrNull() ?: "autres",
    familiesJson = RecipeFamilies.encode(families)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyRow(recipes: List<RecipeEntity>, onFamilyClick: (String) -> Unit) {
    val families = recipes.flatMap { recipe -> RecipeFamilies.decode(recipe.familiesJson, recipe.family) }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        families.forEach { family ->
            AssistChip(onClick = { onFamilyClick(family.key) }, label = { Text("${family.key} ${family.value}") })
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeEntity, onDelete: (RecipeEntity) -> Unit, onClick: (RecipeEntity) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable { onClick(recipe) }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(recipe.nameFrench.ifBlank { recipe.name }, style = MaterialTheme.typography.titleMedium)
                if (recipe.nameFrench.isNotBlank() && recipe.nameFrench != recipe.name) {
                    Text(recipe.name, style = MaterialTheme.typography.labelMedium, color = Color(0xFF746A63))
                }
                Spacer(Modifier.height(5.dp))
                Text("${recipe.ingredientOne}${if (recipe.ingredientTwo.isNotBlank()) " · ${recipe.ingredientTwo}" else ""}", color = coral, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(5.dp))
                Text(recipe.family, style = MaterialTheme.typography.labelMedium, color = Color(0xFF746A63))
            }
            IconButton(onClick = { onDelete(recipe) }) { Icon(Icons.Default.DeleteOutline, "Supprimer") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeDetailScreen(recipe: RecipeEntity, onBack: () -> Unit, onEdit: (RecipeEntity) -> Unit, onCrop: (RecipeEntity, Bitmap) -> Unit, onDelete: (RecipeEntity) -> Unit) {
    val context = LocalContext.current
    var showFullScan by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = paper,
        topBar = {
            TopAppBar(
                title = { Text(recipe.nameFrench.ifBlank { recipe.name }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = { shareScan(context, recipe) }, enabled = recipe.scanImagePath != null) {
                        Icon(Icons.Default.Share, "Partager le scan")
                    }
                    IconButton(onClick = { onEdit(recipe) }) { Icon(Icons.Default.Edit, "Modifier") }
                    IconButton(onClick = { onDelete(recipe) }) { Icon(Icons.Default.DeleteOutline, "Supprimer") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                recipe.scanImagePath?.let { path ->
                    BitmapFactory.decodeFile(path)?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Scan original de ${recipe.nameFrench.ifBlank { recipe.name }}",
                            modifier = Modifier.fillMaxWidth().height(260.dp).clickable { showFullScan = true },
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(RecipeFamilies.decode(recipe.familiesJson, recipe.family).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = coral)
                    Text("Ingrédients", style = MaterialTheme.typography.titleMedium)
                    val frenchIngredients = RecipeContent.decodeIngredients(recipe.ingredientsFrenchJson)
                    RecipeContent.decodeIngredients(recipe.ingredientsJson).forEachIndexed { index, ingredient ->
                        val french = frenchIngredients.getOrNull(index)?.name ?: ingredient.name
                        Text(if (french != ingredient.name) "$french (${ingredient.name})" else french, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
    if (showFullScan) {
        FullScanViewer(
            recipe.scanImagePath,
            recipe.nameFrench.ifBlank { recipe.name },
            onDismiss = { showFullScan = false },
            onCrop = { croppedBitmap ->
                onCrop(recipe, croppedBitmap)
                showFullScan = false
            }
        )
    }
}

private fun shareScan(context: android.content.Context, recipe: RecipeEntity) {
    val scan = recipe.scanImagePath?.let { java.io.File(it) }?.takeIf { it.isFile } ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", scan)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, recipe.nameFrench.ifBlank { recipe.name })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Partager la recette"))
}

@Composable
private fun FullScanViewer(imagePath: String?, title: String, onDismiss: () -> Unit, onCrop: (android.graphics.Bitmap) -> Unit) {
    val bitmap = remember(imagePath) { imagePath?.let(BitmapFactory::decodeFile) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var cropMode by remember { mutableStateOf(false) }
    var containerWidth by remember { mutableStateOf(0) }
    var containerHeight by remember { mutableStateOf(0) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += panChange
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black).onSizeChanged { size ->
                containerWidth = size.width
                containerHeight = size.height
            }
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Scan original de $title",
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }.transformable(transformState),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fermer", tint = Color.White)
            }
            IconButton(
                onClick = {
                    if (!cropMode) {
                        cropMode = true
                    } else {
                        bitmap?.let { cropped ->
                            cropVisibleBitmap(cropped, scale, offset, containerWidth, containerHeight)?.let(onCrop)
                        }
                    }
                },
                enabled = bitmap != null && containerWidth > 0 && containerHeight > 0,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Icon(
                    if (cropMode) Icons.Default.Check else Icons.Default.Crop,
                    if (cropMode) "Valider le recadrage" else "Recadrer le scan",
                    tint = Color.White
                )
            }
        }
    }
}

private fun cropVisibleBitmap(bitmap: android.graphics.Bitmap, scale: Float, offset: Offset, containerWidth: Int, containerHeight: Int): android.graphics.Bitmap? {
    if (containerWidth <= 0 || containerHeight <= 0) return null
    val baseScale = minOf(containerWidth.toFloat() / bitmap.width, containerHeight.toFloat() / bitmap.height)
    val renderedScale = baseScale * scale
    val imageLeft = (containerWidth - bitmap.width * renderedScale) / 2f + offset.x
    val imageTop = (containerHeight - bitmap.height * renderedScale) / 2f + offset.y
    val left = ((-imageLeft) / renderedScale).coerceIn(0f, bitmap.width.toFloat()).toInt()
    val top = ((-imageTop) / renderedScale).coerceIn(0f, bitmap.height.toFloat()).toInt()
    val right = ((containerWidth - imageLeft) / renderedScale).coerceIn(0f, bitmap.width.toFloat()).toInt()
    val bottom = ((containerHeight - imageTop) / renderedScale).coerceIn(0f, bitmap.height.toFloat()).toInt()
    val safeLeft = left.coerceAtMost(bitmap.width - 1)
    val safeTop = top.coerceAtMost(bitmap.height - 1)
    val safeRight = right.coerceAtLeast(safeLeft + 1).coerceAtMost(bitmap.width)
    val safeBottom = bottom.coerceAtLeast(safeTop + 1).coerceAtMost(bitmap.height)
    return android.graphics.Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeRight - safeLeft, safeBottom - safeTop)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeEditScreen(recipe: RecipeEntity, onCancel: () -> Unit, onSave: (RecipeEntity) -> Unit) {
    var draft by remember(recipe) { mutableStateOf(recipe) }
    Scaffold(
        containerColor = paper,
        topBar = {
            TopAppBar(
                title = { Text("Modifier la recette") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Annuler") } },
                actions = { TextButton(onClick = { onSave(draft) }) { Text("Enregistrer") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = draft.nameFrench.ifBlank { draft.name },
                onValueChange = { draft = draft.copy(nameFrench = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Titre") },
                singleLine = true
            )
            FamilySelector(draft.familiesJson, draft.family) { draft = draft.withFamilies(it) }
            OutlinedTextField(
                value = frenchIngredientText(draft),
                onValueChange = { draft = draft.withFrenchIngredients(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ingrédients principaux") },
                minLines = 3
            )
        }
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(54.dp), tint = coral)
        Spacer(Modifier.height(12.dp))
        Text("Votre carnet est encore vide", style = MaterialTheme.typography.titleLarge)
        Text("Scannez une recette papier pour commencer.", color = Color(0xFF746A63))
        Spacer(Modifier.height(18.dp))
        Button(onClick = onScan) { Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(8.dp)); Text("Scanner une recette") }
    }
}

@Composable
private fun CameraPermissionScreen(onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().background(paper).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AddAPhoto, null, Modifier.size(56.dp), tint = coral)
        Spacer(Modifier.height(16.dp)); Text("La caméra est nécessaire pour scanner vos recettes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp)); Button(onClick = onRequest) { Text("Autoriser la caméra") }
    }
}
