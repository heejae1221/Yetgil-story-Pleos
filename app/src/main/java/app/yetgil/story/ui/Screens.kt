package app.yetgil.story.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yetgil.engine.geo.Side
import app.yetgil.engine.plan.DriveMode
import app.yetgil.engine.plan.Frequency
import app.yetgil.engine.plan.ListenerMode
import app.yetgil.story.DriveController
import app.yetgil.story.DrivingCard
import app.yetgil.story.UiState
import java.time.Duration
import java.time.Instant

private enum class Tab(val label: String) { NOW("지금 이야기"), ROUTE("이번 길"), COLLECTION("도감"), SETTINGS("설정") }

@Composable
fun AppScreen(state: UiState, c: DriveController) {
    var tab by remember { mutableStateOf(Tab.NOW) }
    var showDemo by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(Ui.bg)) {
        AppBar(tab, { tab = it }, state)
        Box(Modifier.weight(1f).fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 24.dp)) {
            when (tab) {
                Tab.NOW -> NowTab(state, c)
                Tab.ROUTE -> {
                    androidx.compose.runtime.LaunchedEffect(Unit) { c.onRouteTabOpened() }
                    RouteTab(state)
                }
                Tab.COLLECTION -> CollectionTab(state)
                Tab.SETTINGS -> SettingsTab(state, c)
            }
            state.toast?.let {
                Box(Modifier.align(Alignment.BottomCenter).clip(Ui.rMd).background(Ui.surface2).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(it, style = Ui.body)
                }
            }
        }
        DemoBar(state, c, showDemo) { showDemo = !showDemo }
    }
}

@Composable
private fun AppBar(tab: Tab, onTab: (Tab) -> Unit, state: UiState) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("옛길 이야기", style = Ui.title3)
        Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tab.entries.forEach { t ->
                Column(
                    Modifier.fillMaxHeight().clickable { onTab(t) }.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        t.label,
                        color = if (t == tab) Ui.text1 else Ui.text3,
                        fontSize = 20.sp,
                        fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.width(48.dp).height(3.dp).background(if (t == tab) Ui.accent else Color.Transparent))
                }
            }
        }
        Grow()
        val busy = state.card != null || state.quiz != null || state.parked != null
        if (state.mode == DriveMode.PARKED) Chip("정차 중", ChipKind.Positive, dot = true)
        else if (busy) Chip("이야기 중", ChipKind.Accent, dot = true)
        Chip(listenerLabel(state.settings.listenerMode))
        Text("${state.speedKmh} km/h", style = Ui.body.copy(color = Ui.text2))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ui.line))
}

// ---------------- ① 지금 이야기 ----------------

@Composable
private fun NowTab(state: UiState, c: DriveController) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                state.parked != null -> ParkedPanel(state, c)
                state.quiz != null -> QuizPanel(state, c)
                state.card != null -> DrivingPanel(state.card, c)
                state.parkedIdle -> EmptyPanel("세워 둔 곳 10km 안에 들려줄 긴 이야기가 없어요", "출발하면 지나가는 곳의 이야기를 들려드려요")
                else -> EmptyPanel(
                    state.upcoming?.let { "잠시 후 ${it.side.spoken} · ${it.placeName}" } ?: "지나갈 곳을 찾고 있어요",
                    state.upcoming?.let { "${formatSec(it.startInSec)} 뒤에 ${it.episodeTitle} 이야기를 시작해요" }
                        ?: "주행 중에는 글 대신 목소리로만 들려드려요",
                )
            }
        }
        SideRail(state, Modifier.width(380.dp).fillMaxHeight())
    }
}

@Composable
private fun DrivingPanel(card: DrivingCard, c: DriveController) {
    val elapsed = Duration.between(card.startedAt, Instant.now()).seconds.toInt().coerceAtLeast(0)
    val total = card.estimatedSec.coerceAtLeast(1)
    val toPass = card.secondsToPass
    val horizon = (toPass ?: total).coerceAtLeast(total).toFloat()
    Panel(Modifier.fillMaxSize(), padding = 32.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DirectionTile(card.side)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.caption.replace(" · ", ", "), style = Ui.bodyLg.copy(color = Ui.text2))
                Text(card.placeName, style = Ui.display, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(card.episodeTitle.take(28), ChipKind.Accent)
            Chip(card.category)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("이야기 ${formatClock(elapsed.coerceAtMost(total))} / ${formatClock(total)}", style = Ui.body.copy(color = Ui.text2))
                Grow()
                toPass?.let { Text("통과까지 ${formatClock(it)}", style = Ui.body.copy(color = Ui.text2)) }
            }
            StoryProgress(fill = elapsed / horizon, window = total / horizon, modifier = Modifier.fillMaxWidth())
            Row {
                Text("지금", style = Ui.caption.copy(color = Ui.text3))
                Grow()
                Text("지나가는 곳", style = Ui.caption.copy(color = Ui.text3))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("그만", c::stop, Modifier.weight(1f))
            BigButton("다시 듣기", c::replay, Modifier.weight(1f))
            BigButton("들렀다 가기", c::stopover, Modifier.weight(1f), kind = ButtonKind.Tonal)
        }
    }
}

@Composable
private fun QuizPanel(state: UiState, c: DriveController) {
    val q = state.quiz!!
    Panel(Modifier.fillMaxSize(), padding = 32.dp) {
        if (q.feedback != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(96.dp).background(Ui.positive))
                Text(q.feedback, style = Ui.title2, modifier = Modifier.padding(start = 24.dp))
            }
            return@Panel
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip("퀴즈", ChipKind.Solid)
            Text("방금 들은 ${q.placeName} 이야기에서", style = Ui.body.copy(color = Ui.text2))
        }
        Text(q.question, style = Ui.title1)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                if (q.listening) "말로 대답해요. 운전하는 사람은 대답 안 해도 돼요." else "문제를 읽고 있어요",
                style = Ui.bodyLg.copy(color = Ui.text2),
                modifier = Modifier.weight(1f),
            )
            q.secondsLeft?.let { Text("${it}초", style = Ui.title2.copy(color = Ui.accent)) }
            BigButton("퀴즈 건너뛰기", c::skipQuiz, kind = ButtonKind.Outline, height = 56.dp)
        }
    }
}

@Composable
private fun ParkedPanel(state: UiState, c: DriveController) {
    val p = state.parked!!
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight(), padding = 32.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(p.episodeTitle.take(30), ChipKind.Accent)
                Chip("${p.distance} 거리")
            }
            Text(p.placeName, style = Ui.title1)
            Text(
                p.text,
                style = Ui.bodyLg.copy(color = Ui.text2, lineHeight = 38.sp),
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
            Text("출처: ${p.sources}", style = Ui.caption.copy(color = Ui.text3), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyPanel(title: String, subtitle: String) {
    Panel(Modifier.fillMaxSize(), padding = 32.dp) {
        Spacer(Modifier.weight(1f))
        Text(title, style = Ui.title1)
        Text(subtitle, style = Ui.bodyLg.copy(color = Ui.text2))
        Spacer(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideRail(state: UiState, modifier: Modifier) {
    Row(modifier) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(Ui.line))
        Column(Modifier.padding(start = 24.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.nextAfter?.let {
                Section("이야기가 끝나면") { Text(it, style = Ui.title3) }
            }
            Section("다음 이야기") {
                val up = state.upcoming
                if (up == null) {
                    Text("아직 없어요", style = Ui.title3.copy(color = Ui.text3))
                } else {
                    Text(up.placeName, style = Ui.title2)
                    Text("${up.side.spoken}으로, 약 ${formatSec(up.startInSec)} 뒤", style = Ui.caption)
                }
            }
            Section("운전대 음성 버튼으로 말해도 돼요") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("“그만”", "“다시 들려줘”", "“들렀다 가자”", "“퀴즈는 됐어”").forEach { Chip(it, ChipKind.Line) }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("주행 중에는 글 대신 목소리로만 들려드려요", style = Ui.caption.copy(color = Ui.text3))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = Ui.h2)
        content()
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(Ui.line))
    }
}

@Composable
private fun DirectionTile(side: Side?) {
    Box(Modifier.size(96.dp).clip(Ui.rLg).background(Ui.surface2), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(48.dp)) {
            val w = size.width
            val s = w / 24f
            val stroke = 2.2f * s
            val left = side != Side.RIGHT
            fun x(v: Float) = if (left) v * s else (24f - v) * s
            drawLine(Ui.accent, Offset(x(19f), 12f * s), Offset(x(5f), 12f * s), stroke, StrokeCap.Round)
            drawLine(Ui.accent, Offset(x(12f), 19f * s), Offset(x(5f), 12f * s), stroke, StrokeCap.Round)
            drawLine(Ui.accent, Offset(x(12f), 5f * s), Offset(x(5f), 12f * s), stroke, StrokeCap.Round)
        }
    }
}

// ---------------- ③ 이번 길 ----------------

@Composable
private fun RouteTab(state: UiState) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("이번 길에서 들을 이야기 ${state.preview.size}곳", style = Ui.title1)
        Text(
            state.previewTitle.ifEmpty { "목적지를 정하거나 아래 '시연 주행'을 시작하면 이번 길에서 만날 곳을 보여 드려요" },
            style = Ui.body.copy(color = Ui.text2),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(state.preview) { row ->
                Row(
                    Modifier.fillMaxWidth().height(72.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text("${row.distanceKm}km", style = Ui.body.copy(color = if (row.review) Ui.positive else Ui.accent, fontWeight = FontWeight.Bold), modifier = Modifier.width(96.dp))
                    Text(row.placeName, style = Ui.title3, modifier = Modifier.width(360.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.side.spoken, style = Ui.body.copy(color = Ui.text2), modifier = Modifier.width(80.dp))
                    Text(row.what, style = Ui.body.copy(color = Ui.text2))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ui.line))
            }
        }
    }
}

// ---------------- ⑥ 도감 ----------------

@Composable
private fun CollectionTab(state: UiState) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Text("우리 가족 이야기 도감", style = Ui.title2)
            Stat("${state.visited.size}", "/ ${state.placeNames.size}곳", accent = true)
            Stat("${state.heardEpisodes}", "편 들음")
            Stat("${state.quizResults.count { it.correct == true }}", "/ ${state.quizResults.size} 퀴즈 정답")
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Panel(Modifier.weight(1.4f).fillMaxHeight(), padding = 20.dp) {
                Text("지나간 곳 도장", style = Ui.h2)
                LazyVerticalGrid(GridCells.Adaptive(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.placeNames.sortedByDescending { it.first in state.visited }) { (id, name) ->
                        val heard = id in state.visited
                        Box(
                            Modifier.height(64.dp).clip(Ui.rSm)
                                .background(if (heard) Ui.accentWeak else Color.Transparent)
                                .border(1.dp, if (heard) Ui.accent else Ui.lineStrong, Ui.rSm)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name,
                                color = if (heard) Ui.text1 else Ui.text3,
                                fontSize = 15.sp,
                                fontWeight = if (heard) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("시리즈 수집", style = Ui.h2)
                if (state.series.isEmpty()) Text("아직 모은 시리즈가 없어요", style = Ui.body.copy(color = Ui.text3))
                state.series.forEach { s ->
                    Row {
                        Text(s.name, style = Ui.body.copy(fontWeight = FontWeight.Bold))
                        Grow()
                        Text("${s.collected} / ${s.total}", style = Ui.body.copy(color = Ui.text2))
                    }
                    StoryProgress(fill = s.collected.toFloat() / s.total.coerceAtLeast(1), window = 0f, modifier = Modifier.fillMaxWidth(), height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, unit: String, accent: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, style = Ui.title2.copy(color = if (accent) Ui.accent else Ui.text1))
        Text(unit, style = Ui.body.copy(color = Ui.text2), modifier = Modifier.padding(bottom = 4.dp))
    }
}

// ---------------- ⑦ 설정 ----------------

@Composable
private fun SettingsTab(state: UiState, c: DriveController) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Text("누구와 타나요?", style = Ui.title3)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ListenerMode.entries.forEach { m ->
                    Option(listenerLabel(m), listenerDesc(m), state.settings.listenerMode == m, Modifier.weight(1f)) { c.setListenerMode(m) }
                }
            }
            Text("얼마나 자주 들려줄까요?", style = Ui.title3)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Frequency.entries.forEach { f ->
                    Option(freqLabel(f), "${f.minGap.toMinutes()}분 간격", state.settings.frequency == f, Modifier.weight(1f)) { c.setFrequency(f) }
                }
            }
            if (state.settings.listenerMode == ListenerMode.WITH_CHILD) {
                Text("'아이와 함께'는 검수된 이야기만 들려줘요. 지금 꾸러미: ${state.packSummary}", style = Ui.caption)
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Text("연결 상태", style = Ui.title3)
            state.platform.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(k, style = Ui.body.copy(color = Ui.text2), modifier = Modifier.width(140.dp))
                    Text(v, style = Ui.body)
                }
            }
            Text("이야기 꾸러미: ${state.packSummary}", style = Ui.body.copy(color = Ui.text2))
            Spacer(Modifier.weight(1f))
            BigButton("들은 기록 지우기", c::resetHistory, kind = ButtonKind.Outline, height = 56.dp)
        }
    }
}

@Composable
private fun Option(title: String, desc: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(104.dp).clip(Ui.rMd)
            .background(if (selected) Ui.accentWeak else Ui.surface2)
            .border(2.dp, if (selected) Ui.accent else Color.Transparent, Ui.rMd)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .border(if (selected) 7.dp else 2.dp, if (selected) Ui.accent else Ui.text3, CircleShape)
                    .background(if (selected) Color.White else Color.Transparent),
            )
            Text(title, style = Ui.bodyLg.copy(fontWeight = FontWeight.Bold))
        }
        Text(desc, style = Ui.caption)
    }
}

// ---------------- 시연 도구 ----------------

@Composable
private fun DemoBar(state: UiState, c: DriveController, expanded: Boolean, onToggle: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ui.line))
    Row(
        Modifier.fillMaxWidth().background(Ui.surface1).padding(horizontal = 32.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (expanded) "시연 주행 · ${state.demo.routeName} · ${state.demo.progressKm} / ${state.demo.totalKm}km" else "시연 도구",
            style = Ui.body.copy(color = Ui.text2),
            modifier = Modifier.clickable(onClick = onToggle),
        )
        if (!expanded) return@Row
        Grow()
        if (state.demo.running) BigButton("멈춤", c::pauseDemo, height = 56.dp)
        else BigButton("출발", c::startDemo, kind = ButtonKind.Primary, height = 56.dp)
        BigButton("다음 장소 앞으로", c::skipToNextPlace, height = 56.dp)
        BigButton(if (state.demo.parked) "출발 (D)" else "정차·충전 (P)", c::toggleDemoParked, kind = ButtonKind.Outline, height = 56.dp)
        if (state.quiz?.listening == true) {
            BigButton("정답 말하기", { c.answerQuiz(true) }, kind = ButtonKind.Tonal, height = 56.dp)
            BigButton("틀린 답", { c.answerQuiz(false) }, kind = ButtonKind.Outline, height = 56.dp)
        }
        BigButton("음성 버튼", c::listen, kind = ButtonKind.Outline, height = 56.dp)
    }
}

private fun listenerLabel(m: ListenerMode) = when (m) {
    ListenerMode.ADULTS -> "어른끼리"
    ListenerMode.WITH_TEEN -> "청소년과"
    ListenerMode.WITH_CHILD -> "아이와 함께"
}

private fun listenerDesc(m: ListenerMode) = when (m) {
    ListenerMode.ADULTS -> "모든 이야기, 퀴즈 없음"
    ListenerMode.WITH_TEEN -> "전설과 사료 비교까지, 퀴즈"
    ListenerMode.WITH_CHILD -> "쉬운 말, 검수된 이야기만, 퀴즈"
}

private fun freqLabel(f: Frequency) = when (f) {
    Frequency.OFTEN -> "자주"
    Frequency.NORMAL -> "보통"
    Frequency.RARE -> "가끔"
}

private fun formatClock(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

private fun formatSec(sec: Int) = if (sec >= 60) "${sec / 60}분 ${sec % 60}초" else "${sec}초"
