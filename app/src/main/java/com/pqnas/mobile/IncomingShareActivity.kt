package com.pqnas.mobile

// PQNAS_INCOMING_ANDROID_SHARE_V1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class IncomingShareActivity : Activity() {
    companion object {
        const val EXTRA_MANIFEST_PATH = "com.pqnas.mobile.extra.INCOMING_SHARE_MANIFEST_PATH"
        const val EXTRA_ITEM_COUNT = "com.pqnas.mobile.extra.INCOMING_SHARE_ITEM_COUNT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread {
            try {
                val result = IncomingShareStager.stage(this, intent)

                runOnUiThread {
                    val launch = packageManager.getLaunchIntentForPackage(packageName)
                    if (launch == null) {
                        Toast.makeText(
                            this,
                            "DNA-Nexus received the share, but could not open the app.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return@runOnUiThread
                    }

                    launch.putExtra(EXTRA_MANIFEST_PATH, result.manifestPath)
                    launch.putExtra(EXTRA_ITEM_COUNT, result.itemCount)
                    launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(launch)
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "DNA-Nexus share receive failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }.start()
    }
}
