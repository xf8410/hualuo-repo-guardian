package com.hualuo.repotool.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.Bg
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.Hairline
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.SubInk

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = Accent,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 7.dp),
    )
}

@Composable
internal fun SettingInput(
    label: String,
    value: String,
    placeholder: String,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(label, fontSize = 12.sp, color = SubInk)
        Spacer(Modifier.padding(top = 6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Bg)
                .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = SubInk)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SecretSettingInput(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    SettingInput(label, value, placeholder, secret = true, onChange = onChange)
}

@Composable
internal fun ActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
internal fun CustomModelProviderRow(
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        options.forEach { (id, name) ->
            val selected = id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0xFFF0F5FF) else CardBg)
                    .border(1.dp, if (selected) Accent else Hairline, RoundedCornerShape(12.dp))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                Text(if (selected) "用这家" else "未选", fontSize = 11.sp, color = if (selected) Accent else SubInk)
            }
        }
    }
}
