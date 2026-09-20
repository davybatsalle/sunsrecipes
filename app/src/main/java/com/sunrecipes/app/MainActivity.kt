package com.sunrecipes.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunrecipes.app.data.RecipeEntity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SunRecipesApp() }
    }
}

private val paper = Color(0xFFFAF7F2)
private val ink = Color(0xFF302B27)
private val coral = Color(0xFFD97745)

@Composable
fun SunRecipesApp(recipeViewModel: RecipeViewModel = viewModel()) {
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(recipeViewModel::export) }

    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = coral, background = paper, surface = paper, onBackground = ink, onSurface = ink)) {
        if (showScanner) {
            if (cameraGranted) {
                ScannerScreen(recipeViewModel) { showScanner = false }
            } else {
                CameraPermissionScreen { permissionLauncher.launch(Manifest.permission.CAMERA) }
            }
        } else {
            RecipeHomeScreen(recipeViewModel, onScan = {
                if (cameraGranted) showScanner = true else permissionLauncher.launch(Manifest.permission.CAMERA)
            }, onBackup = { backupLauncher.launch("sun-recipes-backup.json") })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeHomeScreen(viewModel: RecipeViewModel, onScan: () -> Unit, onBackup: () -> Unit) {
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
        topBar = { TopAppBar(title = { Text("Sun's recipes", style = MaterialTheme.typography.headlineSmall) }, actions = {
            IconButton(onClick = onBackup) { Icon(Icons.Default.CloudUpload, "Sauvegarder sur Google Drive") }
        }) },
        floatingActionButton = { FloatingActionButton(onClick = onScan, containerColor = coral, contentColor = Color.White) { Icon(Icons.Default.AddAPhoto, "Scanner une recette") } }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp).fillMaxSize()) {
            Text("Votre carnet de cuisine", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF746A63))
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(value = query, onValueChange = viewModel::updateQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Nom, ingrédient ou famille") }, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(18.dp))
            FamilyRow(recipes)
            Spacer(Modifier.height(12.dp))
            Text("${recipes.size} recette${if (recipes.size > 1) "s" else ""}", style = MaterialTheme.typography.labelLarge, color = Color(0xFF746A63))
            Spacer(Modifier.height(6.dp))
            if (recipes.isEmpty()) EmptyState(onScan) else LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recipes, key = { it.id }) { RecipeCard(it, viewModel::delete) }
            }
        }
    }
}

@Composable
private fun FamilyRow(recipes: List<RecipeEntity>) {
    val families = recipes.groupingBy { it.family }.eachCount().entries.sortedByDescending { it.value }.take(4)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { families.forEach { AssistChip(onClick = {}, label = { Text("${it.key} ${it.value}") }) } }
}

@Composable
private fun RecipeCard(recipe: RecipeEntity, onDelete: (RecipeEntity) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(5.dp))
                Text("${recipe.ingredientOne}${if (recipe.ingredientTwo.isNotBlank()) " · ${recipe.ingredientTwo}" else ""}", color = coral, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(5.dp))
                Text(recipe.family, style = MaterialTheme.typography.labelMedium, color = Color(0xFF746A63))
            }
            IconButton(onClick = { onDelete(recipe) }) { Icon(Icons.Default.DeleteOutline, "Supprimer") }
        }
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.MenuBook, null, Modifier.size(54.dp), tint = coral)
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
