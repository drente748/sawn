package com.sawn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sawn.app.R
import com.sawn.app.feature.JournalSnapshot
import java.time.LocalDate

/**
 * مكوّنات مشتركة بين الرئيسية والإنجازات. الهدف لغةٌ بصرية واحدة: عنوان قسمٍ هادئ،
 * رسم أسبوعي متكرر، وبطاقة رقمٍ ذات تباينٍ مضمون.
 */

@Composable
fun SectionHeader(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 11.dp),
            )
        }
    }
}

/** رسم أعمدة لآخر سبعة أيام — يستخدمه الرئيسية والإنجازات معاً. */
@Composable
fun ProtectionWeekChart(snapshot: JournalSnapshot, modifier: Modifier = Modifier) {
    val letters = listOf(
        R.string.day_letter_mon, R.string.day_letter_tue, R.string.day_letter_wed,
        R.string.day_letter_thu, R.string.day_letter_fri, R.string.day_letter_sat,
        R.string.day_letter_sun,
    )
    val today = LocalDate.now()
    val peak = maxOf(1, snapshot.peak)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        snapshot.last7.forEachIndexed { index, count ->
            val day = today.minusDays((6 - index).toLong())
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((8 + 36 * count / peak).dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            if (count > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
                Text(
                    stringResource(letters[day.dayOfWeek.value - 1]),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** بطاقة رقم مختصرة. [highlight] يجعلها بلون العلامة، ويبقى النص مقروءاً دائماً. */
@Composable
fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
