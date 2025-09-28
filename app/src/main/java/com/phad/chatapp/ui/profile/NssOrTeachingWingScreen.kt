package com.phad.chatapp.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import com.phad.chatapp.R

@Composable
fun NssOrTeachingWingScreen(
    teachingWing: Boolean,
    onNssClick: () -> Unit = {},
    onTeachingWingClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xff1e1716))
    ) {
        // Background and decorative images (reuse from your design)
        Image(
            painter = painterResource(id = R.drawable.vector271),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-40.87).dp, y = (-21).dp)
                .requiredWidth(510.dp)
                .requiredHeight(616.dp)
        )
        Image(
            painter = painterResource(id = R.drawable.vector272),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 56.98.dp, y = 13.92.dp)
                .requiredWidth(371.dp)
                .requiredHeight(504.dp)
                .clip(RoundedCornerShape(153.dp))
        )
        Image(
            painter = painterResource(id = R.drawable.thelogosmall),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xffffcc00)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 38.26.dp, y = 40.24.dp)
                .requiredWidth(72.dp)
                .requiredHeight(40.dp)
        )
        // Main content
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(100.dp))
            if (teachingWing) {
                Text(
                    text = "Select Your Role",
                    color = Color.White,
                    fontSize = 24.sp,
                    style = TextStyle(
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.25f), offset = Offset(0f, 4f), blurRadius = 4f)
                    ),
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                Button(
                    onClick = onNssClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xffffcc00)),
                    border = BorderStroke(3.dp, Color.White)
                ) {
                    Text("NSS", color = Color.Black, fontSize = 18.sp)
                }
                Button(
                    onClick = onTeachingWingClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xffffcc00)),
                    border = BorderStroke(3.dp, Color.White)
                ) {
                    Text("Teaching Wing", color = Color.Black, fontSize = 18.sp)
                }
            } else {
                Text(
                    text = "Select NSS",
                    color = Color.White,
                    fontSize = 24.sp,
                    style = TextStyle(
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.25f), offset = Offset(0f, 4f), blurRadius = 4f)
                    ),
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                Button(
                    onClick = onNssClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xffffcc00)),
                    border = BorderStroke(3.dp, Color.White)
                ) {
                    Text("NSS", color = Color.Black, fontSize = 18.sp)
                }
            }
        }
    }
}

@Preview(widthDp = 440, heightDp = 956)
@Composable
private fun NssOrTeachingWingScreenPreview_TeachingWing() {
    NssOrTeachingWingScreen(teachingWing = true)
}

@Preview(widthDp = 440, heightDp = 956)
@Composable
private fun NssOrTeachingWingScreenPreview_NssOnly() {
    NssOrTeachingWingScreen(teachingWing = false)
} 