
package com.captord.screenrecorder.ui.home

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DetectionSettingsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences(
            "detection_settings",
            Context.MODE_PRIVATE
        )
    }

    var names by remember {
        mutableStateOf(
            prefs.getStringSet("names", setOf("هيبة"))
                ?.toList()
                ?.sorted()
                ?: listOf("هيبة")
        )
    }

    var newName by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var soundUris by remember {
        mutableStateOf(
            names.associateWith { name ->
                prefs.getString("sound_$name", null)
            }
        )
    }

    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val name = selectedName

        if (uri != null && name != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                soundUris = soundUris + (name to uri.toString())

                prefs.edit()
                    .putString("sound_$name", uri.toString())
                    .apply()

                message = "تم حفظ صوت: $name"
            } catch (e: Exception) {
                message = "تعذر حفظ الصوت"
            }
        }
    }

    fun playSound(uriString: String?) {
        if (uriString == null) {
            message = "اختَر صوتًا لهذا الاسم أولًا"
            return
        }

        try {
            val player = MediaPlayer.create(
                context,
                Uri.parse(uriString)
            )

            if (player == null) {
                message = "تعذر تشغيل الملف الصوتي"
            } else {
                player.setOnCompletionListener {
                    it.release()
                }
                player.setOnErrorListener { mp, _, _ ->
                    mp.release()
                    true
                }
                player.start()
                message = "يتم تشغيل الصوت"
            }
        } catch (e: Exception) {
            message = "تعذر تشغيل الصوت"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "إعدادات كشف الأسماء",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                "أضف الأسماء التي تريد اكتشافها، وخصص صوتًا لكل اسم.",
                style = MaterialTheme.typography.bodyMedium
            )

            names.forEach { name ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            if (soundUris[name] != null) {
                                "يوجد صوت مخصص لهذا الاسم"
                            } else {
                                "لم يتم اختيار صوت"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedName = name
                                    soundPicker.launch(arrayOf("audio/*"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("اختيار صوت")
                            }

                            OutlinedButton(
                                onClick = {
                                    playSound(soundUris[name])
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("تجربة")
                            }
                        }

                        TextButton(
                            onClick = {
                                names = names.filterNot { it == name }

                                prefs.edit()
                                    .putStringSet("names", names.toSet())
                                    .remove("sound_$name")
                                    .apply()

                                soundUris = soundUris - name
                                message = "تم حذف الاسم"
                            }
                        ) {
                            Text(
                                "حذف الاسم",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم جديد") },
                singleLine = true
            )

            Button(
                onClick = {
                    val name = newName.trim()

                    if (name.isBlank()) {
                        message = "اكتب اسمًا أولًا"
                    } else if (name in names) {
                        message = "هذا الاسم موجود بالفعل"
                    } else {
                        names = (names + name).sorted()

                        prefs.edit()
                            .putStringSet("names", names.toSet())
                            .apply()

                        soundUris = soundUris + (name to null)
                        newName = ""
                        message = "تمت إضافة الاسم"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إضافة اسم")
            }

            Spacer(Modifier.height(4.dp))

            if (message.isNotBlank()) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
