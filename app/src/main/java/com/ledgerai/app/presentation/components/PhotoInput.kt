package com.ledgerai.app.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/** Two actions every photo field offers: shoot with the camera, or pick from the gallery. */
class PhotoActions(val shoot: () -> Unit, val pick: () -> Unit)

/**
 * Camera and gallery in one place, with the camera permission handled. [onPicked] gets the chosen
 * images (a content or file-provider Uri). Nobody types a file path anywhere.
 */
@Composable
fun rememberPhotoActions(max: Int = 1, onPicked: (List<Uri>) -> Unit): PhotoActions {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pending
        pending = null
        if (saved && uri != null) onPicked(listOf(uri))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(max.coerceAtLeast(2))) { uris ->
        if (uris.isNotEmpty()) onPicked(uris.take(max))
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "capture").also { it.mkdirs() }
        val file = File.createTempFile("shot_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pending = uri
        camera.launch(uri)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }

    return PhotoActions(
        shoot = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                permission.launch(Manifest.permission.CAMERA)
            }
        },
        pick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

/** A photo field for a form: shows the chosen image with a remove button, or the two ways to add one. */
@Composable
fun PhotoField(photo: Uri?, onPhoto: (Uri?) -> Unit, modifier: Modifier = Modifier) {
    val actions = rememberPhotoActions { onPhoto(it.firstOrNull()) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (photo != null) {
            Box(Modifier.size(96.dp)) {
                AsyncUriThumb(photo, Modifier.size(96.dp).clip(RoundedCornerShape(L.RadiusSm)))
                Box(
                    Modifier.align(Alignment.TopEnd).size(28.dp).clip(RoundedCornerShape(50)).background(L.Ink.copy(alpha = 0.75f))
                        .clickable { onPhoto(null) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = L.Page, modifier = Modifier.size(16.dp)) }
            }
        }
        PhotoButton(Icons.Filled.PhotoCamera, if (photo == null) "Take photo" else "Retake", actions.shoot)
        PhotoButton(Icons.Filled.PhotoLibrary, "Choose", actions.pick)
    }
}

@Composable
fun PhotoButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(L.BoxDeep)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = L.Primary, modifier = Modifier.size(20.dp))
        androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = L.Primary)
    }
}
