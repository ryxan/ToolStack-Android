package com.toolstack.io

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds attest via Play Integrity (SHA-256 fingerprints registered in
 * the Firebase console). The sibling file under src/debug returns the debug
 * provider instead.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory =
    PlayIntegrityAppCheckProviderFactory.getInstance()
