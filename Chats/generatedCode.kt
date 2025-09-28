import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Composable
fun Chats(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
                .requiredWidth(width = 440.dp)
                .requiredHeight(height = 956.dp)
                .background(color = Color(0xff0d0302))
        ) {
        Text(
            text = "Messages",
            color = Color.White,
            lineHeight = 4.3.em,
            style = TextStyle(
                        fontSize = 26.077707290649414.sp,
                        fontWeight = FontWeight.Bold),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 36.64.dp,
                                    y = 33.15.dp)
                        .requiredWidth(width = 176.dp)
                        .requiredHeight(height = 55.dp)
                        .wrapContentHeight(align = Alignment.CenterVertically))
        Image(
            painter = painterResource(id = R.drawable.materialsymbolssearchrounded),
            contentDescription = "material-symbols:search-rounded",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 329.dp,
                                    y = 46.dp)
                        .requiredSize(size = 31.dp))
        Image(
            painter = painterResource(id = R.drawable.weuisettingfilled),
            contentDescription = "weui:setting-filled",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 370.dp,
                                    y = 46.dp)
                        .requiredSize(size = 29.dp))
        Box(
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 36.64.dp,
                                    y = 98.43.dp)
                        .requiredSize(size = 72.dp)
            ) {
            Box(
                modifier = Modifier
                                .requiredSize(size = 72.dp)
                                .clip(shape = CircleShape)
                                .border(border = BorderStroke(2.dp, Color(0xffffcc00)),
                                                shape = CircleShape))
            Image(
                painter = painterResource(id = R.drawable.image),
                contentDescription = "image",
                modifier = Modifier
                                .align(alignment = Alignment.TopStart)
                                .offset(x = 3.16.dp,
                                                y = 3.16.dp)
                                .requiredSize(size = 66.dp)
                                .clip(shape = CircleShape))
            }
        Box(
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 131.7.dp,
                                    y = 98.43.dp)
                        .requiredSize(size = 72.dp)
            ) {
            Box(
                modifier = Modifier
                                .requiredSize(size = 72.dp)
                                .clip(shape = CircleShape)
                                .border(border = BorderStroke(2.dp, Color(0xffffcc00)),
                                                shape = CircleShape))
            Image(
                painter = painterResource(id = R.drawable.image),
                contentDescription = "image",
                modifier = Modifier
                                .align(alignment = Alignment.TopStart)
                                .offset(x = 3.16.dp,
                                                y = 3.16.dp)
                                .requiredSize(size = 66.dp)
                                .clip(shape = CircleShape))
            }
        Box(
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 226.75.dp,
                                    y = 98.43.dp)
                        .requiredSize(size = 72.dp)
            ) {
            Box(
                modifier = Modifier
                                .requiredSize(size = 72.dp)
                                .clip(shape = CircleShape)
                                .border(border = BorderStroke(2.dp, Color(0xffffcc00)),
                                                shape = CircleShape))
            Image(
                painter = painterResource(id = R.drawable.image),
                contentDescription = "image",
                modifier = Modifier
                                .align(alignment = Alignment.TopStart)
                                .offset(x = 3.16.dp,
                                                y = 3.16.dp)
                                .requiredSize(size = 66.dp)
                                .clip(shape = CircleShape))
            }
        Box(
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 321.81.dp,
                                    y = 98.43.dp)
                        .requiredSize(size = 72.dp)
            ) {
            Box(
                modifier = Modifier
                                .requiredSize(size = 72.dp)
                                .clip(shape = CircleShape)
                                .border(border = BorderStroke(2.dp, Color(0xffffcc00)),
                                                shape = CircleShape))
            Image(
                painter = painterResource(id = R.drawable.image),
                contentDescription = "image",
                modifier = Modifier
                                .align(alignment = Alignment.TopStart)
                                .offset(x = 3.16.dp,
                                                y = 3.16.dp)
                                .requiredSize(size = 66.dp)
                                .clip(shape = CircleShape))
            }
        Box(
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 416.86.dp,
                                    y = 98.43.dp)
                        .requiredSize(size = 72.dp)
            ) {
            Box(
                modifier = Modifier
                                .requiredSize(size = 72.dp)
                                .clip(shape = CircleShape)
                                .border(border = BorderStroke(2.dp, Color(0xffffcc00)),
                                                shape = CircleShape))
            Image(
                painter = painterResource(id = R.drawable.image),
                contentDescription = "image",
                modifier = Modifier
                                .align(alignment = Alignment.TopStart)
                                .offset(x = 3.16.dp,
                                                y = 3.16.dp)
                                .requiredSize(size = 66.dp)
                                .clip(shape = CircleShape))
            }
        Text(
            text = "Aditya",
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 12.42.em,
            style = TextStyle(
                        fontSize = 10.465526580810547.sp,
                        fontWeight = FontWeight.Bold),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 51.16.dp,
                                    y = 181.35.dp)
                        .requiredWidth(width = 43.dp))
        Text(
            text = "Pranav",
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 12.42.em,
            style = TextStyle(
                        fontSize = 10.465526580810547.sp,
                        fontWeight = FontWeight.Bold),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 146.22.dp,
                                    y = 181.35.dp)
                        .requiredWidth(width = 43.dp))
        Text(
            text = "Aman",
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 12.42.em,
            style = TextStyle(
                        fontSize = 10.465526580810547.sp,
                        fontWeight = FontWeight.Bold),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 241.27.dp,
                                    y = 181.35.dp)
                        .requiredWidth(width = 43.dp))
        Text(
            text = "Kavya",
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 12.42.em,
            style = TextStyle(
                        fontSize = 10.465526580810547.sp,
                        fontWeight = FontWeight.Bold),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 338.34.dp,
                                    y = 181.35.dp)
                        .requiredWidth(width = 43.dp))
        Text(
            text = "Aman",
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 12.42.em,
            style = TextStyle(
                        fontSize = 10.465526580810547.sp,
                        fontWeight = FontWeight.Bold),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 426.62.dp,
                                    y = 181.35.dp)
                        .requiredWidth(width = 43.dp))
        Image(
            painter = painterResource(id = R.drawable.rectangle9),
            contentDescription = "Rectangle 9",
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 0.dp,
                                    y = 233.01.dp)
                        .requiredWidth(width = 440.dp)
                        .requiredHeight(height = 723.dp))
        Text(
            text = "Communities",
            color = Color.Black.copy(alpha = 0.57f),
            lineHeight = 8.12.em,
            style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 26.59.dp,
                                    y = 259.dp)
                        .requiredWidth(width = 112.dp))
        Image(
            painter = painterResource(id = R.drawable.image),
            contentDescription = "image",
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 26.59.dp,
                                    y = 295.01.dp)
                        .requiredSize(size = 66.dp)
                        .clip(shape = CircleShape))
        Text(
            text = "Announcements",
            color = Color.Black.copy(alpha = 0.91f),
            lineHeight = 8.12.em,
            style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 111.39.dp,
                                    y = 312.47.dp)
                        .requiredWidth(width = 148.dp))
        Text(
            text = "Important Update about the meet today. We will...",
            color = Color.Black.copy(alpha = 0.91f),
            lineHeight = 10.83.em,
            style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 111.39.dp,
                                    y = 330.58.dp)
                        .requiredWidth(width = 279.dp))
        Image(
            painter = painterResource(id = R.drawable.image),
            contentDescription = "image",
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 26.59.dp,
                                    y = 382.47.dp)
                        .requiredSize(size = 66.dp)
                        .clip(shape = CircleShape))
        Text(
            text = "TUT-12",
            color = Color.Black.copy(alpha = 0.91f),
            lineHeight = 8.12.em,
            style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 111.39.dp,
                                    y = 399.93.dp)
                        .requiredWidth(width = 148.dp))
        Text(
            text = "For your next class, Everyone prepare the ppts.",
            color = Color.Black.copy(alpha = 0.91f),
            lineHeight = 10.83.em,
            style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 111.39.dp,
                                    y = 418.04.dp)
                        .requiredWidth(width = 279.dp))
        Image(
            painter = painterResource(id = R.drawable.iconparksolidrightc),
            contentDescription = "icon-park-solid:right-c",
            colorFilter = ColorFilter.tint(Color.Black),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 609.15.dp,
                                    y = 739.07.dp)
                        .requiredSize(size = 24.dp))
        Box(
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 68.dp,
                                    y = 839.17.dp)
                        .requiredWidth(width = 304.dp)
                        .requiredHeight(height = 68.dp)
                        .clip(shape = RoundedCornerShape(54.dp))
                        .background(color = Color(0xff0d0302)))
        Box(
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 169.49.dp,
                                    y = 853.42.dp)
                        .requiredSize(size = 39.dp)
                        .clip(shape = CircleShape)
                        .background(color = Color(0xff363636)))
        Icon(
            painter = painterResource(id = R.drawable.materialsymbolslighthomerounded),
            contentDescription = "material-symbols-light:home-rounded",
            tint = Color(0xffafafaf),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 115.38.dp,
                                    y = 861.05.dp))
        Icon(
            painter = painterResource(id = R.drawable.letsiconschatfill),
            contentDescription = "lets-icons:chat-fill",
            tint = Color.White,
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 177.13.dp,
                                    y = 861.05.dp))
        Icon(
            painter = painterResource(id = R.drawable.iconparksolidschedule),
            contentDescription = "icon-park-solid:schedule",
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 238.88.dp,
                                    y = 861.05.dp))
        Icon(
            painter = painterResource(id = R.drawable.iconamoonprofilefill),
            contentDescription = "iconamoon:profile-fill",
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 300.63.dp,
                                    y = 861.05.dp))
        }
 }

@Preview(widthDp = 440, heightDp = 956)
@Composable
private fun ChatsPreview() {
    Chats(Modifier)
 }