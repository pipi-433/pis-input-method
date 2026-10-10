/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import java.lang.reflect.Modifier
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.junit.Assert.assertFalse
import org.junit.Test

class FcitxApiDelegateTest {
    @Test fun everyAbstractApiMethodHasAConcreteDelegate() {
        val delegate = Class.forName(
            "org.fcitx.fcitx5.android.daemon.FcitxDaemon\$fcitxImpl\$2\$1",
            false,
            javaClass.classLoader
        )
        FcitxAPI::class.java.methods.filter { Modifier.isAbstract(it.modifiers) }.forEach { method ->
            val implementation = delegate.getMethod(method.name, *method.parameterTypes)
            assertFalse("Missing concrete delegate for ${method.name}", Modifier.isAbstract(implementation.modifiers))
        }
    }
}
