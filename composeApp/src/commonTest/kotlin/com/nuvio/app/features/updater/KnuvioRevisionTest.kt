package com.nuvio.app.features.updater

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class KnuvioRevisionTest {
    @Test
    fun `knuvio revision is newer than the base build`() {
        assertTrue(VersionUtils.isRemoteNewer("1.1.0-beta.7+k.1", "1.1.0-beta.7+k"))
        assertTrue(VersionUtils.isRemoteNewer("1.1.0-beta.7+k.1", "1.1.0-beta.7"))
        assertTrue(VersionUtils.isRemoteNewer("1.1.0+k.1", "1.1.0+k"))
    }

    @Test
    fun `knuvio revisions use numeric ordering`() {
        assertTrue(VersionUtils.isRemoteNewer("1.1.0-beta.7+k.10", "1.1.0-beta.7+k.9"))
    }

    @Test
    fun `base build matches the plain upstream version`() {
        assertFalse(VersionUtils.isRemoteNewer("1.1.0-beta.7+k", "1.1.0-beta.7"))
        assertFalse(VersionUtils.isRemoteNewer("1.1.0-beta.7", "1.1.0-beta.7+k"))
    }

    @Test
    fun `next upstream version beats any revision of the previous one`() {
        assertTrue(VersionUtils.isRemoteNewer("1.1.0-beta.8+k", "1.1.0-beta.7+k.5"))
        assertTrue(VersionUtils.isRemoteNewer("1.1.0+k", "1.1.0-beta.9+k.3"))
        assertTrue(VersionUtils.isRemoteNewer("1.1.1-beta.1+k", "1.1.0+k.4"))
        assertFalse(VersionUtils.isRemoteNewer("1.1.0-beta.7+k.9", "1.1.0-beta.8+k"))
    }

    @Test
    fun `stable revisions are not prereleases`() {
        assertFalse(VersionUtils.isPrerelease("1.1.0+k.2"))
        assertTrue(VersionUtils.isPrerelease("1.1.0-beta.7+k.2"))
    }

    @Test
    fun `upstream mobile tags order with their revisions`() {
        assertTrue(VersionUtils.isRemoteNewer("0.6.9-beta+k.1", "0.6.9-beta+k"))
        assertTrue(VersionUtils.isRemoteNewer("0.6.10-beta+k", "0.6.9-beta+k.3"))
        assertFalse(VersionUtils.isRemoteNewer("0.6.9-beta+k", "0.6.9-beta"))
    }

    @Test
    fun `other build metadata is still ignored`() {
        assertFalse(VersionUtils.isRemoteNewer("1.0.0+18", "1.0.0+17"))
        assertFalse(VersionUtils.isRemoteNewer("1.0.0+kx.2", "1.0.0"))
    }
}
