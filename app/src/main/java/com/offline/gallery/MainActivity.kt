package com.offline.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GalleryApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("trash_prefs", Context.MODE_PRIVATE) }
    
    var trashedIds by remember { 
        mutableStateOf(sharedPrefs.getStringSet("trashed_ids", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()) 
    }
    
    var photos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var videos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Photos, 1: Videos, 2: Trash
    var selectedMediaForPreview by remember { mutableStateOf<MediaItem?>(null) }
    var hasPermission by remember { mutableStateOf(false) }

    fun saveTrashedIds(newSet: Set<Long>) {
        trashedIds = newSet
        sharedPrefs.edit().putStringSet("trashed_ids", newSet.map { it.toString() }.toSet()).apply()
    }

    fun loadMedia() {
        val photoList = mutableListOf<MediaItem>()
        val videoList = mutableListOf<MediaItem>()

        // Load Photos
        val photoUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val photoProjection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(photoUri, photoProjection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(photoUri, id)
                photoList.add(MediaItem(id, uri, false))
            }
        }

        // Load Videos
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val videoProjection = arrayOf(MediaStore.Video.Media._ID)
        context.contentResolver.query(videoUri, videoProjection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(videoUri, id)
                videoList.add(MediaItem(id, uri, true))
            }
        }

        photos = photoList
        videos = videoList
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        hasPermission = granted
        if (granted) loadMedia()
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            hasPermission = true
            loadMedia()
        } else {
            launcher.launch(permissionsToRequest)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when (selectedTab) {
                            0 -> "Photos"
                            1 -> "Videos"
                            else -> "Recycle Bin (Trash)"
                        }
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Photo, contentDescription = "Photos") },
                    label = { Text("Photos") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Videocam, contentDescription = "Videos") },
                    label = { Text("Videos") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Delete, contentDescription = "Trash") },
                    label = { Text("Trash") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (!hasPermission) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        launcher.launch(permissionsToRequest)
                    }) {
                        Text("স্টোরেজ পারমিশন দিন")
                    }
                }
            } else {
                val currentItems = when (selectedTab) {
                    0 -> photos.filter { it.id !in trashedIds }
                    1 -> videos.filter { it.id !in trashedIds }
                    else -> (photos + videos).filter { it.id in trashedIds }
                }

                if (currentItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (selectedTab == 2) "রিসাইকেল বিন খালি" else "কোনো ফাইল পাওয়া যায়নি", color = Color.Gray)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        items(currentItems, key = { it.id }) { item ->
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .aspectRatio(1f)
                                    .background(Color.DarkGray)
                                    .clickable { selectedMediaForPreview = item }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(item.uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (item.isVideo) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ফুলস্ক্রিন প্রিভিউ ও ট্র্যাশ/রিস্টোর অ্যাকশন
    selectedMediaForPreview?.let { media ->
        val isItemInTrash = media.id in trashedIds
        Dialog(
            onDismissRequest = { selectedMediaForPreview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = media.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // টপ বার বাটন
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { selectedMediaForPreview = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Row {
                        if (isItemInTrash) {
                            IconButton(onClick = {
                                saveTrashedIds(trashedIds - media.id)
                                Toast.makeText(context, "রিস্টোর করা হয়েছে", Toast.LENGTH_SHORT).show()
                                selectedMediaForPreview = null
                            }) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = Color.White)
                            }
                            IconButton(onClick = {
                                saveTrashedIds(trashedIds - media.id)
                                photos = photos.filter { it.id != media.id }
                                videos = videos.filter { it.id != media.id }
                                Toast.makeText(context, "স্থায়ীভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                selectedMediaForPreview = null
                            }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Permanently", tint = Color.Red)
                            }
                        } else {
                            IconButton(onClick = {
                                saveTrashedIds(trashedIds + media.id)
                                Toast.makeText(context, "ট্র্যাশে সরানো হয়েছে", Toast.LENGTH_SHORT).show()
                                selectedMediaForPreview = null
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Move to Trash", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
