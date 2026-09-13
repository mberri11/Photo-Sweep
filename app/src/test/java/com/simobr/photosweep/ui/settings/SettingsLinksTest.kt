package com.simobr.photosweep.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The privacy policy URL.
 *
 * The same string has to appear in the Play Console listing and in AdMob's app settings, so it
 * is one constant rather than a resource, and it is asserted rather than trusted: a policy page
 * fetched over plain http can be rewritten in transit, which is a peculiar thing to allow for
 * the page that tells a user what an app does with their photos.
 */
class SettingsLinksTest {

    @Test
    fun `the privacy policy url is https and not a placeholder`() {
        assertTrue("the url must not be empty", PRIVACY_POLICY_URL.isNotBlank())
        assertTrue(
            "the privacy policy must be served over https, not <$PRIVACY_POLICY_URL>",
            PRIVACY_POLICY_URL.startsWith("https://"),
        )
        assertFalse("http:// is not https://", PRIVACY_POLICY_URL.startsWith("http://"))
    }

    @Test
    fun `the privacy policy url is the published one`() {
        assertEquals(
            "https://simobr-studio.github.io/photosweep-privacy.html",
            PRIVACY_POLICY_URL,
        )
    }

    /** A URL with whitespace in it is a URL somebody pasted across a line break. */
    @Test
    fun `the privacy policy url carries no whitespace`() {
        assertEquals(PRIVACY_POLICY_URL.trim(), PRIVACY_POLICY_URL)
        assertFalse(PRIVACY_POLICY_URL.any { it.isWhitespace() })
    }
}
