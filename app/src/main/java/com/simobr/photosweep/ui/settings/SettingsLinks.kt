package com.simobr.photosweep.ui.settings

/**
 * The one place the privacy policy URL is written.
 *
 * One constant, not a string resource: this URL must also appear, character for character, in
 * the Play Console listing and in AdMob's app settings, and a value that can be localised is a
 * value that can end up differing per locale. It is public information, committed on purpose.
 *
 * `SettingsLinksTest` pins that it stays `https`. A privacy policy fetched over plain http can
 * be rewritten in transit, which is a strange thing to let happen to the page that tells the
 * user what the app does with their photos.
 */
const val PRIVACY_POLICY_URL = "https://simobr-studio.github.io/photosweep-privacy.html"
