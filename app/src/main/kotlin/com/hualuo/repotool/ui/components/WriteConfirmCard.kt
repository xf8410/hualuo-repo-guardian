package com.hualuo.repotool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualuo.repotool.ui.state.WriteConfirmGate
import com.hualuo.repotool.ui.theme.Accent
import com.hualuo.repotool.ui.theme.CardBg
import com.hualuo.repotool.ui.theme.ErrRed
import com.hualuo.repotool.ui.theme.Hairline
import com.hualuo.repotool.ui.theme.Ink
import com.hualuo.repotool.ui.theme.Scrim
import com.hualuo.repotool.ui.theme.SubInk

/**
 * 写入确认卡（0.7.0 刀③）：模型提议改仓库时，这张卡摆出来等人点头。
 *
 * 红线对齐：卡上必须能把「要动什么」看全——目标仓/分支、文件路径、新建还是覆盖、
 * 正文多少字符、开头一段原文（给人核对，不是打码摘要）；**点头才写，拒绝/关掉什么都不写**。
 * 卡在壳最上层（压过设置层与弹层）：写仓库是全 App 最重的一个动作，不许被任何界面盖住。
 */
@Composable
fun WriteConfirmCard(gate: WriteConfirmGate) {
    val card = gate.pending ?: return
    val proposal = card.proposal
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .padding(18.dp),
        ) {
            Text("模型请求修改仓库", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(10.dp))

            Field("仓库", proposal.repo)
            Field("分支", proposal.branch)
            Field("文件", proposal.path)
            Field("动作", if (proposal.isNewFile) "新建文件" else "覆盖已有文件（带 sha 防硬盖）")
            Field("正文", "${proposal.contentChars} 字符")
            Field("提交说明", proposal.message)

            Spacer(Modifier.height(8.dp))
            Text("正文开头（核对用）：", fontSize = 12.sp, color = SubInk)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                    .background(Color(0xFFFAFBFC))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    proposal.contentPreview,
                    fontSize = 12.sp,
                    color = Ink,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF3F4F6))
                        .clickable { gate.reject(card.id) },
                    contentAlignment = Alignment.Center,
                ) { Text("不写", fontSize = 14.sp, color = Ink) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Accent)
                        .clickable { gate.approve(card.id) },
                    contentAlignment = Alignment.Center,
                ) { Text("确认写入", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "点「不写」或 5 分钟不处理，这次改动一律不发生；写坏了用仓库历史回滚。",
                fontSize = 11.5.sp,
                color = ErrRed.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(label, fontSize = 12.5.sp, color = SubInk, modifier = Modifier.width(64.dp))
        Text(
            value,
            fontSize = 12.5.sp,
            color = Ink,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}
