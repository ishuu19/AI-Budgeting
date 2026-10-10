package com.ledgerai.app.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class FastModelRouteTest {

    @Test
    fun releaseOnlineCloudOnEdgeConfiguredNoClientKey_isProxy() {
        assertEquals(
            FastChannel.PROXY,
            chooseFastChannel(
                cloudEnabled = true,
                online = true,
                allowClientKey = false,
                hasClientOpenRouterKey = false,
                edgeConfigured = true,
            ),
        )
    }

    @Test
    fun releaseOnlineEdgeConfiguredClientKeyPresent_isProxy() {
        assertEquals(
            FastChannel.PROXY,
            chooseFastChannel(
                cloudEnabled = true,
                online = true,
                allowClientKey = false,
                hasClientOpenRouterKey = true,
                edgeConfigured = true,
            ),
        )
    }

    @Test
    fun debugOnlineCloudOnClientKeyPresent_isDirect() {
        assertEquals(
            FastChannel.DIRECT,
            chooseFastChannel(
                cloudEnabled = true,
                online = true,
                allowClientKey = true,
                hasClientOpenRouterKey = true,
                edgeConfigured = true,
            ),
        )
    }

    @Test
    fun offline_isSkip() {
        assertEquals(
            FastChannel.SKIP,
            chooseFastChannel(
                cloudEnabled = true,
                online = false,
                allowClientKey = false,
                hasClientOpenRouterKey = false,
                edgeConfigured = true,
            ),
        )
    }

    @Test
    fun cloudFallbackOff_isSkip() {
        assertEquals(
            FastChannel.SKIP,
            chooseFastChannel(
                cloudEnabled = false,
                online = true,
                allowClientKey = false,
                hasClientOpenRouterKey = false,
                edgeConfigured = true,
            ),
        )
    }

    @Test
    fun releaseOnlineCloudOnEdgeNotConfigured_isSkip() {
        assertEquals(
            FastChannel.SKIP,
            chooseFastChannel(
                cloudEnabled = true,
                online = true,
                allowClientKey = false,
                hasClientOpenRouterKey = false,
                edgeConfigured = false,
            ),
        )
    }
}
