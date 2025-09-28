import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun TheLogoSmall(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.thelogosmall),
        contentDescription = "The Logo Small",
        colorFilter = ColorFilter.tint(Color(0xffffcc00)),
        modifier = modifier
                .requiredWidth(width = 72.dp)
                .requiredHeight(height = 40.dp))
 }

@Preview(widthDp = 72, heightDp = 40)
@Composable
private fun TheLogoSmallPreview() {
    TheLogoSmall(Modifier)
 }