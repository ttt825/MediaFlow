package com.lollipop.mediaflow.tools

import android.app.AlertDialog
import android.content.Context
import android.provider.DocumentsContract
import android.widget.Toast
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore

object MediaDeleteHelper {

    fun showDeleteConfirmDialog(
        context: Context,
        file: MediaInfo.File,
        gallery: MediaStore.Gallery?,
        onDeleteSuccess: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_title_delete_confirm)
            .setMessage(context.getString(R.string.dialog_message_delete_file, file.name))
            .setPositiveButton(R.string.button_delete) { dialog, _ ->
                dialog.dismiss()
                performDelete(context, file, gallery, onDeleteSuccess)
            }
            .setNegativeButton(R.string.button_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun performDelete(
        context: Context,
        file: MediaInfo.File,
        gallery: MediaStore.Gallery?,
        onDeleteSuccess: () -> Unit
    ) {
        doAsync(
            error = {
                onUI {
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_delete_failed, file.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        ) {
            val deleted = try {
                DocumentsContract.deleteDocument(context.contentResolver, file.uri)
            } catch (e: Exception) {
                false
            }
            onUI {
                if (deleted) {
                    gallery?.remove(file)
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_delete_success, file.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDeleteSuccess()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_delete_failed, file.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

}
