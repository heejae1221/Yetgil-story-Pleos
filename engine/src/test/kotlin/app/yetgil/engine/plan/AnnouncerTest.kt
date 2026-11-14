package app.yetgil.engine.plan

import app.yetgil.engine.geo.Side
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnouncerTest {
    @Test
    fun `받침에 따라 을 를`() {
        assertEquals("수덕사를", Announcer.withObjectParticle("수덕사"))
        assertEquals("덕산온천을", Announcer.withObjectParticle("덕산온천"))
        assertEquals("독산성 세마대를", Announcer.withObjectParticle("독산성 세마대"))
    }

    @Test
    fun `안내 멘트와 화면 문구`() {
        assertEquals("잠시 후 오른쪽으로 통도사를 지나요", Announcer.spoken("통도사", Side.RIGHT))
        assertEquals("잠시 후 왼쪽 · 약 750m 옆을 지나요", Announcer.onScreen(Side.LEFT, 742.0))
    }

    @Test
    fun `거리 반올림`() {
        assertEquals("약 50m", Announcer.formatDistance(12.0))
        assertEquals("약 1km", Announcer.formatDistance(1_020.0))
        assertEquals("약 2.5km", Announcer.formatDistance(2_460.0))
    }
}
