package com.toolstack.io

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the App Check debug provider — Play Integrity can't attest a
 * sideloaded debug app. On first launch the provider prints a debug token to
 * logcat; register it in the Firebase console (App Check → app → debug tokens).
 *
 * The sibling file under src/release returns the Play Integrity factory.
 * Splitting by source set keeps firebase-appcheck-debug a debugImplementation,
 * so the debug provider never ships in release builds.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory =
    DebugAppCheckProviderFactory.getInstance()
