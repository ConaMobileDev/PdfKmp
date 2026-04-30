package com.conamobile.romchi.platform

import platform.posix.exit

actual fun restartApplication() {
    exit(0)
}
