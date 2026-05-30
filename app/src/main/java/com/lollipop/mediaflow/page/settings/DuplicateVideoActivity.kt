package com.lollipop.mediaflow.page.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaLoader
import com.lollipop.mediaflow.data.MediaMetadata
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.data.RootUri
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.doAsync
import com.lollipop.mediaflow.tools.onUI
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import java.io.File

class DuplicateVideoActivity : BasicComposeActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DuplicateVideoActivity::class.java)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private val log = registerLog()

    private val duplicateGroups = SnapshotStateList<DuplicateGroup>()

    private var isScanning by mutableStateOf(false)

    private val rootUriList = SnapshotStateList<RootUri>()

    private var selectedRootUriString by mutableStateOf<String?>(null)

    private val privateVideoGallery by lazy {
        MediaStore.loadGallery(this, MediaVisibility.Private, MediaType.Video)
    }

    private val privateMediaStore by lazy {
        MediaStore.loadStore(this, MediaVisibility.Private)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        loadRootUris()
    }

    private fun loadRootUris() {
        privateMediaStore.loadRootUri { success ->
            if (success) {
                rootUriList.clear()
                rootUriList.addAll(privateMediaStore.cache.rootList)
                if (rootUriList.isNotEmpty() && selectedRootUriString == null) {
                    selectedRootUriString = null
                }
                startScan()
            }
        }
    }

    private fun startScan() {
        isScanning = true
        duplicateGroups.clear()
        val gallery = privateVideoGallery
        gallery.loadAll { g, _ ->
            val fileList = ArrayList(g.fileList)
            findDuplicates(fileList)
        }
    }

    private fun findDuplicates(fileList: List<MediaInfo.File>) {
        isScanning = true
        duplicateGroups.clear()
        doAsync(
            error = {
                log.e("findDuplicates", it)
                onUI { isScanning = false }
            }
        ) {
            for (file in fileList) {
                if (file.metadata == null) {
                    MediaLoader.loadMediaMetadataSync(
                        this@DuplicateVideoActivity,
                        file,
                        cacheOnly = false
                    )
                }
            }

            val filtered = if (selectedRootUriString != null) {
                fileList.filter { it.rootUriString == selectedRootUriString }
            } else {
                fileList
            }

            val groups = if (selectedRootUriString != null) {
                findDuplicatesByDuration(filtered)
            } else {
                val byName = findDuplicatesByName(filtered)
                val byDuration = findDuplicatesByDuration(filtered)
                mergeDuplicateGroups(byName, byDuration)
            }

            onUI {
                isScanning = false
                duplicateGroups.clear()
                duplicateGroups.addAll(groups)
            }
        }
    }

    private fun mergeDuplicateGroups(
        byName: List<DuplicateGroup>,
        byDuration: List<DuplicateGroup>
    ): List<DuplicateGroup> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<DuplicateGroup>()
        var index = 1
        for (group in byName) {
            val key = group.files.map { it.uriString }.sorted().joinToString("|")
            if (seen.add(key)) {
                result.add(DuplicateGroup(index++, group.files))
            }
        }
        for (group in byDuration) {
            val key = group.files.map { it.uriString }.sorted().joinToString("|")
            if (seen.add(key)) {
                result.add(DuplicateGroup(index++, group.files))
            }
        }
        return result
    }

    private fun findDuplicatesByName(fileList: List<MediaInfo.File>): List<DuplicateGroup> {
        val nameMap = mutableMapOf<String, MutableList<MediaInfo.File>>()
        for (file in fileList) {
            val nameWithoutExt = File(file.name).nameWithoutExtension.lowercase()
            nameMap.getOrPut(nameWithoutExt) { mutableListOf() }.add(file)
        }
        return nameMap.values
            .filter { it.size > 1 }
            .mapIndexed { index, files -> DuplicateGroup(index + 1, files) }
    }

    private fun findDuplicatesByDuration(fileList: List<MediaInfo.File>): List<DuplicateGroup> {
        val durationMap = mutableMapOf<Long, MutableList<MediaInfo.File>>()
        for (file in fileList) {
            val duration = file.metadata?.duration ?: 0
            if (duration > 0) {
                val bucket = duration / 1000
                durationMap.getOrPut(bucket) { mutableListOf() }.add(file)
            }
        }
        val result = mutableListOf<DuplicateGroup>()
        var index = 1
        for ((_, files) in durationMap) {
            if (files.size < 2) continue
            val nameGroups = mutableMapOf<String, MutableList<MediaInfo.File>>()
            for (file in files) {
                val nameWithoutExt = File(file.name).nameWithoutExtension.lowercase()
                val matched = nameGroups.entries.any { (key, group) ->
                    if (isSimilarName(key, nameWithoutExt)) {
                        group.add(file)
                        true
                    } else {
                        false
                    }
                }
                if (!matched) {
                    nameGroups[nameWithoutExt] = mutableListOf(file)
                }
            }
            for (group in nameGroups.values) {
                if (group.size >= 2) {
                    result.add(DuplicateGroup(index++, group))
                }
            }
        }
        return result
    }

    private fun isSimilarName(name1: String, name2: String): Boolean {
        if (name1 == name2) return true
        if (name1.contains(name2) || name2.contains(name1)) return true
        val maxLen = maxOf(name1.length, name2.length)
        if (maxLen == 0) return true
        val distance = levenshteinDistance(name1, name2)
        return distance.toFloat() / maxLen < 0.3F
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun deleteFile(file: MediaInfo.File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_delete_confirm)
            .setMessage(getString(R.string.duplicate_delete_confirm_message, file.name))
            .setPositiveButton(R.string.button_delete) { dialog, _ ->
                dialog.dismiss()
                performDelete(file)
            }
            .setNegativeButton(R.string.button_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun performDelete(file: MediaInfo.File) {
        doAsync(
            error = {
                onUI {
                    Toast.makeText(
                        this@DuplicateVideoActivity,
                        getString(R.string.msg_delete_failed, file.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        ) {
            val deleted = try {
                DocumentsContract.deleteDocument(contentResolver, file.uri)
            } catch (e: Exception) {
                false
            }
            onUI {
                if (deleted) {
                    privateVideoGallery.remove(file)
                    Toast.makeText(
                        this@DuplicateVideoActivity,
                        getString(R.string.msg_delete_success, file.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    for (group in duplicateGroups) {
                        group.files.remove(file)
                    }
                    duplicateGroups.removeAll { it.files.isEmpty() }
                } else {
                    Toast.makeText(
                        this@DuplicateVideoActivity,
                        getString(R.string.msg_delete_failed, file.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val layoutDirection = LocalLayoutDirection.current
        val groups = remember { duplicateGroups }
        val scanning = isScanning
        val currentRootUris = remember { rootUriList }
        val currentSelected = selectedRootUriString

        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(currentThemeColor().windowBackground)
                    .padding(
                        start = (padding.calculateLeftPadding(layoutDirection)).coerceAtLeast(16.dp),
                        end = (padding.calculateRightPadding(layoutDirection)).coerceAtLeast(16.dp),
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.height(padding.calculateTopPadding()))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = currentThemeColor().buttonBackground
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onBackPressedDispatcher.onBackPressed()
                                    }
                                    .padding(6.dp),
                                tint = currentThemeColor().buttonText,
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = stringResource(R.string.duplicate_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    var expanded by remember { mutableStateOf(false) }
                    val selectedName = if (currentSelected == null) {
                        stringResource(R.string.duplicate_all)
                    } else {
                        currentRootUris.find { it.uriString == currentSelected }?.name
                            ?: stringResource(R.string.duplicate_all)
                    }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            label = {
                                Text(stringResource(R.string.source_manager))
                            },
                            singleLine = true
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.duplicate_all)) },
                                onClick = {
                                    expanded = false
                                    if (selectedRootUriString != null) {
                                        selectedRootUriString = null
                                        startScan()
                                    }
                                }
                            )
                            for (rootUri in currentRootUris) {
                                DropdownMenuItem(
                                    text = { Text(rootUri.name) },
                                    onClick = {
                                        expanded = false
                                        if (selectedRootUriString != rootUri.uriString) {
                                            selectedRootUriString = rootUri.uriString
                                            startScan()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (scanning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1F),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.duplicate_scanning),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else if (groups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1F),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.duplicate_no_result),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6F),
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1F)
                        ) {
                            items(groups) { group ->
                                DuplicateGroupItem(
                                    group = group,
                                    onDeleteClick = { file -> deleteFile(file) }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(42.dp))
                                Spacer(modifier = Modifier.height(padding.calculateBottomPadding()))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DuplicateGroupItem(
        group: DuplicateGroup,
        onDeleteClick: (MediaInfo.File) -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.duplicate_group_title,
                    group.index,
                    group.files.size
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7F),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val chunked = group.files.chunked(2)
            for (row in chunked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (file in row) {
                        DuplicateVideoCard(
                            file = file,
                            onDeleteClick = { onDeleteClick(file) },
                            modifier = Modifier.weight(1F)
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1F))
                    }
                }
            }
        }
    }

    @Composable
    private fun DuplicateVideoCard(
        file: MediaInfo.File,
        onDeleteClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDeleteClick() }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }
                        },
                        update = { imageView ->
                            Glide.with(imageView)
                                .load(file.uri)
                                .centerCrop()
                                .into(imageView)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    )

                    val duration = file.metadata?.duration ?: 0
                    if (duration > 0) {
                        Text(
                            text = MediaMetadata.formatDuration(duration),
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.6F),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    class DuplicateGroup(
        val index: Int,
        val files: MutableList<MediaInfo.File>
    )
}
