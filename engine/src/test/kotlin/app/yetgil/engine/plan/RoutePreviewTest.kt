package app.yetgil.engine.plan

import app.yetgil.engine.Fixtures
import app.yetgil.engine.Fixtures.NOW
import app.yetgil.engine.Fixtures.place
import app.yetgil.engine.Fixtures.pointBeside
import app.yetgil.engine.geo.Side
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutePreviewTest {
    @Test
    fun `이번 길에서 만날 곳을 가까운 순서로`() {
        val places = listOf(
            place("far", pointBeside(25_000.0, 1_000.0)),
            place("near", pointBeside(2_000.0, -500.0)),
            place("off-route", pointBeside(10_000.0, 8_000.0)),
        )
        val items = RoutePreview.build(Fixtures.northRoute, places, StorySettings(), ListeningHistory(), NOW)
        assertEquals(listOf("near", "far"), items.map { it.place.id })
        assertEquals(Side.LEFT, items[0].side)
        assertEquals(2_000.0, items[0].distanceFromStartM, 20.0)
    }
}
