import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun Vector273(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.vector273),
        contentDescription = "Vector 273",
        modifier = modifier
                .requiredWidth(width = 67.dp)
                .requiredHeight(height = 73.dp)
                .clip(shape = RoundedCornerShape(107.dp)))
 }

@Preview(widthDp = 67, heightDp = 73)
@Composable
private fun Vector273Preview() {
    Vector273(Modifier)
 }