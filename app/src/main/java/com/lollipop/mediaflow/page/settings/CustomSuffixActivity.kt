package com.lollipop.mediaflow.page.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BasicComposeActivity

class CustomSuffixActivity : BasicComposeActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CustomSuffixActivity::class.java).apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
        }
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        CustomSuffixContent(innerPadding)
    }

    @Composable
    private fun CustomSuffixContent(innerPadding: PaddingValues) {
        var suffixList by remember {
            mutableStateOf(Preferences.customVideoSuffixes.get().toList().sorted())
        }
        var inputText by remember { mutableStateOf("") }

        ContentColumn(
            innerPadding = innerPadding
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.hint_custom_video_suffix),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7F)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text(stringResource(R.string.hint_custom_video_suffix_input)) },
                        singleLine = true,
                        modifier = Modifier.weight(1F)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = {
                            val normalized = inputText.trim().lowercase().removePrefix(".")
                            if (normalized.isEmpty()) {
                                Toast.makeText(
                                    this@CustomSuffixActivity,
                                    R.string.msg_suffix_empty,
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            val current = Preferences.customVideoSuffixes.get()
                            if (current.contains(normalized)) {
                                Toast.makeText(
                                    this@CustomSuffixActivity,
                                    R.string.msg_suffix_exists,
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            Preferences.customVideoSuffixes.add(normalized)
                            suffixList = Preferences.customVideoSuffixes.get().toList().sorted()
                            inputText = ""
                            Toast.makeText(
                                this@CustomSuffixActivity,
                                getString(R.string.msg_suffix_added, normalized),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(stringResource(R.string.button_add_suffix))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(suffixList) { suffix ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ".$suffix",
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1F)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                Preferences.customVideoSuffixes.remove(suffix)
                                suffixList =
                                    Preferences.customVideoSuffixes.get().toList().sorted()
                            }
                            .padding(8.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            if (suffixList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.empty_custom_video_suffix),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5F)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                }
            }
        }
    }

}

