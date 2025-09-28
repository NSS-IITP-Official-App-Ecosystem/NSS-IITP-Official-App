import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ContentScale.Crop
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Composable
fun Home(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
                .requiredWidth(width = 440.dp)
                .requiredHeight(height = 956.dp)
                .background(color = Color(0xff0d0302))
        ) {
        Image(
            painter = painterResource(id = R.drawable.thelogosmall),
            contentDescription = "The Logo Small",
            colorFilter = ColorFilter.tint(Color(0xffffcc00)),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 38.13.dp,
                                    y = 40.24.dp)
                        .requiredWidth(width = 72.dp)
                        .requiredHeight(height = 40.dp))
        Image(
            painter = painterResource(id = R.drawable.fluentbot32filled),
            contentDescription = "fluent:bot-32-filled",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 369.dp,
                                    y = 45.67.dp)
                        .requiredSize(size = 29.dp))
        Image(
            painter = painterResource(id = R.drawable.mditickcircle),
            contentDescription = "mdi:tick-circle",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 326.dp,
                                    y = 45.67.dp)
                        .requiredWidth(width = 30.dp)
                        .requiredHeight(height = 29.dp))
        Image(
            painter = painterResource(id = R.drawable.rectangle9),
            contentDescription = "Rectangle 9",
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 0.dp,
                                    y = 110.66.dp)
                        .requiredWidth(width = 440.dp)
                        .requiredHeight(height = 845.dp))
        Text(
            lineHeight = Infinity.sp,
            text = buildAnnotatedString {
    withStyle(style = SpanStyle(
        color = Color.Black,
        fontSize = 32.451995849609375.sp,
        fontWeight = FontWeight.Bold)) {append("Good Morning\n")}
    withStyle(style = SpanStyle(
        color = Color.Black,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold)) {append("Shikhar...")}},
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 38.13.dp,
                                    y = 191.86.dp)
                        .requiredWidth(width = 219.dp)
                        .requiredHeight(height = 68.dp))
        Text(
            text = "Updates",
            color = Color.Black.copy(alpha = 0.72f),
            lineHeight = 3.5.em,
            style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 36.64.dp,
                                    y = 378.13.dp)
                        .requiredWidth(width = 219.dp)
                        .requiredHeight(height = 68.dp))
        Image(
            painter = painterResource(id = R.drawable.image2),
            contentDescription = "image 2",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 36.48.dp,
                                    y = 435.65.dp)
                        .requiredSize(size = 283.dp)
                        .clip(shape = RoundedCornerShape(22.dp)))
        Image(
            painter = painterResource(id = R.drawable.image),
            contentDescription = "image",
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 341.dp,
                                    y = 435.65.dp)
                        .requiredSize(size = 283.dp)
                        .clip(shape = RoundedCornerShape(22.dp)))
        Text(
            text = "Learn how Socio-Fix matters to you and society.",
            color = Color.Black.copy(alpha = 0.65f),
            lineHeight = 8.91.em,
            style = TextStyle(
                        fontSize = 14.587982177734375.sp),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 38.13.dp,
                                    y = 732.07.dp)
                        .requiredWidth(width = 225.dp))
        Text(
            text = "Let us pledge to follow the vision of the great makers of our constitution.",
            color = Color.Black.copy(alpha = 0.65f),
            lineHeight = 8.91.em,
            style = TextStyle(
                        fontSize = 14.587982177734375.sp),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 342.65.dp,
                                    y = 732.07.dp)
                        .requiredWidth(width = 258.dp))
        Image(
            painter = painterResource(id = R.drawable.iconparksolidrightc),
            contentDescription = "icon-park-solid:right-c",
            colorFilter = ColorFilter.tint(Color.Black),
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 276.63.dp,
                                    y = 739.07.dp)
                        .requiredSize(size = 24.dp))
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
        Icon(
            painter = painterResource(id = R.drawable.materialsymbolslighthomerounded),
            contentDescription = "material-symbols-light:home-rounded",
            modifier = Modifier
                        .align(alignment = Alignment.TopStart)
                        .offset(x = 115.38.dp,
                                    y = 861.05.dp))
        Icon(
            painter = painterResource(id = R.drawable.letsiconschatfill),
            contentDescription = "lets-icons:chat-fill",
            tint = Color.White.copy(alpha = 0.6f),
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
private fun HomePreview() {
    Home(Modifier)
 }