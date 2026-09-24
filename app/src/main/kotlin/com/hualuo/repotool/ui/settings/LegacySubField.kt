package com.hualuo.repotool.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.components.SegRow
import com.hualuo.repotool.ui.components.SliderRow
import com.hualuo.repotool.ui.model.SubField
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.theme.*

@Composable
internal fun LegacySubField(
    state: AppUiState,
    field: SubField,
    inputs: SnapshotStateMap<String, String>,
    segs: SnapshotStateMap<String, Int>,
    sliders: SnapshotStateMap<String, Float>,
    radio: SnapshotStateMap<String, String>,
) {
    when (field) {
        is SubField.Row -> LegacyRow(state, field) {}
        is SubField.Seg -> Column(Modifier.padding(horizontal = 6.dp)) {
            Text(field.label, fontSize = 12.5.sp, color = SubInk)
            SegRow(field.options, segs[field.label] ?: field.sel) { segs[field.label] = it }
        }
        is SubField.Input -> LegacyRow(state, SubField.Row(field.label)) {
            val v = inputs[field.label] ?: ""
            if (v.isEmpty()) Text(field.placeholder, fontSize = 13.sp, color = SubInk)
            BasicTextField(
                value = v,
                onValueChange = { inputs[field.label] = it },
                textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                modifier = Modifier.weight(1f),
            )
        }
        is SubField.Slider -> Column(Modifier.padding(bottom = 9.dp)) {
            val v = sliders[field.label] ?: field.value.toFloat()
            SliderRow(field.label, field.min.toFloat(), field.max.toFloat(), v) { sliders[field.label] = it }
        }
        is SubField.Action -> LegacyRow(state, SubField.Row(field.title)) {
            Text(field.desc, fontSize = 12.5.sp, color = SubInk, modifier = Modifier.weight(1f))
        }
        is SubField.Head -> Column(Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
            Text(field.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(field.desc, fontSize = 12.5.sp, color = SubInk)
        }
        is SubField.BigInput -> BasicTextField(
            value = inputs[field.placeholder] ?: "",
            onValueChange = { inputs[field.placeholder] = it },
            textStyle = TextStyle(fontSize = 14.sp, color = Ink),
            modifier = Modifier.fillMaxWidth(),
        )
        is SubField.Radio -> Column(Modifier.fillMaxWidth()) {
            Text(field.label, fontSize = 13.sp, color = SubInk, modifier = Modifier.padding(6.dp))
            field.options.forEach { choice ->
                val selected = radio[field.label] == choice.id
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(16.dp)).background(CardBg)
                        .clickable { radio[field.label] = choice.id }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(18.dp).border(if (selected) 5.dp else 1.5.dp, if (selected) Accent else ChevGray, RoundedCornerShape(9.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(choice.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(choice.desc, fontSize = 12.5.sp, color = SubInk)
                    }
                    if (choice.needsKey) Text("需密钥", fontSize = 11.sp, color = SubInk)
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun LegacyRow(state: AppUiState, field: SubField.Row, value: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(16.dp)).background(CardBg).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(field.label, fontSize = 14.sp, color = Ink)
        Spacer(Modifier.weight(1f))
        if (field.value.isNotEmpty()) Text(field.value, fontSize = 12.sp, color = SubInk)
        value()
    }
}
