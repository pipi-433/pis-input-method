/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis.voice

import android.app.Activity
import android.os.Bundle
import android.Manifest
import android.widget.Toast

class PisVoicePermissionActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (saved == null) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        Toast.makeText(this, if (results.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED)
            "已授权，请重新长按空格说话" else "未授权录音，可继续使用打字", Toast.LENGTH_SHORT).show()
        finish()
    }
}
