package br.com.tecal.almoxarifado.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tecal.almoxarifado.R
import br.com.tecal.almoxarifado.ui.theme.TecalBlueAccent
import br.com.tecal.almoxarifado.ui.theme.TecalNavy
import br.com.tecal.almoxarifado.ui.theme.TecalNavyDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TecalBrandOrange = Color(0xFFF58220)
private val TecalDeepBg = Color(0xFF03101D)

@Composable
fun AnimatedSplashScreen(
    onSplashFinished: () -> Unit
) {
    val logoScale = remember { Animatable(0.58f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetDp = remember { Animatable(26f) }
    val shimmerProgress = remember { Animatable(-0.6f) }
    val loadProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "tecal_orbital")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(durationMillis = 480))
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.62f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            delay(280)
            shimmerProgress.animateTo(
                targetValue = 1.9f,
                animationSpec = tween(durationMillis = 920, easing = LinearOutSlowInEasing)
            )
        }
        launch {
            delay(260)
            launch { textAlpha.animateTo(1f, animationSpec = tween(520)) }
            launch { textOffsetDp.animateTo(0f, animationSpec = tween(560, easing = FastOutSlowInEasing)) }
        }
        launch {
            delay(180)
            loadProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1620, easing = FastOutSlowInEasing)
            )
        }

        delay(1980)
        onSplashFinished()
    }

    val statusLabel = when {
        loadProgress.value < 0.35f -> "Inicializando ambiente corporativo..."
        loadProgress.value < 0.75f -> "Ativando proteção Keystore & HMAC..."
        else -> "Pronto"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(TecalDeepBg, TecalNavyDark, TecalNavy)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Arcos orbitais animados ao redor do emblema central
        Canvas(
            modifier = Modifier
                .size(310.dp)
                .align(Alignment.Center)
                .offset(y = (-34).dp)
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        TecalBrandOrange.copy(alpha = 0.16f * glowPulse),
                        TecalBlueAccent.copy(alpha = 0.08f * glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Anel fino estático de engenharia
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius * 0.90f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Arco dinâmico laranja Tecal
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        TecalBrandOrange.copy(alpha = 0.85f),
                        Color.Transparent
                    )
                ),
                startAngle = orbitAngle,
                sweepAngle = 115f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.90f, center.y - radius * 0.90f),
                size = androidx.compose.ui.geometry.Size(radius * 1.80f, radius * 1.80f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Arco contra-rotativo azul corporativo
            drawArc(
                color = Color(0xFF38BDF8).copy(alpha = 0.65f),
                startAngle = -orbitAngle * 1.25f + 160f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.81f, center.y - radius * 0.81f),
                size = androidx.compose.ui.geometry.Size(radius * 1.62f, radius * 1.62f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Cartão central com a logo oficial Tecal + efeito shimmer diagonal
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 22.dp,
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.65f)),
                modifier = Modifier
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
                    .widthIn(max = 264.dp)
                    .fillMaxWidth(0.76f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_tecal_full),
                        contentDescription = "Tecal Engenharia - Soluções em Tecnologia",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(106.dp)
                    )

                    // Feixe de luz diagonal passando sobre a marca
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val bandWidth = size.width * 0.35f
                        val startX = (size.width + bandWidth) * shimmerProgress.value - bandWidth
                        if (shimmerProgress.value in -0.5f..1.85f) {
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.42f),
                                        Color.Transparent
                                    ),
                                    start = Offset(startX, 0f),
                                    end = Offset(startX + bandWidth, size.height)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Títulos corporativos com slide-up e fade-in coordenados
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .offset { IntOffset(0, textOffsetDp.value.dp.roundToPx()) }
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = TecalBrandOrange.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, TecalBrandOrange.copy(alpha = 0.48f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(TecalBrandOrange)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CONTROLE DE RETIRADA DO ESTOQUE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp,
                            color = Color(0xFFFED7AA)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Expedição Digital • TOTVS RM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Movimento 1.1.10 • Coligada 2",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF93C5FD),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Barra de progresso suave + status de inicialização
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth(0.75f)
                    .alpha(textAlpha.value)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(loadProgress.value.coerceIn(0.05f, 1f))
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(TecalBrandOrange, Color(0xFF38BDF8))
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

