package com.toolstack.io.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.toolstack.io.MainActivity

/**
 * Creates a launcher-pinned shortcut that deep-links directly to a specific
 * tool screen via the [toolstack://screen/{route}][SCHEME] URI.
 *
 * Callers should check [ShortcutManagerCompat.isRequestPinShortcutSupported]
 * before calling this — or just call it and let Android handle unsupported
 * launchers gracefully (the system dialog simply will not appear).
 */
object ShortcutUtil {

    const val SCHEME = "toolstack"
    const val HOST   = "screen"

    /**
     * Request a pinned shortcut from the launcher.
     *
     * Android shows a system-level "Add to Home Screen?" confirmation dialog.
     * The launcher decides whether to place it; there is no callback for the
     * user's choice, which is normal Android behaviour.
     *
     * @param context  Any context; application context is preferred to avoid
     *                 leaking an Activity reference through the callback PendingIntent.
     * @param route    The [Screen] route string, e.g. `"sae_metric"`.
     * @param label    Short label shown under the icon on the home screen (≤ ~15 chars recommended).
     * @param iconResId Drawable resource to use as the shortcut icon.
     */
    fun requestPinShortcut(
        context: Context,
        route: String,
        label: String,
        iconResId: Int
    ) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("$SCHEME://$HOST/$route"),
            context,
            MainActivity::class.java
        ).apply {
            // Bring an existing task to the front rather than stacking a new
            // Activity on top of whatever is already running.
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, "shortcut_$route")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, iconResId))
            .setIntent(intent)
            .build()

        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /**
     * Extracts the route string from a `toolstack://screen/{route}` URI,
     * or returns null if the URI does not match the scheme/host.
     */
    fun extractRoute(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != SCHEME || uri.host != HOST) return null
        // pathSegments for "toolstack://screen/sae_metric" → ["sae_metric"]
        return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
